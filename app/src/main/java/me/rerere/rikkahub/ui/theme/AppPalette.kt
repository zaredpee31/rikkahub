package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.companion.Snow

/** 全 app 唯一的配色来源：雪天配色。只有浅色一套，色值来自 [Snow]。 */
@Immutable
internal data class PalaceColors(
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val glass: Color,
    val glassStrong: Color,
    val border: Color,
    val ink: Color,
    val ink2: Color,
    val mute: Color,
    val faint: Color,
    val dot: Color,
    val accent: Color,
    val onAccent: Color,
    val chip: Color,
    val error: Color,
    val ok: Color,
    val warnBg: Color,
    val warnInk: Color,
)

private val LightPalace = PalaceColors(
    bgTop = Snow.bgTop, bgMid = Snow.bgMid, bgBottom = Snow.bgBottom,
    glass = Snow.glass, glassStrong = Snow.glassStrong, border = Color(0x99FFFFFF),
    ink = Snow.ink, ink2 = Snow.ink2, mute = Snow.mute, faint = Snow.faint, dot = Snow.dot,
    accent = Snow.dark, onAccent = Color.White, chip = Snow.chip,
    error = Snow.error, ok = Snow.ok, warnBg = Color(0xFFF3EEE4), warnInk = Color(0xFF7A6340),
)

@Composable
internal fun palaceColors(): PalaceColors = LightPalace

/**
 * 把 Palace 配色映射到 Material 的 ColorScheme，每个字段都显式给值，
 * 不留 Material 默认的紫色/蓝色。
 */
internal fun appColorScheme(p: PalaceColors): ColorScheme = lightColorScheme(
    primary = p.accent,
    onPrimary = p.onAccent,
    primaryContainer = p.chip,
    onPrimaryContainer = p.ink,
    inversePrimary = p.faint,
    secondary = p.ink2,
    onSecondary = p.onAccent,
    secondaryContainer = p.chip,
    onSecondaryContainer = p.ink2,
    tertiary = p.mute,
    onTertiary = p.onAccent,
    tertiaryContainer = p.chip,
    onTertiaryContainer = p.ink2,
    background = p.bgMid,
    onBackground = p.ink,
    surface = p.bgMid,
    onSurface = p.ink,
    surfaceVariant = p.chip,
    onSurfaceVariant = p.ink2,
    surfaceTint = p.accent,
    inverseSurface = p.ink,
    inverseOnSurface = p.bgTop,
    error = p.error,
    onError = Color.White,
    errorContainer = p.warnBg,
    onErrorContainer = p.error,
    outline = p.dot,
    outlineVariant = p.faint,
    scrim = Color.Black,
    surfaceBright = p.glassStrong,
    surfaceDim = p.bgBottom,
    surfaceContainer = p.chip,
    surfaceContainerHigh = p.glass,
    surfaceContainerHighest = p.glassStrong,
    surfaceContainerLow = p.bgTop,
    surfaceContainerLowest = p.glassStrong,
    primaryFixed = p.chip,
    primaryFixedDim = p.faint,
    onPrimaryFixed = p.ink,
    onPrimaryFixedVariant = p.ink2,
    secondaryFixed = p.chip,
    secondaryFixedDim = p.faint,
    onSecondaryFixed = p.ink,
    onSecondaryFixedVariant = p.ink2,
    tertiaryFixed = p.chip,
    tertiaryFixedDim = p.faint,
    onTertiaryFixed = p.ink,
    onTertiaryFixedVariant = p.ink2,
)
