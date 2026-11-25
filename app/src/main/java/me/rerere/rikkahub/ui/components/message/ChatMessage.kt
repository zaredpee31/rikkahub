package me.rerere.rikkahub.ui.components.message

import me.rerere.rikkahub.companion.musiBubble
import me.rerere.rikkahub.data.ai.tools.local.ASK_USER_TOOL_NAME
import me.rerere.rikkahub.companion.musiPullUp
import me.rerere.rikkahub.companion.LocalMusiThink
import me.rerere.rikkahub.companion.MusiEnvelope
import me.rerere.rikkahub.companion.MusiVoiceBar
import me.rerere.rikkahub.companion.MusiVoiceNote
import me.rerere.rikkahub.companion.MusiQuoted
import androidx.compose.ui.graphics.Brush
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Video01
import me.rerere.rikkahub.ui.components.ai.LocalGlassHazeState
import me.rerere.rikkahub.ui.components.ai.GlassGradient
import me.rerere.rikkahub.ui.components.ai.GlassShadow
import me.rerere.rikkahub.ui.components.ai.liquidGlass
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.music.NowPlaying
import me.rerere.rikkahub.music.SongCard
import me.rerere.rikkahub.music.SongRequests
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.richtext.buildMarkdownPreviewHtml
import me.rerere.rikkahub.ui.components.sticker.StickerMarkdownBlock
import me.rerere.rikkahub.ui.components.sticker.StickerOnlyBlock
import me.rerere.rikkahub.ui.components.sticker.isStickerOnly
import me.rerere.rikkahub.ui.components.sticker.rememberStickerSegments
import me.rerere.rikkahub.ui.components.ui.ChainOfThought
import me.rerere.rikkahub.ui.components.ui.Favicon
import me.rerere.rikkahub.ui.components.ui.FaviconRow
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.context.LocalWorkspaceId
import me.rerere.rikkahub.ui.theme.LocalChatFontFamily
import me.rerere.rikkahub.ui.theme.rememberChatFontFamily
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.urlDecode
import me.rerere.rikkahub.webpage.WebPages
import me.rerere.ui.charts.ChartCard
import me.rerere.ui.charts.ChartSpec
import me.rerere.ui.webview.WebViewContentCache
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import me.rerere.rikkahub.companion.LocalChatSkin
import me.rerere.rikkahub.companion.skinColor
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.em
import me.rerere.rikkahub.companion.LocalSkinFold
import androidx.compose.foundation.clickable

@Composable
fun ChatMessage(
    node: MessageNode,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    model: Model? = null,
    assistant: Assistant? = null,
    lastMessage: Boolean = false,
    onFork: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (MessageNode) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    floor: Int? = null,
) {
    val message = node.messages[node.selectIndex]
    if (message.isContextCheckpoint) {
        ChatMessageContextCheckpoint(
            message = message,
            onEdit = onEdit,
            onDelete = onDelete,
            modifier = modifier,
        )
        return
    }
    if (message.homeNote != null) {
        ChatMessageHomeNote(message = message, onDelete = onDelete, modifier = modifier)
        return
    }
    val settings = LocalSettings.current.displaySetting
    val chatFontFamily = LocalChatFontFamily.current ?: rememberChatFontFamily(settings)
    val textStyle = LocalTextStyle.current.copy(
        fontSize = LocalTextStyle.current.fontSize * settings.fontSizeRatio,
        lineHeight = LocalTextStyle.current.lineHeight * settings.fontSizeRatio,
        fontFamily = chatFontFamily,
        // NOIR .mes_text：两端对齐 + 字距 0.5
        textAlign = TextAlign.Justify,
        letterSpacing = 0.5.sp,
    )
    var showActionsSheet by remember { mutableStateOf(false) }
    var showSelectCopySheet by remember { mutableStateOf(false) }
    val navController = LocalNavController.current
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var skinFoldOpen by remember(message.id) { mutableStateOf(false) }
    val musiThink = remember(message.id) { mutableStateOf(false) }
    val musiOn = message.role == MessageRole.ASSISTANT && LocalChatSkin.current?.bubbleThem?.avatarFrame == "polaroid"
    val skinFoldMode = message.role != MessageRole.USER && LocalChatSkin.current?.bubbleThem?.caption != null
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (message.role == MessageRole.USER) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!message.parts.isEmptyUIMessage()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                ChatMessageAssistantAvatar(
                    message = message,
                    musiThink = if (musiOn) musiThink else null,
                    musiCaption = if (musiOn) LocalChatSkin.current?.bubbleThem?.caption else null,
                    onMusiCaption = { skinFoldOpen = !skinFoldOpen; musiThink.value = skinFoldOpen },
                    model = model,
                    assistant = assistant,
                    loading = loading,
                    modifier = Modifier.weight(1f)
                )
                ChatMessageUserAvatar(
                    message = message,
                    avatar = settings.userAvatar,
                    nickname = settings.userNickname,
                    modifier = Modifier.weight(1f)
                )
            }
            val skinCap = LocalChatSkin.current?.let { if (message.role == MessageRole.USER) it.bubbleMe else it.bubbleThem }
            if (!musiOn) skinCap?.caption?.let { caption ->
                if (skinCap.avatarFrame != null && caption.startsWith("❝") && caption.endsWith("❞")) {
                    MusiQuoted(
                        caption.removePrefix("❝").removeSuffix("❞").trim(),
                        skinCap.captionSize,
                        skinColor(skinCap.captionColor) ?: Color(0xFFB0B0B0),
                        Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                } else
                Text(
                    text = caption,
                    fontSize = skinCap.captionSize.sp,
                    letterSpacing = 0.05.em,
                    color = skinColor(skinCap.captionColor) ?: Color(0xFFB0B0B0),
                    maxLines = 1,
                    modifier = Modifier
                        .then(
                            if (skinFoldMode) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { skinFoldOpen = !skinFoldOpen } else Modifier
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        CompositionLocalProvider(
            LocalTextStyle provides textStyle,
            LocalWorkspaceId provides assistant?.workspaceId?.toString(),
        ) {
            CompositionLocalProvider(LocalSkinFold provides (if (skinFoldMode) skinFoldOpen else null), LocalMusiThink provides (if (musiOn) musiThink else null)) {
            val partsBlock = @Composable { Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = if (message.role == MessageRole.USER) Alignment.End else Alignment.Start,
            ) {
            if (musiOn && musiThink.value) {
                Column(Modifier.padding(top = 12.dp)) {
                    message.parts.filterIsInstance<UIMessagePart.Reasoning>().forEach { r ->
                        key(r.createdAt) { MusiThinkingPanel(r, assistant) }
                    }
                }
            }
            MessagePartsBlock(
                assistant = assistant,
                role = message.role,
                parts = message.parts,
                annotations = message.annotations,
                loading = loading,
                model = model,
                onToolApproval = onToolApproval,
                onToolAnswer = onToolAnswer,
                onUserMessageClick = if (message.role == MessageRole.USER) onEdit else null,
            )
            } }
            if (musiOn && !message.parts.isEmptyUIMessage()) {
                Box(Modifier.padding(start = 44.dp).musiPullUp(14.dp)) { partsBlock() }
            } else partsBlock()
            }
        }

        if (message.role == MessageRole.USER && LocalChatSkin.current?.bubbleMe?.avatarFrame == "polaroid" && !message.parts.isEmptyUIMessage()) {
            MusiEnvelope(read = !lastMessage, modifier = Modifier.padding(end = 2.dp))
        }
        val showActions = if (lastMessage) {
            !loading
        } else {
            message.parts.isEmptyUIMessage().not()
        }

        val motionScheme = MaterialTheme.motionScheme
        AnimatedVisibility(
            visible = showActions,
            enter = slideInVertically(motionScheme.defaultSpatialSpec()) { it / 2 } +
                fadeIn(motionScheme.defaultEffectsSpec()),
            exit = slideOutVertically(motionScheme.fastSpatialSpec()) { it / 2 } +
                fadeOut(motionScheme.fastEffectsSpec())
        ) {
            Column(
                // 助手横条和上面气泡的间距 12dp（外层 spacedBy 4 + 这里 8）
                modifier = Modifier
                    .animateContentSize()
                    .then(if (message.role == MessageRole.USER) Modifier else Modifier.padding(top = 8.dp))
            ) {
                ChatMessageActionButtons(
                    message = message,
                    onRegenerate = onRegenerate,
                    node = node,
                    onUpdate = onUpdate,
                    onOpenActionSheet = {
                        showActionsSheet = true
                    },
                    floor = floor,
                )
            }
        }

        EditedFilesList(
            parts = message.parts,
            assistant = assistant,
        )


    }
    if (showActionsSheet) {
        ChatMessageActionsSheet(
            message = message,
            onEdit = onEdit,
            onDelete = onDelete,
            onShare = onShare,
            onFork = onFork,
            model = model,
            onSelectAndCopy = {
                showSelectCopySheet = true
            },
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onWebViewPreview = {
                val textContent = message.parts
                    .filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()
                if (textContent.isNotBlank()) {
                    val htmlContent = buildMarkdownPreviewHtml(
                        context = context,
                        markdown = textContent,
                        colorScheme = colorScheme
                    )
                    val contentId = WebViewContentCache.store(context.cacheDir, htmlContent)
                    navController.navigate(Screen.WebView(contentId = contentId))
                }
            },
            onDismissRequest = {
                showActionsSheet = false
            }
        )
    }

    if (showSelectCopySheet) {
        ChatMessageCopySheet(
            message = message,
            onDismissRequest = {
                showSelectCopySheet = false
            }
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun MessagePartsBlock(
    assistant: Assistant?,
    role: MessageRole,
    model: Model?,
    parts: List<UIMessagePart>,
    annotations: List<UIMessageAnnotation>,
    loading: Boolean,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onUserMessageClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // 消息输出HapticFeedback
    val hapticFeedback = LocalHapticFeedback.current
    val settings = LocalSettings.current
    val partsState by rememberUpdatedState(parts)
    // 网页带来的图只发给模型，气泡里不显示
    val hiddenImages = remember(parts) { WebPages.imageUris(parts) }

    val handleClickCitation: (String) -> Unit = remember {
        handler@{ citationId ->
            partsState.forEach { part ->
                if (part is UIMessagePart.Tool && part.toolName == "search_web" && part.isExecuted) {
                    val outputText = part.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                    val items =
                        runCatching { JsonInstant.parseToJsonElement(outputText).jsonObject["items"]?.jsonArray }.getOrNull()
                            ?: return@forEach
                    items.forEach { item ->
                        val id = item.jsonObject["id"]?.jsonPrimitive?.content ?: return@forEach
                        val url = item.jsonObject["url"]?.jsonPrimitive?.content ?: return@forEach
                        if (citationId == id) {
                            context.openUrl(url)
                            return@handler
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(settings.displaySetting) {
        snapshotFlow { partsState }
            .debounce(50.milliseconds)
            .collect { parts ->
                if (parts.isNotEmpty() && loading && settings.displaySetting.enableMessageGenerationHapticEffect) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                }
            }
    }

    // Render parts in original order (group thinking/tool as chain-of-thought)
    val groupedParts = remember(parts) { parts.groupMessageParts() }
    groupedParts.fastForEach { block ->
        when (block) {
            is MessagePartBlock.ThinkingBlock -> {
                // MUSI：思考的开关是头像旁边的小云，开了画 CD 播放器面板；工具步骤照旧
                val musiThink = LocalMusiThink.current
                val stepsNoThink = if (musiThink != null) block.steps.filter { it !is ThinkingStep.ReasoningStep } else block.steps
                // CIAO：普通工具画成小胶囊；要批准、问答的还走原来的样子
                val musiChips = if (musiThink != null) stepsNoThink.filterIsInstance<ThinkingStep.ToolStep>()
                    .filter { it.tool.toolName != ASK_USER_TOOL_NAME && !it.tool.isPending } else emptyList()
                val restSteps = if (musiChips.isEmpty()) stepsNoThink else stepsNoThink.filter { it !in musiChips }
                if (musiChips.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        musiChips.forEach { st ->
                            key(st.tool.toolCallId.ifBlank { st.hashCode().toString() }) {
                                MusiToolStep(tool = st.tool, loading = loading && !st.tool.isExecuted, assistant = assistant)
                            }
                        }
                    }
                }
                // CIAO 下思考已经单独交给小云，工具步骤不跟着 now playing 折叠
                if (restSteps.isNotEmpty() && (musiThink != null || LocalSkinFold.current != false)) {
                    val isReasoningOnlyBlock = restSteps.fastAll { it is ThinkingStep.ReasoningStep }
                    ChainOfThought(
                        modifier = Modifier.animateContentSize(),
                        steps = restSteps,
                        collapsedAdaptiveWidth = isReasoningOnlyBlock,
                        // 跟 Claude app 一样，思考那块不要底色
                        cardColors = CardDefaults.cardColors(
                            containerColor = Color.Transparent,
                        ),
                    ) { step ->
                        when (step) {
                            is ThinkingStep.ReasoningStep -> {
                                key(step.reasoning.createdAt) {
                                    ChatMessageReasoningStep(
                                        reasoning = step.reasoning,
                                        model = model,
                                        assistant = assistant,
                                        collapsedAdaptiveWidth = isReasoningOnlyBlock,
                                    )
                                }
                            }

                            is ThinkingStep.ToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageToolStep(
                                        tool = step.tool,
                                        loading = loading && !step.tool.isExecuted,
                                        assistant = assistant,
                                        onToolApproval = onToolApproval,
                                        onToolAnswer = onToolAnswer,
                                    )
                                }
                            }

                            is ThinkingStep.ServerToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageServerToolStep(tool = step.tool)
                                }
                            }
                        }
                    }
                }
            }

            is MessagePartBlock.ChartBlock -> key(block.index) {
                val spec = remember(block.tool.input) { ChartSpec.fromJson(block.tool.inputAsJson()) }
                spec?.let { ChartCard(spec = it) }
            }

            is MessagePartBlock.ContentBlock -> key(block.index) {
                when (val part = block.part) {
                    is UIMessagePart.Text -> {
                        val affectScope = if (role == MessageRole.USER) {
                            AssistantAffectScope.USER
                        } else {
                            AssistantAffectScope.ASSISTANT
                        }
                        // 附上的网页内容只发给模型，气泡下面只显示一行小字
                        val webStatuses = remember(part.text, role) {
                            if (role == MessageRole.USER) WebPages.statuses(part.text) else emptyList()
                        }
                        // 先藏掉  和网页内容，消息里存的、发给模型的还是原文
                        // 此刻 <trace> 也先剥出来，挂在下面做小条
                        val traced = remember(part.text, role) {
                            val t = NowPlaying.stripTag(part.text)
                            if (role == MessageRole.ASSISTANT) me.rerere.rikkahub.companion.CikeTrace.strip(t)
                            else me.rerere.rikkahub.companion.Traced(t, null)
                        }
                        val rawText = traced.text
                        //  拿出来做成卡片，显示在这段文字下面
                        val songs = remember(rawText) { SongRequests.find(rawText) }
                        val visibleText = SongRequests.strip(rawText).replaceRegexes(
                            assistant = assistant,
                            scope = affectScope,
                            visual = true,
                        )
                        // [崽崽:xxx] 能对上内置表情的换成图片，对不上的保持原文
                        // 只影响显示，消息里存的、发给模型的都还是原文
                        // 骰子 [骰子:n] 和语音 [语音:秒]文字 拆开单独画，其余文字走下面原来的气泡
                        val renderText: @Composable (String) -> Unit = { text ->
                            val segments = rememberStickerSegments(text)

                            if (text.isBlank()) {
                                // 这段只有暗号，文字部分不显示
                            } else if (segments.isStickerOnly()) {
                                // 只发了表情：不套气泡，直接显示图
                                StickerOnlyBlock(segments = segments)
                            } else {
                                val textContent = @Composable {
                                    if (role == MessageRole.USER || (LocalChatSkin.current?.assistantBubble ?: settings.displaySetting.showAssistantBubble)) {
                                        // 两侧同一套气泡；宽度 fit-content，用户侧左边留 24dp 和助手回复错开
                                        ChatMessageBubble(
                                            role = role,
                                            onClick = if (role == MessageRole.USER) ({ onUserMessageClick?.invoke() }) else null,
                                            modifier = Modifier
                                                .then(if (role == MessageRole.USER) Modifier.padding(start = 24.dp) else Modifier)
                                                .animateContentSize(),
                                        ) {
                                            StickerMarkdownBlock(
                                                segments = segments,
                                                onClickCitation = handleClickCitation,
                                            )
                                        }
                                    } else {
                                        StickerMarkdownBlock(
                                            segments = segments,
                                            onClickCitation = handleClickCitation,
                                            modifier = Modifier
                                                .animateContentSize()
                                        )
                                    }
                                }

                                // 流式生成期间不启用 SelectionContainer：Markdown 在不断重渲染，
                                // 内部可选择的 Text 会频繁注册/注销，与 Compose 选择工具栏在绘制阶段
                                // 对 selectable 列表的排序产生并发修改，导致 ConcurrentModificationException。
                                // 生成结束后内容稳定，再启用文本选择。
                                if (loading) {
                                    textContent()
                                } else {
                                    SelectionContainer {
                                        textContent()
                                    }
                                }
                            }
                        }
                        if (hasSpecial(visibleText)) {
                            SpecialContent(
                                text = visibleText,
                                seed = part.text,
                                mine = role == MessageRole.USER,
                                renderText = renderText,
                                voiceFile = (part.metadata?.get("voice_file") as? JsonPrimitive)?.contentOrNull,
                            )
                        } else {
                            renderText(visibleText)
                        }

                        // 点歌卡片，点了才放，不自动播放
                        songs.fastForEach { song ->
                            SongCard(song = song)
                        }

                        // 此刻：只露气味名，点开才看；还在流式写的时候不画
                        if (!loading) traced.trace?.let { TraceStrip(trace = it) }

                        // 链接读没读到：已读取：标题 / 没打开
                        webStatuses.fastForEach { status ->
                            Text(
                                text = status,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }

                    is UIMessagePart.Video -> {
                        ChatMessageMediaTile(
                            icon = HugeIcons.Video01,
                            onClick = { context.openLocalFile(part.url) },
                        )
                    }

                    is UIMessagePart.Audio -> {
                        ChatMessageMediaTile(
                            icon = HugeIcons.MusicNote03,
                            onClick = { context.openLocalFile(part.url) },
                        )
                    }

                    is UIMessagePart.Image -> {
                        if (part.url !in hiddenImages) {
                            val isImageLoading =
                                part.url.isBlank() || part.url.matches(Regex("^data:image/[^;]*;base64,\\s*$"))
                            if (isImageLoading) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(MaterialTheme.shapes.large)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .shimmer(isLoading = true)
                                )
                            } else {
                                ZoomableAsyncImage(
                                    model = part.url,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.large)
                                        .height(72.dp)
                                )
                            }
                        }
                    }

                    is UIMessagePart.Document -> {
                        ChatMessageFileChip(
                            text = part.fileName,
                            onClick = { context.openLocalFile(part.url) },
                            icon = {
                                when (part.mime) {
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                                        Icon(
                                            painter = painterResource(R.drawable.docx),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    "application/pdf" -> {
                                        Icon(
                                            painter = painterResource(R.drawable.pdf),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    else -> {
                                        Icon(
                                            imageVector = HugeIcons.File02,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                        )
                    }

                    else -> {
                        // Skip unknown part types (e.g., deprecated ToolCall, ToolResult, Search)
                    }
                }
            }
        }
    }

    // Annotations (always rendered at the end)
    if (annotations.isNotEmpty()) {
        ChatMessageCitations(annotations = annotations)
    }
}

/**
 * 含骰子或语音的一段文字：按顺序画。骰子直接显示成图，语音显示成语音条（点一下朗读转写），
 * 其余文字交给 [renderText]，走跟普通消息一样的气泡。
 */
@Composable
private fun SpecialContent(
    text: String,
    seed: String,
    mine: Boolean,
    renderText: @Composable (String) -> Unit,
    voiceFile: String? = null,
) {
    val segments = remember(text, seed) { splitSpecialText(text, seed) }
    val tts = LocalTTSState.current
    val speaking by tts.isSpeaking.collectAsState()
    var playingIndex by remember { mutableIntStateOf(-1) }
    // 长按语音条后展开转写文字的那几条
    var shownText by remember(text) { mutableStateOf(setOf<Int>()) }
    // 她自己录的原声（ears3 挂在 metadata.voice_file 上）；文件没了就退回 TTS
    val voicePath = remember(voiceFile) { voiceFile?.takeIf { java.io.File(it).exists() } }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playerIndex by remember { mutableIntStateOf(-1) }
    fun stopPlayer() {
        player?.release()
        player = null
        playerIndex = -1
    }
    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }
    // 朗读结束后，语音条的波纹停下来
    LaunchedEffect(speaking) {
        if (!speaking) playingIndex = -1
    }
    val display = LocalSettings.current.displaySetting
    val musiVoice = LocalChatSkin.current?.bubbleThem?.avatarFrame == "polaroid"
    Column(
        verticalArrangement = Arrangement.spacedBy(BubbleGap),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        segments.forEachIndexed { index, segment ->
            key(index) {
                when (segment) {
                    is SpecialSegment.Text -> renderText(segment.text)
                    is SpecialSegment.Dice -> DiceFace(
                        value = segment.value,
                        roll = false,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )

                    is SpecialSegment.Voice -> {
                        val playing = (playingIndex == index && speaking) || playerIndex == index
                        val onVoiceClick: () -> Unit = {
                                if (playing) {
                                    tts.stop()
                                    stopPlayer()
                                    playingIndex = -1
                                } else {
                                    stopPlayer()
                                    val mp = voicePath?.let { path ->
                                        runCatching {
                                            MediaPlayer().apply {
                                                setDataSource(path)
                                                prepare()
                                            }
                                        }.getOrNull()
                                    }
                                    if (mp != null) {
                                        tts.stop()
                                        playingIndex = -1
                                        mp.setOnCompletionListener { stopPlayer() }
                                        player = mp
                                        playerIndex = index
                                        mp.start()
                                    } else {
                                        playingIndex = index
                                        tts.speak(segment.text.replace(VoiceToneTail, "").trim())
                                    }
                                }
                            }
                        val onVoiceLong: () -> Unit = {
                                shownText = if (index in shownText) shownText - index else shownText + index
                            }
                        if (musiVoice) {
                            MusiVoiceBar(
                                segment.seconds, mine, playing,
                                Modifier.combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onVoiceClick,
                                    onLongClick = onVoiceLong,
                                ),
                            )
                        } else
                        ChatMessageBubble(
                            role = if (mine) MessageRole.USER else MessageRole.ASSISTANT,
                            onClick = onVoiceClick,
                            onLongClick = onVoiceLong,
                            contentPadding = PaddingValues(0.dp),
                            tail = false,
                        ) {
                            VoiceBar(
                                seconds = segment.seconds,
                                mine = mine,
                                playing = playing,
                                color = palaceColors().ink,
                            )
                        }
                        // 长按后，转写的文字小字显示在语音条下面
                        if (index in shownText && musiVoice) {
                            MusiVoiceNote(segment.text)
                        } else if (index in shownText) {
                            Text(
                                text = segment.text,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// 语音条末尾 ears 听出来的语气，TTS 念的时候去掉
private val VoiceToneTail = Regex("""（语气：.*）\s*$""", RegexOption.DOT_MATCHES_ALL)

// 两侧气泡共用 NOIR User 侧原值：圆角 20，朝向头像的那个顶角收成 4，内边距 9/13，行高 1.5
private val BubbleCorner = 20.dp
private val BubbleTailCorner = 4.dp
private val BubblePadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp)

/** 连发多段时，每段各自成泡，间距 8dp。 */
internal val BubbleGap = 8.dp

/**
 * 消息气泡，两侧同一套：液态玻璃（fill 白 0.58）+ 圆角 20dp。
 * 用户侧（靠右）右上角收成 4dp，助手侧（靠左）左上角收成 4dp；[tail] 为 false 时四角都是 20dp。
 */
@Composable
private fun ChatMessageBubble(
    role: MessageRole,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = BubblePadding,
    shapeOverride: Shape? = null,
    tail: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val skinBubble = LocalChatSkin.current?.let { if (role == MessageRole.USER) it.bubbleMe else it.bubbleThem }
    val corner = skinBubble?.radius?.dp ?: BubbleCorner
    val tailCorner = skinBubble?.tailRadius?.dp ?: BubbleTailCorner
    val sideTail = skinBubble?.sideTail == true
    val musiB = skinBubble?.avatarFrame == "polaroid"
    val shape = shapeOverride ?: when {
        musiB -> RoundedCornerShape(corner)
        !tail -> RoundedCornerShape(corner)
        role == MessageRole.USER -> RoundedCornerShape(
            topStart = corner, topEnd = tailCorner, bottomEnd = if (sideTail) tailCorner else corner, bottomStart = corner,
        )
        else -> RoundedCornerShape(
            topStart = tailCorner, topEnd = corner, bottomEnd = corner, bottomStart = if (sideTail) tailCorner else corner,
        )
    }
    val skinFillColor = skinColor(skinBubble?.color)
    val hazeState = LocalGlassHazeState.current
    val glass = if (musiB && skinFillColor != null && shapeOverride == null) {
        Modifier.musiBubble(role == MessageRole.USER, skinFillColor, corner, tail)
    } else if (skinBubble != null && skinFillColor != null && !skinBubble.glass) {
        val borderColor = skinColor(skinBubble.border)
        Modifier
            .then(
                if (skinBubble.shadow) Modifier.shadow(
                    elevation = 2.dp, shape = shape, clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f),
                ) else Modifier
            )
            .clip(shape)
            .background(skinFillColor, shape)
            .then(if (skinBubble.gloss) Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f))), shape) else Modifier)
            .then(
                skinColor(skinBubble.strip)?.let { stripColor ->
                    val stripDp = skinBubble.stripWidth
                    Modifier.drawBehind {
                        val w = stripDp.dp.toPx()
                        val x = if (role == MessageRole.USER) size.width - w else 0f
                        drawRect(stripColor, topLeft = Offset(x, 0f), size = Size(w, size.height))
                    }
                } ?: Modifier
            )
            .then(
                if (borderColor != null && skinBubble.borderWidth > 0f) {
                    Modifier.border(skinBubble.borderWidth.dp, borderColor, shape)
                } else Modifier
            )
    } else if (hazeState != null) Modifier.liquidGlass(
        hazeState, shape,
        // 阴影和横条、小胶囊统一成单层 0 1dp 3dp 黑 0.04。默认那两层（第二层 4dp/12dp 黑 0.04）
        // 在圆角外侧堆出四个很明显的角。
        shadows = listOf(GlassShadow(1.dp, 3.dp, Color.Black.copy(alpha = 0.04f))),
        gradient = GlassGradient,
        topFade = role != MessageRole.USER,
    ) else Modifier.background(Color.White.copy(alpha = 0.58f), shape)
    Box(
        modifier = modifier
            .then(glass)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            )
            // MarkdownBlock 自带 4dp 的水平内边距
            .padding(
                if (skinBubble?.padX != null || skinBubble?.padY != null) {
                    PaddingValues(horizontal = (skinBubble?.padX ?: 14f).dp, vertical = (skinBubble?.padY ?: 10f).dp)
                } else contentPadding
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides (skinColor(skinBubble?.text) ?: palaceColors().ink)) {
            ProvideTextStyle(LocalTextStyle.current.copy(lineHeight = LocalTextStyle.current.fontSize * 1.5f)) {
                content()
            }
        }
    }
}

@Composable
private fun ChatMessageCitations(annotations: List<UIMessageAnnotation>) {
    var expand by remember { mutableStateOf(false) }
    val urls = remember(annotations) {
        annotations.map { annotation ->
            when (annotation) {
                is UIMessageAnnotation.UrlCitation -> annotation.url
            }
        }
    }
    Column(
        modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChatMessageFileChip(
            text = stringResource(R.string.citations_count, annotations.size),
            onClick = { expand = !expand },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { FaviconRow(urls = urls, size = 18.dp) },
        )
        if (expand) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                ProvideTextStyle(
                    LocalTextStyle.current.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        annotations.fastForEachIndexed { index, annotation ->
                            when (annotation) {
                                is UIMessageAnnotation.UrlCitation -> {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Favicon(annotation.url, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = buildAnnotatedString {
                                                append("${index + 1}. ")
                                                withLink(LinkAnnotation.Url(annotation.url)) {
                                                    append(annotation.title.urlDecode())
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
