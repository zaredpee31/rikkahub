package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ui.toast.ToastType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.music.NowPlaying
import me.rerere.rikkahub.music.NowPlayingPermissionGate
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.ai.ChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ai.FilesPicker
import me.rerere.rikkahub.ui.components.ai.SearchMode
import me.rerere.rikkahub.ui.components.ai.completion.WorkspaceCompletionProvider
import me.rerere.rikkahub.ui.components.ai.rememberChatAttachmentPickerActions
import me.rerere.rikkahub.ui.components.sticker.StickerPickerSheet
import me.rerere.rikkahub.ui.components.sticker.stickerTag
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    val selectedFolderId by drawerVm.selectedFolderId.collectAsStateWithLifecycle()
    LaunchedEffect(selectedFolderId) {
        vm.updateDraftFolder(selectedFolderId)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            focusManager.clearFocus(force = true)
            softwareKeyboardController?.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    LaunchedEffect(isBigScreen) {
        if (isBigScreen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    val startVoiceMode = rememberVoiceModeStarter(vm, setting)

    val inputState = vm.inputState

    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { file ->
                filesManager.getFileMimeType(file)
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
            }
        }
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    chatListState.scrollToItem(index)
                }
            } else {
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
            vm.chatListInitialized = true
        }
    }

    when {
        isBigScreen -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        settings = setting,
                        drawerVm = drawerVm,
                        currentConversationId = conversation.id,
                        onCloseDrawer = { scope.launch { drawerState.close() } },
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    assistant = assistant,
                    currentChatModel = currentChatModel,
                    bigScreen = true,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }

        else -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        settings = setting,
                        drawerVm = drawerVm,
                        currentConversationId = conversation.id,
                        onCloseDrawer = { scope.launch { drawerState.close() } },
                    )
                }
            ) {
                ChatPageContent(
                    onStartVoiceMode = startVoiceMode,
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    assistant = assistant,
                    currentChatModel = currentChatModel,
                    bigScreen = false,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
            BackHandler(drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
        }
    }
}

@Composable
private fun ChatPageContent(
    onStartVoiceMode: () -> Unit,
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    assistant: Assistant,
    currentChatModel: Model?,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val workspaceRepository: WorkspaceRepository = koinInject()

    var previewMode by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    var showFilesSheet by remember { mutableStateOf(false) }
    // 崽崽表情面板
    var showStickerSheet by remember { mutableStateOf(false) }
    // 一起听歌：没开通知使用权时弹一次引导
    NowPlayingPermissionGate()
    val attachmentPickerActions = rememberChatAttachmentPickerActions(
        inputState = inputState,
        setting = setting,
        onAttachmentAdded = { showFilesSheet = false },
    )
    val allowAudioVideoAttachments =
        currentChatModel?.findProvider(setting.providers) is ProviderSetting.Google

    val completionProviders = remember(assistant.workspaceId, conversation.workspaceCwd, workspaceRepository) {
        assistant.workspaceId?.let { workspaceId ->
            listOf(
                WorkspaceCompletionProvider(
                    workspaceId = workspaceId.toString(),
                    repository = workspaceRepository,
                    currentCwd = conversation.workspaceCwd,
                )
            )
        }.orEmpty()
    }

    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        AssistantBackground(setting = setting, modifier = Modifier.hazeSource(hazeState))
        Scaffold(
            topBar = {
                TopBar(
                    settings = setting,
                    assistant = assistant,
                    chatModel = currentChatModel,
                    conversation = conversation,
                    bigScreen = bigScreen,
                    drawerState = drawerState,
                    previewMode = previewMode,
                    loading = loadingJob != null,
                    onNewChat = {
                        navigateToChatPage(navController)
                    },
                    onClickMenu = {
                        previewMode = !previewMode
                    },
                    onUpdateTitle = {
                        vm.updateTitle(it)
                    }
                )
            },
            bottomBar = {
                val messageQueue by vm.messageQueue.collectAsStateWithLifecycle()
                val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
                ChatInput(
                    onStartVoiceMode = onStartVoiceMode,
                    voiceState = voiceState,
                    onStopVoiceMode = vm.voiceSession::stop,
                    state = inputState,
                    messageQueue = messageQueue,
                    onRemoveQueuedMessage = vm::removeQueuedMessage,
                    onBeginEditQueuedMessage = vm::beginEditQueuedMessage,
                    onFinishEditQueuedMessage = vm::finishEditQueuedMessage,
                    onResumeMessageQueue = vm::resumeMessageQueue,
                    loading = loadingJob != null,
                    settings = setting,
                    assistant = assistant,
                    chatModel = currentChatModel,
                    hazeState = hazeState,
                    completionProviders = completionProviders,
                    onCancelClick = {
                        vm.stopGeneration()
                    },
                    onSendClick = {
                        if (currentChatModel == null) {
                            toaster.show("请先选择模型", type = ToastType.Error)
                            return@ChatInput
                        }
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            // 切歌后第一条消息带上 [正在听:歌名-歌手]
                            vm.handleMessageSend(NowPlaying.attachTo(inputState.getContents()))
                            scope.launch {
                                delay(100.milliseconds)
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onLongSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(
                                content = NowPlaying.attachTo(inputState.getContents()),
                                answer = false
                            )
                            scope.launch {
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onUpdateChatModel = vm::setChatModel,
                    onUpdateAssistant = vm::updateAssistant,
                    onMoreClick = {
                        showFilesSheet = true
                    },
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                ChatList(
                    innerPadding = innerPadding,
                    conversation = conversation,
                    state = chatListState,
                    loading = loadingJob != null,
                    processingStatus = processingStatus,
                    previewMode = previewMode,
                    settings = setting,
                    hazeState = hazeState,
                    errors = errors,
                    onDismissError = onDismissError,
                    onClearAllErrors = onClearAllErrors,
                    onRegenerate = {
                        vm.regenerateAtMessage(it)
                    },
                    onEdit = {
                        inputState.editingMessage = it.id
                        // 编辑时输入框里不显示听歌暗号
                        inputState.setContents(NowPlaying.stripTag(it.parts))
                    },
                    onForkMessage = {
                        scope.launch {
                            val fork = vm.forkMessage(message = it)
                            navigateToChatPage(navController, chatId = fork.id)
                        }
                    },
                    onDelete = {
                        if (loadingJob != null) {
                            vm.showDeleteBlockedWhileGeneratingError()
                        } else {
                            vm.deleteMessage(it)
                        }
                    },
                    onUpdateMessage = { newNode ->
                        vm.selectMessageNode(newNode.id, newNode.selectIndex)
                    },
                    onClickSuggestion = { suggestion ->
                        inputState.editingMessage = null
                        inputState.setMessageText(suggestion)
                    },
                    onTranslate = { message, locale ->
                        vm.translateMessage(message, locale)
                    },
                    onClearTranslation = { message ->
                        vm.clearTranslationField(message.id)
                    },
                    onJumpToMessage = { index ->
                        previewMode = false
                        scope.launch {
                            chatListState.requestScrollToItem(index)
                        }
                    },
                    onToolApproval = { toolCallId, approved, reason ->
                        vm.handleToolApproval(toolCallId, approved, reason)
                    },
                    onToolAnswer = { toolCallId, answer ->
                        vm.handleToolAnswer(toolCallId, answer)
                    },
                    onToggleFavorite = { node ->
                        vm.toggleMessageFavorite(node)
                    },
                    onConversationSystemPromptChange = { newPrompt ->
                        vm.updateConversationSystemPrompt(newPrompt)
                    },
                )

                // 小螃蟹桌宠，站在输入框上，会爬到消息顶上
                ClawdPet(
                    innerPadding = innerPadding,
                    listState = chatListState,
                    working = loadingJob != null,
                    typingText = { inputState.textContent.text },
                )
            }
        }

        if (showFilesSheet) {
            ChatFilesPickerSheet(
                inputState = inputState,
                setting = setting,
                conversation = conversation,
                assistant = assistant,
                chatModel = currentChatModel,
                vm = vm,
                attachmentPickerActions = attachmentPickerActions,
                onStartVoiceMode = onStartVoiceMode,
                onOpenStickers = { showStickerSheet = true },
                onDismiss = { showFilesSheet = false },
            )
        }

        if (showStickerSheet) {
            StickerPickerSheet(
                onDismiss = { showStickerSheet = false },
                onPick = { name ->
                    showStickerSheet = false
                    if (currentChatModel == null) {
                        toaster.show("请先选择模型", type = ToastType.Error)
                        return@StickerPickerSheet
                    }
                    // 发出去的是文本 [崽崽:表情名]，显示时才换成图
                    vm.handleMessageSend(
                        NowPlaying.attachTo(listOf(UIMessagePart.Text(text = stickerTag(name))))
                    )
                    scope.launch {
                        delay(100.milliseconds)
                        chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                    }
                },
            )
        }
    }
}

@Composable
private fun ChatFilesPickerSheet(
    inputState: ChatInputState,
    setting: Settings,
    conversation: Conversation,
    assistant: Assistant,
    chatModel: Model?,
    vm: ChatVM,
    attachmentPickerActions: ChatAttachmentPickerActions,
    onStartVoiceMode: () -> Unit,
    onOpenStickers: () -> Unit,
    onDismiss: () -> Unit,
) {
    val voiceState by vm.voiceSession.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showCompressDialog by remember { mutableStateOf(false) }

    fun dismissAll() {
        showCompressDialog = false
        onDismiss()
    }

    val filesSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = filesSheetState,
        onDismissRequest = { dismissAll() },
    ) {
        FilesPicker(
            conversation = conversation,
            state = inputState,
            assistant = assistant,
            chatModel = chatModel,
            mcpManager = vm.mcpManager,
            onCompressContext = vm::handleCompressContext,
            onUpdateAssistant = vm::updateAssistant,
            onUpdateWorkspaceCwd = vm::updateWorkspaceCwd,
            onUpdateSearchMode = { mode ->
                vm.updateSearch(
                    enableWebSearch = mode == SearchMode.LOCAL,
                    builtInSearch = mode == SearchMode.BUILT_IN,
                )
            },
            onUpdateSearchService = { index ->
                vm.updateSettings { it.copy(searchServiceSelected = index) }
            },
            showCompressDialog = showCompressDialog,
            onShowCompressDialogChange = { showCompressDialog = it },
            onDismiss = { dismissAll() },
            onTakePic = attachmentPickerActions.onTakePicture,
            onPickImage = attachmentPickerActions.onPickImage,
            onPickVideo = attachmentPickerActions.onPickVideo,
            onPickAudio = attachmentPickerActions.onPickAudio,
            onPickFile = attachmentPickerActions.onPickFile,
            onSketch = attachmentPickerActions.onSketch,
            onStartVoiceMode = if (
                setting.getSelectedASRProvider()?.supportsServerVadVoiceMode == true &&
                voiceState.phase == VoicePhase.Off
            ) {
                {
                    dismissAll()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onStartVoiceMode()
                }
            } else null,
            // 先关掉加号面板，再弹表情面板
            onOpenStickers = {
                dismissAll()
                onOpenStickers()
            },
        )
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    assistant: Assistant,
    chatModel: Model?,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    loading: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
    onUpdateTitle: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val titleState = useEditState<String> { onUpdateTitle(it) }
    val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
    val defaultAssistantName = stringResource(R.string.assistant_page_default_assistant)
    val newChatTitle = stringResource(R.string.chat_page_new_chat)

    // 点标题、头像、名字都走原来的改标题逻辑：有消息才能改，没消息给提示
    val onClickTitle: () -> Unit = {
        if (conversation.messageNodes.isNotEmpty()) {
            titleState.open(conversation.title)
        } else {
            toaster.show(editTitleWarning, type = ToastType.Warning)
        }
    }

    Column(
        // 先铺一层跟页面一样的底色，滑上来的字就被盖住了
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .padding(horizontal = 8.dp)
    ) {
        // 最上面一条：小写细字标题，后面跟灰色等宽的消息条数
        Row(
            modifier = Modifier
                .padding(start = 12.dp, top = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClickTitle)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = conversation.title.ifBlank { newChatTitle }.lowercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.6.sp,
                color = Color(0xFF4A4A4A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (conversation.messageNodes.isNotEmpty()) {
                Text(
                    text = "  · ${conversation.messageNodes.size}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp,
                    color = Color(0xFFB0B0B0),
                    maxLines = 1,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (!bigScreen) {
                ThinTopIconButton(
                    icon = HugeIcons.Menu03,
                    label = "打开侧边栏",
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    scope.launch { drawerState.open() }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClickTitle)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TopBarAvatar(
                    name = assistant.name.ifEmpty { defaultAssistantName },
                    avatar = assistant.avatar,
                    loading = loading,
                    onClick = onClickTitle,
                )
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = assistant.name.ifEmpty { defaultAssistantName },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1F1E1D),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        // 只改显示，模型 ID 不变
                        text = chatModel?.displayName?.let { prettyModelName(it) } ?: "Claude",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                        color = Color(0xFF9A9A9A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            ThinTopIconButton(
                icon = HugeIcons.MessageAdd01,
                label = "新建对话",
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                onNewChat()
            }
        }
    }

    titleState.EditStateContent { title, onUpdate ->
        AlertDialog(
            onDismissRequest = { titleState.dismiss() },
            title = { Text(stringResource(R.string.chat_page_edit_title)) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { titleState.confirm() }) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { titleState.dismiss() }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }
}

// 细线图标按钮：没有圆底、没有阴影，颜色统一深灰
@Composable
private fun ThinTopIconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFF4A4A4A), modifier = Modifier.size(20.dp))
    }
}

// 助手头像；生成中外面一圈细灰弧线慢慢转，生成结束圈就消失
@Composable
private fun TopBarAvatar(
    name: String,
    avatar: Avatar,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        UIAvatar(
            name = name,
            value = avatar,
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            onClick = onClick,
        )
        if (loading) {
            SpinningRing(modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun SpinningRing(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "TopBarAvatarRing")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1800, easing = LinearEasing)),
        label = "TopBarAvatarRingAngle",
    )
    Canvas(modifier = modifier) {
        val stroke = 1.dp.toPx()
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = Color(0xFFE2E2E2),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = Color(0xFF8A8A8A),
            startAngle = angle,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

// 顶栏只改显示：去掉渠道加的 [标签]，claude-opus-5-5 → Opus 5.5
// 模型 ID 和请求都不经过这里
private val modelTagRegex = Regex("""\s*\[[^\]]*\]\s*""")
private val spaceRegex = Regex("""\s+""")
private val claudeNameRegex = Regex(
    """^claude-([a-z]+)-(\d{1,2})(?:-(\d{1,2}))?(?:-\d{8})?(?:-thinking)?$""",
    RegexOption.IGNORE_CASE
)

private fun prettyModelName(raw: String): String {
    // 方括号连同前后空格一起去掉，多出来的空格合并
    val stripped = raw.replace(modelTagRegex, " ").replace(spaceRegex, " ").trim()
    if (stripped.isEmpty()) return "Claude"

    // 认不出来的名字，去掉方括号后原样显示
    val match = claudeNameRegex.matchEntire(stripped) ?: return stripped
    val (family, major, minor) = match.destructured
    val version = if (minor.isEmpty()) major else "$major.$minor"
    return "${family.lowercase().replaceFirstChar { it.uppercase() }} $version"
}
