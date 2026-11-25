package me.rerere.rikkahub.companion

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.UIAvatar

// MUSI 皮肤（照《M U S I》粉色 by 蹲）：只在皮肤 layout 写 "musi" / avatarFrame 写 "polaroid" 时用到，冷淡风不经过这里
private val MusiRim = Color(0xFF8E98A0)
private val MusiPinkRim = Color(0xFFAC96A2)
private val MusiInk = Color(0xFF32383C)
private val MusiSub = Color(0xFF8E98A0)
private val MusiLabel = Color(0xFF5A646C)

/** 灰白 / 粉白圆钮：底色 + 上半截白色高光 + 细描边。给了 iconUrl 就直接用图。 */
@Composable
fun MusiRoundButton(
    label: String,
    pink: Boolean,
    size: Float = 30f,
    iconUrl: String? = null,
    onClick: (() -> Unit)? = null,
) {
    if (iconUrl != null) {
        SkinIconImage(iconUrl, size, label, onClick = onClick ?: {})
        return
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size.dp)
            .shadow(1.5.dp, CircleShape, clip = false)
            .background(if (pink) Color(0xFFFAF0F5) else Color(0xFFF0F0F0), CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))), CircleShape)
            .border(1.dp, if (pink) MusiPinkRim else MusiRim, CircleShape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = (size * 0.42f).sp, fontWeight = FontWeight.Bold, color = Color(0xFF463A40))
    }
}

/** 一根播放进度条：左右两段小字，中间细轨道 + 圆点 */
@Composable
fun MusiProgress(left: String, right: String, modifier: Modifier = Modifier, at: Float = 0.32f) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(left, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MusiLabel, maxLines = 1)
        Canvas(modifier = Modifier.weight(1f).height(10.dp)) {
            val h = 5.dp.toPx()
            val y = (size.height - h) / 2
            drawRoundRect(color = Color.White, topLeft = Offset(0f, y), size = Size(size.width, h), cornerRadius = CornerRadius(h / 2))
            drawRoundRect(color = MusiRim, topLeft = Offset(0f, y), size = Size(size.width, h), cornerRadius = CornerRadius(h / 2), style = Stroke(width = 0.8.dp.toPx()))
            val c = Offset(size.width * at, size.height / 2)
            drawCircle(color = Color.White, radius = 4.5.dp.toPx(), center = c)
            drawCircle(color = MusiRim, radius = 4.5.dp.toPx(), center = c, style = Stroke(width = 1.dp.toPx()))
            drawCircle(color = MusiRim, radius = 1.6.dp.toPx(), center = c)
        }
        Text(right, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MusiLabel, maxLines = 1)
    }
}

/** 头像外面套一层白色拍立得框（底边宽一点），歪一点 */
fun Modifier.musiFrame(tilt: Float = 4f): Modifier = this
    .rotate(tilt)
    .shadow(2.dp, RoundedCornerShape(3.dp), clip = false)
    .background(Color.White, RoundedCornerShape(3.dp))
    .border(1.dp, MusiRim, RoundedCornerShape(3.dp))
    .padding(start = 3.dp, top = 3.dp, end = 3.dp, bottom = 8.dp)

private val MusiPinkFill = Color(0xFFFAF0F5)

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

/** 在原 CSS 那段 svg 的坐标系（viewBox）里画 */
private inline fun DrawScope.inViewBox(vbW: Float, vbH: Float, crossinline block: DrawScope.() -> Unit) {
    withTransform({ scale(size.width / vbW, size.height / vbH, pivot = Offset.Zero) }) { block() }
}

private val DiscGray by lazy { listOf(svgPath("M8 8 L3.2 3.2 A7.2 7.2 0 0 1 8 0.8 Z"), svgPath("M8 8 L12.8 12.8 A7.2 7.2 0 0 1 8 15.2 Z")) }
private val DiscPink by lazy { listOf(svgPath("M8 8 L12.8 3.2 A7.2 7.2 0 0 1 15.2 8 Z"), svgPath("M8 8 L3.2 12.8 A7.2 7.2 0 0 1 0.8 8 Z")) }

/** 拍立得右下角的小光盘 + 号（.header-avatar-container::after） */
@Composable
fun MusiDisc(modifier: Modifier) {
    Canvas(modifier) {
        inViewBox(16f, 16f) {
            val c = Offset(8f, 8f)
            drawCircle(Color.White, 7.2f, c)
            DiscGray.forEach { drawPath(it, Color(0xFFE8ECEF)) }
            DiscPink.forEach { drawPath(it, MusiPinkFill) }
            drawCircle(Color(0xFF78848C), 7.2f, c, style = Stroke(0.95f))
            drawLine(Color(0xFF465058), Offset(8f, 4.6f), Offset(8f, 11.4f), 1.35f, StrokeCap.Round)
            drawLine(Color(0xFF465058), Offset(4.6f, 8f), Offset(11.4f, 8f), 1.35f, StrokeCap.Round)
        }
    }
}

private val CheckPath by lazy { svgPath("M4.3 7.1 L6.1 8.9 L9.8 5.1") }

/** 名字旁边的认证小圆（.chat-header-title 背景 svg）：粉白径向渐变 + 对勾 */
@Composable
fun MusiVerified(modifier: Modifier) {
    Canvas(modifier) {
        inViewBox(14f, 14f) {
            val c = Offset(7f, 7f)
            drawCircle(Brush.radialGradient(0f to Color.White, 0.55f to MusiPinkFill, 1f to Color(0xFFE5D4DE), center = Offset(4.9f, 3.5f), radius = 9.8f), 6.2f, c)
            drawCircle(Color(0xFF8E7C86), 6.2f, c, style = Stroke(0.95f))
            drawPath(CheckPath, Color(0xFF463A40), style = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private val QuoteOpen by lazy {
    listOf(
        svgPath("M4.5 1.2 C2.4 2.2 1.2 4.1 1.2 6.2 C1.2 7.8 2.2 8.8 3.5 8.8 C4.7 8.8 5.5 7.9 5.5 6.8 C5.5 5.7 4.6 4.9 3.6 4.9 C3.3 4.9 3 5 2.8 5.1 C3.1 3.8 4 2.7 5.2 2 L4.5 1.2 Z"),
        svgPath("M9.8 1.2 C7.7 2.2 6.5 4.1 6.5 6.2 C6.5 7.8 7.5 8.8 8.8 8.8 C10 8.8 10.8 7.9 10.8 6.8 C10.8 5.7 9.9 4.9 8.9 4.9 C8.6 4.9 8.3 5 8.1 5.1 C8.4 3.8 9.3 2.7 10.5 2 L9.8 1.2 Z"),
    )
}
private val QuoteClose by lazy {
    listOf(
        svgPath("M2.2 8.8 C4.3 7.8 5.5 5.9 5.5 3.8 C5.5 2.2 4.5 1.2 3.2 1.2 C2 1.2 1.2 2.1 1.2 3.2 C1.2 4.3 2.1 5.1 3.1 5.1 C3.4 5.1 3.7 5 3.9 4.9 C3.6 6.2 2.7 7.3 1.5 8 L2.2 8.8 Z"),
        svgPath("M7.5 8.8 C9.6 7.8 10.8 5.9 10.8 3.8 C10.8 2.2 9.8 1.2 8.5 1.2 C7.3 1.2 6.5 2.1 6.5 3.2 C6.5 4.3 7.4 5.1 8.4 5.1 C8.7 5.1 9 5 9.2 4.9 C8.9 6.2 8 7.3 6.8 8 L7.5 8.8 Z"),
    )
}

/** 粉白渐变的立体引号，开、合两个颜色一样 */
@Composable
fun MusiQuote(open: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        inViewBox(12f, 10f) {
            val fill = Brush.verticalGradient(0f to Color.White, 0.55f to MusiPinkFill, 1f to Color(0xFFF0DCE6), startY = 0f, endY = 10f)
            (if (open) QuoteOpen else QuoteClose).forEach {
                drawPath(it, fill)
                drawPath(it, Color(0xFF8E7C86), style = Stroke(0.75f, join = StrokeJoin.Round))
            }
            val dots = if (open) listOf(Offset(3.1f, 6.4f), Offset(8.4f, 6.4f)) else listOf(Offset(3.5f, 2.8f), Offset(8.8f, 2.8f))
            dots.forEach { drawCircle(Color.White, 0.6f, it) }
        }
    }
}

/** 一行字两头套上立体引号（用户气泡上面那句「❝ … ❞」） */
@Composable
fun MusiQuoted(text: String, fontSize: Float, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        MusiQuote(true, Modifier.size(width = 8.dp, height = 6.8.dp))
        Text(text, fontSize = fontSize.sp, color = color, maxLines = 1)
        MusiQuote(false, Modifier.size(width = 8.dp, height = 6.8.dp))
    }
}

/**
 * 拍立得头像。照原 CSS 的比例：
 * cover（顶栏那张）60×74，白边 上3 左右3 下14，小字在左下，光盘角标 19；
 * 否则（消息旁 / ME）34×41，白边 2 / 下10.5，小字居中，光盘角标 13.5。
 * size 是外框宽度，高度按比例算。
 */
@Composable
fun MusiPolaroid(
    name: String,
    avatar: Avatar,
    size: Float,
    label: String,
    loading: Boolean = false,
    tilt: Float = -4f,
    onClick: (() -> Unit)? = null,
    stack: Boolean = false,
    badge: Boolean = false,
    cover: Boolean = false,
) {
    val u = if (cover) size / 60f else size / 34f
    val h = if (cover) 74f * u else 41f * u
    val pad = if (cover) 3f * u else 2f * u
    val padB = if (cover) 14f * u else 10.5f * u
    val shape = RoundedCornerShape(4.dp)
    Box(modifier = Modifier.size(width = size.dp, height = h.dp)) {
        if (stack) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        rotationZ = if (tilt > 0f) 7.5f else -7.5f
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .background(Color.White, shape)
                    .border(1.dp, MusiRim, shape),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .rotate(tilt)
                .shadow(2.dp, shape, clip = false)
                .background(Color.White, shape)
                .border(1.1.dp, MusiRim, shape),
        ) {
            UIAvatar(
                name = name,
                value = avatar,
                modifier = Modifier.padding(start = pad.dp, top = pad.dp, end = pad.dp, bottom = padB.dp).fillMaxSize(),
                loading = loading,
                shape = RoundedCornerShape(2.dp),
                onClick = onClick,
            )
            if (label.isNotEmpty()) {
                val fs = if (cover) 7f * u else 6.8f * u
                Text(
                    label,
                    fontSize = fs.sp,
                    lineHeight = fs.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = MusiLabel,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .align(if (cover) Alignment.BottomStart else Alignment.BottomCenter)
                        .padding(start = if (cover) (4f * u).dp else 0.dp, bottom = (if (cover) 3.5f * u else 2.5f * u).dp),
                )
            }
            if (badge) {
                val d = if (cover) 19f * u else 13.5f * u
                MusiDisc(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (if (cover) 6f * u else 4.5f * u).dp, y = (if (cover) 5f * u else -5f * u).dp)
                        .size(d.dp),
                )
            }
        }
    }
}

/** 顶栏签名卡：52 高，左上开引号、右下合引号，最底下一行互动数字（.header-profile-section::after） */
@Composable
fun MusiQuoteCard(quote: String, stats: String?) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(1.dp, shape, clip = false, ambientColor = Color(0xFF505A64), spotColor = Color(0xFF505A64))
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFBF4F7))), shape)
            .border(0.95.dp, Color(0xFF9AA4AC), shape),
    ) {
        MusiQuote(true, Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 6.dp).size(width = 8.dp, height = 6.8.dp))
        Text(
            quote,
            fontSize = 10.sp,
            lineHeight = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF363C40),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 5.5.dp),
        )
        MusiQuote(false, Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 15.dp).size(width = 8.dp, height = 6.8.dp))
        stats?.let {
            Text(
                it,
                fontSize = 7.5.sp,
                lineHeight = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF68727A),
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 7.dp, bottom = 3.dp),
            )
        }
    }
}

/** 楼层条：时间分割线胶囊（原 CSS 第 9 节）的白→浅灰渐变 + 细边 */
fun Modifier.musiCapsule(): Modifier = this
    .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF0F1F3))), CircleShape)
    .border(0.8.dp, Color(0xFFA2ACB4), CircleShape)

/** 时间胶囊左边的小电池 */
@Composable
fun MusiBattery(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 13.dp, height = 7.5.dp)) {
        inViewBox(15f, 8f) {
            drawRoundRect(Color.White, Offset(0.6f, 0.6f), Size(12f, 6.8f), CornerRadius(1.2f))
            drawRoundRect(Color(0xFF78848C), Offset(0.6f, 0.6f), Size(12f, 6.8f), CornerRadius(1.2f), style = Stroke(0.9f))
            drawRoundRect(Color(0xFFF0F0F0), Offset(2f, 2f), Size(4f, 4f), CornerRadius(0.4f))
            drawRoundRect(MusiPinkFill, Offset(6.8f, 2f), Size(3.8f, 4f), CornerRadius(0.4f))
            drawRoundRect(Color(0xFF78848C), Offset(13.2f, 2.2f), Size(1.3f, 3.6f), CornerRadius(0.4f))
        }
    }
}

/** 时间胶囊右边的信号格 */
@Composable
fun MusiSignal(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 10.dp, height = 8.dp)) {
        inViewBox(12f, 9f) {
            drawRect(Color(0xFF9AA4AC), Offset(0.5f, 5.5f), Size(1.8f, 3f))
            drawRect(Color(0xFF9AA4AC), Offset(3.2f, 4f), Size(1.8f, 4.5f))
            drawRect(Color(0xFFF0F0F0), Offset(5.9f, 2.2f), Size(1.8f, 6.3f))
            drawRect(Color(0xFF78848C), Offset(5.9f, 2.2f), Size(1.8f, 6.3f), style = Stroke(0.5f))
            drawRect(MusiPinkFill, Offset(8.6f, 0.5f), Size(1.8f, 8f))
            drawRect(Color(0xFF8E7C86), Offset(8.6f, 0.5f), Size(1.8f, 8f), style = Stroke(0.5f))
        }
    }
}

private val CloudPath by lazy { svgPath("M17.5 14.5 C15 14.5 13.5 12.8 14 10.6 C13.2 8.8 14.5 6.8 16.5 6.8 C17.5 4.5 20.5 3.8 22.8 5.2 C24.8 3.6 28.2 4.2 29.2 6.5 C31.5 6.6 33 8.5 32.5 10.6 C33.2 12.8 31.5 14.5 29 14.5 Z") }
private val CloudCdA by lazy { svgPath("M16 5.2 L13.5 2.7 A3.6 3.6 0 0 1 16 1.6 Z") }
private val CloudCdB by lazy { svgPath("M16 5.2 L18.5 2.7 A3.6 3.6 0 0 1 19.6 5.2 Z") }
private val CloudStem by lazy { svgPath("M23.8 10.8 V6.8 C23.8 6.8 26.2 7.2 26.2 8.8") }

/** 思考折着时的小云：两颗小泡泡 + 云 + 云上一张小光盘 + 音符（原 CSS 第 7 节） */
@Composable
fun MusiCloud(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 42.dp, height = 22.dp)) {
        inViewBox(42f, 22f) {
            val rim = Color(0xFF88929A)
            drawCircle(MusiPinkFill, 1.5f, Offset(3f, 19f)); drawCircle(rim, 1.5f, Offset(3f, 19f), style = Stroke(0.75f))
            drawCircle(Color(0xFFF0F0F0), 2.3f, Offset(8.5f, 14.5f)); drawCircle(rim, 2.3f, Offset(8.5f, 14.5f), style = Stroke(0.8f))
            drawPath(CloudPath, Color.White); drawPath(CloudPath, rim, style = Stroke(0.85f, join = StrokeJoin.Round))
            val cd = Offset(16f, 5.2f)
            drawCircle(Color.White, 3.6f, cd)
            drawPath(CloudCdA, Color(0xFFE8ECEF)); drawPath(CloudCdB, MusiPinkFill)
            drawCircle(Color(0xFF78848C), 3.6f, cd, style = Stroke(0.7f))
            drawCircle(Color.White, 1.1f, cd); drawCircle(Color(0xFF78848C), 1.1f, cd, style = Stroke(0.6f))
            withTransform({ rotate(-20f, pivot = Offset(22.5f, 11.2f)) }) {
                drawOval(Color(0xFF68545E), Offset(20.9f, 10.1f), Size(3.2f, 2.2f))
            }
            drawPath(CloudStem, Color(0xFF68545E), style = Stroke(0.85f, cap = StrokeCap.Round))
        }
    }
}

private val CdPaths by lazy {
    listOf(
        svgPath("M0 0 L-11 -8.5 A14 14 0 0 1 -3 -13.6 Z") to Color(0xFFF0F0F0),
        svgPath("M0 0 L11 8.5 A14 14 0 0 1 3 13.6 Z") to Color(0xFFF0F0F0),
        svgPath("M0 0 L9.5 -10 A14 14 0 0 1 13.5 -3.5 Z") to MusiPinkFill,
        svgPath("M0 0 L-9.5 10 A14 14 0 0 1 -13.5 3.5 Z") to MusiPinkFill,
    )
}

/** 思考面板左边窗口里那张 CD */
@Composable
private fun MusiCd(modifier: Modifier) {
    Canvas(modifier) {
        inViewBox(30f, 30f) {
            withTransform({ translate(15f, 15f) }) {
                val rim = Color(0xFF88929A)
                drawCircle(Color.White, 14f, Offset.Zero)
                CdPaths.forEach { (p, col) -> drawPath(p, col) }
                drawCircle(rim, 14f, Offset.Zero, style = Stroke(0.85f))
                drawCircle(Color(0xFFF0F0F0), 4.8f, Offset.Zero); drawCircle(rim, 4.8f, Offset.Zero, style = Stroke(0.75f))
                drawCircle(Color.White, 1.8f, Offset.Zero); drawCircle(rim, 1.8f, Offset.Zero, style = Stroke(0.65f))
            }
        }
    }
}

/** 思考展开后的 CD 播放器面板（.nuo-thinking-panel）：灰白外壳、四角小螺丝、左边 CD 窗口，右边是思考的字；想的时候 CD 在转 */
@Composable
fun MusiPlayerPanel(spinning: Boolean, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val angle = if (spinning) {
        val t = rememberInfiniteTransition(label = "musiCd")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "musiCd").value
    } else 0f
    Row(
        modifier = Modifier
            .padding(top = 6.dp, bottom = 8.dp)
            .fillMaxWidth(0.94f)
            .shadow(3.dp, shape, clip = false, ambientColor = Color(0xFF646E78), spotColor = Color(0xFF646E78))
            .background(Brush.verticalGradient(0f to Color.White, 0.5f to Color(0xFFF4F5F7), 1f to Color(0xFFEAECEF)), shape)
            .border(0.95.dp, Color(0xFF9AA4AC), shape)
            .padding(1.2.dp)
            .border(1.dp, Color.White, RoundedCornerShape(12.8.dp))
            .drawBehind {
                val r = 1.5.dp.toPx()
                val m = 6.8.dp.toPx()
                listOf(Offset(m, m), Offset(size.width - m, m), Offset(m, size.height - m), Offset(size.width - m, size.height - m)).forEach {
                    drawCircle(Color.White, r, it)
                    drawCircle(Color(0xFF9AA4AC), r, it, style = Stroke(0.7.dp.toPx()))
                }
            }
            .padding(start = 6.dp, top = 13.dp, end = 14.dp, bottom = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier
                .width(36.dp)
                .background(Color(0xFFF8F9FA), RoundedCornerShape(8.dp))
                .border(0.8.dp, Color(0xFFA2ACB4), RoundedCornerShape(8.dp))
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MusiCd(Modifier.size(28.dp).rotate(angle))
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 4.dp)
                        .background(if (i == 0) MusiPinkFill else Color(0xFFE8ECEF), RoundedCornerShape(2.dp))
                        .border(0.6.dp, Color(0xFFA2ACB4), RoundedCornerShape(2.dp)),
                )
            }
        }
        Box(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) { content() }
    }
}

/** 输入框左边：歪着的我的小拍立得（底下写 ME）+ 后面露半张 CD，点了是加号的功能 */
@Composable
fun MusiPlusButton(name: String, avatar: Avatar, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 40.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Canvas(modifier = Modifier.align(Alignment.CenterEnd).size(24.dp)) {
            val r = size.minDimension / 2
            drawCircle(Color.White, r)
            drawCircle(MusiRim, r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
            drawCircle(Color(0xFFD6CED4), r * 0.55f, style = Stroke(1.dp.toPx()))
            drawCircle(MusiRim, r * 0.18f, style = Stroke(1.dp.toPx()))
        }
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            MusiPolaroid(name, avatar, 27f, "ME", tilt = -4f)
        }
    }
}

/** MUSI 顶栏：一排圆钮 / 拍立得头像 + 名字 + 签名卡 / 进度条 */
@Composable
fun MusiTopBar(
    top: SkinTopBar,
    assistantName: String,
    assistantAvatar: Avatar,
    onMenu: (() -> Unit)?,
    onNewChat: () -> Unit,
    onClickAvatar: () -> Unit,
    mcpText: String,
    onMcp: () -> Unit,
) {
    val ink = skinColor(top.text) ?: MusiInk
    val sub = skinColor(top.subText) ?: MusiSub
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(top.fill?.let { Modifier.skinFill(it) } ?: Modifier)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = top.padX.dp)
            .padding(top = 4.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onMenu != null) MusiRoundButton("<", pink = false, size = top.iconSize, iconUrl = top.leftIcon, onClick = onMenu)
            MusiRoundButton(">", pink = true, size = top.iconSize, iconUrl = top.rightIcon, onClick = onNewChat)
            Spacer(Modifier.weight(1f))
            val letters = (top.buttons ?: "MUSI").padEnd(4).take(4).map { it.toString() }
            MusiRoundButton(letters[0], pink = false, size = top.iconSize, onClick = onMcp)
            MusiRoundButton(letters[1], pink = true, size = top.iconSize)
            MusiRoundButton(letters[2], pink = false, size = top.iconSize)
            MusiRoundButton(letters[3], pink = true, size = top.iconSize, onClick = onClickAvatar)
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MusiPolaroid(assistantName, assistantAvatar, top.leftAvatarSize, top.coverLabel.orEmpty(), tilt = 0f, onClick = onClickAvatar, badge = true, cover = true)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(assistantName, fontSize = 15.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MusiVerified(Modifier.size(13.dp))
                    Text(top.handle.orEmpty(), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = sub, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Text(mcpText, fontSize = 9.sp, color = sub, modifier = Modifier.clickable(onClick = onMcp))
                }
                MusiQuoteCard(top.quote.orEmpty(), top.stats)
            }
        }
        MusiProgress(top.progressLeft ?: "00:32", top.progressRight ?: "04:15", Modifier.fillMaxWidth())
    }
}


/** 这条消息的思考面板开没开（MUSI：点头像旁边的小云切换），不是 MUSI 时为 null */
val LocalMusiThink = staticCompositionLocalOf<MutableState<Boolean>?> { null }

/** 头像旁边的小云 + 想了几秒，点了开关思考面板 */
@Composable
fun MusiCloudLabel(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 2.dp, top = 2.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MusiCloud()
        Text(text, fontSize = 8.5.sp, lineHeight = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, color = MusiSub, maxLines = 1)
    }
}

private val EnvRead by lazy { svgPath("M1.2 4.5 L7 1 L12.8 4.5 V11.5 H1.2 V4.5 Z") }
private val EnvReadFlap by lazy { svgPath("M1.2 4.5 L7 8.2 L12.8 4.5") }
private val EnvSentFlap by lazy { svgPath("M1.6 2.5 L7 6.6 L12.4 2.5") }

/** 我方气泡右下角的小信封：回过了是拆开的粉信封（is-read），还没回是合着的灰信封（is-sent） */
@Composable
fun MusiEnvelope(read: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 14.dp, height = 13.dp)) {
        if (read) {
            inViewBox(14f, 13f) {
                drawPath(EnvRead, MusiPinkFill)
                drawPath(EnvRead, Color(0xFF8E7C86), style = Stroke(1f, join = StrokeJoin.Round))
                drawRoundRect(Color.White, Offset(3.2f, 2.8f), Size(7.6f, 4.5f), CornerRadius(0.6f))
                drawRoundRect(Color(0xFFB09CA6), Offset(3.2f, 2.8f), Size(7.6f, 4.5f), CornerRadius(0.6f), style = Stroke(0.75f))
                drawPath(EnvReadFlap, Color(0xFF8E7C86), style = Stroke(0.95f))
            }
        } else {
            withTransform({ translate(top = size.height - size.width * 12f / 14f) }) {
                withTransform({ scale(size.width / 14f, size.width / 14f, pivot = Offset.Zero) }) {
                    drawRoundRect(Color(0xFFF0F0F0), Offset(1.2f, 1.8f), Size(11.6f, 8.4f), CornerRadius(1.2f))
                    drawRoundRect(Color(0xFF9AA4AC), Offset(1.2f, 1.8f), Size(11.6f, 8.4f), CornerRadius(1.2f), style = Stroke(1f))
                    drawPath(EnvSentFlap, Color(0xFF9AA4AC), style = Stroke(1f))
                }
            }
        }
    }
}

private val PlayGrayA by lazy { svgPath("M11 11 L4 4 A10 10 0 0 1 11 1 Z") }
private val PlayGrayB by lazy { svgPath("M11 11 L18 18 A10 10 0 0 1 11 21 Z") }
private val PlayPinkA by lazy { svgPath("M11 11 L18 4 A10 10 0 0 1 21 11 Z") }
private val PlayPinkB by lazy { svgPath("M11 11 L4 18 A10 10 0 0 1 1 11 Z") }
private val PlayTri by lazy { svgPath("M9.6 8.4 L14 11 L9.6 13.6 Z") }

/** 语音左边的光盘播放键（.voice-play-btn::after），我方的粉灰对调 */
@Composable
private fun MusiPlayDisc(mine: Boolean, playing: Boolean, modifier: Modifier) {
    val angle = if (playing) {
        val t = rememberInfiniteTransition(label = "musiPlay")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "musiPlay").value
    } else 0f
    Canvas(modifier) {
        inViewBox(22f, 22f) {
            val c = Offset(11f, 11f)
            val a = if (mine) MusiPinkFill else Color(0xFFE8ECEF)
            val b = if (mine) Color(0xFFE8ECEF) else MusiPinkFill
            drawCircle(Color.White, 10f, c)
            withTransform({ rotate(angle, pivot = c) }) {
                drawPath(PlayGrayA, a); drawPath(PlayGrayB, a)
                drawPath(PlayPinkA, b); drawPath(PlayPinkB, b)
            }
            drawCircle(if (mine) Color(0xFF9A8690) else Color(0xFF78848C), 10f, c, style = Stroke(1f))
            drawCircle(Color.White, 5f, c)
            drawCircle(if (mine) Color(0xFFB09CA6) else Color(0xFF88929A), 5f, c, style = Stroke(0.8f))
            val tri = if (mine) Color(0xFF5A4A52) else Color(0xFF465058)
            drawPath(PlayTri, tri)
            drawPath(PlayTri, tri, style = Stroke(0.6f, join = StrokeJoin.Round))
        }
    }
}

private val WaveHeights = floatArrayOf(0.45f, 0.8f, 0.55f, 1f, 0.6f, 0.35f, 0.9f, 0.5f, 0.75f, 0.4f, 0.95f, 0.6f, 0.3f, 0.85f, 0.5f, 0.7f)

/** MUSI 语音条（原 CSS 第 5 节）：光盘播放键 + 上下细线夹着的白底声波条 + 粉底秒数（左边一道虚线，右边圆头） */
@Composable
fun MusiVoiceBar(seconds: Int, mine: Boolean, playing: Boolean, modifier: Modifier = Modifier) {
    val rim = if (mine) Color(0xFF9A8690) else Color(0xFF88929A)
    val bar = if (mine) Color(0xFF68545E) else Color(0xFF5A646C)
    val n = (10 + seconds / 2).coerceAtMost(26)
    val lit = if (playing) {
        val t = rememberInfiniteTransition(label = "musiWave")
        t.animateFloat(0f, n.toFloat(), infiniteRepeatable(tween(n * 120, easing = LinearEasing)), label = "musiWave").value
    } else -1f
    Row(modifier = modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        MusiPlayDisc(mine, playing, Modifier.size(22.dp).zIndex(1f))
        Row(
            modifier = Modifier
                .offset(x = (-3).dp)
                .height(18.dp)
                .background(Color.White)
                .drawBehind {
                    val w = 0.9.dp.toPx()
                    drawLine(rim, Offset(0f, w / 2), Offset(size.width, w / 2), w)
                    drawLine(rim, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                }
                .padding(start = 9.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        ) {
            repeat(n) { i ->
                val h = WaveHeights[(i * 7 + seconds) % WaveHeights.size]
                val on = !playing || i <= lit
                Box(Modifier.size(width = 2.2.dp, height = (3.5f + 9.5f * h).dp).background(if (on) bar else bar.copy(alpha = 0.28f), CircleShape))
            }
        }
        Box(
            modifier = Modifier
                .offset(x = (-3).dp)
                .height(18.dp)
                .background(if (mine) Color(0xFFF4F5F7) else MusiPinkFill, RoundedCornerShape(topEnd = 9.dp, bottomEnd = 9.dp))
                .drawBehind {
                    val w = 0.9.dp.toPx()
                    val r = size.height / 2
                    drawLine(rim, Offset(0f, w / 2), Offset(size.width - r, w / 2), w)
                    drawLine(rim, Offset(0f, size.height - w / 2), Offset(size.width - r, size.height - w / 2), w)
                    drawArc(rim, -90f, 180f, false, Offset(size.width - 2 * r + w / 2, w / 2), Size(2 * r - w, 2 * r - w), style = Stroke(w))
                    drawLine(
                        Color(0xFFB09CA6), Offset(0f, 0f), Offset(0f, size.height), 0.8.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 1.5.dp.toPx())),
                    )
                }
                .padding(start = 5.5.dp, end = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val m = seconds / 60
            Text(
                "%d:%02d".format(m, seconds % 60),
                fontSize = 9.5.sp,
                lineHeight = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = if (mine) Color(0xFF5A4A52) else Color(0xFF4A545C),
                maxLines = 1,
            )
        }
    }
}

/** 长按语音条展开的转写卡片，右下角一行 ♪ voice note */
@Composable
fun MusiVoiceNote(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp, topEnd = 8.dp)
    Column(
        modifier = modifier
            .padding(start = 14.dp)
            .widthIn(max = 260.dp)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF7F8F9))), shape)
            .border(0.9.dp, Color(0xFF9AA4AC), shape)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 5.dp),
    ) {
        Text(text, fontSize = 11.5.sp, lineHeight = 16.sp, color = Color(0xFF363C40))
        Text("♪ voice note", fontSize = 8.sp, fontStyle = FontStyle.Italic, color = MusiSub, modifier = Modifier.align(Alignment.End))
    }
}


/* ---------------- 气泡（原 CSS 第 4 节） ---------------- */

private val TailPath by lazy { svgPath("M6.57861 12.4116C6.7104 7.18219 5.31252 4.76742 1.57861 0.911621C8.65137 2.68579 10.9546 3.85168 10.5786 6.41162C10.486 5.19895 10.8642 4.44591 15.5786 2.41162 C15.5786 8.5 11.5 12.4116 6.57861 12.4116 Z") }

/** CSS 的 radial-gradient(rx ry at c)：圆形渐变压成椭圆 */
private fun DrawScope.ellipseGlow(c: Offset, rx: Float, ry: Float, vararg stops: Pair<Float, Color>) {
    if (rx <= 0f || ry <= 0f) return
    withTransform({ scale(1f, ry / rx, pivot = c) }) {
        drawCircle(Brush.radialGradient(*stops, center = c, radius = rx), rx, c)
    }
}

/**
 * MUSI 文字气泡：底色 + 左上一团白光、底边一道白光；描边是 0.35px 的细灰边，连着小尾巴一起描（CSS 用 drop-shadow 描的是气泡和尾巴合起来的外轮廓）。
 * 对方的尾巴在左上角朝头像勾出去，我的在右下角（CSS 里 scale(-1,-1) 翻过来）。
 */
fun Modifier.musiBubble(mine: Boolean, fill: Color, radius: Dp, tail: Boolean): Modifier {
    val shape = RoundedCornerShape(radius)
    val rim = if (mine) Color(0xA6AC96A2) else Color(0xA6919BA2)
    return this
        .drawWithContent {
            drawContent()
            val r = radius.toPx()
            val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
            val sw = 0.7.dp.toPx()
            if (tail) {
                val tw = 14.dp.toPx()
                val th = 11.5.dp.toPx()
                val left = if (mine) size.width + 4.8.dp.toPx() - tw else -4.8.dp.toPx()
                val top = if (mine) size.height + 0.5.dp.toPx() - th else -0.5.dp.toPx()
                val m = android.graphics.Matrix().apply {
                    setScale(tw / 16f, th / 13f)
                    if (mine) postScale(-1f, -1f, tw / 2f, th / 2f)
                    postTranslate(left, top)
                }
                val t = android.graphics.Path(TailPath.asAndroidPath()).apply { transform(m) }.asComposePath()
                drawPath(Path.combine(PathOperation.Union, body, t), rim, style = Stroke(sw))
                val tailFill = if (mine) SolidColor(fill) else Brush.linearGradient(
                    0f to Color(0xFFF0F0F0), 0.65f to Color(0xFFF6F6F6), 1f to Color(0xFFFAFAFA),
                    start = Offset(left, top), end = Offset(left + tw, top + th),
                )
                drawPath(t, tailFill)
            } else {
                drawPath(body, rim, style = Stroke(sw))
            }
        }
        .clip(shape)
        .background(fill, shape)
        .drawBehind {
            val w = size.width
            val h = size.height
            ellipseGlow(
                Offset(0.36f * w, 0.14f * h), 0.82f * w, 0.55f * h,
                0f to Color.White.copy(alpha = if (mine) 0.99f else 0.98f),
                0.48f to Color.White.copy(alpha = if (mine) 0.52f else 0.45f),
                0.82f to Color.White.copy(alpha = 0f),
            )
            ellipseGlow(
                Offset(0.5f * w, 0.92f * h), 0.7f * w, 0.32f * h,
                0f to Color.White.copy(alpha = if (mine) 0.8f else 0.75f),
                1f to Color.White.copy(alpha = 0f),
            )
        }
}

/** 往上提一截、占的高度也少一截：让对方气泡贴着拍立得下沿，尾巴勾到头像上 */
fun Modifier.musiPullUp(by: Dp): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val up = by.roundToPx()
    layout(p.width, (p.height - up).coerceAtLeast(0)) { p.place(0, -up) }
}

/* ---------------- 楼层条：照独立引用气泡（原 CSS 第 12 节）的两层叠卡 ---------------- */

private val CursorPath by lazy { svgPath("M5 4.5 L14 8.5 L10 10.3 L12.2 14.5 L10.2 15.3 L8 11.2 L5.5 13.5 Z") }

/** 前面一张白灰渐变卡，后面往右下错 2.5 垫一张；右下角压一个小粉点 + 鼠标箭头。我这边（pink）换粉色 */
fun Modifier.musiStacked(pink: Boolean): Modifier = this
    // 箭头和后面那张垫卡会伸出卡片右下角，先在外头留出位置，不然被上层裁掉
    .padding(end = 4.dp, bottom = 6.dp)
    .drawWithContent {
        drawContent()
        val d = 12.5.dp.toPx()
        val tx = size.width + 4.dp.toPx() - d
        val ty = size.height + 4.5.dp.toPx() - d
        withTransform({
            translate(tx, ty)
            scale(d / 16f, d / 16f, pivot = Offset.Zero)
        }) {
            drawCircle(MusiPinkFill, 3.5f, Offset(5f, 5f))
            drawCircle(Color(0xFFB09CA6), 3.5f, Offset(5f, 5f), style = Stroke(0.85f))
            drawPath(CursorPath, Color.White)
            drawPath(CursorPath, Color(0xFF525A60), style = Stroke(1.05f, join = StrokeJoin.Round))
        }
    }
    .drawBehind {
        val r = CornerRadius(8.dp.toPx())
        val o = 2.5.dp.toPx()
        val rim = if (pink) Color(0xFFB09CA6) else Color(0xFF9AA4AC)
        drawRoundRect(if (pink) Color(0xFFF3E2EC) else Color(0xFFE4E8EC), Offset(o, o), size, r)
        drawRoundRect(rim, Offset(o, o), size, r, style = Stroke(0.75.dp.toPx()))
        drawRoundRect(Brush.verticalGradient(listOf(Color.White, if (pink) MusiPinkFill else Color(0xFFF0F2F5))), Offset.Zero, size, r)
        drawRoundRect(rim, Offset.Zero, size, r, style = Stroke(0.8.dp.toPx()))
        val y = 1.2.dp.toPx()
        drawLine(Color.White, Offset(r.x, y), Offset(size.width - r.x, y), 1.dp.toPx())
    }

private val ChipBack by lazy { svgPath("M5 2.5 H11 C11.8 2.5 12.5 3.1 12.5 3.9 V6.8 C12.5 7.6 11.8 8.2 11 8.2 H9.8 L10.5 9.8 L8.2 8.2 H5 C4.2 8.2 3.5 7.6 3.5 6.8 V3.9 C3.5 3.1 4.2 2.5 5 2.5 Z") }
private val ChipFront by lazy { svgPath("M3.2 4.5 H8.2 C8.9 4.5 9.5 5 9.5 5.7 V8.2 C9.5 8.9 8.9 9.4 8.2 9.4 H5.2 L3.2 10.8 L3.7 9.4 H3.2 C2.5 9.4 2 8.9 2 8.2 V5.7 C2 5 2.5 4.5 3.2 4.5 Z") }

/** 叠卡最左边的小标签：两个叠着的对话泡 + 字（原图是 RE:，这里放楼层号） */
@Composable
fun MusiChip(text: String, pink: Boolean) {
    val shape = RoundedCornerShape(4.5.dp)
    Row(
        modifier = Modifier
            .height(14.dp)
            .background(if (pink) MusiPinkFill else Color(0xFFEAECEF), shape)
            .border(0.8.dp, if (pink) Color(0xFFB09CA6) else Color(0xFF88929A), shape)
            .padding(start = 1.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Canvas(Modifier.size(13.dp)) {
            inViewBox(14f, 14f) {
                drawPath(ChipBack, Color.White)
                drawPath(ChipBack, if (pink) Color(0xFF88929A) else Color(0xFF78848C), style = Stroke(0.8f))
                drawPath(ChipFront, if (pink) Color(0xFFF0F0F0) else MusiPinkFill)
                drawPath(ChipFront, if (pink) Color(0xFF78848C) else Color(0xFF8E7C86), style = Stroke(0.8f))
            }
        }
        Text(
            text,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            color = if (pink) Color(0xFF5A4A52) else Color(0xFF4A545C),
            maxLines = 1,
        )
    }
}


/* ---------------- 在打字（原 CSS 第 13 节）：两颗耳塞 + 垂下来的耳机线 + 右边一张 CD，轻轻晃 ---------------- */

private val CableA by lazy { svgPath("M6 12 C6 30 18 38 34 34 C44 31 48 23 56 23") }
private val CableB by lazy { svgPath("M28 10 C28 23 22 33 34 34") }
private val TypingCd by lazy {
    listOf(
        svgPath("M0 0 L12 -12 A17 17 0 0 1 17 0 Z") to MusiPinkFill,
        svgPath("M0 0 L-12 12 A17 17 0 0 1 -17 0 Z") to MusiPinkFill,
        svgPath("M0 0 L-12 -12 A17 17 0 0 1 0 -17 Z") to Color(0xFFE8ECEF),
        svgPath("M0 0 L12 12 A17 17 0 0 1 0 17 Z") to Color(0xFFE8ECEF),
    )
}
private val TypingNote by lazy { svgPath("M10.3 -6.6 V-12.2 C10.3 -12.2 13 -11.7 13 -9.9") }

private val SwayRot = floatArrayOf(-3f, 2f, -1.5f, 3f, -3f)
private val SwayY = floatArrayOf(0f, -2.5f, -4f, -1.5f, 0f)

@Composable
fun MusiTyping(text: String) {
    val t = rememberInfiniteTransition(label = "musiTyping")
    val p = t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "musiTyping").value
    val seg = (p * 4f).toInt().coerceIn(0, 3)
    val f = (p * 4f - seg).coerceIn(0f, 1f)
    val e = f * f * (3f - 2f * f)
    val rot = SwayRot[seg] + (SwayRot[seg + 1] - SwayRot[seg]) * e
    val dy = SwayY[seg] + (SwayY[seg + 1] - SwayY[seg]) * e
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(
            Modifier
                .size(width = 86.dp, height = 44.dp)
                .graphicsLayer {
                    rotationZ = rot
                    translationY = dy.dp.toPx()
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
        ) {
            inViewBox(86f, 44f) {
                listOf(Offset(6f, 8f), Offset(28f, 6f)).forEach { c ->
                    val g = Brush.radialGradient(
                        0f to Color.White, 0.55f to MusiPinkFill, 1f to Color(0xFFD5C2CC),
                        center = Offset(c.x - 4.2f + 0.35f * 8.4f, c.y - 4.2f + 0.3f * 8.4f), radius = 5.9f,
                    )
                    drawCircle(g, 4.2f, c)
                    drawCircle(Color(0xFF8E7C86), 4.2f, c, style = Stroke(0.9f))
                    drawCircle(Color.White, 1.3f, Offset(c.x - 1.4f, c.y - 1.6f))
                }
                drawPath(CableA, Color(0xFF8E7C86), style = Stroke(2.2f, cap = StrokeCap.Round))
                drawPath(CableA, Color.White, style = Stroke(1.1f, cap = StrokeCap.Round))
                drawPath(CableB, Color(0xFF8E7C86), style = Stroke(2f, cap = StrokeCap.Round))
                drawPath(CableB, MusiPinkFill, style = Stroke(1f, cap = StrokeCap.Round))
                withTransform({ translate(66f, 22f) }) {
                    drawCircle(Color.White, 17f, Offset.Zero)
                    TypingCd.forEach { (path, col) -> drawPath(path, col) }
                    drawCircle(Color(0xFF8E7C86), 17f, Offset.Zero, style = Stroke(1.05f))
                    drawCircle(MusiPinkFill, 6.5f, Offset.Zero)
                    drawCircle(Color(0xFF9A8690), 6.5f, Offset.Zero, style = Stroke(0.9f))
                    drawCircle(Color.White, 2.4f, Offset.Zero)
                    drawCircle(Color(0xFF78848C), 2.4f, Offset.Zero, style = Stroke(0.8f))
                    withTransform({ rotate(-20f, pivot = Offset(9f, -6.2f)) }) {
                        drawOval(Color(0xFF5A4A52), Offset(7.3f, -7.4f), Size(3.4f, 2.4f))
                    }
                    drawPath(TypingNote, Color(0xFF5A4A52), style = Stroke(1f, cap = StrokeCap.Round))
                }
            }
        }
        Text(
            text,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF525A60),
            maxLines = 1,
            modifier = Modifier.musiCapsule().padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}


/* ---------------- 此刻 <trace>：灰白小胶囊 + 小 CD，点开是一张 B 面小卡 ---------------- */

@Composable
fun MusiTraceHead(text: String, arrow: String?, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier)
            .padding(top = 2.dp)
            .musiCapsule()
            .padding(start = 3.dp, end = 9.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MusiCd(Modifier.size(14.dp))
        Text(text, fontSize = 10.sp, lineHeight = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF525A60), maxLines = 1)
        arrow?.let { Text(it, fontSize = 8.sp, lineHeight = 10.sp, color = MusiSub) }
    }
}

val MusiTraceInk = Color(0xFF363C40)
val MusiTraceSoft = Color(0xFF8E98A0)

@Composable
fun MusiTraceRule() {
    Box(Modifier.padding(vertical = 5.dp).size(width = 110.dp, height = 0.8.dp).background(Color(0xFFE4D6DE)))
}

@Composable
fun MusiTraceCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    Column(
        modifier = Modifier
            .padding(start = 10.dp, top = 4.dp)
            .widthIn(max = 300.dp)
            .shadow(1.dp, shape, clip = false, ambientColor = Color(0xFF646E78), spotColor = Color(0xFF646E78))
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF7F4F6))), shape)
            .border(0.9.dp, Color(0xFF9AA4AC), shape)
            .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 5.dp),
    ) {
        content()
        Text("♪ b-side", fontSize = 8.sp, fontStyle = FontStyle.Italic, color = MusiSub, modifier = Modifier.align(Alignment.End))
    }
}
