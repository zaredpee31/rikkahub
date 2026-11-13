package me.rerere.rikkahub.companion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 消息页的雪天配色：灰白、磨砂、深灰点缀。 */
object Snow {
    val bgTop = Color(0xFFF4F4F2)
    val bgMid = Color(0xFFE9EAE8)
    val bgBottom = Color(0xFFDCDDDB)
    val glass = Color(0xD9FFFFFF)
    val glassStrong = Color(0xF2FFFFFF)
    val hairline = Color(0x14000000)
    val ink = Color(0xFF2F3237)
    val ink2 = Color(0xFF5A5E66)
    val mute = Color(0xFF9A9DA3)
    val faint = Color(0xFFBFC2C7)
    val dot = Color(0xFFCDCFD3)
    val dark = Color(0xFF4A4C50)
    val chip = Color(0xFFF2F2F2)
    val error = Color(0xFFB4615A)
    val ok = Color(0xFF7FA58B)
}

val SnowMono = FontFamily.Monospace

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    size: Float = 9f,
    color: Color = Snow.mute,
    spacing: Float = 0.14f,
    weight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = SnowMono,
        fontSize = size.sp,
        letterSpacing = (size * spacing).sp,
        color = color,
        fontWeight = weight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

fun Modifier.snowGlass(shape: Shape, strong: Boolean = false, elevation: Dp = 0.dp): Modifier =
    this
        .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000)) else Modifier)
        .clip(shape)
        .background(if (strong) Snow.glassStrong else Snow.glass)
        .border(0.5.dp, Color(0x99FFFFFF), shape)

/** 一条点点的细线。 */
@Composable
fun DottedLine(modifier: Modifier = Modifier, color: Color = Snow.dot) {
    Canvas(modifier.height(2.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 3.4.dp.toPx())),
        )
    }
}

/** 小雪花符号。 */
@Composable
fun FlakeGlyph(modifier: Modifier = Modifier, color: Color = Snow.mute, size: Dp = 12.dp, stroke: Float = 1.1f) {
    Canvas(modifier.size(size)) { drawFlake(center, this.size.minDimension / 2f, color, stroke.dp.toPx()) }
}

fun DrawScope.drawFlake(c: Offset, r: Float, color: Color, width: Float, angle: Float = 0f) {
    rotate(angle, c) {
        for (i in 0 until 3) {
            val a = (i * 60.0 + 90.0) * PI / 180.0
            val dx = (cos(a) * r).toFloat(); val dy = (sin(a) * r).toFloat()
            drawLine(color, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), width, StrokeCap.Round)
            for (s in listOf(-1f, 1f)) {
                val bx = c.x + dx * 0.55f * s; val by = c.y + dy * 0.55f * s
                for (t in listOf(-1, 1)) {
                    val b = a + t * PI / 4.0
                    drawLine(
                        color, Offset(bx, by),
                        Offset(bx - (cos(b) * r * 0.32f * s).toFloat(), by - (sin(b) * r * 0.32f * s).toFloat()),
                        width, StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private class Flake(val x: Float, val y: Float, val r: Float, val speed: Float, val sway: Float, val phase: Float, val alpha: Float, val crystal: Boolean, val spin: Float)

/**
 * 灰白底 + 雪。snowing 时雪慢慢下起来，停了慢慢收住。
 * 雪画在最上层但不挡点击。
 */
@Composable
fun SnowBackdrop(snowing: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val density by animateFloatAsState(if (snowing) 1f else 0f, tween(2200), label = "snow")
    val flakes = remember {
        val rnd = Random(1113)
        List(90) {
            val crystal = rnd.nextFloat() < 0.14f
            Flake(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                r = if (crystal) 4.5f + rnd.nextFloat() * 4f else 0.9f + rnd.nextFloat() * 1.8f,
                speed = 0.025f + rnd.nextFloat() * 0.045f,
                sway = 6f + rnd.nextFloat() * 14f, phase = rnd.nextFloat() * 6.28f,
                alpha = 0.45f + rnd.nextFloat() * 0.5f, crystal = crystal, spin = (rnd.nextFloat() - 0.5f) * 40f,
            )
        }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) time += ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
            }
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Snow.bgTop, Snow.bgMid, Snow.bgBottom)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0x99FFFFFF), Color(0x00FFFFFF)), center = Offset(size.width * 0.15f, size.height * 0.12f), radius = size.width * 0.8f),
                radius = size.width * 0.8f, center = Offset(size.width * 0.15f, size.height * 0.12f),
            )
            drawCircle(
                Brush.radialGradient(listOf(Color(0x66FFFFFF), Color(0x00FFFFFF)), center = Offset(size.width * 0.9f, size.height * 0.62f), radius = size.width * 0.7f),
                radius = size.width * 0.7f, center = Offset(size.width * 0.9f, size.height * 0.62f),
            )
        }
        content()
        Canvas(Modifier.fillMaxSize()) {
            val t = time
            val h = size.height + 40f
            val visible = (flakes.size * density).toInt()
            for (i in 0 until visible) {
                val f = flakes[i]
                val y = ((f.y * h + t * f.speed * size.height) % h) - 20f
                val x = ((f.x * size.width + sin(t * 0.6f + f.phase) * f.sway * density) % size.width + size.width) % size.width
                val a = f.alpha * (0.35f + 0.65f * density)
                if (f.crystal) {
                    drawFlake(Offset(x, y), f.r.dp.toPx(), Color(0xFF8E9196).copy(alpha = a * 0.55f), 1.6f, t * f.spin)
                    drawFlake(Offset(x, y), f.r.dp.toPx(), Color.White.copy(alpha = a), 0.8f, t * f.spin)
                } else {
                    val r = f.r.dp.toPx()
                    drawCircle(Color(0xFF9A9DA3).copy(alpha = a * 0.35f), r + 0.6f, Offset(x, y))
                    drawCircle(Color.White.copy(alpha = a), r, Offset(x, y))
                }
            }
        }
    }
}

/** 积雪：depth 0~1，越下越厚，停了慢慢化。画在元素上沿。 */
@Composable
fun SnowCap(depth: Float, modifier: Modifier = Modifier, seed: Int = 1) {
    if (depth < 0.02f) return
    Canvas(modifier) {
        val rnd = Random(seed)
        val n = 3 + seed % 2
        val maxH = size.height * (0.35f + depth * 0.65f)
        for (i in 0 until n) {
            val w = size.width * (0.32f + rnd.nextFloat() * 0.3f)
            val cx = size.width * ((i + 0.5f) / n) + (rnd.nextFloat() - 0.5f) * size.width * 0.08f
            val hh = maxH * (0.6f + rnd.nextFloat() * 0.4f)
            drawOval(Color(0x33808080), Offset(cx - w / 2, size.height - hh + 1.2f), Size(w, hh * 2))
            drawOval(Color.White.copy(alpha = (depth * 3f).coerceAtMost(1f)), Offset(cx - w / 2, size.height - hh), Size(w, hh * 2))
        }
    }
}

enum class SnowSky { Clear, Partly, Cloud, Rain, Snowy, Fog }

/** wttr.in 的天气代码 → 图标。 */
fun skyOf(code: Int): SnowSky = when (code) {
    113 -> SnowSky.Clear
    116 -> SnowSky.Partly
    119, 122 -> SnowSky.Cloud
    143, 248, 260, 152 -> SnowSky.Fog
    179, 227, 230, 323, 326, 329, 332, 335, 338, 368, 371, 392, 395, 182, 185, 281, 284, 311, 314, 317, 320, 350, 362, 365, 374, 377 -> SnowSky.Snowy
    else -> if (code >= 176) SnowSky.Rain else SnowSky.Cloud
}

@Composable
fun SkyIcon(sky: SnowSky, night: Boolean, modifier: Modifier = Modifier, color: Color = Snow.ink2, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.075f
        val st = Stroke(width = w, cap = StrokeCap.Round)
        fun sun(c: Offset, r: Float, rays: Boolean) {
            if (night) {
                drawArc(color, 40f, 280f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = st)
                drawArc(color, 140f, 80f, false, Offset(c.x - r * 0.2f, c.y - r * 1.1f), Size(r * 1.4f, r * 1.4f), style = st)
            } else {
                drawCircle(color, r, c, style = st)
                if (rays) for (i in 0 until 8) {
                    val a = i * PI / 4
                    drawLine(color, Offset(c.x + (cos(a) * r * 1.5f).toFloat(), c.y + (sin(a) * r * 1.5f).toFloat()),
                        Offset(c.x + (cos(a) * r * 1.95f).toFloat(), c.y + (sin(a) * r * 1.95f).toFloat()), w, StrokeCap.Round)
                }
            }
        }
        fun cloud(dy: Float) {
            val l = s * 0.14f; val r = s * 0.86f; val b = s * 0.66f + dy
            drawRoundRect(color, Offset(l, b - s * 0.24f), Size(r - l, s * 0.24f), CornerRadius(s * 0.12f), style = st)
            drawArc(color, 180f, 180f, false, Offset(s * 0.3f, b - s * 0.48f), Size(s * 0.36f, s * 0.4f), style = st)
        }
        when (sky) {
            SnowSky.Clear -> sun(Offset(s / 2, s / 2), s * 0.2f, true)
            SnowSky.Partly -> { sun(Offset(s * 0.68f, s * 0.32f), s * 0.14f, false); cloud(s * 0.06f) }
            SnowSky.Cloud -> cloud(0f)
            SnowSky.Fog -> for (i in 0 until 3) drawLine(color, Offset(s * (0.16f + i * 0.04f), s * (0.34f + i * 0.16f)), Offset(s * (0.84f - i * 0.06f), s * (0.34f + i * 0.16f)), w, StrokeCap.Round)
            SnowSky.Rain -> { cloud(-s * 0.1f); for (i in 0 until 3) drawLine(color, Offset(s * (0.32f + i * 0.16f), s * 0.7f), Offset(s * (0.28f + i * 0.16f), s * 0.86f), w, StrokeCap.Round) }
            SnowSky.Snowy -> { cloud(-s * 0.1f); for (i in 0 until 3) drawCircle(color, w * 0.9f, Offset(s * (0.32f + i * 0.16f), s * (0.78f + (i % 2) * 0.06f))) }
        }
    }
}

@Composable
fun RoundIcon(icon: ImageVector, modifier: Modifier = Modifier, dark: Boolean = false, enabled: Boolean = true, size: Dp = 36.dp, onClick: () -> Unit) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dark) (if (enabled) Snow.dark else Snow.faint) else Color.White)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.46f), tint = if (dark) Color.White else Snow.ink2)
    }
}

/** 顶栏：一块磨砂，左返回，中标题，右边一个按钮，下面一行等宽小字。 */
@Composable
fun SnowTopBar(
    title: String,
    sub: String,
    leftIcon: ImageVector,
    onLeft: () -> Unit,
    rightIcon: ImageVector? = null,
    onRight: () -> Unit = {},
    status: @Composable () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .snowGlass(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp), strong = true, elevation = 6.dp)
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(leftIcon, onClick = onLeft)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Snow.ink, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                MonoText(sub, size = 9f)
            }
            status()
            if (rightIcon != null) { Spacer(Modifier.width(8.dp)); RoundIcon(rightIcon, onClick = onRight) }
        }
    }
}

@Composable
fun SnowChip(text: String, modifier: Modifier = Modifier, dark: Boolean = false, leading: @Composable (() -> Unit)? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (dark) Snow.dark else Snow.chip)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        leading?.invoke()
        MonoText(text, size = 9f, color = if (dark) Color.White else Snow.mute, spacing = 0.1f)
    }
}
