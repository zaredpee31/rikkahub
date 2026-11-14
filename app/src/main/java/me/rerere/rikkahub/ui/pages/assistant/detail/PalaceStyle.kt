package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Book02
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.FingerPrint
import me.rerere.hugeicons.stroke.Moon02
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Sun01
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.rikkahub.companion.DottedLine
import me.rerere.rikkahub.companion.MonoText
import me.rerere.rikkahub.companion.Snow
import me.rerere.rikkahub.data.palace.PalaceRoom
import me.rerere.rikkahub.ui.theme.LocalDarkMode

/** 记忆页的配色：浅色直接用消息页的雪天配色，深色是同一套的暗版 */
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
    val dark: Boolean,
)

private val LightPalace = PalaceColors(
    bgTop = Snow.bgTop, bgMid = Snow.bgMid, bgBottom = Snow.bgBottom,
    glass = Snow.glass, glassStrong = Snow.glassStrong, border = Color(0x99FFFFFF),
    ink = Snow.ink, ink2 = Snow.ink2, mute = Snow.mute, faint = Snow.faint, dot = Snow.dot,
    accent = Snow.dark, onAccent = Color.White, chip = Snow.chip,
    error = Snow.error, ok = Snow.ok, warnBg = Color(0xFFF3EEE4), warnInk = Color(0xFF7A6340),
    dark = false,
)

private val DarkPalace = PalaceColors(
    bgTop = Color(0xFF222326), bgMid = Color(0xFF1C1D1F), bgBottom = Color(0xFF161718),
    glass = Color(0xCC2B2D31), glassStrong = Color(0xF22D2F33), border = Color(0x14FFFFFF),
    ink = Color(0xFFE7E8EA), ink2 = Color(0xFFBABDC3), mute = Color(0xFF8C9097), faint = Color(0xFF5F6369), dot = Color(0xFF46494E),
    accent = Color(0xFFDADBDD), onAccent = Color(0xFF1F2023), chip = Color(0xFF35373B),
    error = Color(0xFFD38E87), ok = Color(0xFF8FB89A), warnBg = Color(0xFF3A342A), warnInk = Color(0xFFE2CFA8),
    dark = true,
)

@Composable
internal fun palaceColors(): PalaceColors = if (LocalDarkMode.current) DarkPalace else LightPalace

/** 七个房间各自的浅色，浅色模式下是雪天里的一点颜色，深色模式再压暗 */
internal fun PalaceColors.roomTint(room: String): Color = when (room) {
    PalaceRoom.LIVING -> if (dark) Color(0xFF2C3530) else Color(0xFFEAF1EC)
    PalaceRoom.BEDROOM -> if (dark) Color(0xFF352C30) else Color(0xFFF4EAEB)
    PalaceRoom.STUDY -> if (dark) Color(0xFF2C3138) else Color(0xFFE9EEF3)
    PalaceRoom.USER -> if (dark) Color(0xFF35332C) else Color(0xFFF3F0E7)
    PalaceRoom.SELF -> if (dark) Color(0xFF322E38) else Color(0xFFF0ECF3)
    PalaceRoom.ATTIC -> if (dark) Color(0xFF323335) else Color(0xFFEDEEEF)
    PalaceRoom.WINDOWSILL -> if (dark) Color(0xFF35302C) else Color(0xFFF3EEE8)
    else -> chip
}

internal fun PalaceColors.roomIcon(room: String): ImageVector = when (room) {
    PalaceRoom.LIVING -> HugeIcons.BubbleChat
    PalaceRoom.BEDROOM -> HugeIcons.Favourite
    PalaceRoom.STUDY -> HugeIcons.Book02
    PalaceRoom.USER -> HugeIcons.UserCircle
    PalaceRoom.SELF -> HugeIcons.FingerPrint
    PalaceRoom.ATTIC -> HugeIcons.Moon02
    PalaceRoom.WINDOWSILL -> HugeIcons.Sun01
    else -> HugeIcons.BubbleChat
}

/** 雪天的灰白渐变底，内容自己处理滚动和边距 */
@Composable
internal fun PalaceBackdrop(content: @Composable () -> Unit) {
    val c = palaceColors()
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bgMid, c.bgBottom)))
    ) { content() }
}

/** 磨砂顶栏：左返回，中标题加一行等宽小字，右边可选一个圆形按钮 */
@Composable
internal fun PalaceTopBar(
    title: String,
    sub: String,
    onBack: () -> Unit,
    rightIcon: ImageVector? = null,
    onRight: () -> Unit = {},
) {
    val c = palaceColors()
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
            .background(c.glassStrong)
            .border(0.5.dp, c.border, RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PalaceRoundIcon(HugeIcons.ArrowLeft01, onClick = onBack)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            MonoText(sub, size = 9f, color = c.mute)
        }
        if (rightIcon != null) PalaceRoundIcon(rightIcon, onClick = onRight)
    }
}

@Composable
internal fun PalaceRoundIcon(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 36.dp, onClick: () -> Unit) {
    val c = palaceColors()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(c.glassStrong)
            .border(0.5.dp, c.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.46f), tint = c.ink2)
    }
}

/** 一块磨砂面板，圆角 22 */
@Composable
internal fun PalaceGlass(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    shape: Shape = RoundedCornerShape(22.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = palaceColors()
    Column(
        modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(shape)
            .background(tint ?: c.glass)
            .border(0.5.dp, c.border, shape),
        content = content,
    )
}

/** 小标题：等宽标签、标题、一条点线 */
@Composable
internal fun PalaceSection(tag: String, title: String) {
    val c = palaceColors()
    Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MonoText(tag, size = 8.5f, color = c.mute)
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 12.5.sp, color = c.ink2, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(10.dp))
        DottedLine(Modifier.weight(1f), color = c.dot)
    }
}

/** 一行搜索框，磨砂底 */
@Composable
internal fun PalaceSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = palaceColors()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp, color = c.ink),
        cursorBrush = SolidColor(c.ink),
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.glassStrong)
                    .border(0.5.dp, c.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HugeIcons.Search01, null, Modifier.size(15.dp), tint = c.mute)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, fontSize = 13.sp, color = c.faint)
                    inner()
                }
            }
        },
    )
}

/** 浅色提示条，整条可点 */
@Composable
internal fun PalaceBanner(text: String, onClick: (() -> Unit)? = null, warn: Boolean = false) {
    val c = palaceColors()
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (warn) c.warnBg else c.glassStrong)
            .border(0.5.dp, if (warn) c.warnInk.copy(alpha = 0.3f) else c.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(HugeIcons.Alert02, null, Modifier.size(15.dp), tint = if (warn) c.warnInk else c.mute)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = if (warn) c.warnInk else c.ink2, modifier = Modifier.weight(1f))
        if (onClick != null) Icon(HugeIcons.ArrowRight01, null, Modifier.size(14.dp), tint = c.faint)
    }
}

/** 两个字的小按钮 */
@Composable
internal fun PalaceTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = palaceColors()
    Text(
        text,
        fontSize = 13.sp,
        color = if (enabled) c.ink else c.faint,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 子页面的通用骨架：顶栏加可滚动内容，底部留出导航栏 */
@Composable
internal fun PalaceSubPage(title: String, sub: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(title, sub, onBack)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                content = content,
            )
        }
    }
}
