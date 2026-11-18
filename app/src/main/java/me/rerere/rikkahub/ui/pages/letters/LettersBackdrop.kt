package me.rerere.rikkahub.ui.pages.letters

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private val Paper = Color(0xFFF2F2F0)
private val TornFill = Color(0xFFF8F8F6)

/** 撕纸边的归一化折线点（x 0..100，y 以 80 为满高），算法同网页 torn()。 */
private fun tornPoints(seed: Int, a: Float, b: Float): List<Pair<Float, Float>> {
    var s = seed.toLong()
    fun rnd(): Float { s = (s * 9301 + 49297) % 233280; return s / 233280f }
    fun base(x: Float) = a + (b - a) * x / 100f
    val pts = ArrayList<Pair<Float, Float>>()
    var x = 100f
    while (x >= 0f) {
        var j = rnd() * 9f + sin(x / (6 + seed % 5)) * 3.5f
        if (rnd() > .93f) j += rnd() * 10f
        pts += x to base(x) + j
        x -= 1.2f
    }
    return pts
}

@Composable
private fun TornEdge(seed: Int, a: Float, b: Float, flip: Boolean, modifier: Modifier) {
    val pts = remember(seed) { tornPoints(seed, a, b) }
    Canvas(modifier) {
        val sx = size.width / 100f
        val sy = size.height / 80f
        fun path(dy: Float) = Path().apply {
            for ((i, p) in pts.withIndex()) {
                val px = p.first * sx
                val py = (p.second * sy + dy).let { if (flip) size.height - it else it }
                if (i == 0) {
                    moveTo(size.width, if (flip) size.height else 0f)
                    lineTo(px, py)
                } else lineTo(px, py)
            }
            lineTo(0f, if (flip) size.height else 0f)
            close()
        }
        drawPath(path(3.dp.toPx()), Color(0x2E9A9DA2))
        drawPath(path(1.2.dp.toPx()), Color(0x1F000000))
        drawPath(path(0f), TornFill)
    }
}

@Composable
private fun Orbit(size: Int, big: Boolean, reverse: Boolean, alpha: Float, animate: Boolean, modifier: Modifier) {
    val turn = rememberLoop(if (big) 70_000 else 80_000, enabled = animate)
    Canvas(
        modifier
            .size(size.dp)
            .graphicsLayer { rotationZ = turn.value * 360f * (if (reverse) -1f else 1f); this.alpha = alpha },
    ) {
        val c = center
        val k = this.size.width / (if (big) 210f else 180f)
        val ink = if (big) Color(0xFF9A9EA4) else Color(0xFFA3A7AD)
        if (big) {
            drawCircle(ink, 100f * k, c, style = Stroke(.6f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * k, 6f * k))))
            drawCircle(ink, 82f * k, c, style = Stroke(.4f * k))
            drawCircle(ink, 2f * k, Offset(c.x, 5f * k))
        } else {
            drawCircle(ink, 84f * k, c, style = Stroke(.5f * k))
            drawOval(
                ink, Offset(c.x - 84f * k, c.y - 40f * k), androidx.compose.ui.geometry.Size(168f * k, 80f * k),
                style = Stroke(.4f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * k, 5f * k))),
            )
        }
    }
}

@Composable
private fun Mote(x: Float, y: Float, ms: Int, animate: Boolean) {
    val t = rememberLoop(ms, reverse = true, enabled = animate)
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = t.value * 10.dp.toPx(); translationY = -t.value * 16.dp.toPx() },
    ) {
        drawCircle(Color(0xFF8E9197).copy(alpha = .12f + .38f * t.value), 1.25.dp.toPx(), Offset(size.width * x, size.height * y))
    }
}

/** 整页底：颗粒纸、斜纹、撕纸边、慢转圆环、漂浮小点。 */
@Composable
internal fun BoxScope.LettersBackdrop(animate: Boolean) {
    val noise = rememberNoiseBrush()
    val hatch = rememberLoop(48_000, enabled = animate)
    Box(Modifier.matchParentSize().graphicsLayer { }) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Paper)
            drawRect(noise, alpha = .32f)
            val step = 14.dp.toPx()
            val shift = hatch.value * step * 10f % step
            var x = -size.height + shift
            while (x < size.width) {
                drawLine(Color(0x09 shl 24 or 0x8C9096), Offset(x, size.height), Offset(x + size.height, 0f), 1f)
                x += step
            }
        }
        Orbit(230, big = true, reverse = false, alpha = .4f, animate = animate, modifier = Modifier.align(Alignment.TopEnd).offset(x = 60.dp, y = 120.dp))
        Orbit(190, big = false, reverse = true, alpha = .32f, animate = animate, modifier = Modifier.align(Alignment.BottomStart).offset(x = (-60).dp, y = (-110).dp))
        Mote(.24f, .16f, 14_000, animate); Mote(.84f, .46f, 16_000, animate)
        Mote(.12f, .70f, 18_000, animate); Mote(.66f, .84f, 15_000, animate)
        // 上边左厚右薄，下边相反（网页 torn(7,60,20) / torn(31,18,56)）
        TornEdge(7, 60f, 20f, false, Modifier.align(Alignment.TopStart).fillMaxWidth().height(96.dp))
        TornEdge(31, 18f, 56f, true, Modifier.align(Alignment.BottomStart).fillMaxWidth().height(96.dp))
    }
}
