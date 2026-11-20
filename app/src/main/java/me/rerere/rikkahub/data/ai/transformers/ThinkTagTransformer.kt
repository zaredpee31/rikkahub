package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Clock
import kotlin.time.Instant

// 回复开头的独白：<think>…</think>，也认 <thinking>…</thinking> 和 [[思考：…]]（情绪二期第 10 条）；各认各的收尾
private val THINKING_REGEXES = listOf(
    Regex("\\A\\s*<think>([\\s\\S]*?)(</think>|$)"),
    Regex("\\A\\s*<thinking>([\\s\\S]*?)(</thinking>|$)"),
    Regex("\\A\\s*\\[\\[思考[:：]?([\\s\\S]*?)(\\]\\]|$)"),
)

private fun findThink(text: String): MatchResult? = THINKING_REGEXES.firstNotNullOfOrNull { it.find(text) }

// 部分供应商不会返回reasoning parts, 所以需要这个transformer
object ThinkTagTransformer : OutputMessageTransformer {
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformThinkTags(
            now = Clock.System.now(),
            generationFinished = false,
        )
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformThinkTags(
            now = Clock.System.now(),
            generationFinished = true,
        )
    }
}

internal fun List<UIMessage>.transformThinkTags(
    now: Instant,
    generationFinished: Boolean,
): List<UIMessage> = map { message ->
    if (message.role != MessageRole.ASSISTANT) {
        return@map message
    }
    // 中转站要是还是返回了原生思考，回复开头自己写的 <think> 也照样拆出来，不让它混进正文
    val leadingText = message.parts.firstOrNull { part ->
        part is UIMessagePart.Text && part.text.isNotBlank()
    } as? UIMessagePart.Text
    val hasLeadingThink = leadingText != null && findThink(leadingText.text) != null
    if (message.hasPart<UIMessagePart.Reasoning>() && !hasLeadingThink) {
        return@map if (generationFinished) {
            message.copy(
                parts = message.parts.map { part ->
                    if (part is UIMessagePart.Reasoning && part.finishedAt == null) {
                        part.copy(finishedAt = now)
                    } else {
                        part
                    }
                }
            )
        } else {
            message
        }
    }

    val textPartIndex = message.parts.indexOfFirst { part ->
        part is UIMessagePart.Text && part.text.isNotBlank()
    }
    val textPart = message.parts.getOrNull(textPartIndex) as? UIMessagePart.Text
        ?: return@map message
    val match = findThink(textPart.text) ?: return@map message
    val hasClosingTag = !match.groups[2]?.value.isNullOrEmpty()
    val reasoning = UIMessagePart.Reasoning(
        reasoning = match.groupValues[1].trim(),
        createdAt = message.createdAt.toInstant(timeZone = TimeZone.currentSystemDefault()),
        finishedAt = if (generationFinished || hasClosingTag) now else null,
    )
    val strippedText = textPart.copy(text = textPart.text.removeRange(match.range))

    message.copy(
        parts = buildList {
            addAll(message.parts.subList(0, textPartIndex))
            add(reasoning)
            add(strippedText)
            addAll(message.parts.subList(textPartIndex + 1, message.parts.size))
        }
    ).let { m ->
        if (!generationFinished) m else m.copy(parts = m.parts.map { part ->
            if (part is UIMessagePart.Reasoning && part.finishedAt == null) part.copy(finishedAt = now) else part
        })
    }
}
