package me.rerere.rikkahub.ui.components.ai

import me.rerere.rikkahub.companion.MusiPlusButton
import androidx.compose.ui.text.style.TextAlign
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import me.rerere.ui.toast.ToastType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessagePart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.ui.components.message.HoldToTalk
import me.rerere.rikkahub.ui.components.message.RecordingCard
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getSelectedASRProvider
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.service.MessageQueueState
import me.rerere.rikkahub.service.QueuedMessage
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionContext
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionItem
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionList
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.components.ui.KeepScreenOn
import me.rerere.rikkahub.ui.components.ui.PalaceButton
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTextButton
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.SoundEffectPlayer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.koin.compose.koinInject
import me.rerere.rikkahub.ui.pages.chat.VoicePhase
import me.rerere.rikkahub.ui.pages.chat.VoiceSessionState
import java.io.File
import kotlin.uuid.Uuid
import me.rerere.rikkahub.companion.LocalChatSkin
import me.rerere.rikkahub.companion.MusiProgress
import me.rerere.rikkahub.companion.skinColor
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import me.rerere.rikkahub.companion.SkinIconImage

private const val TAG = "ChatInput"

@Composable
fun ChatInput(
    state: ChatInputState,
    loading: Boolean,
    settings: Settings,
    // 会话视角下的助手和模型：会话开始后以会话上固定的配置为准
    assistant: Assistant,
    chatModel: Model?,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    completionProviders: List<ChatCompletionProvider> = emptyList(),
    onUpdateAssistant: (Assistant) -> Unit,
    onMoreClick: () -> Unit,
    // 打开表情包选择，不传就不显示表情按钮
    onOpenStickers: (() -> Unit)? = null,
    // 打开药盒，不传就不显示药丸按钮
    onOpenPills: (() -> Unit)? = null,
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
    // 直接发出一条文字消息（骰子 [骰子:n]、语音 [语音:秒]文字），不经过输入框里正在写的内容；
    // metadata 会原样挂到 Text part 上（按住说话带 {"voice_file": 录音路径}），不传就没有
    onSendSpecial: ((String, JsonObject?) -> Unit)? = null,
) {
    val toaster = LocalToaster.current

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val skinInput = LocalChatSkin.current?.input
    val capsule = skinInput?.layout == "capsule" || skinInput?.layout == "musi"
    // musi：表情、麦克风、药盒收进胶囊里面右侧，胶囊上面一根进度条
    val inside = skinInput?.layout == "musi"
    val iconSz = skinInput?.iconSize ?: 30f
    val containerShape = RoundedCornerShape((skinInput?.radius ?: 22f).dp)

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
    // ears 情绪识别的 HTTP 请求也走项目里共用的 OkHttpClient
    val httpClient: OkHttpClient = koinInject()
    LaunchedEffect(Unit) {
        soundEffectPlayer.preload(R.raw.asr_start, R.raw.asr_stop)
    }
    val asrPermission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permissionState = asrPermission)
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

    // 按住说话：录一段，松手后转成文字，以 [语音:秒]文字 发出去
    val holdScope = rememberCoroutineScope()
    var holdMode by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var holdCancelling by remember { mutableStateOf(false) }
    var holdHeard by remember { mutableStateOf("") }
    var holdStart by remember { mutableStateOf(0L) }
    DisposableEffect(Unit) {
        onDispose { if (asr.state.value.isRecording) asr.stop() }
    }

    // 底部渐变遮罩：从输入区顶部一直过渡到屏幕底部（键盘弹出时到键盘上沿），避免消息透到系统导航栏
    // 皮肤有底色就用皮肤底色，不然底下会透出一截灰
    val scrimColor = skinColor(LocalChatSkin.current?.background?.color) ?: MaterialTheme.colorScheme.background
    val imeInsets = WindowInsets.ime
    Surface(
        color = Color.Transparent,
        modifier = Modifier.drawWithCache {
            val brush = Brush.verticalGradient(
                colors = listOf(scrimColor.copy(alpha = 0f), scrimColor),
                startY = 0f,
                endY = (size.height - imeInsets.getBottom(this)).coerceAtLeast(1f),
            )
            onDrawBehind { drawRect(brush) }
        },
    ) {
        Column(
            modifier = modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (holding) {
                RecordingCard(
                    heard = holdHeard,
                    amplitudes = asrState.amplitudes,
                    cancelling = holdCancelling,
                    modifier = Modifier.padding(horizontal = 30.dp),
                )
            }
            MessageQueuePanel(
                state = messageQueue,
                onRemove = onRemoveQueuedMessage,
                onBeginEdit = onBeginEditQueuedMessage,
                onFinishEdit = onFinishEditQueuedMessage,
                onResume = onResumeMessageQueue,
            )
            if (inside) MusiProgress(skinInput?.progressLeft ?: "01:24", skinInput?.progressRight ?: "03:48", Modifier.fillMaxWidth().padding(horizontal = 4.dp))
            // 皮肤 buttonsOnTop：加号、表情、发送也放到胶囊上面那一排
            val onTop = capsule && skinInput?.buttonsOnTop == true
            val plusBtn: @Composable () -> Unit = {
                val plus = skinInput?.plusIcon
                if (inside) {
                    MusiPlusButton(settings.displaySetting.userNickname, settings.displaySetting.userAvatar, onMoreClick)
                } else if (plus != null) {
                    SkinIconImage(plus, skinInput?.plusSize ?: iconSz, "更多", modifier = Modifier.offset(y = (skinInput?.plusY ?: -10f).dp), onClick = onMoreClick)
                } else {
                    ThinIconButton(icon = ThinPlusIcon, contentDescription = stringResource(R.string.more_options), tint = ChatIconInk, onClick = onMoreClick)
                }
            }
            val tailBtns: @Composable () -> Unit = {
                if (onOpenStickers != null && !inside) {
                    val sticker = skinInput?.stickerIcon
                    if (sticker != null) {
                        SkinIconImage(sticker, skinInput?.stickerSize ?: iconSz, "表情包", modifier = Modifier.offset(y = (skinInput?.stickerY ?: 0f).dp), onClick = onOpenStickers)
                    } else {
                        ThinIconButton(icon = ThinSmileIcon, contentDescription = "表情包", tint = ChatIconInk, onClick = onOpenStickers)
                    }
                }
                val showStop = loading && state.isEmpty()
                val send = skinInput?.sendIcon
                if (send != null) {
                    SkinIconImage(
                        send, skinInput?.sendSize ?: iconSz, if (showStop) "停止" else "发送",
                        modifier = Modifier.offset(y = (skinInput?.sendY ?: 0f).dp).alpha(if (state.isEmpty() && !showStop) 0.6f else 1f),
                        onLongClick = { if (!state.isEmpty()) sendMessageWithoutAnswer() },
                    ) { if (showStop || !state.isEmpty()) sendMessage() }
                } else if (!asrState.isRecording) {
                    SendButton(
                        hazeState = hazeState,
                        loading = loading,
                        empty = state.isEmpty(),
                        onClick = { sendMessage() },
                        onLongClick = { sendMessageWithoutAnswer() },
                    )
                }
            }
            if (capsule) {
                // 药盒、麦克风放到胶囊上面一排小方块
                val showMic = onSendSpecial != null && !voiceState.isActive && asrState.isAvailable
                if (!inside && (onTop || (skinInput?.showChips != false && (onOpenPills != null || showMic)))) {
                    val chipBg = skinColor(skinInput?.chipColor) ?: Color.White.copy(alpha = 0.7f)
                    val chipTint = skinColor(skinInput?.iconTint) ?: ChatIconInk
                    Row(
                        modifier = (if (onTop) Modifier.fillMaxWidth() else Modifier).padding(start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy((skinInput?.chipGap ?: 8f).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onTop) plusBtn()
                        if (onOpenPills != null) {
                            SkinChip(skinInput?.pillIcon, ThinPillIcon, "药盒", chipBg, chipTint, onOpenPills)
                        }
                        if (showMic) {
                            SkinChip(
                                if (holdMode) skinInput?.keyboardIcon else skinInput?.micIcon,
                                if (holdMode) ThinKeyboardIcon else ThinMicIcon,
                                if (holdMode) "切回打字" else "按住说话",
                                chipBg, chipTint,
                            ) { holdMode = !holdMode }
                        }
                        if (onTop) {
                            Spacer(Modifier.weight(1f))
                            tailBtns()
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((skinInput?.gap ?: 6f).dp),
            ) {
            if (capsule && !onTop) plusBtn()
            Surface(
                modifier = Modifier
                    .then(if (capsule) Modifier.weight(1f) else Modifier.fillMaxWidth())
                    .then(
                        if (capsule && skinInput?.shadow == true) Modifier.shadow(
                            elevation = (skinInput?.shadowElevation ?: 4f).dp, shape = containerShape, clip = false,
                            ambientColor = Color.Black.copy(alpha = skinInput?.shadowAlpha ?: 0.25f), spotColor = Color.Black.copy(alpha = skinInput?.shadowAlpha ?: 0.25f),
                        ) else Modifier
                    )
                    .then(
                        skinColor(skinInput?.color)?.let { fillColor ->
                            val borderColor = skinColor(skinInput?.border)
                            val borderWidth = skinInput?.borderWidth ?: 0f
                            Modifier.background(fillColor, containerShape).then(
                                if (borderColor != null && borderWidth > 0f) {
                                    Modifier.border(borderWidth.dp, borderColor, containerShape)
                                } else Modifier
                            )
                        } ?: Modifier.liquidGlass(hazeState, containerShape, fillAlpha = 0.56f, edgeAlpha = 0.90f)
                    )
                    // 皮肤 gloss：胶囊上半截叠一层白色渐变，看着鼓起来
                    .then(if (capsule && skinInput?.gloss == true) Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent)), containerShape) else Modifier),
                shape = containerShape,
                tonalElevation = 0.dp,
                color = Color.Transparent,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = if (capsule) (skinInput?.padX ?: 8f).dp else 8.dp, vertical = if (capsule) 0.dp else 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (voiceState.phase != VoicePhase.Off) {
                        VoiceModeRow(
                            state = voiceState,
                            onStop = onStopVoiceMode,
                            onRetry = { onStartVoiceMode?.invoke() },
                        )
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    if (state.messageContent.isNotEmpty()) {
                        MediaFileInputRow(state = state)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                    if (holdMode && onSendSpecial != null && !state.isEditing()) {
                        HoldToTalk(
                            recording = holding,
                            cancelling = holdCancelling,
                            onDown = {
                                when {
                                    !asrPermission.allRequiredPermissionsGranted -> {
                                        asrPermission.requestPermissions()
                                        false
                                    }

                                    !asr.state.value.isAvailable -> {
                                        toaster.show("先在设置里配好语音识别", type = ToastType.Warning)
                                        false
                                    }

                                    asr.state.value.isRecording -> false
                                    else -> {
                                        holdHeard = ""
                                        holdCancelling = false
                                        holding = true
                                        holdStart = System.currentTimeMillis()
                                        asr.start { holdHeard = it }
                                        true
                                    }
                                }
                            },
                            onSlide = { holdCancelling = it },
                            onUp = { cancelled ->
                                val secs = ((System.currentTimeMillis() - holdStart) / 1000).toInt().coerceIn(1, 60)
                                asr.stop()
                                holdScope.launch {
                                    if (cancelled) {
                                        holding = false
                                        holdCancelling = false
                                        holdHeard = ""
                                        // 滑走取消：这段录音不要了，等它落盘后删掉，不调 ears
                                        withTimeoutOrNull(20_000) {
                                            asr.state.first { it.recordingPath != null || it.status == ASRStatus.Idle }
                                        }?.recordingPath?.let { path ->
                                            runCatching { File(path).delete() }
                                                .onFailure { Log.w(TAG, "Delete cancelled recording failed", it) }
                                        }
                                        return@launch
                                    }
                                    // 等整段录音落盘：stop() 存好 WAV 后路径先于 Idle 出现；没写出来就以 Idle 为准
                                    val recordingPath = withTimeoutOrNull(20_000) {
                                        asr.state.first { it.recordingPath != null || it.status == ASRStatus.Idle }
                                    }?.recordingPath
                                    val voiceMetadata = recordingPath?.let { buildJsonObject { put("voice_file", it) } }
                                    // 照 ears 原作：松手后把整段录音发给耳朵（/api/listen），字和语气一起拿回来，不再上传硅基
                                    val earsSetting = (settings.getSelectedASRProvider() as? ASRProviderSetting.SiliconFlow)
                                        ?.takeIf { it.earsUrl.isNotBlank() && it.earsKey.isNotBlank() }
                                    withTimeoutOrNull(20_000) {
                                        asr.state.first { it.status == ASRStatus.Idle }
                                    }
                                    val err = asr.state.value.errorMessage
                                    val ears = when {
                                        err != null -> null
                                        recordingPath == null -> EarsResult.Failed("没录到声音，再说一次")
                                        earsSetting == null -> EarsResult.Failed("没配耳朵地址或钥匙")
                                        else -> listenEars(httpClient, earsSetting.earsUrl, earsSetting.earsKey, recordingPath)
                                    }
                                    holding = false
                                    holdCancelling = false
                                    holdHeard = ""
                                    when {
                                        err != null -> toaster.show(err, type = ToastType.Error)
                                        ears is EarsResult.Heard -> onSendSpecial(
                                            "[语音:$secs]${ears.text}${ears.suffix}",
                                            voiceMetadata,
                                        )
                                        ears is EarsResult.Failed -> toaster.show(ears.message)
                                    }
                                }
                            },
                        )
                    } else {
                        TextInputRow(
                            state = state,
                            assistant = assistant,
                            completionProviders = completionProviders,
                            onSendMessage = { sendMessage() },
                        )
                    }
                    }
                    if (inside) {
                        val showMicIn = onSendSpecial != null && !voiceState.isActive && asrState.isAvailable
                        val inSz = skinInput?.chipIconSize ?: 24f
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (onOpenStickers != null) skinInput?.stickerIcon?.let { SkinIconImage(it, inSz, "表情包", onClick = onOpenStickers) }
                            if (showMicIn) (if (holdMode) skinInput?.keyboardIcon else skinInput?.micIcon)?.let {
                                SkinIconImage(it, inSz, if (holdMode) "切回打字" else "按住说话") { holdMode = !holdMode }
                            }
                            if (onOpenPills != null) skinInput?.pillIcon?.let { SkinIconImage(it, inSz, "药盒", onClick = onOpenPills) }
                        }
                    }
                    }

                    if (capsule && loading) KeepScreenOn()
                    if (!capsule) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // 1. 加号：打开加号面板
                            ThinIconButton(
                                icon = ThinPlusIcon,
                                contentDescription = stringResource(R.string.more_options),
                                tint = ChatIconInk,
                                onClick = onMoreClick,
                            )

                            // 2. 表情包：直接打开表情包选择
                            if (onOpenStickers != null) {
                                ThinIconButton(
                                    icon = ThinSmileIcon,
                                    contentDescription = stringResource(R.string.files_picker_stickers),
                                    tint = ChatIconInk,
                                    onClick = onOpenStickers,
                                )
                            }

                            // 2.5 药盒：在表情旁边
                            if (onOpenPills != null) {
                                ThinIconButton(
                                    icon = ThinPillIcon,
                                    contentDescription = "药盒",
                                    tint = ChatIconInk,
                                    onClick = onOpenPills,
                                )
                            }

                            // 3. 按住说话 / 打字 切换
                            if (onSendSpecial != null && !voiceState.isActive && asrState.isAvailable) {
                                ThinIconButton(
                                    icon = if (holdMode) ThinKeyboardIcon else ThinMicIcon,
                                    contentDescription = if (holdMode) "切回打字" else "按住说话",
                                    tint = ChatIconInk,
                                    onClick = { holdMode = !holdMode },
                                )
                            }
                        }

                        if (loading) {
                            KeepScreenOn()
                        }

                        AnimatedVisibility(
                            visible = !asrState.isRecording,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            SendButton(
                                hazeState = hazeState,
                                loading = loading,
                                empty = state.isEmpty(),
                                onClick = { sendMessage() },
                                onLongClick = { sendMessageWithoutAnswer() },
                            )
                        }
                    }
                    }
                }
            }
            if (capsule && !onTop) tailBtns()
            }

        }
    }

}

private val SendIconInk = Color(0xFF636366)

private val ChatInputLabelStyle = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.sp,
    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
    letterSpacing = 0.8.sp,
)

@Composable
private fun SendButton(
    hazeState: HazeState,
    loading: Boolean,
    empty: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showStop = loading && empty
    // 玻璃模式下 Send / Stop 底是 --lg-fill（白 0.56），图标描边色 #636366，不是实色深块
    val ink = SendIconInk
    val contentColor = if (empty && !showStop) ink.copy(alpha = 0.4f) else ink
    Surface(
        modifier = modifier
            .height(28.dp)
            .testTag("chat_send_button")
            .liquidGlass(hazeState, CircleShape, fillAlpha = 0.56f, edgeAlpha = 1.0f)
            .clip(CircleShape)
            .combinedClickable(
                enabled = showStop || !empty,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = CircleShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = if (showStop) ThinStopIcon else ThinEnvelopeIcon,
                contentDescription = stringResource(if (showStop) R.string.stop else R.string.send),
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (showStop) "Stop" else "Send",
                style = ChatInputLabelStyle,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun InputLeadingMark() {
    val settings = LocalSettings.current.displaySetting
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 用户头像：32dp 圆形，0.5dp 白 1.0 描边，阴影 0 2 8 黑 0.06；不可点
        UIAvatar(
            name = settings.userNickname,
            value = settings.userAvatar,
            modifier = Modifier
                .dropShadow(
                    CircleShape,
                    Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.06f), offset = DpOffset(0.dp, 2.dp)),
                )
                .size(32.dp)
                .border(0.5.dp, Color.White, CircleShape),
            shape = CircleShape,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .height(18.dp)
                .background(Color.White.copy(alpha = 0.75f)),
        )
    }
}

@Composable
private fun ThinIconButton(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(30.dp),
        shape = CircleShape,
        tonalElevation = 0.dp,
        color = Color.Transparent,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun TextInputRow(
    state: ChatInputState,
    assistant: Assistant,
    completionProviders: List<ChatCompletionProvider>,
    onSendMessage: () -> Unit,
) {
    val settings = LocalSettings.current
    val filesManager: FilesManager = koinInject()

    var isEditingReasoning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.isEditing()) {
            // 编辑消息时输入框上方的提示条：磨砂卡，文字和按钮用 Palace 配色
            val palace = palaceColors()
            PalaceGlass {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.editing), fontSize = 13.sp, color = palace.ink2)
                    Spacer(Modifier.weight(1f))
                    if (state.editingReasoning != null) {
                        PalaceButton(
                            text = stringResource(R.string.chat_input_edit_reasoning),
                            onClick = { isEditingReasoning = true },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = stringResource(R.string.cancel_edit),
                        tint = palace.ink2,
                        modifier = Modifier.clickable { state.clearInput() }
                    )
                }
            }
        }

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
                                state.addImages(
                                    filesManager.createChatFilesByContents(
                                        listOf(uri)
                                    )
                                )
                            }
                            uri != null
                        }
                    }

                    settings.displaySetting.pasteLongTextAsFile && transferableContent.hasMediaType(MediaType.Text) -> {
                        transferableContent.consume { item ->
                            val text = item.text?.toString()
                            if (text != null && text.length > settings.displaySetting.pasteLongTextThreshold) {
                                val document = filesManager.createChatTextFile(text)
                                state.addFiles(listOf(document))
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
                        provider.complete(context)
                            ?.takeIf { it.items.isNotEmpty() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                val primary = lists.firstOrNull()
                completionList = primary?.let { list ->
                    val mergedItems = lists
                        .filter { it.replacementRange == list.replacementRange }
                        .flatMap { it.items }
                        .distinctBy { it.label to it.insertText }
                        .sortedWith(
                            compareByDescending<ChatCompletionItem> { it.sortScore }
                                .thenBy { it.label.length }
                                .thenBy { it.label.lowercase() }
                        )
                        .take(8)
                    list.copy(items = mergedItems)
                }
            }
        }

        completionList?.takeIf { it.items.isNotEmpty() }?.let { list ->
            CompletionPopup(
                completionList = list,
                onItemClick = { item ->
                    state.applyCompletion(list.replacementRange, item)
                    completionList = null
                },
            )
        }

        val capsuleRow = LocalChatSkin.current?.input?.layout.let { it == "capsule" || it == "musi" }
        val capsulePadY = LocalChatSkin.current?.input?.padY ?: 2f
        // 上半部分输入区：BasicTextField 自己控制内边距，单行时上下留白约 6dp
        BasicTextField(
            state = state.textContent,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_input")
                .contentReceiver(receiveContentListener)
                .onFocusChanged {
                    isFocused = it.isFocused
                },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = skinColor(LocalChatSkin.current?.input?.text) ?: MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(skinColor(LocalChatSkin.current?.input?.text) ?: Color(0xFF333333)),
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (settings.displaySetting.sendOnEnter) ImeAction.Send else ImeAction.Default
            ),
            onKeyboardAction = {
                if (settings.displaySetting.sendOnEnter && !state.isEmpty()) {
                    onSendMessage()
                }
            },
            decorator = TextFieldDecorator { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                        .padding(horizontal = 8.dp, vertical = if (capsuleRow) capsulePadY.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // NOIR 输入框左边：半透白圆底 + 灰色小人头像轮廓 + 一条竖分隔线，纯装饰不可点
                    if (LocalChatSkin.current?.input?.layout.let { it != "capsule" && it != "musi" }) InputLeadingMark()
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (state.textContent.text.isEmpty()) {
                            Text(
                                text = LocalChatSkin.current?.input?.placeholder ?: "和阿辞说句话…",
                                modifier = if (LocalChatSkin.current?.input?.layout == "musi") Modifier.fillMaxWidth() else Modifier,
                                textAlign = if (LocalChatSkin.current?.input?.layout == "musi") TextAlign.Center else null,
                                fontSize = 14.sp,
                                letterSpacing = 0.3.sp,
                                color = skinColor(LocalChatSkin.current?.input?.placeholderColor) ?: palaceColors().faint,
                            )
                        }
                        innerTextField()
                    }
                    if (isFocused) {
                        // 全屏编辑：功能不变，换成和声波同粗细的细线小图标
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { isFullScreen = !isFullScreen },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = ThinExpandIcon,
                                contentDescription = null,
                                tint = Color(0xFFA0A0A0),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            },
        )
        if (isFullScreen) {
            FullScreenEditor(
                textState = state.textContent,
                placeholder = stringResource(R.string.chat_input_placeholder),
            ) {
                isFullScreen = false
            }
        }
        val reasoningState = state.editingReasoning
        if (isEditingReasoning && reasoningState != null) {
            FullScreenEditor(textState = reasoningState, placeholder = stringResource(R.string.chat_input_reasoning_placeholder)) {
                isEditingReasoning = false
            }
        }
    }
}

@Composable
private fun CompletionPopup(
    completionList: ChatCompletionList,
    onItemClick: (ChatCompletionItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            items(
                items = completionList.items,
                key = { item -> "${item.label}:${item.insertText}" },
            ) { item ->
                Surface(
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.label,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.detail?.let { detail ->
                                Text(
                                    text = detail,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

private fun ChatInputState.applyCompletion(
    replacementRange: TextRange,
    item: ChatCompletionItem,
) {
    val textLength = textContent.text.length
    val start = replacementRange.min.coerceIn(0, textLength)
    val end = replacementRange.max.coerceIn(start, textLength)
    textContent.edit {
        replace(start, end, item.insertText)
        selection = TextRange(start + item.insertText.length)
    }
}

@Composable
private fun FullScreenEditor(
    textState: TextFieldState,
    placeholder: String,
    onDone: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = {
            onDone()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row {
                        PalaceTextButton(
                            text = stringResource(R.string.chat_page_save),
                            onClick = {
                                onDone()
                            },
                        )
                    }
                    TextField(
                        state = textState,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .fillMaxSize(),
                        shape = RoundedCornerShape(32.dp),
                        placeholder = {
                            Text(placeholder)
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        colors = TextFieldDefaults.colors().copy(
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}

// ears /api/listen 的结果：Heard 有字，suffix 照原作复制按钮拼好的语气尾巴；Failed 是要弹给她的话
private sealed interface EarsResult {
    data class Heard(val text: String, val suffix: String) : EarsResult
    data class Failed(val message: String) : EarsResult
}

/**
 * 照 ears 原作 static/index.html 的 send()：把整段录音发给耳朵（POST {earsUrl}/api/listen）。
 * 回的 JSON 带 error 就把 error 弹出来让她重说；网络出错（回的不是 JSON）；照原作不设超时，等耳朵回完弹「网络出错，再试一次」。
 * 日志里不出现钥匙。
 */
private suspend fun listenEars(
    httpClient: OkHttpClient,
    earsUrl: String,
    earsKey: String,
    recordingPath: String,
): EarsResult = run {
    withContext(Dispatchers.IO) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "a.wav",
                    File(recordingPath).readBytes().toRequestBody("audio/wav".toMediaType()),
                )
                .build()
            val request = Request.Builder()
                .url("${earsUrl.trimEnd('/')}/api/listen")
                .addHeader("Authorization", "Bearer $earsKey")
                .post(body)
                .build()
            val result: EarsResult = httpClient.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(respBody) }.getOrNull()
                val error = json?.optString("error", "")?.trim().orEmpty()
                if (error.isNotEmpty()) return@use EarsResult.Failed(error)
                if (json == null || !resp.isSuccessful) {
                    Log.w(TAG, "ears listen HTTP ${resp.code}: ${respBody.take(200)}")
                    return@use EarsResult.Failed("网络出错，再试一次")
                }
                val text = json.optString("text", "").trim()
                val emotion = json.optString("emotion", "").trim()
                val hint = json.optString("hint", "").trim()
                // 不是她的声音时耳朵不转字，只回一句 hint
                if (text.isEmpty()) return@use EarsResult.Failed(hint.ifEmpty { "没听清，再说一次" })
                // relative 每项拼成「键值」，用「、」连起来；没有 relative 就是 null
                val relative = json.optJSONObject("relative")?.let { rel ->
                    rel.keys().asSequence().map { key -> key + rel.optString(key, "") }
                        .joinToString("、")
                        .takeIf { it.isNotEmpty() }
                }
                val parts = buildList {
                    add("语气：$emotion")
                    if (hint.isNotEmpty()) add(hint)
                    if (relative != null) add("和平时比$relative")
                }
                EarsResult.Heard(text = text, suffix = "（${parts.joinToString("，")}）")
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "ears listen failed", e)
            EarsResult.Failed("网络出错，再试一次")
        }
    }
}

// 皮肤输入框上面那排小方块：http 开头当图片，别的当 emoji，没写用原来的线条图标
@Composable
private fun SkinChip(
    icon: String?,
    fallback: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    bg: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    val si = LocalChatSkin.current?.input
    val chipShape = RoundedCornerShape((si?.chipRadius ?: 10f).dp)
    Box(
        modifier = Modifier
            .size((si?.chipSize ?: 34f).dp)
            .background(bg, chipShape)
            .clip(chipShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon == null -> Icon(fallback, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
            icon.startsWith("http") -> SkinIconImage(icon, si?.chipIconSize ?: 22f, description, onClick = onClick)
            else -> Text(icon, fontSize = (si?.chipEmojiSize ?: 17f).sp)
        }
    }
}
