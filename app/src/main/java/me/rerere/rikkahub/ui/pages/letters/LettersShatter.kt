package me.rerere.rikkahub.ui.pages.letters

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.min

/**
 * 寄出时纸碎成 8 片（4x2）飞向板边：每片错开 50ms，飞 900ms，缩到 0.12 倍，转 ±40°，淡到 0.2。
 * progress 0..1 对应总共 1250ms。
 */
@Composable
internal fun ShatterOverlay(from: Rect, to: Offset, progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val d = density
        val fw = from.width / 4f
        val fh = min(from.height, 400f * d) / 2f
        for (i in 0 until 8) {
            val fx = from.left + (i % 4) * fw
            val fy = from.top + 40f * d + (i / 4) * fh
            val c = Offset(fx + fw / 2, fy + fh / 2)
            val t = ((progress * 1250f - i * 50f) / 900f).coerceIn(0f, 1f)
            val e = 1f - (1f - t) * (1f - t) * (1f - t)
            val sc = 1f + (.12f - 1f) * e
            val a = 1f + (.2f - 1f) * e
            translate(left = (to.x - c.x) * e, top = (to.y - c.y) * e) {
                scale(sc, sc, pivot = c) {
                    rotate((if (i % 2 == 1) 40f else -40f) * e, pivot = c) {
                        drawRect(PaperC.copy(alpha = a), Offset(fx, fy), Size(fw - 3f * d, fh - 3f * d))
                        drawRect(Color(0xFF8E9197).copy(alpha = .35f * a), Offset(fx, fy), Size(fw - 3f * d, fh - 3f * d), style = Stroke(1f * d))
                    }
                }
            }
        }
    }
}
