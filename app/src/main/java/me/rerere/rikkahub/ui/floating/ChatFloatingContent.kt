package me.rerere.rikkahub.ui.floating

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Fullscreen
import me.rerere.hugeicons.stroke.Remove01
import me.rerere.hugeicons.stroke.Stop
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Wrench01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.ASK_USER_TOOL_NAME
import me.rerere.rikkahub.data.model.GenerationPhase
import me.rerere.rikkahub.service.ChatError
import me.rerere.ui.markdown.Markdown
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.random.Random
import kotlin.uuid.Uuid

// ---------- 崽崽 Clawd ----------

private val BubbleCrabOrange = Color(0xFFD97757)
private val BubbleCrabInk = Color(0xFF1F1E1D)
// 悬浮球上崽崽的米白描边，像贴纸
private val BubbleCrabOutline = Color(0xFFFAF9F5)
// Claude 按钮橙
private val ClaudeClay = Color(0xFFC96442)

// O = 橙色身体，K = 黑眼睛，. = 空
private val BubbleCrabBody = listOf(
    "..OOOOOOOOO..",
    "..OOKOOOKOO..",
    "OOOOKOOOKOOOO",
    "OOOOOOOOOOOOO",
    "..OOOOOOOOO..",
    "...O.O.O.O...",
)

// 最下面一行腿：站着四条都在，走路时两两交替
private const val BUBBLE_LEGS_STAND = "...O.O.O.O..."
private const val BUBBLE_LEGS_A = "...O...O....."
private const val BUBBLE_LEGS_B = ".....O...O..."
private const val BUBBLE_CRAB_COLS = 13

/** [fill] 是崽崽占画布宽度的比例，[outline] 不为空时先画一圈描边 */
private fun DrawScope.drawPixelCrab(
    eyesClosed: Boolean,
    legs: String,
    hop: Boolean,
    fill: Float,
    outline: Color?,
) {
    val rows = BubbleCrabBody + legs
    // 取整像素，边缘干净
    val px = floor(size.width * fill / BUBBLE_CRAB_COLS).coerceAtLeast(1f)
    val left = floor((size.width - px * BUBBLE_CRAB_COLS) / 2f)
    val top = floor((size.height - px * rows.size) / 2f) + if (hop) -px else 0f

    // 描边：每格往外扩一点，用不透明色画在身体下面
    if (outline != null) {
        val o = floor(px * 0.35f).coerceAtLeast(1f)
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c != '.') {
                    drawRect(
                        color = outline,
                        topLeft = Offset(left + x * px - o, top + y * px - o),
                        size = Size(px + 2 * o, px + 2 * o),
                    )
                }
            }
        }
    }

    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            val color = when (c) {
                'O' -> BubbleCrabOrange
                // 眨眼时上面那格眼睛变成身体色，只剩一条缝
                'K' -> if (eyesClosed && y == 1) BubbleCrabOrange else BubbleCrabInk
                else -> null
            }
            if (color != null) {
                drawRect(
                    color = color,
                    topLeft = Offset(left + x * px, top + y * px),
                    size = Size(px + 0.5f, px + 0.5f),
                )
            }
        }
    }
}

/** 会动的崽崽：[walking] 时原地踏步，平时隔一会儿眨眼 */
@Composable
private fun ClawdCrab(
    walking: Boolean,
    fill: Float,
    modifier: Modifier = Modifier,
    outline: Color? = null,
) {
    // 收起到面板时窗口隐藏，帧还在跑，停下动画
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val moving = walking && visible

    var step by remember { mutableIntStateOf(0) }
    var blink by remember { mutableStateOf(false) }

    // 回消息的时候踏步
    LaunchedEffect(moving) {
        if (!moving) {
            step = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(180)
            step = (step + 1) % 4
        }
    }

    // 隔一会儿眨一下眼
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2200L, 4200L))
            blink = true
            delay(140)
            blink = false
        }
    }

    val legs = when {
        !moving -> BUBBLE_LEGS_STAND
        step % 2 == 0 -> BUBBLE_LEGS_A
        else -> BUBBLE_LEGS_B
    }
    val hop = moving && step % 2 == 1

    Canvas(modifier = modifier) {
        drawPixelCrab(eyesClosed = blink, legs = legs, hop = hop, fill = fill, outline = outline)
    }
}

// ---------- Claude 配色 ----------

/** 面板用 Claude 的米白 / 深色配色，跟着当前是浅色还是深色走 */
@Composable
private fun ClaudePanelTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val dark = base.surface.luminance() < 0.5f
    val scheme = if (dark) {
        base.copy(
            primary = ClaudeClay,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF4A2E25),
            onPrimaryContainer = Color(0xFFF5D0C2),
            secondaryContainer = Color(0xFF3A3936),
            onSecondaryContainer = Color(0xFFE8E6DC),
            surface = Color(0xFF262624),
            onSurface = Color(0xFFFAF9F5),
            onSurfaceVariant = Color(0xFFB7B5A9),
            surfaceContainerLowest = Color(0xFF30302E),
            surfaceContainerHigh = Color(0xFF262624),
            outlineVariant = Color(0xFF3E3E3A),
        )
    } else {
        base.copy(
            primary = ClaudeClay,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF5E6DF),
            onPrimaryContainer = Color(0xFF7A3B26),
            secondaryContainer = Color(0xFFF0EEE6),
            onSecondaryContainer = Color(0xFF3D3929),
            surface = Color(0xFFFAF9F5),
            onSurface = Color(0xFF1F1E1D),
            onSurfaceVariant = Color(0xFF73726C),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFFAF9F5),
            outlineVariant = Color(0xFFE5E3DA),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** 悬浮窗展示的会话状态 */
data class FloatingChatUiState(
    val conversationId: Uuid,
    val title: String,
    val generating: Boolean,
    /** 网络重试等临时提示 */
    val processingStatus: String?,
    /** 本轮回复在做什么 */
    val phase: GenerationPhase,
    /** 本轮是否已经有回复 */
    val hasReply: Boolean,
    /** 本轮回复的正文（Markdown），还没写正文时是思考内容 */
    val preview: String,
    val previewIsReasoning: Boolean,
    val pendingTools: List<UIMessagePart.Tool>,
    val queuedCount: Int,
    val queuePaused: Boolean,
    /** 这一轮出现的错误 */
    val error: ChatError?,
) {
    val failed: Boolean get() = !generating && error != null
}

/** 收起时的小图标：只有崽崽本人，生成中原地踏步，结束后在角上标出结果 */
@Composable
fun ChatFloatingBubble(state: FloatingChatUiState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // 不用 Surface：它会吃掉触摸事件，拖动交给 FloatingX 处理
    Box(modifier = Modifier.padding(6.dp)) {
        ClawdCrab(
            walking = state.generating,
            fill = 0.92f,
            outline = BubbleCrabOutline,
            // 能点的范围还是原来 52dp 那么大
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        )
        when {
            state.generating -> {}
            state.error != null -> StatusBadge(HugeIcons.Alert01, colors.error, colors.onError)
            state.pendingTools.isNotEmpty() -> StatusBadge(HugeIcons.Wrench01, colors.error, colors.onError)
            state.hasReply -> StatusBadge(HugeIcons.Tick01, ClaudeClay, Color.White)
        }
    }
}

@Composable
private fun BoxScope.StatusBadge(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    badgeSize: Dp = 20.dp,
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .size(badgeSize)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(badgeSize / 2))
    }
}

/** 展开后的面板：看进度、审批工具、停止生成和快速回复。[maxHeight] 是窗口能占的高度 */
@Composable
fun ChatFloatingPanel(
    state: FloatingChatUiState,
    maxHeight: Dp,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
    onOpenConversation: () -> Unit,
    onToolApproval: (toolCallId: String, approved: Boolean) -> Unit,
    onStop: () -> Unit,
    onSend: (String) -> Unit,
    onResumeQueue: () -> Unit,
) {
    ClaudePanelTheme {
        // 窗口和屏幕一样宽，大屏上限制宽度并居中
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight), contentAlignment = Alignment.TopCenter) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PanelHeader(
                        state = state,
                        onOpenConversation = onOpenConversation,
                        onCollapse = onCollapse,
                        onDismiss = onDismiss,
                    )
                    // 多个审批、横屏或大字体时放不下，中间这段滚动，标题和输入栏始终可见
                    if (state.preview.isNotEmpty() || state.pendingTools.isNotEmpty() || state.queuedCount > 0) {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (state.preview.isNotEmpty()) {
                                MessagePreview(
                                    text = state.preview,
                                    isReasoning = state.previewIsReasoning,
                                    generating = state.generating,
                                )
                            }
                            state.pendingTools.forEach { tool ->
                                PendingToolCard(
                                    tool = tool,
                                    onApproval = { approved -> onToolApproval(tool.toolCallId, approved) },
                                    onOpenConversation = onOpenConversation,
                                )
                            }
                            if (state.queuedCount > 0) {
                                QueueRow(count = state.queuedCount, paused = state.queuePaused, onResume = onResumeQueue)
                            }
                        }
                    }
                    ReplyBar(
                        conversationId = state.conversationId,
                        generating = state.generating,
                        onSend = onSend,
                        onStop = onStop,
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(
    state: FloatingChatUiState,
    onOpenConversation: () -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 左上角是崽崽：回复时踏步，回完站着；出错或等审批时角上标一下
        Box(modifier = Modifier.size(40.dp)) {
            ClawdCrab(
                walking = state.generating,
                fill = 0.9f,
                modifier = Modifier.fillMaxSize(),
            )
            when {
                state.generating -> {}
                state.error != null -> StatusBadge(HugeIcons.Alert01, colors.error, colors.onError, 16.dp)
                state.pendingTools.isNotEmpty() -> StatusBadge(HugeIcons.Wrench01, colors.error, colors.onError, 16.dp)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.statusText(),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.failed) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenConversation) {
            Icon(HugeIcons.Fullscreen, contentDescription = stringResource(R.string.floating_chat_open_conversation))
        }
        IconButton(onClick = onCollapse) {
            Icon(HugeIcons.Remove01, contentDescription = stringResource(R.string.floating_chat_collapse))
        }
        IconButton(onClick = onDismiss) {
            Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.floating_chat_close))
        }
    }
}

// 用 ui 模块的通用 Markdown：应用里的 MarkdownBlock 依赖导航、Activity 等悬浮窗里没有的东西
@Composable
private fun MessagePreview(text: String, isReasoning: Boolean, generating: Boolean) {
    val scrollState = rememberScrollState()
    // 生成中跟随最新内容。Markdown 在后台解析，内容变高晚于文本变化，按可滚动距离跟随
    LaunchedEffect(generating) {
        if (generating) snapshotFlow { scrollState.maxValue }.collect { scrollState.scrollTo(it) }
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = if (isReasoning) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Markdown(
            content = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .verticalScroll(scrollState)
                .padding(12.dp),
        )
    }
}

@Composable
private fun PendingToolCard(
    tool: UIMessagePart.Tool,
    onApproval: (approved: Boolean) -> Unit,
    onOpenConversation: () -> Unit,
) {
    val isAskUser = tool.toolName == ASK_USER_TOOL_NAME
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isAskUser) {
                    stringResource(R.string.floating_chat_ask_user)
                } else {
                    stringResource(R.string.floating_chat_tool_request, tool.toolName.substringAfterLast("__"))
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (!isAskUser && tool.input.isNotBlank()) {
                Text(
                    text = tool.input.take(300),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (isAskUser) {
                    // 问答有多个问题和选项，在应用里回答
                    Button(onClick = onOpenConversation) { Text(stringResource(R.string.floating_chat_answer)) }
                } else {
                    OutlinedButton(onClick = { onApproval(false) }) { Text(stringResource(R.string.chat_message_tool_deny)) }
                    Button(onClick = { onApproval(true) }) { Text(stringResource(R.string.chat_message_tool_approve)) }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(count: Int, paused: Boolean, onResume: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                if (paused) R.string.chat_page_queue_paused_count else R.string.chat_page_queue_pending_count,
                count,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (paused) {
            TextButton(onClick = onResume) { Text(stringResource(R.string.chat_page_queue_resume)) }
        }
    }
}

@Composable
private fun ReplyBar(
    conversationId: Uuid,
    generating: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    var text by rememberSaveable(conversationId) { mutableStateOf("") }
    val fieldShape = RoundedCornerShape(24.dp)
    // Claude 那种方圆按钮
    val buttonShape = RoundedCornerShape(12.dp)

    fun send() {
        val content = text.trim()
        if (content.isEmpty()) return
        onSend(content)
        text = ""
        focusManager.clearFocus()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .border(1.dp, colors.outlineVariant, fieldShape),
            // 生成中发送的消息会排队，等这一轮结束后再发
            placeholder = {
                Text(
                    stringResource(
                        if (generating) R.string.floating_chat_reply_queued_placeholder else R.string.floating_chat_reply_placeholder
                    )
                )
            },
            maxLines = 4,
            shape = fieldShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerLowest,
                unfocusedContainerColor = colors.surfaceContainerLowest,
                disabledContainerColor = colors.surfaceContainerLowest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        if (generating) {
            FilledTonalIconButton(onClick = onStop, shape = buttonShape) {
                Icon(HugeIcons.Stop, contentDescription = stringResource(R.string.stop))
            }
        }
        FilledIconButton(
            onClick = ::send,
            enabled = text.isNotBlank(),
            shape = buttonShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                // 没字的时候是淡一点的橙，不变灰
                disabledContainerColor = colors.primary.copy(alpha = 0.4f),
                disabledContentColor = colors.onPrimary.copy(alpha = 0.8f),
            ),
        ) {
            Icon(HugeIcons.ArrowUp02, contentDescription = stringResource(R.string.send))
        }
    }
}

@Composable
private fun FloatingChatUiState.statusText(): String {
    if (!generating) {
        return when {
            error != null -> error.title ?: error.error.message ?: stringResource(R.string.floating_chat_status_failed)
            pendingTools.any { it.toolName == ASK_USER_TOOL_NAME } -> stringResource(R.string.floating_chat_status_awaiting_answer)
            pendingTools.isNotEmpty() -> stringResource(R.string.floating_chat_status_awaiting_approval)
            hasReply -> stringResource(R.string.floating_chat_status_done)
            else -> stringResource(R.string.floating_chat_status_idle)
        }
    }
    // 网络重试等提示优先
    processingStatus?.let { return it }
    return when (phase) {
        is GenerationPhase.RunningTool -> stringResource(R.string.notification_live_update_tool, phase.toolName)
        is GenerationPhase.Thinking -> stringResource(R.string.notification_live_update_thinking)
        is GenerationPhase.Writing -> stringResource(R.string.notification_live_update_writing)
        GenerationPhase.Waiting -> stringResource(R.string.notification_live_update_title)
    }
}
