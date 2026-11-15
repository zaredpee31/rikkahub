package me.rerere.rikkahub.ui.components.ai

import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant

/** 不带按钮的扩展选择弹层（模式注入 / 世界书 / Skills），给消息页这种自己画入口的地方用；内容和普通窗口完全一样。 */
@Composable
fun ExtensionPickerSheet(
    assistant: Assistant,
    settings: Settings,
    onUpdateAssistant: (Assistant) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        ExtensionPickerPage(
            assistant = assistant,
            settings = settings,
            onUpdateAssistant = onUpdateAssistant,
            onBack = onDismiss,
            onDismissAll = onDismiss,
        )
    }
}
