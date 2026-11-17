package me.rerere.rikkahub.companion

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.rerere.rikkahub.R
import me.rerere.rikkahub.conversationIntent
import me.rerere.rikkahub.utils.NotificationUtil
import kotlin.uuid.Uuid

/** 家里主动消息的通知：小图标是猫头线条剪影，大图标是她画的猫头原图，点开进那个会话。 */
object HomeNotify {
    const val CHANNEL = "home_message"
    const val CHANNEL_LINK = "home_link"
    private const val MESSAGE_ID = 3511

    fun ensureChannels(c: Context) {
        val nm = NotificationManagerCompat.from(c)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("阿辞的消息").setVibrationEnabled(true).build()
        )
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_LINK, NotificationManagerCompat.IMPORTANCE_MIN)
                .setName("后台连接（可以在系统里隐藏）").setVibrationEnabled(false).setShowBadge(false).build()
        )
    }

    @SuppressLint("MissingPermission")
    fun message(c: Context, conversationId: Uuid, name: String, text: String) {
        if (!NotificationUtil.hasNotificationPermission(c)) return
        ensureChannels(c)
        val short = if (text.length > 300) text.take(300) + "…" else text
        val open = PendingIntent.getActivity(
            c, MESSAGE_ID, conversationIntent(c, conversationId.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cat)
            .setLargeIcon(cat(c))
            .setColor(0xFFD97757.toInt())
            .setContentTitle(name)
            .setContentText(short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(short))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(c).notify(MESSAGE_ID, n)
    }

    /** 大图标：小葵最早画的猫头 */
    private fun cat(c: Context): Bitmap? = runCatching { BitmapFactory.decodeResource(c.resources, R.drawable.cat_icon) }.getOrNull()
}
