package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.PalaceButton
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceRoundIcon
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.utils.copyMessageToClipboard

@Composable
fun ChatMessageCopySheet(
    message: UIMessage,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val c = palaceColors()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        sheetGesturesEnabled = false,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header：关闭用圆形图标按钮，标题 16sp，复制全部用 chip 小按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PalaceRoundIcon(HugeIcons.Cancel01, onClick = onDismissRequest)

                Text(
                    text = stringResource(R.string.select_and_copy),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.ink,
                )

                PalaceButton(
                    text = stringResource(R.string.copy_all),
                    onClick = {
                        context.copyMessageToClipboard(message)
                        onDismissRequest()
                    },
                )
            }

            // Content
            val textParts =
                message.parts.filterIsInstance<UIMessagePart.Text>().filter { it.text.isNotBlank() }

            if (textParts.isEmpty()) {
                // No text content available
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_text_content_to_copy),
                        fontSize = 13.sp,
                        color = c.mute,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                PalaceGlass(modifier = Modifier.weight(1f)) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            textParts.fastForEach { textPart ->
                                Text(
                                    text = textPart.text,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                    color = c.ink,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
