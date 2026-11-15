package me.rerere.rikkahub.ui.pages.chat.snow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 普通会话页的 snow.log 白卡片风格，只给 [me.rerere.rikkahub.ui.pages.chat.ChatPage] 用。
 * 消息页、设置抽屉都不走这里。颜色写死成单子里的值，不跟随主题色，这样和冷灰底的消息页一眼能分开。
 */

internal val SnowBackground = Color(0xFFFFFFFF)
internal val SnowHairline = Color(0xFFECECEC)
internal val SnowGray = Color(0xFFF5F5F5)
internal val SnowGraySoft = Color(0xFFF8F8F8)
internal val SnowInk = Color(0xFF4A4A4A)
internal val SnowInkFaint = Color(0xFFB5B5B5)
internal val SnowInkSoft = Color(0xFF8A8A8A)
internal val SnowTrack = Color(0xFFE6E6E6)

internal val SnowMono = FontFamily.Monospace

/** 等宽小标签，12sp 以下不放大，单子要的是固定尺寸的小字。 */
@Composable
internal fun SnowLabel(text: String, color: Color = SnowInkSoft, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = SnowMono,
        fontSize = 9.sp,
        letterSpacing = 1.2.sp,
        color = color,
        modifier = modifier,
    )
}

/** 数据格里的大号细体数字。 */
@Composable
internal fun SnowFigure(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraLight,
        color = SnowInk,
        maxLines = 1,
        modifier = modifier,
    )
}

/** 圆点虚线，横向铺满剩余宽度。 */
@Composable
internal fun SnowDottedLine(modifier: Modifier = Modifier, color: Color = SnowHairline) {
    Canvas(modifier = modifier.height(1.dp).fillMaxWidth()) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
            cap = StrokeCap.Round,
        )
    }
}

/** 竖向圆点虚线，隔开数据格。 */
@Composable
internal fun SnowDottedDivider(modifier: Modifier = Modifier, color: Color = SnowHairline) {
    Canvas(modifier = modifier.width(1.dp).fillMaxHeight()) {
        drawLine(
            color = color,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 六角雪花，静态画的，不转动。
 * HugeIcons 里没有雪花，而单子只允许雪花当徽章用，所以这里用 Canvas 画一枚。
 */
@Composable
internal fun SnowFlakeIcon(modifier: Modifier = Modifier, color: Color = SnowInk, strokeWidth: Dp = 1.dp) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        repeat(6) { index ->
            val angle = Math.toRadians((index * 60).toDouble())
            val tip = Offset(
                x = center.x + (kotlin.math.cos(angle) * radius).toFloat(),
                y = center.y + (kotlin.math.sin(angle) * radius).toFloat(),
            )
            drawLine(color, center, tip, strokeWidth = stroke.width, cap = StrokeCap.Round)
            // 每根臂中间分出两根短枝
            val branch = Offset(
                x = center.x + (kotlin.math.cos(angle) * radius * 0.62f).toFloat(),
                y = center.y + (kotlin.math.sin(angle) * radius * 0.62f).toFloat(),
            )
            val branchLength = radius * 0.28f
            listOf(30.0, -30.0).forEach { offset ->
                val branchAngle = Math.toRadians(index * 60 + offset)
                drawLine(
                    color = color,
                    start = branch,
                    end = Offset(
                        x = branch.x + (kotlin.math.cos(branchAngle) * branchLength).toFloat(),
                        y = branch.y + (kotlin.math.sin(branchAngle) * branchLength).toFloat(),
                    ),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** 细进度条，[fraction] 超出 0..1 会被截断。 */
@Composable
internal fun SnowBar(fraction: Float, modifier: Modifier = Modifier) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(SnowTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(2.dp)
                .background(SnowInk),
        )
    }
}

/** 淡色线条图标按钮，不带底。 */
@Composable
internal fun SnowIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = SnowInkSoft,
    size: Dp = 16.dp,
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size))
    }
}

/** 标签在上、大号数字在下的一格数据。 */
@Composable
internal fun SnowStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    footer: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SnowLabel(label)
        SnowFigure(value)
        footer?.invoke()
    }
}

/** 顶行：等宽小字 + 点线铺满。 */
@Composable
internal fun SnowMetaLine(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SnowLabel(text, color = SnowInkFaint)
        SnowDottedLine(modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 浅灰不对称圆角块，左上角收尖，给思考折叠和用户日期块用。 */
internal fun snowCornerShape(topStart: Int, topEnd: Int, bottomEnd: Int, bottomStart: Int) =
    RoundedCornerShape(
        topStart = topStart.dp,
        topEnd = topEnd.dp,
        bottomEnd = bottomEnd.dp,
        bottomStart = bottomStart.dp,
    )

/** 卡片之间那条 0.5dp 的细线。 */
@Composable
internal fun SnowHairlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(SnowHairline),
    )
}

/** 把毫秒格式化成「12.4s」或「1m 05s」，拿不到时长时调用方自己决定隐藏。 */
internal fun formatSnowDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val fraction = (millis % 1000) / 100
    return if (minutes > 0) {
        "%dm %02ds".format(minutes, seconds)
    } else {
        "%d.%ds".format(seconds, fraction)
    }
}

/** 两个时间点之间的间隔，按「3m」「2h」「1d」这种短格式。 */
internal fun formatSnowGap(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        else -> "${minutes / (60 * 24)}d"
    }
}
