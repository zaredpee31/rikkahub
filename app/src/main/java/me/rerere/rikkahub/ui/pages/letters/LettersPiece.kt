package me.rerere.rikkahub.ui.pages.letters

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/** 拼块的外观：嵌在板上 / 漂着 / 新来的（带亮边）/ 锁定（更淡更糊）。 */
internal enum class PieceLook { Placed, Free, Fresh, Locked }

/** 灰度 + 对比度 + 亮度，合成一个颜色矩阵（对应网页 grayscale/contrast/brightness）。 */
private fun tone(contrast: Float, brightness: Float): ColorFilter {
    val k = contrast * brightness
    val o = (0.5f - 0.5f * contrast) * brightness * 255f
    val row = floatArrayOf(.213f * k, .715f * k, .072f * k, 0f, o)
    return ColorFilter.colorMatrix(ColorMatrix(row + row + row + floatArrayOf(0f, 0f, 0f, 1f, 0f)))
}

private val PlacedTone = tone(1.32f, .9f)
private val FreeTone = tone(1.36f, .9f)
private val LockedTone = tone(1.1f, 1.05f)

private fun piecePath(slot: Int, cw: Float, ch: Float, d: Float): Path = Path().apply {
    addRoundRect(RoundRect(0f, 0f, cw, ch, CornerRadius(2.5f * d)))
    val knob = 3.75f * d
    if (hasRightKnob(slot)) addOval(Rect(Offset(cw + .75f * d - knob, ch / 2 - knob), Size(knob * 2, knob * 2)))
    if (hasBottomKnob(slot)) addOval(Rect(Offset(cw / 2 - knob, ch + .75f * d - knob), Size(knob * 2, knob * 2)))
}

/**
 * 一块拼块。尺寸就是一格（凸耳画在格外），图片按 cover 铺满整板再取这一格那一片。
 * flash：落位回弹时的白闪，0..1。
 */
@Composable
internal fun PieceView(
    slot: Int,
    image: ImageBitmap,
    noise: ShaderBrush,
    look: PieceLook,
    flash: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(CELL_W.dp, CELL_H.dp)) {
        val d = density
        val cw = CELL_W * d
        val ch = CELL_H * d
        val path = piecePath(slot, cw, ch, d)
        val floating = look != PieceLook.Placed
        // 软影：几层半透明错位叠出来
        val reach = if (floating) 8f else 3f
        for (i in 1..4) {
            translate(left = (.4f * i) * d, top = (reach * i / 4f) * d) {
                drawPath(path, Color(0x3C282C32).copy(alpha = if (floating) .07f else .05f))
            }
        }
        val s = max(BOARD_W / image.width, BOARD_H / image.height)
        val rw = image.width * s
        val rh = image.height * s
        val ox = -((rw - BOARD_W) / 2f + slotX(slot)) * d
        val oy = -((rh - BOARD_H) / 2f + slotY(slot)) * d
        clipPath(path) {
            drawImage(
                image = image,
                dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                dstSize = IntSize((rw * d).roundToInt(), (rh * d).roundToInt()),
                colorFilter = when (look) {
                    PieceLook.Locked -> LockedTone
                    PieceLook.Placed -> PlacedTone
                    else -> FreeTone
                },
            )
            drawPath(path, noise, alpha = .5f, blendMode = BlendMode.Multiply)
            drawPath(
                path,
                Brush.linearGradient(listOf(Color.White.copy(alpha = .28f), Color.Transparent), Offset.Zero, Offset(cw * .9f, ch * .9f)),
            )
            if (flash > 0f) drawPath(path, Color.White.copy(alpha = .55f * flash))
        }
        // 亚克力内高光：左上亮、右下暗
        val lw = 1.2f * d
        drawLine(Color.White.copy(alpha = .78f), Offset(2f * d, lw / 2), Offset(cw - 2f * d, lw / 2), lw)
        drawLine(Color.White.copy(alpha = .78f), Offset(lw / 2, 2f * d), Offset(lw / 2, ch - 2f * d), lw)
        drawLine(Color(0x4D5A5E66), Offset(2f * d, ch - lw / 2), Offset(cw - 2f * d, ch - lw / 2), lw)
        drawLine(Color(0x4D5A5E66), Offset(cw - lw / 2, 2f * d), Offset(cw - lw / 2, ch - 2f * d), lw)
        if (look == PieceLook.Fresh || flash > 0f) {
            val a = if (look == PieceLook.Fresh) 1f else flash
            drawPath(path, Color.White.copy(alpha = .28f * a), style = Stroke(5f * d))
            drawPath(path, Color.White.copy(alpha = .95f * a), style = Stroke(1.5f * d))
        }
    }
}
