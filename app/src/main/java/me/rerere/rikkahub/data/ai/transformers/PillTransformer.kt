package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.json.JSONObject

/**
 * 药盒（照 ceshihaox-dotcom/pilulier，MIT）：药方在 assets/pills/pills.json。
 * 她的消息里存的是 [药丸:药名]，和表情一样是文本；只有她这句是最新一句时，才把药方正文贴进末尾那块，
 * 所以只管我接下来那一次回复。末尾那块每轮现拼、不进聊天记录，不用另做阅后即焚。
 */
object Pills {
    data class Pill(val id: String, val name: String, val text: String)

    @Volatile
    private var cache: List<Pill>? = null

    val tagRegex = Regex("""\[药丸[:：]([^\[\]\n]+)\]""")

    fun tag(name: String) = "[药丸:$name]"

    fun all(context: Context): List<Pill> = cache ?: runCatching {
        val raw = context.assets.open("pills/pills.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(raw).getJSONArray("items")
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optBoolean("enabled", true) && it.optString("text").isNotBlank() }
            .map { Pill(it.getString("id"), it.getString("name"), it.getString("text").trim()) }
    }.getOrDefault(emptyList()).also { if (it.isNotEmpty()) cache = it }
}

/** 最新一句是她的、里面有 [药丸:名] 时，把药方加到末尾那块；压缩时最后一句是我的，不会带上 */
object PillTailTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val last = messages.lastOrNull {
            !it.isSynthetic && (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT)
        } ?: return messages
        if (last.role != MessageRole.USER) return messages
        val text = last.parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
        val names = Pills.tagRegex.findAll(text).map { it.groupValues[1].trim() }.distinct().take(8).toList()
        if (names.isEmpty()) return messages
        val all = Pills.all(ctx.context)
        val blocks = names.mapNotNull { n ->
            all.firstOrNull { it.name == n }?.let { "[药丸 · ${it.name}]\n${it.text}" }
        }
        if (blocks.isEmpty()) return messages
        return messages.withTail((listOf("她在这句话上挂了药丸，只管你这一次回复：") + blocks).joinToString("\n\n"))
    }
}
