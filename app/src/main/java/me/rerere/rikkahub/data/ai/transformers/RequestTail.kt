package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 这一刻才有的东西（心情、独白要求）放在请求最末尾单独一条，不贴在她的消息上（照 SullyOS 的 volatileTail）。
 * 聊天记录一个字都不动，缓存点打在这条前面（ClaudeProvider 认「最后一条是合成的 user」），下一轮整段历史都能读缓存；
 * 工具续发时它也跟着排在最新的工具结果后面。几样东西都往同一条里加，顺序按变换器的先后。
 */
internal fun List<UIMessage>.withTail(text: String): List<UIMessage> {
    if (text.isBlank()) return this
    val last = lastOrNull()
    return if (last != null && last.role == MessageRole.USER && last.isSynthetic) {
        dropLast(1) + last.copy(parts = last.parts + UIMessagePart.Text("\n\n" + text))
    } else {
        this + UIMessage.user(text).copy(isSynthetic = true)
    }
}
