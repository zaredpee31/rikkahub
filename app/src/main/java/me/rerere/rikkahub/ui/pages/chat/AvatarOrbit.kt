package me.rerere.rikkahub.ui.pages.chat

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 顶栏助手头像的双星轨（NOIR「Char 头像 3D 前后穿插双星轨」）。
 * viewBox 0 0 164 82，1 个单位 = 0.5dp，画布 82dp x 41dp，不裁剪，溢出头像外框但不影响布局。
 * 后层（头像下面）画两条轨道的上半圈，前层（头像上面）画下半圈，流光和粒子按位置分层。
 */
internal class AvatarOrbitPhase(val theta1: State<Float>, val theta2: State<Float>)

private const val TwoPi = (2 * PI).toFloat()

/** 轨道 1 一圈 4.2 秒，轨道 2 一圈 5.7 秒，线性无限循环。轨道 2 起始相位差 π 且方向相反。 */
@Composable
internal fun rememberAvatarOrbitPhase(): AvatarOrbitPhase {
    val transition = rememberInfiniteTransition(label = "AvatarOrbit")
    val t1 = transition.animateFloat(
        initialValue = 0f,
        targetValue = TwoPi,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "AvatarOrbitPhase1",
    )
    val t2 = transition.animateFloat(
        initialValue = 0f,
        targetValue = TwoPi,
        animationSpec = infiniteRepeatable(tween(5700, easing = LinearEasing), RepeatMode.Restart),
        label = "AvatarOrbitPhase2",
    )
    return AvatarOrbitPhase(t1, t2)
}

/** [front] 为 false 画后层（上半圈），为 true 画前层（下半圈）。放在头像 Box 里，位置由这里决定。 */
@Composable
internal fun AvatarOrbitLayer(front: Boolean, phase: AvatarOrbitPhase) {
    // 画布顶边在头像顶往下 18%（48dp * 0.18 = 8.64dp）。父 Box 居中放头像，画布中心比头像中心低 8.64 + 20.5 - 24 = 5.14dp
    Box(
        modifier = Modifier
            .requiredSize(82.dp, 41.dp)
            .offset(y = 5.14.dp)
            .drawBehind { drawOrbits(front, phase.theta1.value, phase.theta2.value) },
    )
}

private class Orbit(
    val rx: Float,
    val ry: Float,
    val rotation: Float,
    /** 轨道 1 的 x 取 82 - rx*cosθ，轨道 2 取 82 + rx*cosθ */
    val xSign: Float,
    /** 流光拖尾占半圈的比例 */
    val trailFraction: Float,
    /** 流光在后半圈 / 前半圈的线宽（viewBox 单位） */
    val trailBackWidth: Float,
    val trailFrontWidth: Float,
    /** θ 增加的方向：轨道 1 为 +1，轨道 2 为 -1 */
    val direction: Float,
    val backColor: Color,
    val backWidth: Float,
    val backDash: FloatArray?,
    val frontColor: Color,
    val frontWidth: Float,
    val frontDash: FloatArray?,
)

private val Orbit1 = Orbit(
    rx = 74f, ry = 18f, rotation = -18f, xSign = -1f,
    trailFraction = 0.28f, trailBackWidth = 2.2f, trailFrontWidth = 2.5f, direction = 1f,
    backColor = Color.White.copy(alpha = 0.38f), backWidth = 1.2f, backDash = floatArrayOf(4f, 4f),
    frontColor = Color.White.copy(alpha = 0.55f), frontWidth = 1.2f, frontDash = null,
)

private val Orbit2 = Orbit(
    rx = 68f, ry = 15f, rotation = 15f, xSign = 1f,
    trailFraction = 0.22f, trailBackWidth = 1.8f, trailFrontWidth = 2f, direction = -1f,
    backColor = Color.White.copy(alpha = 0.28f), backWidth = 1f, backDash = null,
    frontColor = Color.White.copy(alpha = 0.40f), frontWidth = 1f, frontDash = floatArrayOf(6f, 4f),
)

private const val CenterX = 82f
private const val CenterY = 41f
private const val Sample = 28

private fun Orbit.pointAt(theta: Float, k: Float): Offset =
    Offset((CenterX + xSign * rx * cos(theta)) * k, (CenterY + ry * sin(theta)) * k)

/** 四角星，单位同 viewBox */
private val StarPath: Path = Path().apply {
    moveTo(0f, -7f)
    lineTo(1.6f, -1.6f)
    lineTo(7f, 0f)
    lineTo(1.6f, 1.6f)
    lineTo(0f, 7f)
    lineTo(-1.6f, 1.6f)
    lineTo(-7f, 0f)
    lineTo(-1.6f, -1.6f)
    close()
}

private fun DrawScope.drawOrbits(front: Boolean, theta1: Float, theta2: Float) {
    val k = 0.5.dp.toPx()
    drawOrbit(Orbit1, theta1, front, k, particleIsStar = true)
    // 轨道 2 逆时针（θ 递减）、起始相位差 π
    drawOrbit(Orbit2, PI.toFloat() - theta2, front, k, particleIsStar = false)
}

private fun DrawScope.drawOrbit(orbit: Orbit, theta: Float, front: Boolean, k: Float, particleIsStar: Boolean) {
    val pivot = Offset(CenterX * k, CenterY * k)
    rotate(orbit.rotation, pivot) {
        // 轨迹线：后层是 sinθ < 0 的上半圈，前层是 sinθ >= 0 的下半圈
        val from = if (front) 0f else PI.toFloat()
        val track = arcPath(orbit, from, from + PI.toFloat(), k)
        strokeWithGlow(
            path = track,
            color = if (front) orbit.frontColor else orbit.backColor,
            width = (if (front) orbit.frontWidth else orbit.backWidth) * k,
            dash = (if (front) orbit.frontDash else orbit.backDash)?.map { it * k }?.toFloatArray(),
            round = false,
        )

        // 流光拖尾：从头往后退半圈的 trailFraction，落在本层的那几段才画
        val trailTheta = PI.toFloat() * orbit.trailFraction
        val trail = Path()
        var open = false
        for (i in 0..Sample) {
            val t = theta - orbit.direction * trailTheta * (1f - i / Sample.toFloat())
            val inFront = sin(t) >= 0f
            if (inFront == front) {
                val p = orbit.pointAt(t, k)
                if (!open) {
                    trail.moveTo(p.x, p.y)
                    open = true
                } else {
                    trail.lineTo(p.x, p.y)
                }
            } else {
                open = false
            }
        }
        strokeWithGlow(
            path = trail,
            color = Color.White,
            width = (if (front) orbit.trailFrontWidth else orbit.trailBackWidth) * k,
            dash = null,
            round = true,
        )

        // 粒子只在前半圈显示
        if (front && sin(theta) >= 0f) {
            val head = orbit.pointAt(theta, k)
            withTransform({
                translate(head.x, head.y)
                scale(k, k, Offset.Zero)
            }) {
                if (particleIsStar) {
                    fillWithGlow(StarPath, Color.White, k)
                } else {
                    val dot = Path().apply { addOval(Rect(Offset.Zero, 2.8f)) }
                    fillWithGlow(dot, Color.White.copy(alpha = 0.95f), k)
                }
            }
        }
    }
}

private fun arcPath(orbit: Orbit, from: Float, to: Float, k: Float): Path {
    val path = Path()
    for (i in 0..Sample * 2) {
        val t = from + (to - from) * i / (Sample * 2f)
        val p = orbit.pointAt(t, k)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    return path
}

/** NOIR `drop-shadow(0 0 4px rgba(255,255,255,0.85))`：先在底下垫一层模糊的白，再画实线。API 28 以下硬件画布不支持模糊遮罩，只画实线。 */
private fun DrawScope.strokeWithGlow(path: Path, color: Color, width: Float, dash: FloatArray?, round: Boolean) {
    val effect = dash?.let { PathEffect.dashPathEffect(it, 0f) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                isAntiAlias = true
                style = PaintingStyle.Stroke
                strokeWidth = width
                strokeCap = if (round) StrokeCap.Round else StrokeCap.Butt
                pathEffect = effect
                this.color = Color.White.copy(alpha = 0.85f)
            }
            paint.asFrameworkPaint().maskFilter = BlurMaskFilter(4.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(path, paint)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width, cap = if (round) StrokeCap.Round else StrokeCap.Butt, pathEffect = effect),
    )
}

/** 粒子的柔光。已经在 scale(k) 之后，模糊半径要除回 k，保持 4dp。 */
private fun DrawScope.fillWithGlow(path: Path, color: Color, k: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                isAntiAlias = true
                style = PaintingStyle.Fill
                this.color = Color.White.copy(alpha = 0.85f)
            }
            paint.asFrameworkPaint().maskFilter =
                BlurMaskFilter(4.dp.toPx() / k, BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(path, paint)
        }
    }
    drawPath(path = path, color = color)
}
