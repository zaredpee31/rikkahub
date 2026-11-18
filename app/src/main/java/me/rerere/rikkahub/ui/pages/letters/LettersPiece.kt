package me.rerere.rikkahub.ui.pages.letters

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
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

/** 凸耳半径（dp）。圆心离边 0.5R：凸出 1.5R，和边相交的口宽约 1.73R，有个收口的"脖子"。 */
private const val TAB_R = 6.2f

/**
 * 拼块轮廓：一格矩形，四条边按 edgesOf 加凸耳（并上圆）或挖凹槽（减去圆）。
 * 相邻两块用的是同一个圆，所以一凸一凹完全互补，拼上无缝。
 */
private fun piecePath(slot: Int, cw: Float, ch: Float, d: Float): Path {
    val e = edgesOf(slot)
    val r = TAB_R * d
    val k = r * .5f
    var p = Path().apply { addRect(Rect(0f, 0f, cw, ch)) }
    fun tab(cx: Float, cy: Float, dir: Int) {
        if (dir == 0) return
        val circle = Path().apply { addOval(Rect(Offset(cx, cy), r)) }
        val next = Path()
        next.op(p, circle, if (dir > 0) PathOperation.Union else PathOperation.Difference)
        p = next
    }
    tab(cw / 2, if (e.top > 0) -k else k, e.top)
    tab(if (e.right > 0) cw + k else cw - k, ch / 2, e.right)
    tab(cw / 2, if (e.bottom > 0) ch + k else ch - k, e.bottom)
    tab(if (e.left > 0) -k else k, ch / 2, e.left)
    return p
}

/**
 * 一块拼块。尺寸就是一格（凸耳画在格外），图片按 cover 铺满整板再取这一块那一片，
 * 凸耳区域自然取到隔壁格子对应位置的像素。flash：落位回弹时的白闪，0..1。
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
    val density = LocalDensity.current.density
    val path = remember(slot, density) { piecePath(slot, CELL_W * density, CELL_H * density, density) }
    Canvas(modifier.size(CELL_W.dp, CELL_H.dp)) {
        val d = density
        val cw = CELL_W * d
        val ch = CELL_H * d
        val floating = look != PieceLook.Placed
        // 软影只给漂着的；嵌在板上的不画，免得压到邻块上出接缝
        if (floating) {
            for (i in 1..4) {
                translate(left = (.4f * i) * d, top = (2f * i) * d) {
                    drawPath(path, Color(0xFF282C32).copy(alpha = .07f))
                }
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
        // 亚克力边：沿同一条轮廓，左上亮、右下暗；嵌好的只留一道很淡的缝
        if (floating) {
            drawPath(
                path,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = .85f), Color.White.copy(alpha = .3f), Color(0x805A5E66)),
                    Offset.Zero, Offset(cw, ch),
                ),
                style = Stroke(1.2f * d),
            )
        } else {
            drawPath(path, Color.White.copy(alpha = .3f), style = Stroke(.6f * d))
        }
        if (look == PieceLook.Fresh || flash > 0f) {
            val a = if (look == PieceLook.Fresh) 1f else flash
            drawPath(path, Color.White.copy(alpha = .28f * a), style = Stroke(5f * d))
            drawPath(path, Color.White.copy(alpha = .95f * a), style = Stroke(1.5f * d))
        }
    }
}
