package me.rerere.rikkahub.ui.pages.letters

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

private val Mute = Color(0xFF8E9197)

private val Settle = CubicBezierEasing(.22f, 1f, .36f, 1f)

@Composable
private fun Ring(w: Int, h: Int, ox: Int, oy: Int, ms: Int, reverse: Boolean, alpha: Float, line: Color, dashed: Boolean, animate: Boolean) {
    val turn = rememberLoop(ms, enabled = animate)
    Canvas(
        Modifier
            .offset(ox.dp, oy.dp)
            .size(w.dp, h.dp)
            .graphicsLayer { rotationZ = turn.value * 360f * (if (reverse) -1f else 1f); this.alpha = alpha },
    ) {
        val d = density
        drawOval(
            line,
            style = Stroke(
                (if (dashed) .7f else .5f) * d,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5f * d, 5f * d)) else null,
            ),
        )
        if (!dashed) drawCircle(Color(0x99969AA2), 1.6f * d, Offset(size.width / 2, 2f * d))
    }
}

@Composable
private fun Cell(slot: Int, target: Boolean) {
    Box(
        Modifier
            .offset(slotX(slot).dp, slotY(slot).dp)
            .size(CELL_W.dp, CELL_H.dp)
            .drawBehind {
                val d = density
                if (target) {
                    drawRoundRect(Color.White.copy(alpha = .35f), cornerRadius = CornerRadius(2f * d), style = Stroke(6f * d))
                    drawRoundRect(Color.White.copy(alpha = .95f), cornerRadius = CornerRadius(2f * d), style = Stroke(1f * d))
                } else {
                    drawRoundRect(
                        Color(0x339A9EA6), cornerRadius = CornerRadius(2f * d),
                        style = Stroke(1f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f * d, 2.5f * d))),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text((slot + 1).toString().padStart(2, '0'), fontSize = 6.sp, letterSpacing = .5.sp, color = Mute.copy(alpha = .55f))
    }
}

@Composable
private fun BoardPiece(
    item: PieceItem,
    spot: Spot,
    image: ImageBitmap,
    noise: ShaderBrush,
    home: Boolean,
    landing: Boolean,
    snapTick: Int,
    shakeTick: Int,
    animate: Boolean,
    onLanded: () -> Unit,
    onTap: () -> Unit,
) {
    // t：0 漂在板外，1 嵌在格子里。飞完由这里回调 onLanded，不靠固定延时猜。
    val t = remember { Animatable(if (home) 1f else 0f) }
    // 落位那一下：原作 pieceSnapPulse，1.22 → 0.97 → 1，共 520ms
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(home) {
        if (home) {
            if (t.value < 1f) {
                if (animate) t.animateTo(1f, tween(560, easing = Settle)) else t.snapTo(1f)
                if (animate) launch {
                    pulse.snapTo(1.22f)
                    pulse.animateTo(.97f, tween(312, easing = Settle))
                    pulse.animateTo(1f, tween(208, easing = Settle))
                }
                onLanded()
            }
        } else if (animate) t.animateTo(0f, tween(560, easing = Settle)) else t.snapTo(0f)
    }
    // 漂浮一直在跑，飞的时候按 (1-t) 慢慢收掉，不会起飞那一下突然跳
    val drift = rememberLoop(if (idHash(item.row.id) % 2 == 0) 6500 else 7800, reverse = true, enabled = animate)
    val shake = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(shakeTick) {
        if (shakeTick > 0) for (v in listOf(-5f, 5f, -3f, 2f, 0f)) shake.animateTo(v, tween(100))
    }
    LaunchedEffect(snapTick) {
        if (snapTick > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(520, easing = Settle))
        }
    }
    val settled = home && t.value >= 1f
    val look = when {
        settled -> PieceLook.Placed
        home -> if (item.state == PieceState.Fresh) PieceLook.Fresh else PieceLook.Free
        item.state == PieceState.Locked -> PieceLook.Locked
        item.state == PieceState.Fresh -> PieceLook.Fresh
        else -> PieceLook.Free
    }
    val tag = when {
        home || item.state == PieceState.Placed -> null
        item.state == PieceState.Locked -> if (item.row.lockType == "permanent") "封存" else "还有 ${item.daysLeft} 天"
        item.state == PieceState.Fresh -> "新来的"
        else -> "等阿辞读"
    }
    val flying = t.value in 0.001f..0.999f
    Box(
        Modifier
            .zIndex(if (landing || flying) 40f else if (t.value >= 1f) 2f else 20f)
            .graphicsLayer {
                val k = t.value
                val free = 1f - k
                translationX = (spot.x + (slotX(item.slot) - spot.x) * k + (drift.value * 4f) * free + shake.value).dp.toPx()
                translationY = (spot.y + (slotY(item.slot) - spot.y) * k - (drift.value * 6f) * free).dp.toPx()
                rotationZ = spot.rot * free
                val sc = (spot.scale + (1f - spot.scale) * k) * pulse.value
                scaleX = sc
                scaleY = sc
                alpha = if (look == PieceLook.Locked) .78f else 1f
            }
            .size(CELL_W.dp, CELL_H.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    ) {
        PieceView(item.slot, image, noise, look, flash.value)
        if (tag != null) {
            Text(
                tag,
                Modifier.align(Alignment.BottomCenter).offset(y = 24.dp).wrapContentWidth(unbounded = true),
                fontSize = 7.5.sp, letterSpacing = 1.5.sp, color = Mute, maxLines = 1, softWrap = false,
            )
        }
    }
}

/** 拼图板：虚线椭圆、点线格子、20 格，以及这一板上所有拼块。 */
@Composable
internal fun LettersBoard(
    pieces: List<PieceItem>,
    hiddenId: String?,
    homeIds: Set<String>,
    onLanded: (String) -> Unit,
    image: ImageBitmap,
    landingId: String?,
    snapId: String?,
    snapTick: Int,
    shakeId: String?,
    shakeTick: Int,
    sheen: Float,
    animate: Boolean,
    onTap: (PieceItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val noise = rememberNoiseBrush()
    val landingSlot = pieces.firstOrNull { it.row.id == landingId }?.slot
    val spots = remember(pieces) { floatSpots(pieces) }
    Box(modifier.size(BOARD_W.dp, BOARD_H.dp)) {
        Ring(260, 332, -26, -22, 90_000, false, 1f, Color(0x6B969AA2), true, animate)
        Ring(288, 356, -40, -34, 120_000, true, .7f, Color(0x3D969AA2), false, animate)
        Box(
            Modifier.fillMaxSize().drawBehind {
                val d = density
                drawRoundRect(
                    Color(0x61969AA2), cornerRadius = CornerRadius(4f * d),
                    style = Stroke(1f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * d, 3f * d))),
                )
            },
        )
        for (slot in 0 until SLOTS) Cell(slot, slot == landingSlot)
        pieces.forEach { p ->
            if (p.row.id == hiddenId) return@forEach
            key(p.row.id) {
                val landing = p.row.id == landingId
                BoardPiece(
                    item = p, spot = spots[p.row.id] ?: Spot(-36f, 0f, 0f, .8f), image = image, noise = noise,
                    home = landing || (p.state == PieceState.Placed && p.row.id in homeIds),
                    landing = landing,
                    snapTick = if (p.row.id == snapId) snapTick else 0,
                    shakeTick = if (p.row.id == shakeId) shakeTick else 0,
                    animate = animate,
                    onLanded = { onLanded(p.row.id) },
                    onTap = { onTap(p) },
                )
            }
        }
        if (sheen in 0.001f..0.999f) {
            Canvas(Modifier.fillMaxSize().zIndex(30f)) {
                val cx = size.width * (1.1f - 1.2f * sheen)
                val band = size.width * .45f
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = .6f * (1f - sheen)), Color.Transparent),
                        Offset(cx - band, size.height * .3f), Offset(cx + band, size.height * .7f),
                    ),
                )
            }
        }
    }
}
