package me.rerere.rikkahub.companion

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 闹钟兜底：前台服务活着时它每分钟把闹钟往后推，所以闹钟只在服务/进程被系统杀掉后才会响。
 * 响了以后重排下一次、尽量把前台服务拉回来、取一次消息。用不需要权限的 setAndAllowWhileIdle，
 * Doze 里系统本来就限制约 9 分钟一次，精确闹钟权限换不来更多。
 */
object CompanionAlarm {
    private const val INTERVAL_MS = 5 * 60_000L

    private fun pending(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 3412, Intent(c, CompanionAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(c: Context, delayMs: Long = INTERVAL_MS) {
        val app = c.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pending(app))
        }
    }

    fun cancel(c: Context) {
        val app = c.applicationContext
        runCatching { app.getSystemService(AlarmManager::class.java)?.cancel(pending(app)) }
    }
}

class CompanionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (!CompanionSync.enabled(app) && !CompanionSync.needsDisable(app)) return
        CompanionAlarm.schedule(app)
        if (CompanionSync.enabled(app) && !CompanionReceiverService.running) {
            runCatching { ContextCompat.startForegroundService(app, Intent(app, CompanionReceiverService::class.java)) }
        }
        val pendingResult = goAsync()
        CompanionSync.pollWithin(app, 9_000L) { pendingResult.finish() }
    }
}

/** 开机、app 更新后：闹钟会被清掉，重新排好并拉起接收服务。 */
class CompanionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        CompanionSync.ensureRunning(context)
    }
}
