package me.rerere.rikkahub.ui.theme

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import me.rerere.rikkahub.utils.getActivity

private val ExtendLightColors = lightExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

/** 已经没有深色模式，恒为 false。保留它是因为还有几处按它选资源。 */
val LocalDarkMode = compositionLocalOf { false }

@Composable
fun RikkahubTheme(content: @Composable () -> Unit) {
    val colorScheme = appColorScheme(palaceColors())

    // 更新状态栏图标颜色：配色固定为浅色，图标固定为深色
    val view = LocalView.current
    val activity = view.context.getActivity()
    // 浮窗可能使用 Application Context，没有可更新系统栏的 Activity。
    if (!view.isInEditMode && activity != null) {
        DisposableEffect(view, activity) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, view)
            val previousLightStatusBars = controller.isAppearanceLightStatusBars
            val previousLightNavigationBars = controller.isAppearanceLightNavigationBars
            controller.apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
            onDispose {
                controller.isAppearanceLightStatusBars = previousLightStatusBars
                controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }

    CompositionLocalProvider(
        LocalExtendColors provides ExtendLightColors,
        LocalOverscrollFactory provides null,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
            motionScheme = MotionScheme.expressive()
        )
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
