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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import me.rerere.rikkahub.ui.components.ai.ThinSnowflakeIcon
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    // 直接发一条文字消息：表情包 [崽崽:名]、骰子 [骰子:n]、语音 [语音:秒]文字
    val sendSpecialText: (String) -> Unit = { text ->
        if (currentChatModel == null) {
            toaster.show("请先选择模型", type = ToastType.Error)
        } else {
            vm.handleMessageSend(NowPlaying.attachTo(listOf(UIMessagePart.Text(text = text))))
            scope.launch {
                delay(100.milliseconds)
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
        }
    }
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
                    onSendSpecial = sendSpecialText,
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
                    // 发出去的是文本 [崽崽:表情名]，显示时才换成图
                    sendSpecialText(stickerTag(name))
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
    val assistantName = assistant.name.ifEmpty { defaultAssistantName }

    // 点头像、点名字那组都走原来的改标题逻辑：有消息才能改，没消息给提示
    val onClickTitle: () -> Unit = {
        if (conversation.messageNodes.isNotEmpty()) {
            titleState.open(conversation.title)
        } else {
            toaster.show(editTitleWarning, type = ToastType.Warning)
        }
    }

    // 每分钟刷新一次时间
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }
    // 进入顶栏读一次天气，失败是 null，数字处显示 --
    val weather = rememberTopBarWeather()
    val infoText = "${now.format(topBarTimeFormatter)} · No.${conversation.messageNodes.size}"

    Column(
        // 先铺一层跟页面一样的底色，滑上来的字就被盖住了
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // 第 1 行：信息条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = infoText.uppercase(),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.15.em,
                    color = Color(0xFFA0A0A0),
                    maxLines = 1,
                )
                DottedLine(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
                if (!bigScreen) {
                    ThinTopIconButton(icon = HugeIcons.Menu03, label = "打开侧边栏") {
                        scope.launch { drawerState.open() }
                    }
                }
                ThinTopIconButton(icon = HugeIcons.MessageAdd01, label = "新建对话") {
                    onNewChat()
                }
            }



            // 第 2 行：头像行，高 56dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarAvatar(
                    name = assistantName,
                    avatar = assistant.avatar,
                    loading = loading,
                    onClick = onClickTitle,
                )
                Column(
                    modifier = Modifier
                        .padding(start = 11.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClickTitle),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = assistantName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF353535),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 只改显示，模型 ID 不变
                    val modelName = chatModel?.displayName?.let { prettyModelName(it) } ?: "Claude"
                    Text(
                        text = "${conversation.title.ifBlank { newChatTitle }} · $modelName",
                        fontSize = 10.sp,
                        color = Color(0xFF9A9A9A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 右侧：竖向点线 + TEMP 块（右对齐）
                Row(
                    modifier = Modifier.padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DottedLine(
                        modifier = Modifier
                            .width(1.dp)
                            .height(44.dp),
                        vertical = true,
                    )
                    Column(
                        modifier = Modifier.padding(start = 12.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        TopBarLabel(text = "TEMP")
                        Text(
                            text = if (weather != null) "${weather.min}~${weather.max}°" else "--",
                            fontSize = 21.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.ExtraLight,
                            color = Color(0xFF353535),
                            maxLines = 1,
                        )
                        Text(
                            text = "TODAY · ${weather?.updated?.takeIf { it.isNotBlank() } ?: "--"}",
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFA0A0A0),
                            maxLines = 1,
                        )
                    }
                }
            }

            // 第 4 块：引言，不加灰底。引号自己画，左边缘对齐头像左边缘（左边留 3dp），中文整体比引号低 4dp，引号在文字左上方
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 3.dp, top = 2.dp, bottom = 10.dp),
            ) {
                QuoteMark(modifier = Modifier.padding(top = 1.dp))
                Column(
                    modifier = Modifier
                        .padding(start = 6.dp, top = 4.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    // 中文必须一行：11sp 起，放不下逐步缩小到 9sp，缩小时字距改 0
                    FitOneLineText(
                        text = "我把我整个的灵魂都给你，连同它的怪癖。",
                        maxSize = 11f,
                        minSize = 9f,
                        maxSizeLetterSpacing = 0.04.em,
                        color = Color(0xFF666666),
                        trimFirstLineTop = true,
                    )
                    // 英文同理，8.5sp 起，最小 8sp
                    FitOneLineText(
                        text = "I give you my whole soul, quirks and all.",
                        maxSize = 8.5f,
                        minSize = 8f,
                        maxSizeLetterSpacing = 0.sp,
                        color = Color(0xFFB8B8B8),
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .align(Alignment.Bottom),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = "NOTE",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.18.em,
                        color = Color(0xFFB0B0B0),
                        maxLines = 1,
                        style = LocalTextStyle.current.copy(
                            lineHeight = 10.sp,
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                        ),
                    )
                }
            }
        }
        // 顶栏底部细线，和消息区分开
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0xFFECECEC))
        )
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

private val topBarTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd · HH:mm")

// 细线图标按钮：没有圆底、没有阴影，图标 18dp，颜色 #666
@Composable
private fun ThinTopIconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFF666666), modifier = Modifier.size(18.dp))
    }
}

// 8sp 等宽大写小标签（TEMP / NOW / WIND），字距 0.15em
@Composable
private fun TopBarLabel(text: String) {
    Text(
        text = text,
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.15.em,
        color = Color(0xFFA0A0A0),
        maxLines = 1,
    )
}

// 第 3 行数据条，高 40dp：左 NOW（雪花圆 + 天气 · 体感），中间竖点线，右 WIND（风速 + 进度条）
@Composable
private fun TopBarDataStrip(weather: TopBarWeather?) {
    val valueColor = Color(0xFF353535)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(start = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4A4A4A)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ThinSnowflakeIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
            Column(modifier = Modifier.padding(start = 10.dp)) {
                TopBarLabel(text = "NOW")
                Text(
                    text = if (weather != null) "${weather.text} · 体感 ${weather.feels}°" else "--",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 中间竖点线，左右两半等宽，所以在整行宽度一半处
        DottedLine(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp),
            vertical = true,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            TopBarLabel(text = "WIND")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (weather != null) "${weather.wind} km/h" else "--",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    color = valueColor,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8E8E8)),
                ) {
                    val fraction = if (weather != null) (weather.wind / 40f).coerceIn(0f, 1f) else 0f
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(Color(0xFF999999)),
                        )
                    }
                }
            }
        }
    }
}

// 引言左上的 “ ：字体里的引号自带留白，位置没法保证，所以自己画。
// 14.5 x 10dp，两个实心「6」：下面是圆点，从圆点左上长出一条渐细的弯尾巴，尖朝右上（像倒过来的逗号）
@Composable
private fun QuoteMark(modifier: Modifier = Modifier) {
    val color = Color(0xFFDCDCDC)
    Canvas(modifier = modifier.size(width = 14.5.dp, height = 10.dp)) {
        fun d(v: Float) = v.dp.toPx()
        listOf(0f, 7.6f).forEach { dx ->
            val cx = dx + 2.4f
            val cy = 7.5f
            drawCircle(color, radius = d(2.4f), center = Offset(d(cx), d(cy)))
            drawPath(
                Path().apply {
                    moveTo(d(cx - 2.35f), d(cy - 0.4f))
                    cubicTo(d(cx - 2.12f), d(cy - 3.47f), d(cx - 0.47f), d(cy - 5.67f), d(cx + 2.6f), d(cy - 7.0f))
                    cubicTo(d(cx + 0.67f), d(cy - 5.4f), d(cx + 0.2f), d(cy - 3.73f), d(cx + 1.2f), d(cy - 2.0f))
                    close()
                },
                color = color,
            )
        }
    }
}

// 单行自适应：maxSize 起，溢出就每次缩 0.5sp 直到 minSize；缩小后字距改 0
@Composable
private fun FitOneLineText(
    text: String,
    maxSize: Float,
    minSize: Float,
    maxSizeLetterSpacing: TextUnit,
    color: Color,
    trimFirstLineTop: Boolean = false,
) {
    var size by remember(text) { mutableFloatStateOf(maxSize) }
    Text(
        text = text,
        fontSize = size.sp,
        letterSpacing = if (size >= maxSize) maxSizeLetterSpacing else 0.sp,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = {
            if (it.hasVisualOverflow && size > minSize) {
                size = (size - 0.5f).coerceAtLeast(minSize)
            }
        },
        style = LocalTextStyle.current.copy(
            lineHeight = (size * 1.25f).sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
    )
}

// 圆点虚线：圆头点，横竖都能画
@Composable
private fun DottedLine(
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    Canvas(modifier = modifier) {
        val stroke = 1.2.dp.toPx()
        val effect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 3.2.dp.toPx()), 0f)
        if (vertical) {
            drawLine(
                color = Color(0xFFCFCFCF),
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
                pathEffect = effect,
            )
        } else {
            drawLine(
                color = Color(0xFFCFCFCF),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
                pathEffect = effect,
            )
        }
    }
}

// 助手头像 48dp 圆形，右下角雪花徽章；生成中外圈细弧线转，结束圈消失
@Composable
private fun TopBarAvatar(
    name: String,
    avatar: Avatar,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(54.dp),
        contentAlignment = Alignment.Center
    ) {
        UIAvatar(
            name = name,
            value = avatar,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            onClick = onClick,
        )
        if (loading) {
            SpinningRing(modifier = Modifier.matchParentSize())
        }
        // 小雪花徽章：白底圆 16dp，0.5dp 浅灰边，里面细线 ✻
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(0.5.dp, Color(0xFFE0E0E0), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✻",
                fontSize = 9.sp,
                fontWeight = FontWeight.Light,
                color = Color(0xFF888888),
                style = LocalTextStyle.current.copy(
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    )
                ),
            )
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
