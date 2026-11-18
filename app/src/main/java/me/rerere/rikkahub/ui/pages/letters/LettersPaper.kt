package me.rerere.rikkahub.ui.pages.letters

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Ink = Color(0xFF2B2D31)
internal val Ink2 = Color(0xFF55585E)
internal val MuteC = Color(0xFF8E9197)
internal val FaintC = Color(0xFFB9BCC1)
internal val PaperC = Color(0xFFFBFBF9)

private val Rise = CubicBezierEasing(.22f, 1f, .36f, 1f)

/**
 * 底部弹出的一张纸：背后淡淡的遮罩，点遮罩等于「放回去」。
 * 纸上有颗粒；onBounds 回报纸在屏幕上的位置，寄出动画要从这里碎开。
 */
@Composable
internal fun PaperLayer(
    onClose: () -> Unit,
    closeLabel: String,
    animate: Boolean,
    onBounds: (Rect) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val noise = rememberNoiseBrush()
    val rise = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) rise.animateTo(1f, tween(600, easing = Rise)) }
    val maxH = (LocalConfiguration.current.screenHeightDp * .84f).dp
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x8CF2F2F0))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth(.92f)
                .heightIn(max = maxH)
                .graphicsLayer {
                    translationY = (1f - rise.value) * 30.dp.toPx()
                    scaleY = .94f + .06f * rise.value
                    alpha = rise.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
                }
                .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                .shadow(18.dp, ambientColor = Color(0x2828282C), spotColor = Color(0x2828282C))
                .background(PaperC)
                .drawBehind { drawRect(noise, alpha = .18f) }
                // 纸上的点击不要穿到遮罩
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 26.dp, end = 26.dp, top = 46.dp, bottom = 30.dp)) {
                Box { content() }
            }
            Text(
                closeLabel,
                Modifier.align(Alignment.TopEnd).clickable(onClick = onClose).padding(start = 14.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
                fontSize = 11.sp, letterSpacing = 2.sp, color = MuteC,
            )
        }
    }
}

/** 信纸：日期·寄信人、标题、正文、落款，右下角手写体水印。 */
@Composable
internal fun ReadPaper(date: String, from: String, title: String, body: String?, error: String?, animate: Boolean, onClose: () -> Unit) {
    PaperLayer(onClose = onClose, closeLabel = "放回去 ×", animate = animate) {
        Column(Modifier.fillMaxWidth()) {
            Text("$date · $from 寄", fontSize = 9.sp, letterSpacing = 3.sp, color = MuteC)
            Text(title, Modifier.padding(top = 16.dp, bottom = 22.dp), fontSize = 21.sp, letterSpacing = 1.sp, color = Ink, fontFamily = FontFamily.Serif)
            Text(
                body ?: error ?: "正在拆开……",
                fontSize = 15.sp, lineHeight = 31.5.sp, color = Color(0xFF3A3D42), fontFamily = FontFamily.Serif,
            )
            Text(from, Modifier.fillMaxWidth().padding(top = 26.dp), fontSize = 15.sp, color = Ink, fontFamily = FontFamily.Serif, textAlign = TextAlign.End)
            Text(
                "with love", Modifier.fillMaxWidth().padding(top = 8.dp),
                fontSize = 34.sp, color = Color(0x1F787C82), fontFamily = FontFamily.Cursive, textAlign = TextAlign.End,
            )
        }
    }
}
