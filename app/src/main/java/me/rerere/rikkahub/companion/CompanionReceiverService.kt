package me.rerere.rikkahub.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import java.util.concurrent.TimeUnit

/** Opt-in persistent receiver. The model heartbeat lives on the server, not in every chat. */
class CompanionReceiverService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!CompanionSync.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("companion_receiver", "后台消息连接", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 3410, Intent(this, RouteActivity::class.java).putExtra("companionMessages", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "companion_receiver")
            .setSmallIcon(R.drawable.ic_stat_rikkahub).setContentTitle("阿辞的消息已开启")
            .setContentText("保持接收；可在消息设置中关闭").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) ServiceCompat.startForeground(this, 3410, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(3410, n)
        } catch (_: Exception) { stopSelf(); return START_NOT_STICKY }
        if (!started) {
            started = true
            scheduleFallback(this)
            scope.launch {
                while (isActive && CompanionSync.enabled(this@CompanionReceiverService)) {
                    try { CompanionSync.sync(this@CompanionReceiverService)
                    } catch (e: CancellationException) { throw e
                    } catch (_: Exception) { /* status recorded without secrets by sync */ }
                    delay(60_000)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        fun scheduleFallback(c: Context) {
            val work = PeriodicWorkRequestBuilder<CompanionSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("companion_sync", ExistingPeriodicWorkPolicy.KEEP, work)
        }
    }
}

class CompanionSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!CompanionSync.enabled(applicationContext) && !CompanionSync.needsDisable(applicationContext)) return Result.success()
        return try { CompanionSync.sync(applicationContext); Result.success()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { Result.retry() }
    }
}
