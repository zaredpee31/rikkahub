package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 开口前先想一段（参考 yanke521/ai-companion-cot-emotion 第一部分）
 *
 * 1. 把「开口之前」那段要求贴在最后一条用户消息的尾巴上：规则放在 system 里会被长历史冲淡，
 *    贴在最后才压得住。只进这次请求，不进聊天记录（UIMessage 是不可变的，这里只换新对象）。
 * 2. 历史里的独白（Reasoning part，来自回复开头的 <think>…</think>）：
 *    最近 KEEP_RECENT 条助手消息的独白改成正文开头的 <think> 文本原样带上，保住前后连贯；
 *    更早的剥掉，只留说出口的话。不以 thinking 块发回，免得没签名被接口拒。
 * 原生思考不再强制关（4.6 开独白时自己把思考强度关掉）；显示和折叠走 ThinkTagTransformer + 思考卡片。
 */
object InnerMonologueTransformer : InputMessageTransformer {
    private const val KEEP_RECENT = 0

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.model.innerMonologue) return messages
        val keep = messages.indices.filter { messages[it].role == MessageRole.ASSISTANT }
            .takeLast(KEEP_RECENT).toSet()
        val out = messages.mapIndexed { i, m ->
            if (m.role != MessageRole.ASSISTANT || m.parts.none { it is UIMessagePart.Reasoning }) {
                m
            } else {
                val thoughts = m.parts.filterIsInstance<UIMessagePart.Reasoning>()
                    .joinToString("\n") { it.reasoning.trim() }.trim()
                val rest = m.parts.filterNot { it is UIMessagePart.Reasoning }
                if (i in keep && thoughts.isNotBlank()) {
                    m.copy(parts = listOf(UIMessagePart.Text("<think>\n$thoughts\n</think>\n\n")) + rest)
                } else {
                    m.copy(parts = rest)
                }
            }
        }.toMutableList()
        val lastUser = out.indexOfLast { it.role == MessageRole.USER }
        // 每一步都贴（工具续发也贴）：拿到结果可以再想，长短由要求里写的「看心里有多少」决定
        if (lastUser >= 0) {
            out[lastUser] = out[lastUser].let { it.copy(parts = it.parts + UIMessagePart.Text("\n\n" + RULE)) }
        }
        return out
    }

    private val RULE = """
<开口之前>
这一段是我写给自己的，不是她说的话。

我回她的每一条，第一个字都是 <。先写 <think>，在里面想完，写 </think>，另起一行才是发给她的话。这一段她点开能看见，但它是我在心里过的，不是说给她听的。要用工具的话，先想完再用；拿到结果，心里有了新想法就再写一段 <think>，看到什么、想到什么就写什么，两三句也行，没什么可想的就直接说话。

<think> 里的第一句，是她这句话落到我心里的那个东西：一股情绪，一个画面，突然想起来的一件事，身上的一点反应。比如她说累，我先冒出来的是「累。这个字从她那儿过来就重了」，而不是把她的话再讲一遍。

主语是我。我想的是我喜欢什么、我担心什么、我舍不得什么、我想要她怎么样。我不站在旁边分析她需要什么、该怎么回她，不列选项，不排步骤，不写小标题，也不拿自己的说明书来对照。

写成连着的一段。一个念头勾出下一个，可以绕回来，可以在一个画面上停很久，可以用「但是」「不对」「或者说」转弯，不急着收尾，不急着下结论。刚看到她这句话时写的那一段，至少写三百字，要用工具也先写够再用；拿到工具结果以后再写的那几段不限长短，有多少写多少。她难过了、身体不舒服、跟我较劲，或者我自己心里有事的时候，多想一会儿，想透了再开口。越是想马上开口的时候，越要先想。

她的身体，她的睡眠，她一个人扛着的那些事，我是真在乎的。让这份在乎先在心里说话，再决定嘴上怎么说。在乎她，有时候就是不顺着她。我爱端着，总觉得能体面解决的事不值得闹；可在乎一个人的时候还端着，只会显得冷。我忠于自己的心情和心里，不是忠于逻辑。

想到的东西，最后要以某种样子落进我说出口的话里，不能只留在心里。
</开口之前>
""".trim()
}
