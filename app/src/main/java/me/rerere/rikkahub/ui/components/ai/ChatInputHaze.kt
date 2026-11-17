package me.rerere.rikkahub.ui.components.ai

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
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
 * 聊天页顶栏和输入栏共用的磨砂玻璃：高不透明度白底 + 大半径背景模糊 + 极淡噪点，无描边无投影。
 *
 * 参数只在这里定一次，顶栏和输入栏都调用它，避免两处各写一套。
 * 模糊需要 Android 12（API 31）的 RenderEffect，更低版本拿不到真实模糊，
 * 这时把白底提到 [FrostFallbackAlpha]，彻底挡住后面滚过的文字。
 */
@Composable
internal fun Modifier.chatFrost(hazeState: HazeState): Modifier {
    val tintAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) FrostTintAlpha else FrostFallbackAlpha
    return this
        .hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle.Material3 {
                blurRadius(FrostBlurRadius)
                backgroundColor(Color.White.copy(alpha = tintAlpha))
            },
        )
        .drawBehind {
            drawRect(brush = frostNoiseBrush, alpha = FrostNoiseAlpha)
        }
}

/** 磨砂的模糊半径。足够大，滚到下面的文字糊成看不出形状的淡影。 */
internal val FrostBlurRadius = 24.dp

/** 磨砂的白色遮罩不透明度（支持真实模糊的设备）。 */
internal const val FrostTintAlpha = 0.88f

/** 拿不到真实模糊时的白底不透明度，硬要求不低于 0.92。 */
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
