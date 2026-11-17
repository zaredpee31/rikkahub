package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.theme.palaceColors

/**
 * 雪天样式的小按钮：c.chip 底 + c.ink2 文字。
 * [danger] 为 true 时只把文字换成低饱和红（c.error），底色不变，不铺红块。
 */
@Composable
internal fun PalaceButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val c = palaceColors()
    Text(
        text = text,
        fontSize = 13.sp,
        color = when {
            !enabled -> c.faint
            danger -> c.error
            else -> c.ink2
        },
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * 磨砂弹窗：PalaceGlass 卡（圆角 22，和加号面板一致），标题 16sp，内容可滚动，底部一排按钮靠右。
 * 用来替换 Material 默认的 AlertDialog。
 */
@Composable
internal fun PalaceGlassDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = palaceColors()
    Dialog(onDismissRequest = onDismiss) {
        // 弹窗盖在遮罩上，用不透明一点的 glassStrong，字不会被后面的内容干扰
        PalaceGlass(tint = c.glassStrong) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.ink)
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = buttons,
                )
            }
        }
    }
}

/** 确认弹窗：取消 + 确认两个按钮，[danger] 时确认按钮只有文字变红 */
@Composable
internal fun PalaceConfirmDialog(
    show: Boolean,
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    if (!show) return
    val c = palaceColors()
    PalaceGlassDialog(
        title = title,
        onDismiss = onDismiss,
        buttons = {
            PalaceButton(text = dismissText, onClick = onDismiss)
            PalaceButton(text = confirmText, onClick = onConfirm, danger = danger)
        },
    ) {
        Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = c.ink2)
    }
}
