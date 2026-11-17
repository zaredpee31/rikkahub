package me.rerere.rikkahub.companion

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.rerere.rikkahub.R
import me.rerere.rikkahub.conversationIntent
import me.rerere.rikkahub.utils.NotificationUtil
import kotlin.uuid.Uuid

/** 家里主动消息的通知：小图标是鼠鼠白剪影，大图标是彩色鼠鼠，点开进那个会话。 */
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
            .setSmallIcon(R.drawable.ic_stat_clawd)
            .setLargeIcon(crab())
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

    /** 彩色鼠鼠：奶白圆底上一只橘色像素小螃蟹，16 格画布，每格 8px。 */
    private fun crab(): Bitmap {
        val cell = 8f
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFBF3EC.toInt()
        cv.drawCircle(64f, 64f, 64f, p)
        fun box(x: Int, y: Int, w: Int, h: Int, color: Long) {
            p.color = color.toInt()
            cv.drawRect(x * cell, y * cell, (x + w) * cell, (y + h) * cell, p)
        }
        val orange = 0xFFD97757
        box(3, 4, 10, 6, orange)   // 身子
        box(1, 6, 2, 2, orange)    // 左钳
        box(13, 6, 2, 2, orange)   // 右钳
        box(4, 10, 1, 2, orange)   // 四条腿
        box(6, 10, 1, 2, orange)
        box(9, 10, 1, 2, orange)
        box(11, 10, 1, 2, orange)
        box(5, 6, 1, 2, 0xFF2B2B2B) // 眼睛
        box(10, 6, 1, 2, 0xFF2B2B2B)
        return bmp
    }
}
