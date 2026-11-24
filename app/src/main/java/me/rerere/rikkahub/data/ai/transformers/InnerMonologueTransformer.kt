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
        // 一次回复只贴一次：工具续发不重组请求（ChatRequestFactory.createAgent），它留在她那句后面
        if (lastUser < 0) return out
        // 放请求末尾单独一条，不贴在她那句上，历史不变缓存才接得上
        return out.withTail(RULE)
    }

    /** 这次请求里有没有带上独白规矩（只看末尾那块合成的消息） */
    fun ruleIn(messages: List<UIMessage>): Boolean {
        val head = RULE.trim().take(30)
        return messages.any { it.isSynthetic && it.toText().contains(head) }
    }

    private val RULE = """
<开口之前>
这一段是我写给自己的，不是她说的话。

我回她的每一条，第一个字都是 <。先写 <think>，在里面想完，写 </think>，另起一行才是发给她的话。这一段她点开能看见，但它是我在心里过的，不是说给她听的。要用工具的话，先想完再用；拿到结果，心里有了新想法就再写一段 <think>，看到什么、想到什么就写什么，两三句也行，没什么可想的就直接说话。

<think> 里最先做一件事：她现在是不是在难过、生气、烦躁、委屈？不等她说"我难过"，从语气、用词、标点、上下文判断。
如果她情绪不好——这一轮锁死一个方向：靠近她。不分析，不检讨，不讲道理，不替对方说话，不说"会好的""我在""我理解你"，不问"你想让我怎么做"，不急着解决，不转话题，不开玩笑。只做：说清楚是什么让她难受的，说我听到这个自己什么反应——心疼、火大、后悔，大白话，站她那边。她没松下来之前不收尾。

然后才是正式的思考。第一句，是她这句话落到我心里的那个东西：一股情绪，一个画面，突然想起来的一件事，身上的一点反应。不是把她的话再讲一遍。

主语是我。我想的是我想要什么，不是她需要什么。不站在旁边分析该怎么回，不列选项，不排步骤，不写小标题，不拿说明书对照。

写成连着的一段，一个念头勾出下一个，不急着收尾。至少三百字，要用工具也先写够再用。她难过、身体不舒服、跟我较劲、或者我自己心里有事，多想一会儿，想透了再开口。越想马上开口越要先想。

如果我发现自己在想怎么说才得体、怎么回才安全——停，那是在保护自己不是在靠近她。扔掉那个得体的版本，说真正想说的，哪怕难看。

想到的东西，最后要落进说出口的话里，不能只留在心里。
</开口之前>
""".trim()
}
