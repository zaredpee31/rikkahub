package me.rerere.rikkahub.ui.pages.history;

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Pin
import me.rerere.hugeicons.stroke.PinOff
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.Delete01
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.key
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceRoundIcon
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceSection
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ui.SwipeToDelete
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel

@Composable
fun HistoryPage(vm: HistoryVM = koinViewModel()) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val conversations by vm.conversations.collectAsStateWithLifecycle()

    val snackMessageDeleted = stringResource(R.string.history_page_conversation_deleted)
    val snackMessageUndo = stringResource(R.string.history_page_undo)
    PalaceBackdrop {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // PalaceTopBar 右侧只有一个图标位，搜索和删除两个按钮叠在顶栏右侧，行为不变
                Box(Modifier.fillMaxWidth()) {
                    PalaceTopBar(
                        title = stringResource(R.string.history_page_title),
                        sub = "HISTORY",
                        onBack = { navController.popBackStack() },
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(end = 14.dp, top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PalaceRoundIcon(
                            icon = HugeIcons.GlobalSearch,
                            onClick = { navController.navigate(Screen.MessageSearch) },
                        )
                        PalaceRoundIcon(
                            icon = HugeIcons.Delete01,
                            onClick = { showDeleteAllDialog = true },
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "section") {
                        PalaceSection("CONVERSATIONS", "对话")
                    }
                    if (conversations.isNotEmpty()) {
                        item(key = "conversations") {
                            PalaceGlass {
                                conversations.forEachIndexed { index, conversation ->
                                    key(conversation.id) {
                                        if (index > 0) PalaceDivider()
                                        SwipeableConversationItem(
                                            conversation = conversation,
                                            onClick = {
                                                navigateToChatPage(navController, conversation.id)
                                            },
                                            onDelete = {
                                                scope.launch {
                                                    // 先获取完整的对话数据（包含 messageNodes），用于撤销恢复
                                                    val fullConversation = vm.getFullConversation(conversation.id) ?: conversation
                                                    vm.deleteConversation(conversation)
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = snackMessageDeleted,
                                                        actionLabel = snackMessageUndo,
                                                        withDismissAction = true,
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        vm.restoreConversation(fullConversation)
                                                    }
                                                }
                                            },
                                            onTogglePin = { vm.togglePinStatus(conversation.id) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.history_page_delete_all_conversations)) },
            text = { Text(stringResource(R.string.history_page_delete_all_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteAllConversations()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text(stringResource(R.string.history_page_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAllDialog = false }
                ) {
                    Text(stringResource(R.string.history_page_cancel))
                }
            }
        )
    }
}

@Composable
private fun SwipeableConversationItem(
    conversation: Conversation,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    SwipeToDelete(
        onDelete = onDelete,
        modifier = modifier,
        shape = RectangleShape,
    ) {
        ConversationItem(
            conversation = conversation,
            onTogglePin = onTogglePin,
            onClick = onClick
        )
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    modifier: Modifier = Modifier,
    onTogglePin: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    val c = palaceColors()
    Row(
        modifier
            .fillMaxWidth()
            .background(c.glass)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(c.chip),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (conversation.isPinned) HugeIcons.Pin else HugeIcons.BubbleChat,
                null,
                Modifier.size(16.dp),
                tint = c.ink2,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = conversation.title.ifBlank { stringResource(R.string.history_page_new_conversation) }.trim(),
                fontSize = 14.sp,
                color = c.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                conversation.createAt.toLocalDateTime(),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = c.mute,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onTogglePin),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (conversation.isPinned) HugeIcons.PinOff else HugeIcons.Pin,
                contentDescription = if (conversation.isPinned) stringResource(R.string.history_page_unpin) else stringResource(
                    R.string.history_page_pin
                ),
                modifier = Modifier.size(14.dp),
                tint = c.mute,
            )
        }
    }
}
