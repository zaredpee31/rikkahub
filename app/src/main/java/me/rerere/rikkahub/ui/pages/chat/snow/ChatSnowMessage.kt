package me.rerere.rikkahub.ui.pages.chat.snow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import me.rerere.rikkahub.ui.context.LocalTTSState
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Edit02
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.hugeicons.stroke.Video01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.Translate
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.music.NowPlaying
import me.rerere.rikkahub.music.SongCard
import me.rerere.rikkahub.music.SongRequests
import me.rerere.rikkahub.ui.components.message.ChatMessageActionsSheet
import me.rerere.rikkahub.ui.components.message.ChatMessageCopySheet
import me.rerere.rikkahub.ui.components.message.ChatMessageFileChip
import me.rerere.rikkahub.ui.components.message.ChatMessageMediaTile
import me.rerere.rikkahub.ui.components.message.ChatMessageServerToolStep
import me.rerere.rikkahub.ui.components.message.ChatMessageToolStep
import me.rerere.rikkahub.ui.components.message.CollapsibleTranslationText
import me.rerere.rikkahub.ui.components.message.EditedFilesList
import me.rerere.rikkahub.ui.components.message.LanguageSelectionDialog
import me.rerere.rikkahub.ui.components.message.groupMessageParts
import me.rerere.rikkahub.ui.components.message.openLocalFile
import me.rerere.rikkahub.ui.components.ui.ChainOfThoughtScope
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.richtext.buildMarkdownPreviewHtml
import me.rerere.rikkahub.ui.components.sticker.StickerMarkdownBlock
import me.rerere.rikkahub.ui.components.sticker.StickerOnlyBlock
import me.rerere.rikkahub.ui.components.sticker.isStickerOnly
import me.rerere.rikkahub.ui.components.sticker.rememberStickerSegments
import me.rerere.rikkahub.ui.components.ui.Favicon
import me.rerere.rikkahub.ui.components.ui.FaviconRow
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.webpage.WebPages
import me.rerere.ui.charts.ChartCard
import me.rerere.ui.charts.ChartSpec
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.ui.webview.WebViewContentCache
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 普通会话页的消息卡。一条消息一张铺满宽的白卡，不用气泡。
 * 只从 [me.rerere.rikkahub.ui.pages.chat.ChatList] 进来，消息页不走这里。
 *
 * 数据格只放真值：拿不到的（旧消息没有生成耗时、模型被删后没有上下文上限）整格或整排隐藏，不编数字。
 */
@Composable
internal fun SnowMessageCard(
    node: MessageNode,
    floor: Int,
    model: Model?,
    assistant: Assistant?,
    loading: Boolean,
    previousCreatedAt: kotlinx.datetime.LocalDateTime?,
    contextTokens: Int?,
    contextLimit: Int?,
    modifier: Modifier = Modifier,
    onFork: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (MessageNode) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
) {
    val message = node.messages[node.selectIndex]
    val context = LocalContext.current
    val navController = LocalNavController.current
    val citationContext = rememberUpdatedState(message.parts)
    val handleClickCitation: (String) -> Unit = remember {
        handler@{ citationId ->
            citationContext.value.forEach { part ->
                if (part is UIMessagePart.Tool && part.toolName == "search_web" && part.isExecuted) {
                    val outputText = part.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                    val items = runCatching {
                        JsonInstant.parseToJsonElement(outputText).jsonObject["items"]?.jsonArray
                    }.getOrNull() ?: return@forEach
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
    var showActionsSheet by remember { mutableStateOf(false) }
    var showSelectCopySheet by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SnowBackground)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (message.role == MessageRole.USER) {
            SnowUserBody(
                message = message,
                floor = floor,
                assistant = assistant,
                loading = loading,
                previousCreatedAt = previousCreatedAt,
                contextTokens = contextTokens,
                contextLimit = contextLimit,
                onEdit = onEdit,
                onRegenerate = { showRegenerateConfirm = true },
                onOpenMore = { showActionsSheet = true },
                onClickCitation = handleClickCitation,
            )
        } else {
            SnowAssistantBody(
                message = message,
                floor = floor,
                model = model,
                assistant = assistant,
                loading = loading,
                onEdit = onEdit,
                onRegenerate = {
                    if (message.role == MessageRole.USER) showRegenerateConfirm = true else onRegenerate()
                },
                onTranslate = if (onTranslate != null) ({ showTranslateDialog = true }) else null,
                onOpenMore = { showActionsSheet = true },
                onClickCitation = handleClickCitation,
                onToolApproval = onToolApproval,
                onToolAnswer = onToolAnswer,
            )
        }

        SnowCardFooter(
            node = node,
            floor = floor,
            isUser = message.role == MessageRole.USER,
            onUpdate = onUpdate,
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
            onSelectAndCopy = { showSelectCopySheet = true },
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
                        colorScheme = MaterialTheme.colorScheme,
                    )
                    val contentId = WebViewContentCache.store(context.cacheDir, htmlContent)
                    navController.navigate(Screen.WebView(contentId = contentId))
                }
            },
            onDismissRequest = { showActionsSheet = false },
        )
    }

    if (showSelectCopySheet) {
        ChatMessageCopySheet(message = message, onDismissRequest = { showSelectCopySheet = false })
    }

    if (showTranslateDialog && onTranslate != null) {
        LanguageSelectionDialog(
            onLanguageSelected = { language ->
                showTranslateDialog = false
                onTranslate(message, language)
            },
            onClearTranslation = {
                showTranslateDialog = false
                onClearTranslation(message)
            },
            onDismissRequest = { showTranslateDialog = false },
        )
    }

    RikkaConfirmDialog(
        show = showRegenerateConfirm,
        title = stringResource(R.string.regenerate),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        text = { Text(stringResource(R.string.regenerate_confirm_message)) },
        onConfirm = {
            showRegenerateConfirm = false
            onRegenerate()
        },
        onDismiss = { showRegenerateConfirm = false },
    )
}

/** 阿辞（助手）卡。 */
@Composable
private fun SnowAssistantBody(
    message: UIMessage,
    floor: Int,
    model: Model?,
    assistant: Assistant?,
    loading: Boolean,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onTranslate: (() -> Unit)?,
    onOpenMore: () -> Unit,
    onClickCitation: (String) -> Unit,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)?,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)?,
) {
    val context = LocalContext.current
    val created = message.createdAt.toJavaLocalDateTime()
    val timeText = DateTimeFormatter.ofPattern("HH:mm").format(created)
    val dateText = DateTimeFormatter.ofPattern("yyyy.MM.dd").format(created)
    val modelName = model?.displayName?.takeIf { it.isNotBlank() }
        ?: message.modelSnapshot?.displayName?.takeIf { it.isNotBlank() }

    // 输出 tokens 取本条累计用量；旧消息没有用量时整块数据区隐藏
    val usage = message.usage
    // 用时跟原来的用量统计同一套：优先 generationTimeMs，没有就用创建到结束的间隔
    val durationMillis = remember(message) { message.snowDurationMillis() }
    val tps = durationMillis?.let { usage?.completionTokens?.toFloat()?.div(it)?.times(1000) }
    val searchCount = message.parts.count {
        (it is UIMessagePart.Tool && it.toolName == "search_web") ||
            (it is UIMessagePart.ServerTool && it.toolName.contains("search", ignoreCase = true))
    }
    val toolCount = message.parts.count { it is UIMessagePart.Tool || it is UIMessagePart.ServerTool }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SnowMetaLine(
            text = "$timeText  ·  No.$floor",
            trailing = {
                SnowIconButton(HugeIcons.Copy01, stringResource(R.string.copy)) {
                    context.copyMessageToClipboard(message)
                }
                SnowIconButton(HugeIcons.Refresh03, stringResource(R.string.regenerate), onRegenerate)
                SnowReadAloudButton(message)
                if (onTranslate != null) {
                    SnowIconButton(HugeIcons.Translate, stringResource(R.string.translate), onTranslate)
                }
                SnowIconButton(HugeIcons.Edit02, stringResource(R.string.edit), onEdit)
                SnowIconButton(HugeIcons.MoreVertical, stringResource(R.string.more_options), onOpenMore)
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                UIAvatar(
                    name = assistant?.name ?: "",
                    value = assistant?.avatar ?: me.rerere.rikkahub.data.model.Avatar.Dummy,
                    modifier = Modifier.size(44.dp),
                    loading = loading,
                )
                SnowFlakeIcon(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(SnowBackground)
                        .padding(1.dp),
                    color = SnowInk,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = assistant?.name?.takeIf { it.isNotBlank() } ?: "—",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = SnowInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (modelName != null) {
                        Text(
                            text = modelName,
                            fontFamily = SnowMono,
                            fontSize = 10.sp,
                            color = SnowInkSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SnowGray)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                SnowLabel(dateText, color = SnowInkFaint)
            }
            if (usage != null) {
                SnowDottedDivider(modifier = Modifier.height(48.dp))
                Column(horizontalAlignment = Alignment.End) {
                    SnowLabel("TOKENS")
                    SnowFigure(usage.completionTokens.formatNumber())
                    SnowLabel(usage.promptTokens.formatNumber(), color = SnowInkFaint)
                }
            }
        }

        if (durationMillis != null || tps != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (durationMillis != null) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(22.dp).clip(CircleShape).background(SnowInk),
                            contentAlignment = Alignment.Center,
                        ) {
                            SnowFlakeIcon(modifier = Modifier.size(12.dp), color = Color.White, strokeWidth = 0.8.dp)
                        }
                        Column {
                            SnowLabel("TIME")
                            Text(formatSnowDuration(durationMillis), fontSize = 13.sp, color = SnowInk)
                        }
                    }
                }
                if (tps != null) {
                    if (durationMillis != null) SnowDottedDivider(modifier = Modifier.height(32.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SnowLabel("SPEED")
                        Text("${"%.1f".format(tps)} t/s", fontSize = 13.sp, color = SnowInk)
                        // 上限按单子给的 ~100 t/s，只是进度条的刻度，不是编出来的数值
                        SnowBar(tps / 100f)
                    }
                }
            }
        }

        SnowQuoteBlock(
            quote = "我把我整个的灵魂都给你，连同它的怪癖。",
            footer = buildList {
                if (searchCount > 0) add("SEARCH  $searchCount")
                if (toolCount > 0) add("TOOLS  $toolCount")
            },
        )

        SnowThinkingFold(message = message, model = model, assistant = assistant, loading = loading)

        SnowMessageContent(
            message = message,
            assistant = assistant,
            loading = loading,
            alignEnd = false,
            onClickCitation = onClickCitation,
            onToolApproval = onToolApproval,
            onToolAnswer = onToolAnswer,
        )

        message.translation?.let { translation ->
            CollapsibleTranslationText(content = translation, onClickCitation = onClickCitation)
        }
    }
}

/** 小葵（用户）卡。 */
@Composable
private fun SnowUserBody(
    message: UIMessage,
    floor: Int,
    assistant: Assistant?,
    loading: Boolean,
    previousCreatedAt: kotlinx.datetime.LocalDateTime?,
    contextTokens: Int?,
    contextLimit: Int?,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenMore: () -> Unit,
    onClickCitation: (String) -> Unit,
) {
    val context = LocalContext.current
    val created = message.createdAt.toJavaLocalDateTime()
    val settings = LocalSettings.current.displaySetting
    val wordCount = remember(message.parts) {
        message.parts.filterIsInstance<UIMessagePart.Text>().sumOf { it.text.length }
    }
    val gapMillis = previousCreatedAt?.let {
        java.time.Duration.between(it.toJavaLocalDateTime(), created).toMillis().takeIf { gap -> gap > 0 }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SnowMiniDots(count = 4, filled = 3)
            SnowMiniDots(count = 2, filled = 1)
            SnowDottedLine(modifier = Modifier.weight(1f))
            SnowIconButton(HugeIcons.Copy01, stringResource(R.string.copy)) {
                context.copyMessageToClipboard(message)
            }
            SnowIconButton(HugeIcons.Refresh03, stringResource(R.string.regenerate), onRegenerate)
            SnowIconButton(HugeIcons.Edit02, stringResource(R.string.edit), onEdit)
            SnowIconButton(HugeIcons.MoreVertical, stringResource(R.string.more_options), onOpenMore)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .size(56.dp)
                    .clip(snowCornerShape(20, 4, 20, 4))
                    .background(SnowGray),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SnowLabel(
                    created.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                    color = SnowInkSoft,
                )
                Text(
                    text = "%02d".format(created.dayOfMonth),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    color = SnowInk,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = created.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    fontSize = 13.sp,
                    color = SnowInk,
                )
                SnowLabel(DateTimeFormatter.ofPattern("HH:mm").format(created), color = SnowInkFaint)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = settings.userNickname.takeIf { it.isNotBlank() } ?: "—",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = SnowInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            UIAvatar(
                name = settings.userNickname,
                value = settings.userAvatar,
                modifier = Modifier.size(36.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SnowStatCell("WORDS", wordCount.formatNumber(), modifier = Modifier.weight(1f)) {
                SnowBar((wordCount / 500f).coerceAtMost(1f))
            }
            SnowDottedDivider()
            if (gapMillis != null) {
                SnowStatCell("GAP", formatSnowGap(gapMillis), modifier = Modifier.weight(1f))
                SnowDottedDivider()
            }
            if (contextTokens != null) {
                SnowStatCell("CONTEXT", contextTokens.formatNumber(), modifier = Modifier.weight(1f)) {
                    contextLimit?.let { SnowBar(contextTokens.toFloat() / it) }
                }
            }
        }

        // 上下文占用十格：拿不到上限整排隐藏，不拿默认值充数
        if (contextTokens != null && contextLimit != null && contextLimit > 0) {
            val lit = ((contextTokens.toFloat() / contextLimit) * 10).toInt().coerceIn(0, 10)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SnowLabel("CONTEXT")
                repeat(10) { index ->
                    Box(
                        modifier = Modifier
                            .size(width = 14.dp, height = 8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (index < lit) SnowInk else SnowTrack),
                    )
                }
                SnowLabel("$lit/10", color = SnowInkFaint)
            }
        }

        SnowQuoteBlock(quote = "Eat well, sleep well, and tell me about your day.", note = "NOTE 01")

        SnowMessageContent(
            message = message,
            assistant = assistant,
            loading = loading,
            alignEnd = true,
            onUserMessageClick = onEdit,
            onClickCitation = onClickCitation,
            onToolApproval = null,
            onToolAnswer = null,
        )
    }
}

/** 浅灰不对称圆角引言块。没有附加行时点线整行不画。 */
@Composable
private fun SnowQuoteBlock(quote: String, note: String? = null, footer: List<String> = emptyList()) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(snowCornerShape(4, 18, 18, 18))
            .background(SnowGray)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = quote,
                fontSize = 13.sp,
                color = SnowInkSoft,
                modifier = Modifier.weight(1f),
            )
            if (note != null) SnowLabel(note, color = SnowInkFaint)
        }
        if (footer.isNotEmpty()) {
            SnowDottedLine()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                footer.forEach { SnowLabel(it, color = SnowInkFaint) }
            }
        }
    }
}

/** 思考过程折叠成一行：圆 · 渐淡细线 · 三个点 · 渐淡细线 · 圆。展开是左上角尖的浅灰块。 */
@Composable
private fun SnowThinkingFold(
    message: UIMessage,
    model: Model?,
    assistant: Assistant?,
    loading: Boolean,
) {
    val reasoning = message.parts.filterIsInstance<UIMessagePart.Reasoning>()
    if (reasoning.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = !expanded },
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SnowDot()
            SnowFadeLine(modifier = Modifier.weight(1f), fadeToEnd = true)
            repeat(3) { SnowDot(size = 3.dp) }
            SnowFadeLine(modifier = Modifier.weight(1f), fadeToEnd = false)
            SnowDot()
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(snowCornerShape(2, 16, 16, 16))
                    .background(SnowGraySoft)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SnowLabel("THOUGHT")
                reasoning.fastForEach { part ->
                    Text(
                        text = part.reasoning,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        color = SnowInkSoft,
                    )
                }
            }
        }
    }
}

@Composable
private fun SnowDot(size: androidx.compose.ui.unit.Dp = 5.dp) {
    Box(modifier = Modifier.size(size).clip(CircleShape).background(SnowInkFaint))
}

/** 从实到透明（或反过来）的细线。 */
@Composable
private fun SnowFadeLine(modifier: Modifier = Modifier, fadeToEnd: Boolean) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = if (fadeToEnd) {
                        listOf(SnowInkFaint, Color.Transparent)
                    } else {
                        listOf(Color.Transparent, SnowInkFaint)
                    },
                ),
            ),
    )
}

/** 顶行那两段小进度点。 */
@Composable
private fun SnowMiniDots(count: Int, filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < filled) SnowInk else SnowTrack),
            )
        }
    }
}

/** 卡底：右下角楼层号，有分支时两个淡灰小圆箭头夹页码。 */
@Composable
private fun SnowCardFooter(
    node: MessageNode,
    floor: Int,
    isUser: Boolean,
    onUpdate: (MessageNode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (isUser) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SnowGray)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = floor.toString(),
                    fontFamily = SnowMono,
                    fontSize = 11.sp,
                    color = SnowInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SnowInk.copy(alpha = 0.08f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
                Icon(
                    HugeIcons.Tick02,
                    contentDescription = null,
                    tint = SnowInkSoft,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Box(modifier = Modifier.size(1.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (node.messages.size > 1) {
                SnowBranchArrow(HugeIcons.ArrowLeft01, enabled = node.selectIndex > 0) {
                    if (node.selectIndex > 0) onUpdate(node.copy(selectIndex = node.selectIndex - 1))
                }
                SnowLabel("${node.selectIndex + 1}/${node.messages.size}", color = SnowInkSoft)
                SnowBranchArrow(HugeIcons.ArrowRight01, enabled = node.selectIndex < node.messages.lastIndex) {
                    if (node.selectIndex < node.messages.lastIndex) {
                        onUpdate(node.copy(selectIndex = node.selectIndex + 1))
                    }
                }
            }
            if (!isUser) {
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(SnowInk),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(floor.toString(), fontFamily = SnowMono, fontSize = 10.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SnowBranchArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(SnowGray)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = SnowInk.copy(alpha = if (enabled) 0.7f else 0.25f),
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * 正文。复用原来的渲染：markdown、表情、图片、文件、图表、引用。
 * 用户正文靠右放进浅灰块，助手正文纯文字无底色。
 */
@Composable
private fun SnowMessageContent(
    message: UIMessage,
    assistant: Assistant?,
    loading: Boolean,
    alignEnd: Boolean,
    onUserMessageClick: (() -> Unit)? = null,
    onClickCitation: (String) -> Unit,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)?,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)?,
) {
    val context = LocalContext.current
    val hiddenImages = remember(message.parts) { WebPages.imageUris(message.parts) }
    val grouped = remember(message.parts) { message.parts.groupMessageParts() }
    val affectScope = if (message.role == MessageRole.USER) {
        AssistantAffectScope.USER
    } else {
        AssistantAffectScope.ASSISTANT
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        grouped.fastForEach { block ->
            when (block) {
                is me.rerere.rikkahub.ui.components.message.MessagePartBlock.ThinkingBlock -> {
                    // 推理文字由 SnowThinkingFold 画。工具调用仍走原来的步骤，
                    // 这样审批、问答和工具结果都还在，只是不套那张思考卡片。
                    val tools = block.steps.filter {
                        it !is me.rerere.rikkahub.ui.components.message.ThinkingStep.ReasoningStep
                    }
                    if (tools.isNotEmpty()) {
                        SnowToolSteps(
                            steps = tools,
                            model = null,
                            assistant = assistant,
                            loading = loading,
                            onToolApproval = onToolApproval,
                            onToolAnswer = onToolAnswer,
                        )
                    }
                }

                is me.rerere.rikkahub.ui.components.message.MessagePartBlock.ChartBlock -> key(block.index) {
                    val spec = remember(block.tool.input) { ChartSpec.fromJson(block.tool.inputAsJson()) }
                    spec?.let { ChartCard(spec = it) }
                }

                is me.rerere.rikkahub.ui.components.message.MessagePartBlock.ContentBlock -> key(block.index) {
                    when (val part = block.part) {
                        is UIMessagePart.Text -> {
                            val rawText = NowPlaying.stripTag(part.text)
                            val songs = remember(rawText) { SongRequests.find(rawText) }
                            val visibleText = SongRequests.strip(rawText).replaceRegexes(
                                assistant = assistant,
                                scope = affectScope,
                                visual = true,
                            )
                            val segments = rememberStickerSegments(visibleText)
                            if (visibleText.isNotBlank()) {
                                val textBlock: @Composable () -> Unit = {
                                    if (segments.isStickerOnly()) {
                                        StickerOnlyBlock(segments = segments)
                                    } else if (alignEnd) {
                                        Box(
                                            modifier = Modifier
                                                .clip(snowCornerShape(22, 22, 4, 22))
                                                .background(SnowGray)
                                                .clickable(
                                                    enabled = onUserMessageClick != null,
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = { onUserMessageClick?.invoke() },
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                        ) {
                                            CompositionLocalProvider(LocalContentColor provides SnowInk) {
                                                StickerMarkdownBlock(segments = segments, onClickCitation = onClickCitation)
                                            }
                                        }
                                    } else {
                                        StickerMarkdownBlock(segments = segments, onClickCitation = onClickCitation)
                                    }
                                }
                                // 生成中不套 SelectionContainer，和原来一样避开并发修改
                                if (loading) textBlock() else SelectionContainer { textBlock() }
                            }
                            songs.fastForEach { song -> SongCard(song = song) }
                            if (message.role == MessageRole.USER) {
                                WebPages.statuses(part.text).fastForEach { status ->
                                    SnowLabel(status, color = SnowInkFaint)
                                }
                            }
                        }

                        is UIMessagePart.Image -> {
                            if (part.url !in hiddenImages && part.url.isNotBlank()) {
                                ZoomableAsyncImage(
                                    model = part.url,
                                    contentDescription = null,
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).height(72.dp),
                                )
                            }
                        }

                        is UIMessagePart.Document -> {
                            ChatMessageFileChip(
                                text = part.fileName,
                                onClick = { context.openLocalFile(part.url) },
                                icon = {
                                    Icon(HugeIcons.File02, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                            )
                        }

                        is UIMessagePart.Video -> ChatMessageMediaTile(
                            icon = HugeIcons.Video01,
                            onClick = { context.openLocalFile(part.url) },
                        )

                        is UIMessagePart.Audio -> ChatMessageMediaTile(
                            icon = HugeIcons.MusicNote03,
                            onClick = { context.openLocalFile(part.url) },
                        )

                        else -> Unit
                    }
                }
            }
        }

        if (message.annotations.isNotEmpty()) {
            SnowCitations(message.annotations)
        }

        EditedFilesList(parts = message.parts, assistant = assistant)
    }
}

/**
 * 工具调用沿用原来的步骤组件，审批、问答、结果展开都在里面。
 * 作用域实现是 private 的，这里只借它的接口，不画时间线。
 */
@Composable
private fun SnowToolSteps(
    steps: List<me.rerere.rikkahub.ui.components.message.ThinkingStep>,
    model: Model?,
    assistant: Assistant?,
    loading: Boolean,
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)?,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)?,
) {
    val scope = remember {
        object : ChainOfThoughtScope {
            @Composable
            override fun ChainOfThoughtStep(
                icon: (@Composable () -> Unit)?,
                label: (@Composable () -> Unit),
                extra: (@Composable () -> Unit)?,
                onClick: (() -> Unit)?,
                collapsedAdaptiveWidth: Boolean,
                content: (@Composable () -> Unit)?,
            ) {
                SnowToolStepRow(icon, label, extra, onClick, content)
            }

            @Composable
            override fun ControlledChainOfThoughtStep(
                expanded: Boolean,
                onExpandedChange: (Boolean) -> Unit,
                icon: (@Composable () -> Unit)?,
                label: (@Composable () -> Unit),
                extra: (@Composable () -> Unit)?,
                onClick: (() -> Unit)?,
                collapsedAdaptiveWidth: Boolean,
                contentVisible: Boolean,
                content: (@Composable () -> Unit)?,
            ) {
                SnowToolStepRow(icon, label, extra, onClick, if (contentVisible) content else null)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.fastForEach { step ->
            when (step) {
                is me.rerere.rikkahub.ui.components.message.ThinkingStep.ToolStep -> key(step.tool.toolCallId) {
                    scope.ChatMessageToolStep(
                        tool = step.tool,
                        loading = loading && !step.tool.isExecuted,
                        assistant = assistant,
                        onToolApproval = onToolApproval,
                        onToolAnswer = onToolAnswer,
                    )
                }
                is me.rerere.rikkahub.ui.components.message.ThinkingStep.ServerToolStep -> key(step.tool.toolCallId) {
                    scope.ChatMessageServerToolStep(tool = step.tool)
                }
                is me.rerere.rikkahub.ui.components.message.ThinkingStep.ReasoningStep -> Unit
            }
        }
    }
}

@Composable
private fun SnowToolStepRow(
    icon: (@Composable () -> Unit)?,
    label: (@Composable () -> Unit),
    extra: (@Composable () -> Unit)?,
    onClick: (() -> Unit)?,
    content: (@Composable () -> Unit)?,
) {
    var expanded by remember { mutableStateOf(content != null && onClick == null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SnowGraySoft)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    enabled = onClick != null || content != null,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (onClick != null) onClick() else expanded = !expanded },
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon?.invoke()
            Box(modifier = Modifier.weight(1f)) { label() }
            extra?.invoke()
        }
        if (expanded && content != null) {
            Box(modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) { content() }
        }
    }
}

@Composable
private fun SnowCitations(annotations: List<UIMessageAnnotation>) {
    var expand by remember { mutableStateOf(false) }
    val urls = remember(annotations) {
        annotations.map { annotation ->
            when (annotation) {
                is UIMessageAnnotation.UrlCitation -> annotation.url
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(SnowGray)
                .clickable { expand = !expand }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FaviconRow(urls = urls, size = 14.dp)
            SnowLabel(stringResource(R.string.citations_count, annotations.size), color = SnowInkSoft)
        }
        if (expand) {
            annotations.fastForEachIndexed { index, annotation ->
                if (annotation is UIMessageAnnotation.UrlCitation) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Favicon(annotation.url, modifier = Modifier.size(14.dp))
                        SnowLabel("${index + 1}. ${annotation.title}", color = SnowInkSoft)
                    }
                }
            }
        }
    }
}

/** 跟原来的用量统计同一套：优先 generationTimeMs，没有就用创建到结束的间隔。拿不到就隐藏。 */
private fun UIMessage.snowDurationMillis(): Long? {
    val finished = finishedAt ?: return null
    val millis = generationTimeMs ?: java.time.Duration.between(
        createdAt.toJavaLocalDateTime(),
        finished.toJavaLocalDateTime(),
    ).toMillis()
    return millis.takeIf { it > 0 }
}

/** 朗读。生成中或没有文字时不显示。 */
@Composable
private fun SnowReadAloudButton(message: UIMessage) {
    val tts = LocalTTSState.current
    val settings = LocalSettings.current
    val speaking by tts.isSpeaking.collectAsState()
    val available by tts.isAvailable.collectAsState()
    if (!available) return
    val text = message.toText()
    if (text.isBlank()) return
    SnowIconButton(
        icon = if (speaking) HugeIcons.StopCircle else HugeIcons.VolumeHigh,
        contentDescription = stringResource(R.string.tts),
        onClick = {
            if (speaking) {
                tts.stop()
            } else {
                var textToSpeak = text
                if (settings.displaySetting.ttsOnlyReadQuoted) {
                    textToSpeak = textToSpeak.extractQuotedContentAsText() ?: textToSpeak
                }
                if (settings.displaySetting.ttsOnlyReadOutsideBrackets) {
                    textToSpeak = textToSpeak.removeBracketedContent() ?: textToSpeak
                }
                tts.speak(textToSpeak)
            }
        },
    )
}
