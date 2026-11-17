package me.rerere.rikkahub.ui.pages.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.ui.components.ui.ItemAction
import me.rerere.rikkahub.ui.components.ui.ItemActionMenu
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.EditState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantImporter
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceCheckRow
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDialog
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceField
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.ui.components.RikkaConfirmDialog
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun AssistantPage(vm: AssistantVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val createState = useEditState<Assistant> {
        vm.addAssistant(it)
    }
    val navController = LocalNavController.current

    // 搜索关键词状态
    var searchQuery by remember { mutableStateOf("") }
    // 待删除的助手
    var deleteTarget by remember { mutableStateOf<Assistant?>(null) }
    // 待复制的助手(仅在有独立记忆时需要询问是否一并复制)
    var cloneTarget by remember { mutableStateOf<Assistant?>(null) }
    var cloneWithMemories by remember { mutableStateOf(false) }

    // 根据搜索关键词过滤助手
    val filteredAssistants = remember(settings.assistants, searchQuery) {
        settings.assistants.filter { assistant ->
            searchQuery.isBlank() || assistant.name.contains(searchQuery, ignoreCase = true)
        }
    }

    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = stringResource(R.string.assistant_page_title),
                sub = "ASSISTANTS",
                rightIcon = HugeIcons.Add01,
                onRight = { createState.open(Assistant()) },
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val lazyListState = rememberLazyListState()
                val isFiltering = searchQuery.isNotBlank()
                val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    if (!isFiltering) {
                        val newAssistants = settings.assistants.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                        vm.updateSettings { it.copy(assistants = newAssistants) }
                    }
                }

                AssistantSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = stringResource(R.string.assistant_page_search_placeholder),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    state = lazyListState,
                ) {
                    items(filteredAssistants, key = { assistant -> assistant.id }) { assistant ->
                        ReorderableItem(
                            state = reorderableState,
                            key = assistant.id,
                        ) { isDragging ->
                            val memories by vm.getMemories(assistant).collectAsStateWithLifecycle(
                                initialValue = emptyList(),
                            )
                            AssistantItem(
                                assistant = assistant,
                                settings = settings,
                                memories = memories,
                                onEdit = {
                                    navController.navigate(Screen.AssistantDetail(id = assistant.id.toString()))
                                },
                                onCopy = {
                                    // 全局记忆是共享的, 复制后的助手本来就能读到, 无需询问
                                    if (!assistant.useGlobalMemory && memories.isNotEmpty()) {
                                        cloneWithMemories = false
                                        cloneTarget = assistant
                                    } else {
                                        vm.copyAssistant(assistant)
                                    }
                                },
                                onDelete = { deleteTarget = assistant },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .then(longPressReorder(isDragging, enabled = !isFiltering)),
                            )
                        }
                    }
                }
            }
        }
    }

    AssistantCreationSheet(createState)

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.assistant_page_delete),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let { vm.removeAssistant(it) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.assistant_page_delete_dialog_text))
    }

    RikkaConfirmDialog(
        show = cloneTarget != null,
        title = stringResource(R.string.assistant_page_clone),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            cloneTarget?.let { vm.copyAssistant(it, copyMemories = cloneWithMemories) }
            cloneTarget = null
        },
        onDismiss = { cloneTarget = null },
    ) {
        PalaceCheckRow(
            title = stringResource(R.string.assistant_page_clone_with_memories),
            desc = null,
            checked = cloneWithMemories,
            onChange = { cloneWithMemories = it },
        )
    }
}

/** 搜索框：磨砂底，有内容时右侧带一个清除按钮。PalaceSearchField 没有清除按钮，所以这里单独画 */
@Composable
private fun AssistantSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = palaceColors()
    val shape = CircleShape
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.5.sp, color = c.ink),
        cursorBrush = SolidColor(c.ink),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(c.glass)
                    .border(0.5.dp, c.border, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HugeIcons.Search01, null, Modifier.size(15.dp), tint = c.mute)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, fontSize = 13.5.sp, color = c.faint)
                    inner()
                }
                if (value.isNotBlank()) {
                    Icon(
                        HugeIcons.Cancel01,
                        null,
                        Modifier.size(15.dp).clip(CircleShape).clickable { onValueChange("") },
                        tint = c.mute,
                    )
                }
            }
        },
    )
}

@Composable
private fun AssistantCreationSheet(state: EditState<Assistant>) {
    state.EditStateContent { assistant, update ->
        PalaceDialog(
            title = stringResource(R.string.assistant_page_add),
            onDismiss = { state.dismiss() },
            confirmText = stringResource(R.string.assistant_page_save),
            dismissText = stringResource(R.string.assistant_page_cancel),
            onConfirm = { state.confirm() },
        ) {
            PalaceField(
                value = assistant.name,
                onValueChange = { update(assistant.copy(name = it)) },
                label = stringResource(R.string.assistant_page_name),
                singleLine = true,
            )
            AssistantImporter(
                onUpdate = {
                    update(it)
                    state.confirm()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AssistantItem(
    assistant: Assistant,
    settings: Settings,
    modifier: Modifier = Modifier,
    memories: List<MemoryFile>,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = palaceColors()
    // 正在用的助手（阿辞）和最后一个助手不能删，其余都能删
    val canDelete = assistant.id != settings.assistantId && settings.assistants.size > 1
    PalaceGlass(modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UIAvatar(
                name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                value = assistant.avatar,
                modifier = Modifier
                    .size(48.dp)
                    .heroAnimation("assistant_${assistant.id}"),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                    fontSize = 15.sp,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (assistant.enableMemory) {
                    Spacer(Modifier.size(3.dp))
                    Text(
                        text = stringResource(R.string.assistant_page_memory_count, memories.size),
                        fontSize = 11.5.sp,
                        color = c.mute,
                    )
                }
            }

            ItemActionMenu(
                actions = listOf(
                    ItemAction(
                        text = stringResource(R.string.assistant_page_clone),
                        icon = HugeIcons.Copy01,
                        onClick = onCopy,
                    ),
                    ItemAction(
                        text = stringResource(R.string.assistant_page_delete),
                        icon = HugeIcons.Delete01,
                        destructive = true,
                        enabled = canDelete,
                        onClick = onDelete,
                    ),
                ),
            )
        }
    }
}
