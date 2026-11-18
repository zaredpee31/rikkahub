package me.rerere.rikkahub.ui.pages.letters

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp

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
