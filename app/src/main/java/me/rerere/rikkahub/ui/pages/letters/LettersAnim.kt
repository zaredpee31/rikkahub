package me.rerere.rikkahub.ui.pages.letters

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/** 系统把动画关了（动画时长缩放为 0）就跳过飞行和等待。 */
internal fun reduceMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** 0→1 循环的进度。enabled=false 时恒为 0，不跑动画。 */
@Composable
internal fun rememberLoop(ms: Int, reverse: Boolean = false, delayMs: Int = 0, enabled: Boolean = true): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "letters-loop")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(ms, easing = LinearEasing),
            repeatMode = if (reverse) RepeatMode.Reverse else RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "letters-loop",
    )
}

/** 网页 --noise 的替身：160x160 的灰色颗粒，铺开当纸的纹理。 */
@Composable
internal fun rememberNoiseBrush(): ShaderBrush = remember {
    val size = 160
    val rnd = Random(20261118)
    val px = IntArray(size * size) {
        val a = (rnd.nextFloat() * rnd.nextFloat() * 150f).toInt().coerceIn(0, 255)
        (a shl 24) or (0x4D shl 16) or (0x4D shl 8) or 0x4D
    }
    val bmp = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}
