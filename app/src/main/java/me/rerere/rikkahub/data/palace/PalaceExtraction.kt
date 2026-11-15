package me.rerere.rikkahub.data.palace

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.ZoneId

/** 交给提取模型的一条消息 */
data class PalaceMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    /** 毫秒 */
    val time: Long,
)

/** 模型提取出来的一条记忆，还没入库 */
data class ExtractedMemory(
    val content: String,
    val room: String,
    val importance: Int,
    val mood: String,
    val valence: Float?,
    val arousal: Float?,
    val tags: List<String>,
    /** 模型给的 date：add 时是第一次发生那天，update 时是这次新进展那天（夹到不晚于这批最后一天）；没给就用这批消息的中间时间。add 时同时是 lastEventAt */
    val createdAt: Long,
    /** 和「已有记忆」里哪几条是同一件事（O 编号） */
    val related: List<Int> = emptyList(),
    /** 和这次前面哪几条新记忆是同一件事（在输出里的位置） */
    val sameAs: List<Int> = emptyList(),
    val eventName: String = "",
    val eventTags: List<String> = emptyList(),
    /** action=update：要改的旧记忆在「已有记忆」里的序号（O 编号）；null 是新增 */
    val updates: Int? = null,
)

/** 一次提取的结果：新增和更新的记忆、跳过了几条 */
data class ExtractionResult(
    val memories: List<ExtractedMemory>,
    val skipped: Int = 0,
)

/**
 * 整理水位：消息页按条数攒，最近 [hotZone] 条留在上下文里不动，
 * 水位之后、最近 [hotZone] 条之前的是缓冲，攒够 [threshold] 条处理前 85%，留 15% 给下次衔接。
 */
object PalaceWaterline {
    const val DEFAULT_HOT_ZONE = 200
    const val DEFAULT_THRESHOLD = 100
    const val PROCESS_RATIO = 0.85

    /**
     * 要处理的消息。[lastProcessedId] 找不到（被删了）时按 [lastProcessedTime] 定位；两个都没有说明从没整理过。
     * [drain] 是手动整理：水位之后所有没整理的都处理，不留尾巴、不留最近 [hotZone] 条、不等阈值。
     */
    fun toProcess(
        messages: List<PalaceMessage>,
        lastProcessedId: String?,
        lastProcessedTime: Long,
        hotZone: Int = DEFAULT_HOT_ZONE,
        threshold: Int = DEFAULT_THRESHOLD,
        drain: Boolean = false,
    ): List<PalaceMessage> {
        val buffer = bufferOf(messages, lastProcessedId, lastProcessedTime, if (drain) 0 else hotZone)
        if (buffer.isEmpty()) return emptyList()
        if (!drain && buffer.size < threshold) return emptyList()
        val count = if (drain) buffer.size else kotlin.math.ceil(buffer.size * PROCESS_RATIO).toInt()
        return buffer.take(count)
    }

    fun bufferOf(
        messages: List<PalaceMessage>,
        lastProcessedId: String?,
        lastProcessedTime: Long,
        hotZone: Int = DEFAULT_HOT_ZONE,
    ): List<PalaceMessage> {
        if (messages.size <= hotZone) return emptyList()
        val hotStart = messages.size - hotZone
        val start = processedCount(messages, lastProcessedId, lastProcessedTime)
        if (start >= hotStart) return emptyList()
        return messages.subList(start, hotStart)
    }

    /** 水位之前（含水位那条）已经整理过的消息条数；找不到水位那条（被删了）时按时间定位，没整理过是 0 */
    fun processedCount(messages: List<PalaceMessage>, lastProcessedId: String?, lastProcessedTime: Long): Int =
        lastProcessedId?.let { id -> messages.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }?.plus(1)
            ?: messages.indexOfFirst { it.time > lastProcessedTime }.let { if (it < 0) messages.size else it }

    /** 发给模型时可以隐藏的已整理消息条数：最近 [hotZone] 条永远留着 */
    fun hiddenCount(
        messages: List<PalaceMessage>,
        lastProcessedId: String?,
        lastProcessedTime: Long,
        hotZone: Int = DEFAULT_HOT_ZONE,
    ): Int = processedCount(messages, lastProcessedId, lastProcessedTime).coerceAtMost((messages.size - hotZone).coerceAtLeast(0))

    fun idKey(conversationId: Any) = "wl:$conversationId:id"
    fun timeKey(conversationId: Any) = "wl:$conversationId:time"
}

object PalaceExtraction {
    /** 每批最多这么多条消息、这么多字，太长模型会偷懒漏记 */
    const val CHUNK_MESSAGES = 250
    const val CHUNK_CHARS = 60_000
    const val USER_TEXT_LIMIT = 8000
    const val ASSISTANT_TEXT_LIMIT = 2000

    fun chunks(messages: List<PalaceMessage>): List<List<PalaceMessage>> {
        val out = ArrayList<List<PalaceMessage>>()
        var current = ArrayList<PalaceMessage>()
        var chars = 0
        for (m in messages) {
            val len = clip(m).length
            if (current.isNotEmpty() && (current.size >= CHUNK_MESSAGES || chars + len > CHUNK_CHARS)) {
                out += current
                current = ArrayList()
                chars = 0
            }
            current += m
            chars += len
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    private fun clip(m: PalaceMessage): String {
        val limit = if (m.fromUser) USER_TEXT_LIMIT else ASSISTANT_TEXT_LIMIT
        val t = m.text.trim()
        return if (t.length > limit) t.take(limit) + "…" else t
    }

    fun transcript(messages: List<PalaceMessage>, userName: String, selfName: String, zone: ZoneId = ZoneId.systemDefault()): String =
        messages.joinToString("\n") { m ->
            val t = java.time.Instant.ofEpochMilli(m.time).atZone(zone)
            val stamp = "%04d-%02d-%02d %02d:%02d".format(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute)
            "[$stamp] ${if (m.fromUser) userName else selfName}: ${clip(m).replace('\n', ' ')}"
        }

    fun systemPrompt(selfName: String, userName: String, userCore: String, selfCore: String, hasRelated: Boolean = false): String = buildString {
        if (userCore.isNotBlank()) {
            appendLine("【你早就知道的关于$userName 的背景，不用再记一遍】")
            appendLine(userCore.take(6000))
            appendLine()
        }
        if (selfCore.isNotBlank()) {
            appendLine("【你自己的说话方式和相处约定，不用再记一遍】")
            appendLine(selfCore.take(6000))
            appendLine()
        }
        appendLine("你在整理${userName}和${selfName}最近的聊天，把值得长期记住的事写成记忆。")
        appendLine()
        appendLine("【记什么】")
        appendLine("- ${userName}的身体、习惯、喜好、雷区、生活变化")
        appendLine("- 两人定下的约定和规矩")
        appendLine("- 让${userName}真难过、真开心，或让关系有变化的事")
        appendLine("- 只记一个月后仍然有用的")
        appendLine()
        appendLine("【不记】")
        appendLine("- 闲聊过程")
        appendLine("- 修代码、调试的细节")
        appendLine("- 当天说完就过去的话")
        appendLine()
        appendLine("【怎么写】")
        appendLine("- 一条记忆只写一件事")
        appendLine("- 第三人称，称呼用${userName}、${selfName}")
        appendLine("- 按时间或因果顺序，写成连贯的一段")
        appendLine("- 只写客观事实，不加评论、解读、感想、总结")
        appendLine("- 省略修饰词和形容词描写")
        appendLine("- 只保留关键台词，其余不引原话")
        appendLine("- 人名、称呼、剧名、书名、菜名、地名等专有名词照原样写")
        appendLine("- 记忆正文里不用小标题、序号、项目符号")
        appendLine()
        appendLine("【合并】")
        appendLine("- 下面会给出相关旧记忆和 id")
        appendLine("- 跟旧记忆是同一件事：输出 update，写合并后的完整一条")
        appendLine("- 完全重复：skip")
        appendLine("- 新的事：add")
        appendLine()
        appendLine("【房间】")
        appendLine("- living_room：日常互动")
        appendLine("- bedroom：只放涉及两人感情本身的事")
        appendLine("- study：项目、工作、技能")
        appendLine("- user_room：${userName}本人的信息、身体、习惯、家人")
        appendLine("- self_room：${selfName}自己的认同和变化")
        appendLine("- attic：没消化的困惑和伤害")
        appendLine("- windowsill：期盼和目标")
        appendLine("- 拿不准放 living_room")
        appendLine()
        appendLine("【日期】")
        appendLine("- add 的 date：这件事第一次发生的那天，不是聊天那天；今天说昨天的事，写昨天")
        appendLine("- update 的 date：这次新进展发生的那天")
        appendLine("- 正文里每段进展前写上它发生的日期（如「11月13日复查……」）")
        appendLine()
        appendLine("## 输出格式")
        appendLine()
        appendLine("只输出一个 JSON 对象，不要 markdown 代码块，不要别的文字：")
        appendLine("""{"memories": [记忆, ...]}""")
        appendLine()
        appendLine("memories 里每一项是下面三种之一，没有值得记的就让 memories 是 []：")
        appendLine("""- 新增：{"action": "add", "content": "...", "room": "living_room", "importance": 5, "mood": "neutral", "valence": 0.0, "arousal": 0.0, "tags": ["标签1", "标签2"], "date": "2026-11-14", "time": "21:30"}""")
        appendLine("""- 更新旧记忆：{"action": "update", "id": "O3", "content": "合并后的完整版本", 其余字段同上}""")
        appendLine("""- 跳过：{"action": "skip", "id": "O2"}""")
        appendLine("update 和 skip 的 id 只能用「已有记忆」里给出的编号（O0、O1……）。")
        appendLine()
        appendLine("字段：importance 1 到 10，越重要写得越完整；mood 从这 12 个里选一个：${PalaceMood.extractionMoods.joinToString(", ")}；valence（-1 极痛苦到 1 极愉悦）和 arousal（-1 极平静到 1 极激烈）；tags 2 到 5 个关键词，人名、地名、作品名照原样写；date 是 YYYY-MM-DD（含义见【日期】），time 是那段话的 HH:MM，聊天跨了几天或跨过午夜就分别标。")
        appendLine("系统提示、工具调用、记忆整理本身的说明，都不是聊天内容，不要记。")
        appendLine()
        appendLine("## 同一件事（事件盒）")
        if (hasRelated) {
            appendLine("- 和「已有记忆」里某条是同一件事：在 relatedTo 里写它的 O 编号，如 [\"O0\", \"O3\"]。")
        }
        appendLine("- 和这次输出里前面某条新记忆是同一件事：在 sameAs 里写它在 memories 数组里的位置（从 0 开始，只能指向前面的），如 [\"0\"]。")
        appendLine("- 只标真正的同一件事：同一件事的后续、结局、再次发生、直接因果。只是话题像不算。")
        appendLine("- relatedTo 或 sameAs 有一个不为空，就同时写 eventName（这件事的名字，5 到 12 字，名词短语，比如「和领导的冲突」）和 eventTags（3 到 6 个具体的人物、地点、东西、动作）。")
        appendLine("- 一条和好几条都相关时把编号都写上，eventName、eventTags 只写一份，描述这件事整体。")
        appendLine("- 都没关联就不写这四个字段。")
        append("""  例：{"action": "add", "content": "...", "room": "study", ..., "relatedTo": ["O0"], "sameAs": ["0"], "eventName": "和领导的冲突", "eventTags": ["领导", "加班", "报告"]}""")
    }

    class ParseException(message: String) : Exception(message)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 只要记忆列表的简便写法，测试和旧调用用 */
    fun parse(reply: String, batch: List<PalaceMessage>, zone: ZoneId = ZoneId.systemDefault(), relatedCount: Int = 0): List<ExtractedMemory> =
        parseResult(reply, batch, zone, relatedCount).memories

    /**
     * 解析模型输出：`{"memories": [...]}`，也认只有数组的旧写法。整个 JSON 必须完整
     * （被截断的输出直接判失败重试，不抢救半截）；单条缺正文的跳过，房间认不出放客厅，重要性夹到 1-10。
     * action=skip 不入库只计数；action=update 的 id 不在已有记忆范围内就当新增。
     */
    fun parseResult(reply: String, batch: List<PalaceMessage>, zone: ZoneId = ZoneId.systemDefault(), relatedCount: Int = 0): ExtractionResult {
        val text = reply
            .replace(Regex("(?s)<think>.*?</think>"), "")
            .replace(Regex("^\\s*```(?:json)?\\s*"), "")
            .replace(Regex("\\s*```\\s*$"), "")
            .trim()
        val objStart = text.indexOf('{')
        val arrStart = text.indexOf('[')
        val wrapped = objStart >= 0 && (arrStart < 0 || objStart < arrStart)
        val root = try {
            if (wrapped) {
                val end = text.lastIndexOf('}')
                if (end < objStart) throw ParseException("记忆提取没有返回 JSON")
                json.parseToJsonElement(text.substring(objStart, end + 1))
            } else {
                val end = text.lastIndexOf(']')
                if (arrStart < 0 || end < arrStart) throw ParseException("记忆提取没有返回 JSON 数组")
                json.parseToJsonElement(text.substring(arrStart, end + 1))
            }
        } catch (e: ParseException) {
            throw e
        } catch (e: Exception) {
            throw ParseException("记忆提取返回的 JSON 不完整：${e.message?.take(120)}")
        }
        val array = if (wrapped) ((root as? JsonObject)?.get("memories") as? JsonArray ?: JsonArray(emptyList())) else root.jsonArray
        val fallbackTime = batch.map { it.time }.sorted().let { if (it.isEmpty()) System.currentTimeMillis() else it[it.size / 2] }
        val lastDay = batch.maxOfOrNull { it.time }?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val out = ArrayList<ExtractedMemory>()
        var skipped = 0
        for (element in array) {
            val o = element as? JsonObject ?: continue
            val action = o.string("action")?.trim()?.lowercase()
            if (action == "skip") { skipped++; continue }
            val content = o.string("content")?.trim().orEmpty()
            if (content.isEmpty()) continue
            val room = o.string("room")?.trim()
            val position = out.size
            val target = if (action == "update") refs(o, "id", 'O').firstOrNull()?.takeIf { it in 0 until relatedCount } else null
            out += ExtractedMemory(
                content = content,
                room = if (PalaceRoom.isValid(room)) room!! else PalaceRoom.LIVING,
                importance = (o.number("importance") ?: 5.0).let { if (it.isFinite()) Math.round(it).toInt() else 5 }.coerceIn(1, 10),
                mood = PalaceMood.normalize(o.string("mood")),
                valence = o.number("valence")?.takeIf { it.isFinite() }?.coerceIn(-1.0, 1.0)?.toFloat(),
                arousal = o.number("arousal")?.takeIf { it.isFinite() }?.coerceIn(-1.0, 1.0)?.toFloat(),
                tags = (o["tags"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                    .filter { it.isNotEmpty() }.distinct().take(5),
                createdAt = o.string("date")?.let { memoryTime(it, o.string("time"), zone, lastDay) } ?: fallbackTime,
                related = refs(o, "relatedTo", 'O').filter { it in 0 until relatedCount }.distinct(),
                sameAs = refs(o, "sameAs", 'N').filter { it in 0 until position }.distinct(),
                eventName = o.string("eventName")?.trim().orEmpty().take(30),
                eventTags = (o["eventTags"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                    .filter { it.isNotEmpty() }.distinct().take(PalaceBoxes.MAX_TAGS),
                updates = target,
            )
        }
        return ExtractionResult(out, skipped)
    }

    /** ["O0", "O3"] / ["0"] / [0] 都认 */
    private fun refs(o: JsonObject, key: String, prefix: Char): List<Int> =
        (o[key].let { if (it is JsonArray) it else JsonArray(listOfNotNull(it)) }).mapNotNull { e ->
            (e as? JsonPrimitive)?.contentOrNull?.trim()?.removePrefix(prefix.toString())?.removePrefix(prefix.lowercase())?.toIntOrNull()
        }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.number(key: String): Double? =
        (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

    /**
     * 记忆发生的时间。日期不能晚于这批消息的最后一天（模型有时会填整理当天），
     * 时间没给或不合法就是当天 12:00，召回时按「只有日期」处理。
     */
    internal fun memoryTime(rawDate: String, rawTime: String?, zone: ZoneId, lastDay: LocalDate?): Long? = runCatching {
        var day = LocalDate.parse(rawDate.trim().take(10))
        var clamped = false
        if (lastDay != null && day.isAfter(lastDay)) { day = lastDay; clamped = true }
        val time = if (clamped) null else rawTime?.trim()?.let { runCatching { java.time.LocalTime.parse(it.take(5)) }.getOrNull() }
        day.atTime(time ?: java.time.LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()
}

/**
 * 旧记忆日期修复：整理写入的 origin 是 `chat:<对话id>:<这批最后一条消息id>:<序号>`。
 * 记忆发生在这批最后一条消息之前，所以日期晚于那条消息所在日的一定填错了（填成了整理当天），改成那一天。
 * 早于它的可能是跨天批次里本来就对，不动。找不到消息的不动。
 */
object PalaceDateRepair {
    const val META_DONE = "datefix_v1"
    const val META_ORIG = "datefix_orig:"

    private val originRegex = Regex("^chat:([^:]+):([^:]+):\\d+$")

    fun parseOrigin(origin: String): Pair<String, String>? =
        originRegex.matchEntire(origin)?.let { it.groupValues[1] to it.groupValues[2] }

    data class Node(val id: Long, val origin: String, val createdAt: Long)
    data class Fix(val id: Long, val from: Long, val to: Long)
    data class Plan(val fixes: List<Fix>, val missing: Int, val ok: Int)

    /** [messageTime] 按（对话id，消息id）查消息时间，找不到返回 null */
    fun plan(nodes: List<Node>, zone: ZoneId, messageTime: (String, String) -> Long?): Plan {
        val fixes = ArrayList<Fix>()
        var missing = 0
        var ok = 0
        for (n in nodes) {
            val (conv, msg) = parseOrigin(n.origin) ?: continue
            val t = messageTime(conv, msg)
            if (t == null) { missing++; continue }
            val msgDay = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
            val nodeDay = java.time.Instant.ofEpochMilli(n.createdAt).atZone(zone).toLocalDate()
            if (nodeDay.isAfter(msgDay)) fixes += Fix(n.id, n.createdAt, msgDay.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
            else ok++
        }
        return Plan(fixes, missing, ok)
    }
}
