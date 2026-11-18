package me.rerere.rikkahub.ui.floating

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Fullscreen
import me.rerere.hugeicons.stroke.Remove01
import me.rerere.hugeicons.stroke.Stop
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.ASK_USER_TOOL_NAME
import me.rerere.rikkahub.data.model.GenerationPhase
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.ai.GlassShadows
import me.rerere.rikkahub.ui.components.ai.flatGlass
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.ui.markdown.Markdown

// ---------- 小窗样式 ----------

private val PanelShape = RoundedCornerShape(24.dp)
private val BubbleShape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
private val CardShape = RoundedCornerShape(20.dp)
private val InputShape = RoundedCornerShape(22.dp)
private val PanelMuted = Color(0xFF8E8E93)
private val PanelIconInk = Color(0xFF636366)

// ---------- 宫殿样式主题 ----------

/** 全局配色已经是 Palace 那一套，这里只覆盖面板自己要的几项，不再另做一份映射 */
@Composable
private fun PalacePanelTheme(content: @Composable () -> Unit) {
    val colors = palaceColors()
    val scheme = MaterialTheme.colorScheme.copy(
        surface = colors.bgTop,
        onSurfaceVariant = colors.mute,
        outlineVariant = colors.border,
    )
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

// 贴边入口的尺寸与外观，参数来自确认过的 edge.html：可见外形 32x36dp，白 0.92，贴边一侧直边，另一侧圆角 18dp
private val DockBarWidth = 32.dp
private val DockBarHeight = 36.dp
private val DockCorner = 18.dp
private val DockShadowRoom = 24.dp
private val DockShadowRoomVertical = 20.dp
private val DockInk = Color(0xFF3C3C3C)

/** 收起时贴屏幕边的窄条：只留一个三横线聊天图标，状态用顶部一颗小点表达。左右两侧贴边时外形镜像 */
@Composable
fun ChatFloatingBubble(state: FloatingChatUiState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val palace = palaceColors()
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    // 吸边后窗口在屏幕左半还是右半。窗口是被 WindowManager 移动的，Compose 收不到位置变化，只在显示时低频读取
    var atRight by remember { mutableStateOf(true) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        val screenWidth = context.resources.displayMetrics.widthPixels
        val loc = IntArray(2)
        while (true) {
            view.getLocationOnScreen(loc)
            if (view.width > 0) atRight = loc[0] + view.width / 2 >= screenWidth / 2
            delay(250)
        }
    }

    val shape = if (atRight) {
        AbsoluteRoundedCornerShape(topLeft = DockCorner, bottomLeft = DockCorner, topRight = 0.dp, bottomRight = 0.dp)
    } else {
        AbsoluteRoundedCornerShape(topLeft = 0.dp, bottomLeft = 0.dp, topRight = DockCorner, bottomRight = DockCorner)
    }
    val description = state.statusText()
    // 不用 Surface：它会吃掉触摸事件，拖动交给 FloatingX 处理。
    // 外层是透明的触摸与阴影区，宽度固定（左右镜像时窗口不会跟着变宽），可见条贴在靠屏幕边的一侧
    Box(
        modifier = Modifier
            .width(DockBarWidth + DockShadowRoom)
            .padding(vertical = DockShadowRoomVertical)
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = if (atRight) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(DockBarWidth, DockBarHeight)
                // 阴影 (-4,6) blur 20 黑 0.08；镜像后水平方向取反
                .dropShadow(
                    shape,
                    Shadow(
                        radius = 20.dp,
                        color = Color.Black.copy(alpha = 0.08f),
                        offset = DpOffset(if (atRight) (-4).dp else 4.dp, 6.dp),
                    ),
                )
                .clip(shape)
                .background(Color.White.copy(alpha = 0.92f)),
        ) {
            // 22x22 按钮：左 6 右 4 上下 7，镜像时左右对调
            DockIcon(
                modifier = Modifier
                    .padding(
                        start = if (atRight) 6.dp else 4.dp,
                        top = 7.dp,
                        end = if (atRight) 4.dp else 6.dp,
                        bottom = 7.dp,
                    )
                    .size(22.dp),
            )
            val dot = when {
                state.generating -> DockDot(palace.accent, pulse = true)
                state.error != null || state.pendingTools.isNotEmpty() -> DockDot(colors.error, pulse = false)
                state.hasReply -> DockDot(palace.ok, pulse = false)
                else -> null
            }
            if (dot != null) {
                val alpha = if (dot.pulse && visible) {
                    val transition = rememberInfiniteTransition(label = "dockPulse")
                    transition.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                        label = "dockPulseAlpha",
                    ).value
                } else {
                    1f
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 3.dp)
                        .size(6.dp)
                        .alpha(alpha)
                        .background(dot.color, CircleShape),
                )
            }
        }
    }
}

private class DockDot(val color: Color, val pulse: Boolean)

/** 三条递减横线，24 坐标：(4,7.5)-(20,7.5)、(4,12)-(15.5,12)、(4,16.5)-(11.5,16.5)，stroke 1.75 圆端，图标 14dp */
@Composable
private fun DockIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = 14.dp.toPx() / 24f
        val originX = (size.width - 14.dp.toPx()) / 2f
        val originY = (size.height - 14.dp.toPx()) / 2f
        fun line(x1: Float, x2: Float, y: Float) = drawLine(
            color = DockInk,
            start = Offset(originX + x1 * unit, originY + y * unit),
            end = Offset(originX + x2 * unit, originY + y * unit),
            strokeWidth = 1.75f * unit,
            cap = StrokeCap.Round,
        )
        line(4f, 20f, 7.5f)
        line(4f, 15.5f, 12f)
        line(4f, 11.5f, 16.5f)
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
    PalacePanelTheme {
        // 窗口和屏幕一样宽，大屏上限制宽度并居中
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight), contentAlignment = Alignment.TopCenter) {
            // 外观沿用聊天页的 Snow 底色 + 玻璃（白底、0.5dp 白描边、两条内高光、两层柔和阴影）。
            // 悬浮窗背后是别的应用，拿不到聊天页那种可模糊的背景，所以不做背景模糊，底色用不透明的 Snow 渐变保证可读
            Box(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .flatGlass(PanelShape, fillAlpha = 0.58f, edgeAlpha = 0.90f, shadows = GlassShadows)
                    .background(
                        Brush.verticalGradient(
                            listOf(palaceColors().bgTop, palaceColors().bgMid, palaceColors().bgBottom),
                        ),
                    )
                    .background(Color.White.copy(alpha = 0.40f)),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp, end = 4.dp)) {
            Text(
                text = state.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.statusText(),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.failed) colors.error else PanelMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenConversation, modifier = Modifier.size(40.dp)) {
            Icon(HugeIcons.Fullscreen, tint = PanelIconInk, contentDescription = stringResource(R.string.floating_chat_open_conversation))
        }
        IconButton(onClick = onCollapse, modifier = Modifier.size(40.dp)) {
            Icon(HugeIcons.Remove01, tint = PanelIconInk, contentDescription = stringResource(R.string.floating_chat_collapse))
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
            Icon(HugeIcons.Cancel01, tint = PanelIconInk, contentDescription = stringResource(R.string.floating_chat_close))
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
    // 气泡形状和聊天页一致：首角 4dp，其余 20dp；底色白 0.58 + 同一组玻璃描边/高光/阴影
    val contentColor = if (isReasoning) PanelMuted else MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(modifier = Modifier.flatGlass(BubbleShape, fillAlpha = 0.58f, edgeAlpha = 0.90f, shadows = GlassShadows)) {
            Markdown(
                content = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun PendingToolCard(
    tool: UIMessagePart.Tool,
    onApproval: (approved: Boolean) -> Unit,
    onOpenConversation: () -> Unit,
) {
    val isAskUser = tool.toolName == ASK_USER_TOOL_NAME
    Box(modifier = Modifier.flatGlass(CardShape, fillAlpha = 0.56f, edgeAlpha = 1.0f, shadows = GlassShadows)) {
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
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!isAskUser && tool.input.isNotBlank()) {
                Text(
                    text = tool.input.take(300),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (isAskUser) {
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
            color = PanelMuted,
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
                .flatGlass(InputShape, fillAlpha = 0.56f, edgeAlpha = 0.90f, shadows = GlassShadows),
            placeholder = {
                Text(
                    stringResource(
                        if (generating) R.string.floating_chat_reply_queued_placeholder else R.string.floating_chat_reply_placeholder
                    ),
                    color = PanelMuted,
                )
            },
            maxLines = 4,
            shape = InputShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        if (generating) {
            GlassIconButton(onClick = onStop, enabled = true, icon = HugeIcons.Stop, description = stringResource(R.string.stop))
        }
        GlassIconButton(
            onClick = ::send,
            enabled = text.isNotBlank(),
            icon = HugeIcons.ArrowUp02,
            description = stringResource(R.string.send),
        )
    }
}

/** Send / Stop：和聊天页一样的玻璃圆（白 0.56、描边白 1.0），图标 #636366，禁用时 0.4 透明度 */
@Composable
private fun GlassIconButton(onClick: () -> Unit, enabled: Boolean, icon: ImageVector, description: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .flatGlass(CircleShape, fillAlpha = 0.56f, edgeAlpha = 1.0f, shadows = GlassShadows)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) PanelIconInk else PanelIconInk.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp),
        )
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
