package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.FolderAdd
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sorting01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.companion.MonoText
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.reading.ReadingIcons
import me.rerere.rikkahub.ui.components.ai.AssistantPicker
import me.rerere.rikkahub.ui.components.ui.BackupReminderCard
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.UpdateCard
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceDivider
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceRow
import me.rerere.rikkahub.ui.pages.assistant.detail.palaceColors
import androidx.compose.ui.draw.clip
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.ui.toast.ToastType
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.readBooleanPreference
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun ChatDrawerContent(
    navController: Navigator,
    settings: Settings,
    drawerVm: ChatDrawerVM,
    currentConversationId: Uuid?,
    onCloseDrawer: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val repo = koinInject<ConversationRepository>()

    val conversations = drawerVm.conversations.collectAsLazyPagingItems()
    val folders by drawerVm.folders.collectAsStateWithLifecycle()
    val selectedFolderId by drawerVm.selectedFolderId.collectAsStateWithLifecycle()
    val conversationListState = rememberLazyListState(
        initialFirstVisibleItemIndex = drawerVm.scrollIndex,
        initialFirstVisibleItemScrollOffset = drawerVm.scrollOffset,
    )

    LaunchedEffect(conversationListState) {
        snapshotFlow {
            conversationListState.firstVisibleItemIndex to
                conversationListState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collectLatest { (index, offset) ->
                drawerVm.saveScrollPosition(index, offset)
            }
    }

    val conversationJobs by drawerVm.conversationJobs.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
    )

    // 昵称编辑状态
    val nicknameEditState = useEditState<String> { newNickname ->
        drawerVm.updateSettings {
            it.copy(
                displaySetting = it.displaySetting.copy(
                    userNickname = newNickname
                )
            )
        }
    }

    // 移动对话状态
    var showMoveToAssistantSheet by remember { mutableStateOf(false) }
    var conversationToMove by remember { mutableStateOf<Conversation?>(null) }
    val bottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    // 文件夹相关状态
    var showMoveToFolderSheet by remember { mutableStateOf(false) }
    var conversationToMoveFolder by remember { mutableStateOf<Conversation?>(null) }
    val folderSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }

    val updateCheckDisabledUntil = settings.displaySetting.updateCheckDisabledUntilEpochMillis
    var updateChecksEnabled by remember(updateCheckDisabledUntil) {
        mutableStateOf(updateCheckDisabledUntil <= System.currentTimeMillis())
    }
    LaunchedEffect(updateCheckDisabledUntil) {
        while (true) {
            val remaining = updateCheckDisabledUntil - System.currentTimeMillis()
            if (remaining <= 0) {
                updateChecksEnabled = true
                break
            }
            updateChecksEnabled = false
            delay(minOf(remaining, 60 * 60 * 1_000L))
        }
    }

    // 矮屏（如手表、横屏手机）上顶部固定会把会话列表挤没，改为跟随列表一起滚动
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val scrollableHeader = windowHeight < 640.dp
    val c = palaceColors()
    // 跳走之前先关抽屉，否则返回时抽屉还开着
    fun leave(go: () -> Unit) {
        onCloseDrawer()
        go()
    }

    // 抽屉顶部：更新/备份提醒、标题、常用入口和文件夹栏
    val drawerHeader: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (updateChecksEnabled && !BuildConfig.IS_PLAY_STORE) {
                UpdateCard(drawerVm)
            }

            BackupReminderCard(
                settings = settings,
                onClick = { leave { navController.navigate(Screen.Backup) } },
            )

            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Text("Aoi&C", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1)
            }

            PalaceGlass {
                DrawerActions(navController = navController, onLeave = { onCloseDrawer() })
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FolderBar(
                    folders = folders,
                    selectedFolderId = selectedFolderId,
                    onSelect = { drawerVm.selectFolder(it) },
                    onCreate = { showCreateFolderDialog = true },
                    onRename = { folderToRename = it },
                    onDelete = { folderToDelete = it },
                    modifier = Modifier.weight(1f),
                )

                ConversationSortButton(
                    sortOrder = settings.displaySetting.conversationSortOrder,
                    onSortOrderChange = { sortOrder ->
                        drawerVm.updateSettings {
                            it.copy(
                                displaySetting = it.displaySetting.copy(
                                    conversationSortOrder = sortOrder
                                )
                            )
                        }
                        // 换排序后回到顶部，否则会停留在原来滚动到的位置
                        scope.launch { conversationListState.scrollToItem(0) }
                    },
                )
            }
        }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = c.bgTop,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!scrollableHeader) {
                drawerHeader()
            }

            ConversationList(
                current = currentConversationId,
                conversations = conversations,
                conversationJobs = conversationJobs.keys,
                listState = conversationListState,
                header = if (scrollableHeader) drawerHeader else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onClick = {
                    leave { navigateToChatPage(navController, it.id) }
                },
                onRegenerateTitle = {
                    drawerVm.generateTitle(it.id, true)
                },
                onDelete = {
                    scope.launch {
                        drawerVm.deleteConversation(it.id)
                        conversations.refresh()
                        if (it.id == currentConversationId) {
                            leave { navigateToChatPage(navController) }
                        }
                    }
                },
                onPin = {
                    drawerVm.togglePinned(it.id)
                },
                onMoveToAssistant = {
                    conversationToMove = it
                    showMoveToAssistantSheet = true
                },
                onMoveToFolder = {
                    conversationToMoveFolder = it
                    showMoveToFolderSheet = true
                }
            )

            // 助手选择器
            AssistantPicker(
                settings = settings,
                onSelectAssistant = { assistant ->
                    val updateJob = drawerVm.updateSettings { it.copy(assistantId = assistant.id) }
                    scope.launch {
                        updateJob.join()
                        val id = if (context.readBooleanPreference("create_new_conversation_on_start", true)) {
                            Uuid.random()
                        } else {
                            repo.getConversationsOfAssistant(assistant.id)
                                .first()
                                .firstOrNull()
                                ?.id ?: Uuid.random()
                        }
                        leave { navigateToChatPage(navigator = navController, chatId = id) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                onClickSetting = {
                    val currentAssistantId = settings.assistantId
                    leave { navController.navigate(Screen.AssistantDetail(id = currentAssistantId.toString())) }
                }
            )

            // 收藏、设置：小图标
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawerToolIcon(
                    icon = HugeIcons.InLove,
                    description = stringResource(R.string.favorite_page_title),
                    onClick = { leave { navController.navigate(Screen.Favorite) } },
                )
                DrawerToolIcon(
                    icon = HugeIcons.Settings03,
                    description = stringResource(R.string.settings),
                    onClick = { leave { navController.navigate(Screen.Setting) } },
                )
            }

            // 底部：用户头像、昵称，右边新建对话
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                UIAvatar(
                    name = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                    value = settings.displaySetting.userAvatar,
                    onUpdate = { newAvatar ->
                        drawerVm.updateSettings {
                            it.copy(
                                displaySetting = it.displaySetting.copy(
                                    userAvatar = newAvatar
                                )
                            )
                        }
                    },
                    modifier = Modifier.size(36.dp),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            nicknameEditState.open(settings.displaySetting.userNickname)
                        },
                ) {
                    Text(
                        text = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                        fontSize = 14.sp,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    MonoText("Max plan", size = 9f, color = c.mute)
                }

                NewChatButton(
                    onClick = { leave { navigateToChatPage(navController) } },
                )
            }
        }
    }

    // 昵称编辑对话框
    nicknameEditState.EditStateContent { nickname, onUpdate ->
        AlertDialog(
            onDismissRequest = {
                nicknameEditState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_nickname))
            },
            text = {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.chat_page_nickname_placeholder)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        nicknameEditState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        nicknameEditState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 移动到文件夹 Bottom Sheet
    if (showMoveToFolderSheet) {
        val doMove: (Uuid?) -> Unit = { folderId ->
            conversationToMoveFolder?.let { conversation ->
                drawerVm.moveConversationToFolder(conversation.id, folderId)
                scope.launch {
                    folderSheetState.hide()
                    showMoveToFolderSheet = false
                    conversationToMoveFolder = null
                    conversations.refresh()
                }
            }
        }
        ModalBottomSheet(
            onDismissRequest = {
                showMoveToFolderSheet = false
                conversationToMoveFolder = null
            },
            sheetState = folderSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.chat_page_move_to_folder),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 移出文件夹（未归类）
                Surface(
                    onClick = { doMove(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = if (conversationToMoveFolder?.folderId == null) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(HugeIcons.Folder01, null)
                        Text(
                            text = stringResource(R.string.chat_page_remove_from_folder),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(folders) { folder ->
                        val isCurrent = folder.id == conversationToMoveFolder?.folderId
                        Surface(
                            onClick = { doMove(folder.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            tonalElevation = if (isCurrent) 2.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(HugeIcons.Folder01, null)
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 新建文件夹对话框
    if (showCreateFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.chat_page_create_folder)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.chat_page_folder_name)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawerVm.createFolder(name)
                        showCreateFolderDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.chat_page_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 重命名文件夹对话框
    folderToRename?.let { folder ->
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text(stringResource(R.string.chat_page_rename_folder)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawerVm.renameFolder(folder.id, name)
                        folderToRename = null
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.chat_page_save)) }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 删除文件夹确认
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.chat_page_delete_folder)) },
            text = { Text(stringResource(R.string.chat_page_delete_folder_confirm, folder.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (drawerVm.deleteFolder(folder.id)) {
                            folderToDelete = null
                            conversations.refresh()
                        } else {
                            toaster.show(context.getString(R.string.chat_page_delete_folder_generating), type = ToastType.Warning)
                        }
                    }
                ) { Text(stringResource(R.string.chat_page_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }

    // 移动到助手 Bottom Sheet
    if (showMoveToAssistantSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showMoveToAssistantSheet = false
                conversationToMove = null
            },
            sheetState = bottomSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.chat_page_move_to_assistant),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(settings.assistants) { assistant ->
                        AssistantItem(
                            assistant = assistant,
                            isCurrentAssistant = assistant.id == conversationToMove?.assistantId,
                            onClick = {
                                conversationToMove?.let { conversation ->
                                    drawerVm.moveConversationToAssistant(conversation.id, assistant.id)
                                    scope.launch {
                                        bottomSheetState.hide()
                                        showMoveToAssistantSheet = false
                                        conversationToMove = null
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// 侧边栏底部的浅色小图标入口
@Composable
private fun DrawerToolIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = palaceColors()
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(18.dp), tint = c.ink2)
    }
}

// 浅色小胶囊「新建对话」：chip 底，ink2 图标文字，高度约 28dp，比头像矮
@Composable
private fun NewChatButton(onClick: () -> Unit) {
    val c = palaceColors()
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(c.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = HugeIcons.Add01,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = c.ink2,
        )
        Text(
            text = "新建对话",
            fontSize = 11.5.sp,
            color = c.ink2,
            maxLines = 1,
        )
    }
}

@Composable
private fun DrawerActions(navController: Navigator, onLeave: () -> Unit) {
    Column {
        // 一起看书入口
        PalaceRow(
            icon = ReadingIcons.Book,
            title = "一起看书",
            onClick = { onLeave(); navController.navigate(Screen.Bookshelf) },
        )

        PalaceDivider()

        // 养崽崽入口
        PalaceRow(
            icon = PetCrabIcon,
            title = "养崽崽",
            onClick = { onLeave(); navController.navigate(Screen.Pet) },
        )
    }
}

@Composable
private fun FolderBar(
    folders: List<Folder>,
    selectedFolderId: Uuid?,
    onSelect: (Uuid?) -> Unit,
    onCreate: () -> Unit,
    onRename: (Folder) -> Unit,
    onDelete: (Folder) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            FolderChip(
                label = stringResource(R.string.chat_page_folder_default),
                selected = selectedFolderId == null,
                onClick = { onSelect(null) },
                onLongClick = {},
            )
        }
        items(folders) { folder ->
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                FolderChip(
                    label = folder.name,
                    icon = HugeIcons.Folder01,
                    selected = selectedFolderId == folder.id,
                    onClick = { onSelect(folder.id) },
                    onLongClick = { menuExpanded = true },
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_page_rename)) },
                        leadingIcon = { Icon(HugeIcons.PencilEdit01, null) },
                        onClick = {
                            onRename(folder)
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_page_delete)) },
                        leadingIcon = { Icon(HugeIcons.Delete01, null) },
                        onClick = {
                            onDelete(folder)
                            menuExpanded = false
                        }
                    )
                }
            }
        }
        item {
            FolderChip(
                label = stringResource(R.string.chat_page_folder_add),
                icon = HugeIcons.FolderAdd,
                selected = false,
                onClick = onCreate,
                onLongClick = {},
            )
        }
    }
}

@Composable
private fun ConversationSortButton(
    sortOrder: ConversationSortOrder,
    onSortOrderChange: (ConversationSortOrder) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val c = palaceColors()
    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable { menuExpanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HugeIcons.Sorting01,
                contentDescription = stringResource(R.string.chat_page_sort_conversations),
                modifier = Modifier.size(14.dp),
                tint = c.ink2,
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            ConversationSortOrder.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (option) {
                                    ConversationSortOrder.UPDATE_TIME -> R.string.chat_page_sort_by_update_time
                                    ConversationSortOrder.CREATE_TIME -> R.string.chat_page_sort_by_create_time
                                }
                            )
                        )
                    },
                    trailingIcon = {
                        if (option == sortOrder) {
                            Icon(HugeIcons.Tick01, null, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        menuExpanded = false
                        if (option != sortOrder) onSortOrderChange(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun FolderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val c = palaceColors()
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) c.chip else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = c.ink2)
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = if (selected) c.ink else c.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AssistantItem(
    assistant: Assistant,
    isCurrentAssistant: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isCurrentAssistant) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isCurrentAssistant) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UIAvatar(
                name = assistant.name,
                value = assistant.avatar,
                onUpdate = {},
                modifier = Modifier.size(40.dp),
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isCurrentAssistant) {
                    Text(
                        text = stringResource(R.string.assistant_page_current_assistant),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// 养崽崽的图标：圆角身子、两只小钳子、四条小腿、两个眼睛
private val PetCrabIcon: ImageVector by lazy {
    val builder = ImageVector.Builder(
        name = "PetCrab",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    listOf(
        "M6 9.5C6 8.1 7.1 7 8.5 7H15.5C16.9 7 18 8.1 18 9.5V13.5C18 14.9 16.9 16 15.5 16H8.5C7.1 16 6 14.9 6 13.5V9.5Z",
        "M6 12H3.5",
        "M18 12H20.5",
        "M8.5 16V18.5",
        "M11 16V18.5",
        "M13 16V18.5",
        "M15.5 16V18.5",
        "M10 10V11.5",
        "M14 10V11.5",
    ).forEach { d ->
        builder.addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    builder.build()
}
