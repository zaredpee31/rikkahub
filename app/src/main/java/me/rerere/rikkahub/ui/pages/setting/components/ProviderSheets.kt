package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTextButton
import me.rerere.rikkahub.ui.theme.palaceColors

// 新建服务商时先选类型的面板
@Composable
internal fun <T> ProviderTypeSheet(
    title: String,
    types: List<T>,
    typeName: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = palaceColors()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        containerColor = c.bgTop,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PalaceSection("TYPE", title)
            PalaceGlass {
                types.fastForEachIndexed { index, type ->
                    val name = typeName(type)
                    if (index > 0) PalaceDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(type) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(c.chip),
                            contentAlignment = Alignment.Center,
                        ) {
                            AutoAIIcon(name = name, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = name,
                            fontSize = 14.sp,
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(HugeIcons.ArrowRight01, null, Modifier.size(14.dp), tint = c.faint)
                    }
                }
            }
        }
    }
}

// 新建 / 编辑服务商的面板：标题、表单、底部的取消和确认；确认之前改动只留在面板里
@Composable
internal fun <T> ProviderEditSheet(
    title: String,
    initial: T,
    confirmText: String,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable (value: T, onValueChange: (T) -> Unit, modifier: Modifier) -> Unit,
) {
    var current by remember(initial) { mutableStateOf(initial) }
    val c = palaceColors()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        containerColor = c.bgTop,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = c.ink,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            content(current, { current = it }, Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                PalaceTextButton(stringResource(R.string.cancel), onClick = onDismiss)
                PalaceTextButton(
                    text = confirmText,
                    onClick = {
                        onConfirm(current)
                        onDismiss()
                    },
                )
            }
        }
    }
}
