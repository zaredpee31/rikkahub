package me.rerere.rikkahub.companion

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity

/** 常驻前台服务：每分钟找服务器取一次阿辞的消息。通知渠道是最低级别，可以在系统里隐藏。 */
@SuppressLint("MissingPermission")
class HomeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        HomeNotify.ensureChannels(this)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, NOTE_ID, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTE_ID, note())
            }
        } catch (_: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        running = true
        if (!started) {
            started = true
            scope.launch {
                var shown = HomeLink.status
                while (isActive) {
                    HomeAlarm.schedule(this@HomeService)
                    HomeLink.tick(this@HomeService)
                    val now = HomeLink.status.substringBefore(" · ")
                    if (now != shown) {
                        shown = now
                        runCatching { NotificationManagerCompat.from(this@HomeService).notify(NOTE_ID, note()) }
                    }
                    delay(60_000)
                }
            }
        }
        return START_STICKY
    }

    private fun note() = NotificationCompat.Builder(this, HomeNotify.CHANNEL_LINK)
        .setSmallIcon(R.drawable.ic_stat_clawd)
        .setContentTitle("阿辞在家")
        .setContentText(HomeLink.status.substringBefore(" · "))
        .setContentIntent(
            PendingIntent.getActivity(
                this, NOTE_ID, Intent(this, RouteActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()

    override fun onDestroy() {
        running = false
        started = false
        scope.cancel()
        HomeAlarm.schedule(this, 60_000L)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        HomeAlarm.schedule(this, 60_000L)
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val NOTE_ID = 3510
        @Volatile var running = false

        fun start(c: Context) {
            if (running) return
            runCatching { ContextCompat.startForegroundService(c, Intent(c, HomeService::class.java)) }
        }
    }
}

/** 兜底闹钟：服务活着时每分钟往后推，所以只在服务被杀后才会响，响了拉服务、取一次消息。 */
object HomeAlarm {
    private const val INTERVAL_MS = 5 * 60_000L

    private fun pending(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 3512, Intent(c, HomeAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(c: Context, delayMs: Long = INTERVAL_MS) {
        val app = c.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pending(app))
        }
    }
}

class HomeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        HomeAlarm.schedule(app)
        HomeService.start(app)
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { withTimeoutOrNull(9_000L) { HomeLink.tick(app) } } finally { result.finish() }
        }
    }
}

/** 开机、app 更新后闹钟会被清掉，重新拉起。 */
class HomeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        HomeLink.start(context.applicationContext)
    }
}
