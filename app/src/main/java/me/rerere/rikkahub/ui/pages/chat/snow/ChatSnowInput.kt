package me.rerere.rikkahub.ui.pages.chat.snow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRStatus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Voice
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Fullscreen
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.service.MessageQueueState
import me.rerere.rikkahub.service.QueuedMessage
import me.rerere.rikkahub.ui.components.ai.AsrButton
import me.rerere.rikkahub.ui.components.ai.MediaFileInputRow
import me.rerere.rikkahub.ui.components.ai.MessageQueuePanel
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.ReasoningPicker
import me.rerere.rikkahub.ui.components.ai.VoiceModeRow
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionContext
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionItem
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionList
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.ui.KeepScreenOn
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.pages.chat.VoicePhase
import me.rerere.rikkahub.ui.pages.chat.VoiceSessionState
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private val SnowInputShape = RoundedCornerShape(22.dp)

/**
 * 普通会话页专用输入栏。白底圆角浮动框，功能跟原来的输入栏一样：
 * 附件、模型、推理、语音、队列、编辑、补全都在，只是外观换了。
 */
@Composable
fun SnowChatInput(
    state: ChatInputState,
    loading: Boolean,
    settings: Settings,
    assistant: Assistant,
    chatModel: Model?,
    completionProviders: List<ChatCompletionProvider> = emptyList(),
    onUpdateChatModel: (Model) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onMoreClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    messageQueue: MessageQueueState = MessageQueueState(),
    onRemoveQueuedMessage: (Uuid) -> Unit = {},
    onBeginEditQueuedMessage: (Uuid) -> QueuedMessage? = { null },
    onFinishEditQueuedMessage: (Uuid, List<UIMessagePart>?) -> Unit = { _, _ -> },
    onResumeMessageQueue: () -> Unit = {},
    onStartVoiceMode: (() -> Unit)? = null,
    voiceState: VoiceSessionState = VoiceSessionState(),
    onStopVoiceMode: () -> Unit = {},
) {
    val toaster = LocalToaster.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val modelListState = rememberModelListState(
        modelId = chatModel?.id,
        providers = settings.providers,
        type = ModelType.CHAT,
    )

    fun sendMessage() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onSendClick()
    }

    fun sendMessageWithoutAnswer() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onLongSendClick()
    }

    val asr = LocalASRState.current
    val asrState by asr.state.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val soundEffectPlayer: SoundEffectPlayer = koinInject()
    LaunchedEffect(Unit) {
        soundEffectPlayer.preload(R.raw.asr_start, R.raw.asr_stop)
    }
    val asrPermission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permissionState = asrPermission)
    var asrBaseText by remember { mutableStateOf("") }
    LaunchedEffect(asrState.status) {
        when (asrState.status) {
            ASRStatus.Listening -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                soundEffectPlayer.play(R.raw.asr_start)
            }
            ASRStatus.Stopping -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                soundEffectPlayer.play(R.raw.asr_stop)
            }
            else -> {}
        }
    }
    LaunchedEffect(asrState.errorMessage) {
        asrState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            toaster.show(message = message, type = ToastType.Error)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MessageQueuePanel(
            state = messageQueue,
            onRemove = onRemoveQueuedMessage,
            onBeginEdit = onBeginEditQueuedMessage,
            onFinishEdit = onFinishEditQueuedMessage,
            onResume = onResumeMessageQueue,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SnowInputShape)
                .background(SnowBackground)
                .border(0.5.dp, SnowHairline, SnowInputShape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (voiceState.phase != VoicePhase.Off) {
                VoiceModeRow(
                    state = voiceState,
                    onStop = onStopVoiceMode,
                    onRetry = { onStartVoiceMode?.invoke() },
                )
            }
            if (state.messageContent.isNotEmpty()) {
                MediaFileInputRow(state = state)
            }
            SnowTextRow(
                state = state,
                settings = settings,
                completionProviders = completionProviders,
                onSendMessage = { sendMessage() },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SnowModelChip(name = chatModel?.displayName, onClick = { modelListState.open() })
                    if (chatModel?.abilities?.contains(ModelAbility.REASONING) == true) {
                        SnowReasoningChip(
                            level = assistant.reasoningLevel,
                            onUpdate = { onUpdateAssistant(assistant.copy(reasoningLevel = it)) },
                        )
                    }
                }
                SnowIconButton(HugeIcons.Add01, stringResource(R.string.more_options), onMoreClick)
                if (!voiceState.isActive && (asrState.isAvailable || asrState.isRecording)) {
                    AsrButton(
                        state = asrState,
                        onClick = {
                            when (asrState.status) {
                                ASRStatus.Listening -> asr.stop()
                                ASRStatus.Idle, ASRStatus.Error -> {
                                    if (!asrPermission.allRequiredPermissionsGranted) {
                                        asrPermission.requestPermissions()
                                    } else {
                                        asrBaseText = state.textContent.text.toString()
                                        asr.start { transcript ->
                                            val spacer = if (asrBaseText.isBlank() || transcript.isBlank()) "" else " "
                                            state.setMessageText(asrBaseText + spacer + transcript)
                                        }
                                    }
                                }
                                ASRStatus.Connecting, ASRStatus.Stopping -> {}
                            }
                        },
                    )
                }
                if (loading) KeepScreenOn()
                AnimatedVisibility(
                    visible = !asrState.isRecording,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    SnowSendButton(
                        loading = loading,
                        empty = state.isEmpty(),
                        onClick = { sendMessage() },
                        onLongClick = { sendMessageWithoutAnswer() },
                    )
                }
            }
        }
    }

    ModelListSheet(state = modelListState, onSelect = onUpdateChatModel)
}

@Composable
private fun SnowModelChip(name: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(HugeIcons.Voice, contentDescription = null, tint = SnowInkSoft, modifier = Modifier.size(14.dp))
        SnowLabel(
            text = name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.model_list_select_model),
            color = SnowInkSoft,
        )
    }
}

@Composable
private fun SnowReasoningChip(level: ReasoningLevel, onUpdate: (ReasoningLevel) -> Unit) {
    var show by remember { mutableStateOf(false) }
    if (show) {
        ReasoningPicker(
            reasoningLevel = level,
            onDismissRequest = { show = false },
            onUpdateReasoningLevel = onUpdate,
        )
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { show = true },
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        SnowLabel(stringResource(R.string.setting_provider_page_reasoning), color = SnowInkSoft)
    }
}

@Composable
private fun SnowSendButton(
    loading: Boolean,
    empty: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val showStop = loading && empty
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SnowGray)
            .combinedClickable(
                enabled = showStop || !empty,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("chat_send_button"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (showStop) HugeIcons.StopCircle else HugeIcons.ArrowUp02,
                contentDescription = stringResource(if (showStop) R.string.stop else R.string.send),
                tint = if (showStop || !empty) SnowInk else SnowInkFaint,
                modifier = Modifier.size(14.dp),
            )
            SnowLabel(
                text = if (showStop) stringResource(R.string.stop) else stringResource(R.string.send),
                color = if (showStop || !empty) SnowInk else SnowInkFaint,
            )
        }
    }
}

@Composable
private fun SnowTextRow(
    state: ChatInputState,
    settings: Settings,
    completionProviders: List<ChatCompletionProvider>,
    onSendMessage: () -> Unit,
) {
    val filesManager: FilesManager = koinInject()
    var isEditingReasoning by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }
    var completionList by remember { mutableStateOf<ChatCompletionList?>(null) }
    val receiveContentListener = remember(
        settings.displaySetting.pasteLongTextAsFile, settings.displaySetting.pasteLongTextThreshold
    ) {
        ReceiveContentListener { transferableContent ->
            when {
                transferableContent.hasMediaType(MediaType.Image) -> {
                    transferableContent.consume { item ->
                        val uri = item.uri
                        if (uri != null) {
                            state.addImages(filesManager.createChatFilesByContents(listOf(uri)))
                        }
                        uri != null
                    }
                }
                settings.displaySetting.pasteLongTextAsFile && transferableContent.hasMediaType(MediaType.Text) -> {
                    transferableContent.consume { item ->
                        val text = item.text?.toString()
                        if (text != null && text.length > settings.displaySetting.pasteLongTextThreshold) {
                            state.addFiles(listOf(filesManager.createChatTextFile(text)))
                            true
                        } else {
                            false
                        }
                    }
                }
                else -> transferableContent
            }
        }
    }

    LaunchedEffect(completionProviders, isFocused) {
        if (!isFocused || completionProviders.isEmpty()) {
            completionList = null
            return@LaunchedEffect
        }
        snapshotFlow {
            ChatCompletionContext(
                text = state.textContent.text.toString(),
                selection = state.textContent.selection,
            )
        }.collectLatest { context ->
            val lists = completionProviders.mapNotNull { provider ->
                try {
                    provider.complete(context)?.takeIf { it.items.isNotEmpty() }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            val primary = lists.firstOrNull()
            completionList = primary?.let { list ->
                list.copy(
                    items = lists
                        .filter { it.replacementRange == list.replacementRange }
                        .flatMap { it.items }
                        .distinctBy { it.label to it.insertText }
                        .sortedWith(
                            compareByDescending<ChatCompletionItem> { it.sortScore }
                                .thenBy { it.label.length }
                                .thenBy { it.label.lowercase() }
                        )
                        .take(8),
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.isEditing()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SnowGraySoft)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SnowLabel(stringResource(R.string.editing), color = SnowInkSoft)
                Spacer(Modifier.weight(1f))
                if (state.editingReasoning != null) {
                    TextButton(onClick = { isEditingReasoning = true }) {
                        Text(stringResource(R.string.chat_input_edit_reasoning), fontSize = 12.sp, color = SnowInk)
                    }
                }
                SnowIconButton(HugeIcons.Cancel01, stringResource(R.string.cancel_edit), onClick = { state.clearInput() })
            }
        }
        completionList?.takeIf { it.items.isNotEmpty() }?.let { list ->
            SnowCompletionPopup(list) { item ->
                state.applyCompletion(list.replacementRange, item)
                completionList = null
            }
        }
        TextField(
            state = state.textContent,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_input")
                .contentReceiver(receiveContentListener)
                .onFocusChanged { isFocused = it.isFocused },
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("写点什么…", color = SnowInkFaint, fontSize = 15.sp) },
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (settings.displaySetting.sendOnEnter) ImeAction.Send else ImeAction.Default,
            ),
            onKeyboardAction = {
                if (settings.displaySetting.sendOnEnter && !state.isEmpty()) onSendMessage()
            },
            colors = TextFieldDefaults.colors().copy(
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = SnowInk,
                unfocusedTextColor = SnowInk,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, color = SnowInk),
            trailingIcon = {
                if (isFocused) {
                    SnowIconButton(HugeIcons.Fullscreen, "", onClick = { isFullScreen = !isFullScreen })
                }
            },
        )
    }
    if (isFullScreen) {
        SnowFullScreenEditor(state.textContent, "写点什么…") { isFullScreen = false }
    }
    val reasoningState = state.editingReasoning
    if (isEditingReasoning && reasoningState != null) {
        SnowFullScreenEditor(reasoningState, stringResource(R.string.chat_input_reasoning_placeholder)) {
            isEditingReasoning = false
        }
    }
}

@Composable
private fun SnowCompletionPopup(list: ChatCompletionList, onItemClick: (ChatCompletionItem) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SnowGraySoft),
    ) {
        list.items.forEach { item ->
            Text(
                text = item.label,
                fontSize = 13.sp,
                color = SnowInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onItemClick(item) },
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

private fun ChatInputState.applyCompletion(replacementRange: TextRange, item: ChatCompletionItem) {
    val textLength = textContent.text.length
    val start = replacementRange.min.coerceIn(0, textLength)
    val end = replacementRange.max.coerceIn(start, textLength)
    textContent.edit {
        replace(start, end, item.insertText)
        selection = TextRange(start + item.insertText.length)
    }
}

@Composable
private fun SnowFullScreenEditor(textState: TextFieldState, placeholder: String, onDone: () -> Unit) {
    BasicAlertDialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SnowBackground)
                .padding(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            TextButton(onClick = onDone) { Text(stringResource(R.string.chat_page_save), color = SnowInk) }
            TextField(
                state = textState,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder, color = SnowInkFaint) },
                colors = TextFieldDefaults.colors().copy(
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = SnowInk,
                    unfocusedTextColor = SnowInk,
                ),
            )
        }
    }
}
