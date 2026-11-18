package me.rerere.rikkahub.ui.pages.letters

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** 拼图板 + 多板时两侧极小的左右箭头 + 板下的「ASSEMBLED NN / 20」。 */
@Composable
internal fun BoardArea(c: LettersController) {
    val onBoard = c.pieces.filter { it.board == c.boardIndex && it.row.id != c.hiddenId }
    val image = c.image
    Box(contentAlignment = Alignment.Center) {
        if (image != null) {
            LettersBoard(
                pieces = onBoard, image = image, landingId = c.landingId, snapId = c.snapId, snapTick = c.snapTick,
                shakeId = c.shakeId, shakeTick = c.shakeTick, sheen = c.sheen.value, animate = c.animate,
                onTap = { c.tap(it) },
                modifier = Modifier.onGloballyPositioned { c.stageBounds = it.boundsInRoot() },
            )
        } else {
            Box(Modifier.size(BOARD_W.dp, BOARD_H.dp))
        }
        if (c.boardCount > 1) {
            if (c.boardIndex > 0) Arrow("‹", Modifier.align(Alignment.CenterStart).offset(x = (-56).dp)) { c.boardIndex-- }
            if (c.boardIndex < c.boardCount - 1) Arrow("›", Modifier.align(Alignment.CenterEnd).offset(x = 56.dp)) { c.boardIndex++ }
        }
    }
    val placed = onBoard.count { it.state == PieceState.Placed }
    Text(
        "ASSEMBLED %02d / %d".format(placed, SLOTS), Modifier.padding(top = 18.dp),
        fontSize = 8.sp, letterSpacing = 3.sp, color = MuteC,
    )
}

@Composable
private fun Arrow(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(32.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, color = MuteC)
    }
}

/** 板下的文字区：手写体水印 + 中文 + 小写英文。加载中、连不上、空信箱各有一句低调的话。 */
@Composable
internal fun Caption(c: LettersController) {
    val idle = c.capZh == IDLE_ZH
    val rows = c.rows
    val (zh, en) = when {
        idle && rows == null && c.loadError == null -> "正在取信……" to "LOADING"
        idle && rows == null -> "信箱暂时连不上，点这里再试" to "OFFLINE · TAP TO RETRY"
        idle && rows.isNullOrEmpty() -> "还没有信，写第一封吧" to "NOTHING YET"
        else -> c.capZh to c.capEn
    }
    val scope = rememberCoroutineScope()
    val retry = rows == null && c.loadError != null
    Box(
        Modifier
            .fillMaxWidth(.86f)
            .height(78.dp)
            .clickable(enabled = retry, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch { c.refresh() }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            c.capWm, Modifier.offset(y = (-14).dp), fontSize = 40.sp, color = Color(0x29787C82),
            fontFamily = FontFamily.Cursive, textAlign = TextAlign.Center,
        )
        Column(
            Modifier.graphicsLayer { alpha = c.capAlpha.value; translationY = (1f - c.capAlpha.value) * 5.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(zh, fontSize = 15.sp, letterSpacing = 3.sp, color = Ink, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
            Text(en, Modifier.padding(top = 8.dp), fontSize = 8.sp, letterSpacing = 4.sp, color = MuteC, textAlign = TextAlign.Center)
        }
    }
}

/** 「写给阿辞 / A LETTER IN PIECES」：外面两圈慢转的椭圆星轨。 */
@Composable
internal fun WritePill(animate: Boolean = true, onClick: () -> Unit) {
    val cw = rememberLoop(38_000, enabled = animate)
    val ccw = rememberLoop(28_000, enabled = animate)
    Box(
        Modifier
            .padding(top = 8.dp)
            .size(180.dp, 54.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val ink = Color(0xFF8E9197)
        Canvas(Modifier.size(180.dp, 54.dp).graphicsLayer { rotationZ = cw.value * 360f }) {
            val d = density
            drawOval(ink, Offset(6f * d, 5f * d), Size(168f * d, 44f * d), style = Stroke(.55f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * d, 4f * d))))
        }
        Canvas(Modifier.size(180.dp, 54.dp).graphicsLayer { rotationZ = -ccw.value * 360f }) {
            val d = density
            drawOval(ink, Offset(16f * d, 10f * d), Size(148f * d, 34f * d), style = Stroke(.45f * d))
        }
        Canvas(Modifier.size(180.dp, 54.dp)) {
            drawCircle(ink, 1.4f * density, Offset(14f * density, 27f * density))
            drawCircle(ink, 1.4f * density, Offset(166f * density, 27f * density))
        }
        Text("Whisper", Modifier.offset(y = (-10).dp), fontSize = 30.sp, color = Color(0x33787C82), fontFamily = FontFamily.Cursive)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("写给阿辞", fontSize = 12.sp, letterSpacing = 4.sp, color = Ink2, fontFamily = FontFamily.Serif)
            Text("A LETTER IN PIECES", Modifier.padding(top = 3.dp), fontSize = 7.sp, letterSpacing = 3.sp, color = MuteC)
        }
    }
}
