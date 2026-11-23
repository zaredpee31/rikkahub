package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 这一刻才有的东西（心情、独白要求）放在请求最末尾单独一条，不贴在她的消息上（照 SullyOS 的 volatileTail）。
 * 聊天记录一个字都不动。ClaudeProvider 认「最后一条是合成的 user」：把它并进前面那条 user 当单独一块（不加标签，
 * 11-20 实测加 system-reminder 标签 4.6 独白变短），缓存点打在它前面那块上，下一轮整段历史都能读缓存；
 * 工具续发时不重组，它留在她那句后面，这次的助手消息和工具结果接在后面（照 SullyOS）。几样东西都往同一条里加，顺序按变换器的先后。
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

/** 距离上一句过了多久（中文）：不足 1 分钟「不到 1 分钟」，不足 1 小时「N 分钟」，不足 24 小时「X 小时 Y 分钟」，之后「N 天 X 小时」；Y、X 为 0 时省。和服务器 timing.fmt_gap 一样。 */
internal fun formatGapZh(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    if (s < 60) return "不到 1 分钟"
    val m = s / 60
    if (m < 60) return "$m 分钟"
    val h = m / 60
    val mm = m % 60
    if (h < 24) return if (mm > 0) "$h 小时 $mm 分钟" else "$h 小时"
    val d = h / 24
    val hh = h % 24
    return if (hh > 0) "$d 天 $hh 小时" else "$d 天"
}

/**
 * 统一的一行：现在 2026-11-20 22:15（周五），距离她上一句过去 3 小时 12 分钟（上一句在 AC）。
 * lastMs 为空表示没有她的上一句，只写现在。时间按北京时间写，和服务器、QQ 两边一致。
 */
internal fun gapLine(nowMs: Long, lastMs: Long?, lastInQq: Boolean): String {
    val zoned = java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.of("Asia/Shanghai"))
    val wd = "一二三四五六日"[zoned.dayOfWeek.value - 1]
    val head = "现在 ${java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(zoned)}（周$wd）"
    if (lastMs == null) return head
    return head + "，距离她上一句过去 ${formatGapZh((nowMs - lastMs) / 1000)}（上一句在 ${if (lastInQq) "QQ" else "AC"}）"
}

/**
 * 每一轮都往末尾那块加一行「现在几点、距离她上一句多久」。她上一句 = 本会话倒数第二条她的消息 和
 * 服务器给的 QQ 那边她最近一句（HomeLink.herLastQqAtMs，不含当前这句）里更近的那个。
 * 只往末尾加，不插历史中间，不断缓存。旧的 TimeReminderTransformer 不动。
 */
object GapTailTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
        val users = messages.filter { it.role == MessageRole.USER && !it.isSynthetic }
        val acMs = users.getOrNull(users.size - 2)
            ?.createdAt?.let { it.toInstant(tz).toEpochMilliseconds() }
        val qqMs = me.rerere.rikkahub.companion.HomeLink.herLastQqAtMs().takeIf { it > 0 }
        val inQq = qqMs != null && (acMs == null || qqMs > acMs)
        return messages.withTail(gapLine(System.currentTimeMillis(), if (inQq) qqMs else acMs, inQq))
    }
}

/**
 * 门牌「关于我」里的「我的说话方式」那一节：从人设里拆出来，放到末尾那块的情绪后面、独白规矩前面（情绪二期方案二.4）。
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
