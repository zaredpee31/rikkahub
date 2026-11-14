package me.rerere.rikkahub.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.message.ChatMessageReasoningStep
import me.rerere.rikkahub.ui.components.message.ChatMessageServerToolStep
import me.rerere.rikkahub.ui.components.message.ChatMessageToolStep
import me.rerere.rikkahub.ui.components.message.MessageUsageDetails
import me.rerere.rikkahub.ui.components.message.ThinkingStep
import me.rerere.rikkahub.ui.components.message.usageContextLabel
import me.rerere.rikkahub.ui.components.ui.ChainOfThought

/** 这条回复里的工具调用（客户端工具和服务端工具）。 */
internal fun UIMessage.toolSteps(): List<ThinkingStep> = parts.mapNotNull {
    when (it) {
        is UIMessagePart.Tool -> ThinkingStep.ToolStep(it)
        is UIMessagePart.ServerTool -> ThinkingStep.ServerToolStep(it)
        is UIMessagePart.Reasoning -> ThinkingStep.ReasoningStep(it)
        else -> null
    }
}

internal fun UIMessage.hasPendingTool(): Boolean =
    parts.any { it is UIMessagePart.Tool && it.isPending }

/**
 * 名字旁边一个很淡的小字：上下文用量，有工具时后面带个小点数。点一下展开明细。
 * 待确认的工具才用红色提醒；没用量也没工具的消息不显示。
 */
@Composable
internal fun UsageCapsule(message: UIMessage?, expanded: Boolean, onToggle: () -> Unit) {
    if (message == null) return
    val label = remember(message) { message.usageContextLabel() }
    val tools = remember(message) { message.parts.count { it is UIMessagePart.Tool || it is UIMessagePart.ServerTool } }
    val pending = remember(message) { message.hasPendingTool() }
    val tint = when {
        pending -> Snow.error
        expanded -> Snow.ink2
        else -> Snow.faint
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pending) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(Snow.error))
            Spacer(Modifier.width(3.dp))
            MonoText("待确认", size = 7.5f, color = tint, spacing = 0.06f)
            Spacer(Modifier.width(4.dp))
        }
        MonoText(label ?: "–", size = 7.5f, color = tint, spacing = 0.06f)
        if (tools > 0) {
            Spacer(Modifier.width(3.dp))
            MonoText("· ⚙$tools", size = 7.5f, color = tint, spacing = 0.06f)
        }
    }
}

/** 展开后的明细：工具/思考过程用普通窗口的组件（审批、问答都能在这里操作），下面是用量。 */
@Composable
internal fun TracePanel(
    message: UIMessage,
    loading: Boolean,
    assistant: Assistant?,
    onToolApproval: (toolCallId: String, approved: Boolean, reason: String) -> Unit,
    onToolAnswer: (toolCallId: String, answer: String) -> Unit,
) {
    val steps = remember(message) { message.toolSteps() }
    Column(
        Modifier
            .padding(top = 4.dp, bottom = 2.dp)
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .snowGlass(RoundedCornerShape(16.dp), strong = true)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (steps.isNotEmpty()) {
            MonoText("PROCESS", size = 8f)
            ChainOfThought(
                steps = steps,
                cardColors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) { step ->
                when (step) {
                    is ThinkingStep.ToolStep -> key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                        ChatMessageToolStep(
                            tool = step.tool,
                            loading = loading && !step.tool.isExecuted,
                            assistant = assistant,
                            onToolApproval = onToolApproval,
                            onToolAnswer = onToolAnswer,
                        )
                    }
                    is ThinkingStep.ServerToolStep -> key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                        ChatMessageServerToolStep(tool = step.tool)
                    }
                    is ThinkingStep.ReasoningStep -> key(step.reasoning.createdAt) {
                        ChatMessageReasoningStep(
                            reasoning = step.reasoning,
                            model = null,
                            assistant = assistant,
                        )
                    }
                }
            }
        }
        MonoText("USAGE", size = 8f)
        if (message.usage == null) {
            Text("这条没有返回用量（后台主动发的消息目前不带 token 统计）", fontSize = 11.sp, color = Snow.ink2)
        } else {
            MessageUsageDetails(message)
        }
    }
}
