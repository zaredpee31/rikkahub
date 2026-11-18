package me.rerere.rikkahub.ui.pages.letters

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.unit.dp

/** 一只燕子：离顶多少、多宽（dp），飞一趟几秒，起飞延迟，扇一下多少毫秒，颜色。 */
internal data class Bird(val top: Float, val width: Float, val seconds: Int, val delayMs: Int, val flapMs: Int, val color: Color)

// 64x32 的画布：身体朝右、剪刀尾在左；两只翅膀分开画，各绕自己的肩点转
private val LeftShoulder = Offset(30f, 15f)
private val RightShoulder = Offset(34f, 15f)

private val BodyPath = Path().apply {
    moveTo(22f, 16f)
    cubicTo(28f, 13f, 40f, 13f, 46f, 15f)
    lineTo(49f, 16f)
    lineTo(46f, 17.5f)
    cubicTo(40f, 19.5f, 28f, 19f, 22f, 17.5f)
    lineTo(10f, 23f)
    lineTo(18f, 16.5f)
    lineTo(9f, 10f)
    close()
}

private val LeftWing = Path().apply {
    moveTo(31f, 14f)
    quadraticTo(18f, 4f, 6f, 2f)
    quadraticTo(16f, 10f, 29f, 18f)
    close()
}

private val RightWing = Path().apply {
    moveTo(33f, 14f)
    quadraticTo(44f, 4f, 56f, 1f)
    quadraticTo(46f, 10f, 35f, 18f)
    close()
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun smooth(t: Float) = t * t * (3f - 2f * t)

/**
 * 一群燕子从左飞到右。身体不动，翅膀以肩为轴上下摆：前 40% 下压（快），后 60% 上扬（慢）。
 * 颜色预先和纸色混好再画，翅膀和身体重叠处不会叠深。
 */
@Composable
internal fun SwallowFlock(birds: List<Bird>, animate: Boolean, paper: Color) {
    val flies = birds.map { rememberLoop(it.seconds * 1000, delayMs = it.delayMs, enabled = animate) }
    val flaps = birds.map { rememberLoop(it.flapMs, delayMs = it.delayMs % it.flapMs, enabled = animate) }
    Canvas(Modifier.fillMaxSize()) {
        birds.forEachIndexed { i, b ->
            val p = flies[i].value
            val w = b.width.dp.toPx()
            val k = w / 64f
            val x = if (animate) mix(-w - 20.dp.toPx(), size.width + 20.dp.toPx(), p) else size.width * (.18f + .26f * i)
            val bob = if (!animate) 0f else if (p < .5f) mix(8f, -3f, p / .5f) else mix(-3f, 5f, (p - .5f) / .5f)
            val y = (b.top + bob).dp.toPx()
            val alpha = when {
                !animate -> .8f
                p < .06f -> .8f * p / .06f
                p > .94f -> .8f * (1f - p) / .06f
                else -> .8f
            }
            val ink = colorLerp(paper, b.color, alpha)
            val f = flaps[i].value
            val down = if (f < .4f) smooth(f / .4f) else 1f - smooth((f - .4f) / .6f)
            val ang = if (animate) mix(-8f, 48f, down) else 10f
            translate(x, y) {
                scale(k, k, pivot = Offset.Zero) {
                    rotate(-ang, pivot = LeftShoulder) { drawPath(LeftWing, ink) }
                    rotate(ang, pivot = RightShoulder) { drawPath(RightWing, ink) }
                    drawPath(BodyPath, ink)
                }
            }
        }
    }
}
