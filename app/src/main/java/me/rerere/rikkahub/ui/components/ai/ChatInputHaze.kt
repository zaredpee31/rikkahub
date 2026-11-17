package me.rerere.rikkahub.ui.components.ai

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassStyleScope
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.Material3
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.random.Random

/**
 * 聊天页顶栏、输入栏和发送键共用的液态玻璃：hazeGlass（折射 + 边缘高光）+ 极淡噪点，无白色遮罩。
 *
 * 参数只在这里定一次，各处调用时只传自己的 [shape]。
 * hazeGlass 需要 Android 12（API 31）的 RenderEffect，更低版本退回
 * hazeBlur + [FrostFallbackAlpha] 的白底，保证字看得清。
 */
@Composable
internal fun Modifier.chatFrost(hazeState: HazeState, shape: Shape): Modifier {
    val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hazeGlass(
            input = HazeInput.Sources(hazeState),
            style = GlassStyle.Material3(
                containerColor = Color.White.copy(alpha = GlassContainerAlpha),
                tint = Color.White.copy(alpha = GlassTintAlpha),
            ) {
                // 主题已固定浅色，不再读 LocalDarkMode
                appearanceResponse(dark = false)
                optics(
                    GlassDefaults.optics.copy(
                        blurRadius = OpticalSizeValue.Fixed(GlassBlurRadius),
                    )
                )
                shape(shape)
            },
        )
    } else {
        hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle.Material3 {
                blurRadius(FrostFallbackBlurRadius)
                backgroundColor(Color.White.copy(alpha = FrostFallbackAlpha))
            },
        )
    }
    return base.drawBehind {
        drawRect(brush = frostNoiseBrush, alpha = FrostNoiseAlpha)
    }
}

/** 玻璃容器的白色不透明度。越小越透，越大越实。 */
internal const val GlassContainerAlpha = 0.22f

/** 玻璃着色层的白色不透明度。越小越透，越大越实。 */
internal const val GlassTintAlpha = 0.12f

/** 玻璃背景模糊半径。越大底下越糊，越小越能看出背景图形状。 */
internal val GlassBlurRadius = 18.dp

/** 低版本（API < 31）退回 hazeBlur 时的模糊半径。 */
internal val FrostFallbackBlurRadius = 24.dp

/** 低版本拿不到真实模糊时的白底不透明度，硬要求不低于 0.92。 */
internal const val FrostFallbackAlpha = 0.95f

/** 噪点颗粒的整体强度，很淡，只留纸面质感。 */
internal const val FrostNoiseAlpha = 0.05f

/** 平铺的噪点：128x128 随机灰度像素，固定随机种子，只生成一次。 */
private val frostNoiseBrush: ShaderBrush by lazy {
    val size = 128
    val random = Random(7)
    val pixels = IntArray(size * size) {
        val gray = random.nextInt(256)
        val alpha = random.nextInt(256)
        (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray
    }
    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * 输入框容器的背景效果（模糊或玻璃），未开启时原样返回。
 *
 * 开启后容器本身需要保持透明，[containerColor] 由效果负责铺底和着色。
 */
@Composable
internal fun Modifier.chatInputHaze(
    hazeState: HazeState,
    displaySetting: DisplaySetting,
    containerColor: Color,
    shape: RoundedCornerShape,
): Modifier {
    if (!displaySetting.enableBlurEffect) return this
    return when (displaySetting.backgroundEffectType) {
        BackgroundEffectType.BLUR -> hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle.Material3 {
                blurRadius(12.dp)
            },
        )

        BackgroundEffectType.GLASS -> {
            val darkMode = LocalDarkMode.current
            hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = GlassStyle.Material3(
                    containerColor = containerColor,
                    tint = containerColor.copy(alpha = 0.3f),
                ) {
                    appearanceResponse(dark = darkMode)
                    // Keep background text from competing with the input text.
                    optics(
                        GlassDefaults.optics.copy(
                            blurRadius = OpticalSizeValue.Fixed(16.dp),
                            depth = OpticalSizeValue.Fixed(0.5f),
                        )
                    )
                    shape(shape)
                },
            )
        }
    }
}

// Haze 按系统夜间模式选玻璃的明暗预设，应用内单独设置主题时会选错（深色主题下被提亮发白），
// 所以按应用主题显式写入。深色数值对应库里 internal 的 GlassStyle.regularDark。
private fun GlassStyleScope.appearanceResponse(dark: Boolean) {
    if (dark) {
        specularIntensity(0.38f)
        edgeShadow(Color.Black.copy(alpha = 0.32f))
        ambientResponse(0.08f)
        contrast(0.08f)
        whitePoint(-0.22f)
        chromaMultiplier(1.1f)
    } else {
        specularIntensity(GlassDefaults.specularIntensity)
        edgeShadow(GlassDefaults.edgeShadow)
        ambientResponse(GlassDefaults.ambientResponse)
        contrast(GlassDefaults.contrast)
        whitePoint(GlassDefaults.whitePoint)
        chromaMultiplier(GlassDefaults.chromaMultiplier)
    }
}
