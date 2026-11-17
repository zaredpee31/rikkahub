package me.rerere.rikkahub.ui.components.ai

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3

/**
 * NOIR 液态玻璃的五层，按 CSS 原值照抄，不在各处重写：
 *
 * 1. 底：实色白 0.58；渐变默认不画，只有聊天气泡（GlassGradient）和顶栏（GlassTopBarGradient）显式传入
 * 2. 模糊 18dp + 饱和度 180%（`backdrop-filter: blur(18px) saturate(180%)`）
 * 3. 描边 0.5dp 白 0.90
 * 4. 内高光两条：顶边 0.5dp 纯白，底边 0.5dp 白 0.25
 * 5. 外阴影两层叠：0 1dp 3dp 黑 0.05 + 0 4dp 12dp 黑 0.04，用 Compose 的 dropShadow，每层链一个
 *
 * [fillAlpha] 覆盖实色白底的不透明度（横条卡片是 0.56，头像衬垫是 0.5）。
 * [edgeAlpha] 覆盖描边的不透明度（横条卡片是 1.00）。
 * [blurRadius] 覆盖模糊半径（顶栏是 20dp，头像衬垫是 8dp）。
 * [shadows] 覆盖外阴影（头像衬垫、横条是单层 0 1dp 3dp 黑 0.04）。
 * [gradient] 覆盖压在实色上的那层渐变（顶栏是三段渐变，传 null 则不画）。
 * [innerHighlight] 为 false 时不画两条内高光。
 * [topFade] 为 true 时在底色和渐变上再叠一层「上实下透」的白（助手气泡用），描边和内高光画在它上面。
 *
 * 饱和度：haze 没有 saturate 参数，用它的 colorEffects 挂一个
 * `ColorMatrix.setToSaturation(1.8f)` 的 ColorFilter。这个矩阵只对模糊后的背景起作用，
 * 前景内容不受影响，和 CSS 的 backdrop-filter 行为一致。
 * API 31 以下 RenderEffect 不可用，饱和度做不到，退回纯模糊 + 0.95 白底保证字看得清。
 */
@Composable
internal fun Modifier.liquidGlass(
    hazeState: HazeState,
    shape: Shape,
    fillAlpha: Float = GlassFillAlpha,
    edgeAlpha: Float = GlassEdgeAlpha,
    blurRadius: Dp = GlassBlurRadius,
    shadows: List<GlassShadow> = GlassShadows,
    gradient: Brush? = null,
    innerHighlight: Boolean = true,
    topFade: Boolean = false,
): Modifier {
    val blur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle.Material3(containerColor = Color.Transparent) {
                blurRadius(blurRadius)
                colorEffects(listOf(glassSaturation))
            },
        )
    } else {
        hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle.Material3 {
                blurRadius(GlassFallbackBlurRadius)
                backgroundColor(Color.White.copy(alpha = GlassFallbackFillAlpha))
            },
        )
    }
    // 阴影画在裁剪外面，所以放在 clip 前面；模糊和底色在 clip 里面
    val shadowed = shadows.fold(this) { acc, shadow ->
        acc.dropShadow(
            shape,
            Shadow(
                radius = shadow.blurRadius,
                color = shadow.color,
                offset = DpOffset(0.dp, shadow.offsetY),
            ),
        )
    }
    return shadowed
        .clip(shape)
        .then(blur)
        .drawBehind {
            drawRect(Color.White.copy(alpha = fillAlpha))
            if (gradient != null) drawRect(brush = gradient)
            if (topFade) drawTopFade()
        }
        .drawWithContent {
            drawContent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (edgeAlpha > 0f) drawGlassEdge(shape, edgeAlpha)
                if (innerHighlight) drawGlassInnerHighlight(shape)
            }
        }
}

/** 聊天页把 hazeState 放进来，消息里的横条卡片等深层组件从这里取。 */
internal val LocalGlassHazeState = staticCompositionLocalOf<HazeState?> { null }

/** 一层外阴影：垂直偏移、模糊半径、颜色。 */
internal data class GlassShadow(val offsetY: Dp, val blurRadius: Dp, val color: Color)

/** 实色白底的不透明度，`--lg-fill-bubble`。 */
internal const val GlassFillAlpha = 0.58f

/** 描边白色的不透明度，`--lg-edge`。 */
internal const val GlassEdgeAlpha = 0.90f

/** 描边粗细，`border: 0.5px`。 */
internal val GlassEdgeWidth = 0.5.dp

/** 背景模糊半径，`--lg-blur`。 */
internal val GlassBlurRadius = 18.dp

/** 饱和度倍数，`--lg-sat: 180%`。 */
internal const val GlassSaturation = 1.8f

/** 内高光粗细，`inset 0 0.5px 0`。 */
internal val GlassHighlightWidth = 0.5.dp

/** 底边内高光的白色不透明度，`inset 0 -0.5px 0 rgba(255,255,255,0.25)`。 */
internal const val GlassHighlightBottomAlpha = 0.25f

/** 外阴影两层，`--lg-shadow`。 */
internal val GlassShadows = listOf(
    GlassShadow(offsetY = 1.dp, blurRadius = 3.dp, color = Color.Black.copy(alpha = 0.05f)),
    GlassShadow(offsetY = 4.dp, blurRadius = 12.dp, color = Color.Black.copy(alpha = 0.04f)),
)

/** 压在实色白底上的竖向渐变，到 55% 处结束。 */
internal val GlassGradient: Brush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.35f),
    0.55f to Color.White.copy(alpha = 0.12f),
)

/** 顶栏的三段渐变，和气泡的不同。 */
internal val GlassTopBarGradient: Brush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.32f),
    0.6f to Color.White.copy(alpha = 0.22f),
    1f to Color.White.copy(alpha = 0.14f),
)

/** 顶栏模糊半径，`backdrop-filter: blur(20px)`。 */
internal val GlassTopBarBlurRadius = 20.dp

/** API 31 以下退回纯模糊时的半径。 */
internal val GlassFallbackBlurRadius = 24.dp

/** API 31 以下拿不到饱和度时的白底不透明度，保证字看得清。 */
internal const val GlassFallbackFillAlpha = 0.95f

/** 180% 饱和度的颜色矩阵，只作用于 haze 模糊后的背景。 */
private val glassSaturation: HazeColorEffect = HazeColorEffect.colorFilter(
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(GlassSaturation) }),
)

/**
 * 助手气泡的上实下透：顶部到 30% 白 0.90 实色，30% 到 65% 渐变到透明，其下不叠。
 * 气泡高度超过 240dp 时改成固定值：实色 72dp + 渐隐 96dp。
 */
private fun DrawScope.drawTopFade() {
    val solid: Float
    val end: Float
    if (size.height > 240.dp.toPx()) {
        solid = 72.dp.toPx()
        end = solid + 96.dp.toPx()
    } else {
        solid = size.height * 0.30f
        end = size.height * 0.65f
    }
    val top = Color.White.copy(alpha = 0.90f)
    drawRect(
        brush = Brush.verticalGradient(
            0f to top,
            (solid / end) to top,
            1f to Color.White.copy(alpha = 0f),
            startY = 0f,
            endY = end,
        ),
        size = Size(size.width, end),
    )
}

/** 0.5dp 描边，内缩半个线宽让整条落在组件范围内。 */
private fun DrawScope.drawGlassEdge(shape: Shape, edgeAlpha: Float) {
    val strokePx = GlassEdgeWidth.toPx()
    inset(strokePx / 2f) {
        drawOutline(
            outline = shape.createOutline(size, layoutDirection, this),
            color = Color.White.copy(alpha = edgeAlpha),
            style = Stroke(width = strokePx),
        )
    }
}

/**
 * 两条内高光：顶边纯白、底边白 0.25，各 0.5dp。
 * 把轮廓描边后只露出最上 / 最下 0.5dp 的一条，圆角处自然跟着弯。
 */
private fun DrawScope.drawGlassInnerHighlight(shape: Shape) {
    val band = GlassHighlightWidth.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    clipRect(top = 0f, bottom = band) {
        drawOutline(outline, Color.White, style = Stroke(width = band * 2f))
    }
    clipRect(top = size.height - band, bottom = size.height) {
        drawOutline(outline, Color.White.copy(alpha = GlassHighlightBottomAlpha), style = Stroke(width = band * 2f))
    }
}

/** NOIR 波浪图高度固定 65px，viewBox 高 68，横向拉满（preserveAspectRatio=none）。 */
private val WaveHeight = 65.dp
private const val WaveViewBoxHeight = 68f

/** 波谷最深约 6dp，顶栏玻璃层比布局高度多画这一段，布局高度不变。 */
internal val WaveExtra = 7.dp

/**
 * 波浪路径，viewBox `0 0 1440 68`：
 * `M0,0 L1440,0 L1440,44 C1200,66 960,22 720,44 C480,66 240,22 0,44 Z`
 * 横向 `宽度 / 1440`，纵向固定 `65dp / 68`；平均线 y=44 落在 [meanY]。
 * [fill] 为 false 时只返回波浪曲线（从右端到左端），用来描边。
 */
private fun wavePath(width: Float, meanY: Float, ky: Float, fill: Boolean): Path {
    val kx = width / 1440f
    return Path().apply {
        if (fill) {
            moveTo(0f, 0f)
            lineTo(width, 0f)
            lineTo(width, meanY)
        } else {
            moveTo(width, meanY)
        }
        cubicTo(1200f * kx, meanY + 22f * ky, 960f * kx, meanY - 22f * ky, 720f * kx, meanY)
        cubicTo(480f * kx, meanY + 22f * ky, 240f * kx, meanY - 22f * ky, 0f, meanY)
        if (fill) close()
    }
}

@Stable
internal object WaveBottomShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val ky = with(density) { WaveHeight.toPx() } / WaveViewBoxHeight
        val meanY = size.height - with(density) { WaveExtra.toPx() }
        return Outline.Generic(wavePath(size.width, meanY, ky, fill = true))
    }
}

/** 只沿波浪曲线画 1dp、白 0.95 的描边（NOIR `::before`），上边和两侧不画。画在玻璃外面，不被裁剪。 */
internal fun Modifier.waveBottomEdge(): Modifier = drawWithContent {
    drawContent()
    val ky = WaveHeight.toPx() / WaveViewBoxHeight
    drawPath(
        path = wavePath(size.width, size.height - WaveExtra.toPx(), ky, fill = false),
        color = Color.White.copy(alpha = 0.95f),
        style = Stroke(width = 1.dp.toPx()),
    )
}

/** 测量时比布局高度多 [extra]，布局高度不变；后面的修饰符（玻璃、描边）按加高后的尺寸绘制，波谷画在布局底边之外。 */
internal fun Modifier.extendBottom(extra: Dp): Modifier = layout { measurable, constraints ->
    val extraPx = extra.roundToPx()
    val height = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height + extraPx))
    layout(placeable.width, height) { placeable.place(0, 0) }
}

/**
 * 不模糊的小玻璃（NOIR swipe / mes_buttons 的玻璃覆盖）：外阴影 + 白底 + 0.5dp 描边 + 两条内高光，不加渐变。
 */
internal fun Modifier.flatGlass(
    shape: Shape,
    fillAlpha: Float,
    edgeAlpha: Float,
    shadows: List<GlassShadow> = listOf(GlassShadow(1.dp, 3.dp, Color.Black.copy(alpha = 0.04f))),
): Modifier {
    val shadowed = shadows.fold(this) { acc, shadow ->
        acc.dropShadow(
            shape,
            Shadow(
                radius = shadow.blurRadius,
                color = shadow.color,
                offset = DpOffset(0.dp, shadow.offsetY),
            ),
        )
    }
    return shadowed
        .clip(shape)
        .drawBehind { drawRect(Color.White.copy(alpha = fillAlpha)) }
        .drawWithContent {
            drawContent()
            drawGlassEdge(shape, edgeAlpha)
            drawGlassInnerHighlight(shape)
        }
}
