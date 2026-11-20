package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 这一刻才有的东西（心情、独白要求）放在请求最末尾单独一条，不贴在她的消息上（照 SullyOS 的 volatileTail）。
 * 聊天记录一个字都不动。ClaudeProvider 认「最后一条是合成的 user」：把它并进前面那条 user 当单独一块（不加标签，
 * 11-20 实测加 system-reminder 标签 4.6 独白变短），缓存点打在它前面那块上，下一轮整段历史都能读缓存；
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

/** QQ 最近的聊天（<qq_recent>）放末尾那块；原来在人设里，她在 QQ 一说话 AC 下一轮就整段重写 */
object QqRecentTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.assistant.enableMemory) return messages
        return messages.withTail(me.rerere.rikkahub.companion.HomeLink.qqPrompt())
    }
}

/**
 * 门牌「关于你」里的「我的说话方式」那一节：从人设里拆出来，放到末尾那块的情绪后面、独白规矩前面（情绪二期方案二.4）。
 * 服务器说这一轮是浮现档（surface=true）时不放，让情绪顶掉它。人设里拆掉它之后，前面那段更稳，缓存也接得上。
 */
internal object SpeakingStyle {
    private val re = Regex("(?m)^#\\s*我的说话方式[^\\n]*\\n[\\s\\S]*?(?=^#\\s|</plate>|\\z)")
    private val byAssistant = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** 返回拆掉这一节之后的门牌，拆下来的那节记在这个助手名下 */
    fun split(assistantId: String, plates: String): String {
        val m = re.find(plates)
        if (m == null) {
            byAssistant.remove(assistantId)
            return plates
        }
        byAssistant[assistantId] = m.value.trim()
        return plates.removeRange(m.range)
    }

    fun clear(assistantId: String) {
        byAssistant.remove(assistantId)
    }

    fun of(assistantId: String): String = byAssistant[assistantId].orEmpty()
}
