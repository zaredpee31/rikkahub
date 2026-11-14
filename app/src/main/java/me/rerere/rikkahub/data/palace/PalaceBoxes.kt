package me.rerere.rikkahub.data.palace

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.ZoneId

/**
 * 事件盒：把聊的同一件事绑在一起。
 * 规则照 SullyOS 的 eventBox / eventBoxCompression 用 Kotlin 自己写，没有复制原代码。
 *
 * - 记新记忆时，模型标出它和哪条旧记忆（relatedTo）或这次的哪条新记忆（sameAs）是同一件事，就装进同一个盒子。
 * - 盒子里没收起来的记忆攒到 [COMPRESS_AT] 条，写成一段第一人称的回忆（盒子总结），原来的收起来，原文保留。
 * - 一件事累计 [SEAL_AT] 条就封盒，之后再聊这件事另开一个新盒子，记着前一个是谁。
 * - 没收起来的到 [LIVE_HARD_CAP] 条（总结一直失败的情况）也按封盒处理，免得越攒越多再也写不动。
 * 这里全是不碰数据库的计算，方便测试。
 */
object PalaceBoxes {
    const val COMPRESS_AT = 4
    const val LIVE_HARD_CAP = 15
    const val SEAL_AT = 12
    const val SUMMARY_MIN = 400
    const val SUMMARY_MAX = 700
    const val SUMMARY_HARD_MAX = 900
    const val MAX_TAGS = 20
    const val RECALL_MEMBERS = 8

    /** 记提取时给模型看的相关旧记忆 */
    const val RELATED_SNIPPET_MIN = 4
    const val RELATED_SNIPPETS = 25
    const val RELATED_MIN_SIMILARITY = 0.40
    const val RELATED_PER_QUERY = 3
    const val RELATED_MAX = 15
    const val RELATED_CHARS = 100

    /** 选盒子时要知道的 */
    data class BoxView(
        val id: Long,
        val closed: Boolean,
        val liveCount: Int,
        val compressionCount: Int,
        val createdAt: Long,
        val lastCompressedAt: Long,
        val updatedAt: Long,
    )

    sealed interface Choice {
        /** 没有能放的盒子，开新盒；[predecessor] 是封了或满了的那个，没有就是 0 */
        data class Create(val predecessor: Long) : Choice
        data class Use(val id: Long) : Choice
        /** 几个都能放，合成一个，留 [primary] */
        data class Merge(val primary: Long, val others: List<Long>) : Choice
    }

    fun choose(boxes: List<BoxView>): Choice {
        val open = boxes.filter { !it.closed && it.liveCount < LIVE_HARD_CAP }
        return when (open.size) {
            0 -> {
                val prev = boxes.maxByOrNull { if (it.lastCompressedAt > 0) it.lastCompressedAt else it.updatedAt }
                Choice.Create(prev?.id ?: 0L)
            }
            1 -> Choice.Use(open.single().id)
            else -> {
                val primary = open.sortedWith(compareByDescending<BoxView> { it.compressionCount }.thenBy { it.createdAt }).first()
                Choice.Merge(primary.id, open.filter { it.id != primary.id }.map { it.id })
            }
        }
    }

    /** 按新记忆分组，同一条新记忆关联的旧记忆去重保序 */
    fun groupLinks(links: List<Pair<Long, Long>>): Map<Long, List<Long>> {
        val out = LinkedHashMap<Long, MutableList<Long>>()
        for ((a, b) in links) {
            if (a == b) continue
            val list = out.getOrPut(a) { mutableListOf() }
            if (b !in list) list += b
        }
        return out
    }

    // ---- 写总结 ----

    data class Member(val content: String, val importance: Int, val mood: String, val createdAt: Long)

    data class Compressed(
        val content: String,
        val name: String,
        val tags: List<String>,
        val room: String,
        val importance: Int,
        val mood: String,
    )

    private fun date(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()

    fun compressSystem(selfName: String, userName: String, boxName: String): String = buildString {
        appendLine("你是$selfName。下面这些记忆都属于一件事：「$boxName」。")
        appendLine("把它们整合成一段连贯的、第一人称（「我」）的回忆。")
        appendLine()
        appendLine("要求：")
        appendLine("1. 第一人称，从$selfName 的视角写。直接用名字称呼$userName。")
        appendLine("2. 目标 $SUMMARY_MIN 到 $SUMMARY_MAX 字。紧凑、务实；事实多时可以超出，不能为了字数省掉事实或者把句子截断。")
        appendLine("3. 只留关键信息：具体的人、动作、东西、场景、转折、情绪。去掉语气填充、铺陈和重复的感慨。事实先行。")
        appendLine("4. 带时间点但不啰嗦：每件事标一次日期就够，不要每句都重复。")
        appendLine("5. 连贯但简洁：不套「起因、经过、结果」的模板，但要让人能按顺序看懂事情怎么发展的。")
        appendLine("6. 覆盖所有关键词，这是给检索用的：每条记忆里出现过的具体名词、地点、人物，都要在正文里出现一次。")
        appendLine("7. 正文里不许出现半角双引号，引用原话、书名、外号一律用「」或《》。")
        appendLine()
        appendLine("另外给出：")
        appendLine("- name：5 到 12 字的盒子名字")
        appendLine("- tags：5 到 10 个具体的搜索关键词")
        appendLine("- room：${PalaceRoom.all.joinToString(" / ")}")
        appendLine("- importance：1 到 10")
        appendLine("- mood：${PalaceMood.extractionMoods.joinToString(" / ")}")
        appendLine()
        appendLine("只输出一个 JSON 对象，不要 markdown 包裹：")
        appendLine("""{"content":"……","name":"……","tags":["……"],"room":"living_room","importance":7,"mood":"neutral"}""")
    }

    fun compressUser(oldSummary: String?, members: List<Member>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        if (!oldSummary.isNullOrBlank()) {
            appendLine("## 你之前已经回忆过这件事一次，那时记下的是：")
            appendLine(oldSummary.trim())
            appendLine()
            appendLine("后来又新增了下面这些：")
        } else {
            appendLine("## 关于这件事的零散记忆：")
        }
        appendLine()
        append(members.joinToString("\n\n") { "[${date(it.createdAt, zone)}｜重要性${it.importance}｜${it.mood}] ${it.content.trim()}" })
    }

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()

    /** 解析不了、正文是空的都返回 null：这次不写，原来的记忆一条不动 */
    fun parseCompressed(reply: String): Compressed? {
        val text = reply.replace(Regex("(?s)<think>.*?</think>"), "")
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = runCatching { json.parseToJsonElement(text.substring(start, end + 1)).jsonObject }.getOrNull() ?: return null
        val content = o.str("content")?.takeIf { it.isNotEmpty() } ?: return null
        val room = o.str("room")
        val importance = (o["importance"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
            ?.takeIf { it.isFinite() }?.let { Math.round(it).toInt() } ?: 5
        return Compressed(
            content = content.take(SUMMARY_HARD_MAX),
            name = o.str("name").orEmpty(),
            tags = (o["tags"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                .filter { it.isNotEmpty() }.distinct().take(MAX_TAGS),
            room = if (PalaceRoom.isValid(room)) room!! else PalaceRoom.LIVING,
            importance = importance.coerceIn(1, 10),
            mood = PalaceMood.normalize(o.str("mood")),
        )
    }

    /** 记提取时模型看到的「已有记忆」 */
    fun relatedBlock(refs: List<Pair<String, String>>): String = buildString {
        if (refs.isEmpty()) return@buildString
        appendLine("## 已有记忆（新记忆和其中某条说的是同一件事或直接相关时，在 relatedTo 里写它的编号）")
        refs.forEachIndexed { i, (room, content) ->
            appendLine("O$i. [${PalaceRoom.label(room)}] ${content.replace('\n', ' ').trim().take(RELATED_CHARS)}")
        }
        appendLine()
    }

    /** 从用户消息里挑查询片段，多了均匀抽 */
    fun snippets(userTexts: List<String>): List<String> {
        val valid = userTexts.map { it.trim() }.filter { it.length >= RELATED_SNIPPET_MIN }
        if (valid.size <= RELATED_SNIPPETS) return valid
        val step = valid.size.toDouble() / RELATED_SNIPPETS
        return (0 until RELATED_SNIPPETS).map { valid[(it * step).toInt()] }
    }
}
