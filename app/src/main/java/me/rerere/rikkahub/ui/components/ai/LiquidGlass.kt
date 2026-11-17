package me.rerere.rikkahub.ui.components.ai

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import kotlin.random.Random

/**
 * NOIR 液态玻璃的五层，按 CSS 原值照抄，不在各处重写：
 *
 * 1. 底两层：实色白 0.58，上面再压一层竖向渐变（白 0.35 → 白 0.12，到 55% 处结束）
 * 2. 模糊 18dp + 饱和度 180%（`backdrop-filter: blur(18px) saturate(180%)`）
 * 3. 描边 0.5dp 白 0.90
 * 4. 内高光两条：顶边 0.5dp 纯白，底边 0.5dp 白 0.25
 * 5. 外阴影两层叠：0 1dp 3dp 黑 0.05 + 0 4dp 12dp 黑 0.04
 *
 * [fillAlpha] 覆盖实色白底的不透明度（横条卡片是 0.56，头像衬垫是 0.5）。
 * [edgeAlpha] 覆盖描边的不透明度（横条卡片是 1.00）。
 * [blurRadius] 覆盖模糊半径（顶栏是 20dp，头像衬垫是 8dp）。
 * [shadows] 覆盖两层外阴影（头像衬垫是单层 0 10dp 24dp 黑 0.08）。
 * [gradient] 覆盖压在实色上的那层渐变（顶栏是三段渐变，传 null 则不画）。
 * [innerHighlight] 为 false 时不画两条内高光。
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
    gradient: Brush? = GlassGradient,
    innerHighlight: Boolean = true,
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
    return this
        .drawBehind { drawGlassShadows(shape, shadows) }
        .clip(shape)
        .then(blur)
        .drawBehind {
            drawRect(Color.White.copy(alpha = fillAlpha))
            if (gradient != null) drawRect(brush = gradient)
            drawRect(brush = glassNoiseBrush, alpha = GlassNoiseAlpha)
        }
        .drawWithContent {
            drawContent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                drawGlassEdge(shape, edgeAlpha)
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

/** 噪点整体强度，只留一点纸面质感。 */
internal const val GlassNoiseAlpha = 0.05f

/** 180% 饱和度的颜色矩阵，只作用于 haze 模糊后的背景。 */
private val glassSaturation: HazeColorEffect = HazeColorEffect.colorFilter(
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(GlassSaturation) }),
)

/** 128x128 平铺噪点，固定种子，只生成一次。 */
private val glassNoiseBrush: ShaderBrush by lazy {
    val size = 128
    val random = Random(7)
    val pixels = IntArray(size * size) {
        val gray = random.nextInt(256)
        (random.nextInt(256) shl 24) or (gray shl 16) or (gray shl 8) or gray
    }
    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * 外阴影：按 CSS `0 offsetY blur color` 的语义，把轮廓下移 offsetY，
 * 再用若干层逐渐外扩、逐渐变淡的轮廓叠出模糊边缘（Compose 自带的 shadow 只有一层且偏硬）。
 */
private fun DrawScope.drawGlassShadows(shape: Shape, shadows: List<GlassShadow>) {
    val outline = shape.createOutline(size, layoutDirection, this)
    shadows.forEach { shadow ->
        val blurPx = shadow.blurRadius.toPx()
        val offsetPx = shadow.offsetY.toPx()
        val steps = 6
        repeat(steps) { i ->
            val t = (i + 1f) / steps
            val grow = blurPx * t / 2f
            val falloff = (1f - t) * (1f - t)
            withTransform({
                translate(left = 0f, top = offsetPx)
                scale(
                    scaleX = (size.width + grow * 2f) / size.width,
                    scaleY = (size.height + grow * 2f) / size.height,
                )
            }) {
                drawOutline(outline, color = shadow.color, alpha = falloff * 2f / 3f)
            }
        }
    }
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

/**
 * 顶栏下沿的波浪。原始 SVG viewBox `0 0 1440 68`：
 * `M0,0 L1440,0 L1440,44 C1200,66 960,22 720,44 C480,66 240,22 0,44 Z`
 * 横向按 `宽度 / 1440` 等比缩放，纵向用同一个比例，viewBox 底边对齐顶栏底边，
 * 所以顶栏自己的高度一点不变，只是下沿 68 * scale 那一段被波浪裁出形状。
 */
@Stable
internal object WaveBottomShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val scale = size.width / 1440f
        val top = (size.height - 68f * scale).coerceAtLeast(0f)
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, top + 44f * scale)
            cubicTo(
                1200f * scale, top + 66f * scale,
                960f * scale, top + 22f * scale,
                720f * scale, top + 44f * scale,
            )
            cubicTo(
                480f * scale, top + 66f * scale,
                240f * scale, top + 22f * scale,
                0f, top + 44f * scale,
            )
            close()
        }
        return Outline.Generic(path)
    }
}
