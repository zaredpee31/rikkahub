package me.rerere.rikkahub.ui.pages.letters

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SwallowIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Swallow",
        defaultWidth = 64.dp,
        defaultHeight = 32.dp,
        viewportWidth = 64f,
        viewportHeight = 32f,
    ).addPath(
        pathData = addPathNodes("M1 13c9-2 17-1 24 3 4-7 13-13 26-15-8 4-14 10-17 16 4 1 8 3 11 7l-9-3c-3 2-7 3-11 3l3-4c-9 0-18-3-27-7z"),
        fill = SolidColor(Color.Black),
    ).build()
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** 一只燕子：从左飞到右，扇翅，参数同网页 .s1/.s2/.s3。 */
@Composable
private fun Swallow(top: Int, width: Int, seconds: Int, delayMs: Int, color: Color, animate: Boolean, screenW: Float) {
    val fly = rememberLoop(seconds * 1000, delayMs = delayMs, enabled = animate)
    val flap = rememberLoop(1600, reverse = true, enabled = animate)
    Icon(
        imageVector = SwallowIcon,
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .offset(y = top.dp)
            .size(width.dp, (width / 2).dp)
            .graphicsLayer {
                val p = fly.value
                val dx = lerp(-60.dp.toPx(), screenW.dp.toPx() + 60.dp.toPx(), p)
                val dy = (if (p < .5f) lerp(14f, -6f, p / .5f) else lerp(-6f, 8f, (p - .5f) / .5f)).dp.toPx()
                translationX = dx
                translationY = dy
                alpha = when {
                    !animate -> .8f
                    p < .06f -> .85f * p / .06f
                    p > .94f -> .75f * (1f - p) / .06f
                    else -> .8f
                }
                scaleY = lerp(1f, .72f, flap.value)
                rotationZ = lerp(0f, -3f, flap.value)
            },
    )
}

/** 顶部三只燕子慢慢飞过。 */
@Composable
internal fun LettersSwallows(animate: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(120.dp)) {
        val w = maxWidth.value
        Box(Modifier.fillMaxSize()) {
            Swallow(26, 46, 19, 600, Color(0xFF3A3C40), animate, w)
            Swallow(44, 34, 19, 1300, Color(0xFF55585D), animate, w)
            Swallow(18, 26, 27, 9000, Color(0xFF7D8086), animate, w)
        }
    }
}

/** 右侧竖排小字（网页 .vert：顺时针转 90°，从上往下读）。 */
@Composable
internal fun LettersVerticalText(modifier: Modifier = Modifier) {
    Text(
        text = "AOI & C · LETTERS · MONOCHROME EDITION",
        modifier = modifier
            .wrapContentWidth(Alignment.Start, unbounded = true)
            .graphicsLayer { rotationZ = 90f; transformOrigin = TransformOrigin(0f, 0f) },
        fontSize = 8.sp,
        letterSpacing = 5.sp,
        color = Color(0xFFB9BCC1),
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        softWrap = false,
        maxLines = 1,
    )
}

private const val TickerUnit =
    "EVERY FRAGMENT REFLECTS AN UNSPOKEN WORD · AOI & C · KEPT FOR YOU · EVERY FRAGMENT REFLECTS AN UNSPOKEN WORD · AOI & C · KEPT FOR YOU · "

/** 底部滚动小字：两份一样的文字，每次平移一份的宽度，接缝看不出来。 */
@Composable
internal fun LettersTicker(animate: Boolean, modifier: Modifier = Modifier) {
    val t = rememberLoop(60_000, enabled = animate)
    var unitW by remember { mutableIntStateOf(1) }
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = -unitW * t.value },
        ) {
            repeat(2) { i ->
                Text(
                    text = TickerUnit,
                    fontSize = 7.5.sp,
                    letterSpacing = 6.sp,
                    color = Color(0xFFB9BCC1),
                    softWrap = false,
                    maxLines = 1,
                    onTextLayout = { if (i == 0) unitW = it.size.width.coerceAtLeast(1) },
                )
            }
        }
    }
}
