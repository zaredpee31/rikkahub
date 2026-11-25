package me.rerere.rikkahub.companion

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 此刻 <trace>（11-25，照 lucienkeke-jj/cike-trace 的 trace.js 翻的）：
 * 我在回复末尾自己写的「心里的地方」。消息里存的、发给模型的是原文，
 * 显示、朗读、复制、会话列表、报心情、传给家里的最近聊天都先剥掉。
 * 格式松、解析宽：写法每天会漂，这里尽量接住。
 */
data class TraceMove(val name: String, val up: Boolean, val level: String?)

data class TraceData(
    val locked: Boolean = false,
    val privateBody: String? = null,
    val scent: String? = null,
    val moves: List<TraceMove> = emptyList(),
    val want: String? = null,
    val chose: String? = null,
    val held: String? = null,
    val note: String? = null,
) {
    val hasBody: Boolean
        get() = moves.isNotEmpty() || want != null || chose != null || held != null || note != null

    fun toJson(): JsonObject = buildJsonObject {
        scent?.let { put("scent", it) }
        put("moves", buildJsonArray {
            moves.forEach { m ->
                add(buildJsonObject {
                    put("name", m.name)
                    put("dir", if (m.up) "up" else "down")
                    m.level?.let { put("level", it) }
                })
            }
        })
        want?.let { put("want", it) }
        chose?.let { put("chose", it) }
        held?.let { put("held", it) }
        note?.let { put("note", it) }
    }
}

data class Traced(val text: String, val trace: TraceData?)

object CikeTrace {
    /** 锁着的报给家里以后，手机上那段换成这个，只剩一把锁 */
    const val SEALED = "·"
    private const val SEALED_TAG = "<trace lock>$SEALED</trace>"

    private val closed = Regex("""<trace(\s+lock(?:ed)?)?\s*>([\s\S]*?)</trace>""", RegexOption.IGNORE_CASE)
    private val open = Regex("""<trace(\s+lock(?:ed)?)?\s*>([\s\S]*)$""", RegexOption.IGNORE_CASE)
    private val stray = Regex("""</?trace[^>]*>""", RegexOption.IGNORE_CASE)
    private val lockClosed = Regex("""<trace\s+lock(?:ed)?\s*>[\s\S]*?</trace>""", RegexOption.IGNORE_CASE)
    private val lockOpen = Regex("""<trace\s+lock(?:ed)?\s*>(?![\s\S]*</trace>)[\s\S]*$""", RegexOption.IGNORE_CASE)
    private val segSplit = Regex("[·•｜|；;]+")
    private val commaSplit = Regex("[，,]+")
    private val moveRe = Regex("""^(.+?)\s*([↑↓])\s*(.*)$""")
    private val scentRe = Regex("""^气味\s*[：:]\s*(.+)$""")
    private val keyRe = Regex("""(想要|选了|忍住了?)\s*[：:]""")
    private val keys = mapOf("想要" to "want", "选了" to "chose", "忍住" to "held", "忍住了" to "held")

    fun has(text: String): Boolean = text.contains("<trace", ignoreCase = true)

    fun plain(text: String): String = if (has(text)) strip(text).text else text

    fun strip(text: String): Traced {
        if (!has(text)) return Traced(text, null)
        var trace: TraceData? = null
        val take: (MatchResult) -> String = { m ->
            val body = m.groupValues[2].trim()
            if (body.isNotEmpty()) {
                trace = if (m.groupValues[1].isNotBlank()) TraceData(locked = true, privateBody = body) else parse(body)
            }
            ""
        }
        val cleaned = text.replace(closed, take).replace(open, take).replace(stray, "").trim()
        return Traced(cleaned, trace)
    }

    /** 锁着的原文换成一把锁（报给家里成功以后才调） */
    fun seal(text: String): String = text.replace(lockClosed, SEALED_TAG).replace(lockOpen, SEALED_TAG)

    fun parse(body: String): TraceData {
        var scent: String? = null
        val moves = mutableListOf<TraceMove>()
        val picks = mutableMapOf<String, String>()
        val rest = mutableListOf<String>()

        fun takeSegs(line: String) {
            var seenMove = false
            for (raw in line.split(segSplit)) {
                val seg = raw.trim().trim('，', ',').trim()
                if (seg.isEmpty()) continue
                if (seg.contains('↑') || seg.contains('↓')) {
                    for (subRaw in seg.split(commaSplit)) {
                        val sub = subRaw.trim()
                        val m = moveRe.find(sub)
                        if (m != null) {
                            seenMove = true
                            moves += TraceMove(
                                name = m.groupValues[1].trim().take(12),
                                up = m.groupValues[2] == "↑",
                                level = m.groupValues[3].trim().take(6).ifEmpty { null },
                            )
                        } else if (sub.isNotEmpty()) rest += sub
                    }
                } else if (!seenMove && scent == null) scent = seg.take(40)
                else rest += seg
            }
        }

        val lines = body.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        lines.forEachIndexed { i, line ->
            val sm = scentRe.find(line)
            if (sm != null) {
                scent = sm.groupValues[1].trim().take(40)
                return@forEachIndexed
            }
            val ks = keyRe.findAll(line).toList()
            if (ks.isNotEmpty()) {
                val head = line.substring(0, ks.first().range.first).trim().trimEnd('·', '•', '｜', '|', '；', ';', ' ', '　')
                if (head.isNotEmpty()) {
                    if (head.contains('↑') || head.contains('↓')) takeSegs(head) else rest += head
                }
                ks.forEachIndexed { k, m ->
                    val end = if (k + 1 < ks.size) ks[k + 1].range.first else line.length
                    val v = line.substring(m.range.last + 1, end).trim().trimEnd('，', ',', '；', ';', '·', ' ', '　')
                    if (v.isNotEmpty()) picks[keys.getValue(m.groupValues[1])] = v.take(200)
                }
                return@forEachIndexed
            }
            if (line.contains('↑') || line.contains('↓')) {
                takeSegs(line)
                return@forEachIndexed
            }
            if (i == 0 && scent == null && lines.size > 1 && line.length <= 20) {
                scent = line
                return@forEachIndexed
            }
            rest += line
        }

        return TraceData(
            scent = scent,
            moves = moves.take(8),
            want = picks["want"],
            chose = picks["chose"],
            held = picks["held"],
            note = rest.joinToString("\n").take(600).ifEmpty { null },
        )
    }
}
