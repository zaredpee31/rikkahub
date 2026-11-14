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
    /** 这件事发生的那天中午；模型没给日期时用这批消息的中间时间 */
    val createdAt: Long,
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
     * [drain] 是手动整理：缓冲有一条就处理，而且全部处理完，不留尾巴。
     */
    fun toProcess(
        messages: List<PalaceMessage>,
        lastProcessedId: String?,
        lastProcessedTime: Long,
        hotZone: Int = DEFAULT_HOT_ZONE,
        threshold: Int = DEFAULT_THRESHOLD,
        drain: Boolean = false,
    ): List<PalaceMessage> {
        val buffer = bufferOf(messages, lastProcessedId, lastProcessedTime, hotZone)
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
        val start = lastProcessedId?.let { id -> messages.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }?.plus(1)
            ?: messages.indexOfFirst { it.time > lastProcessedTime }.let { if (it < 0) messages.size else it }
        if (start >= hotStart) return emptyList()
        return messages.subList(start, hotStart)
    }
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

    fun systemPrompt(selfName: String, userName: String, userCore: String, selfCore: String): String = buildString {
        appendLine("你是$selfName。下面是你和$userName 在消息页里的一段聊天记录。用你自己的第一人称「我」，把值得长期记住的事提取成记忆。称呼她用「$userName」。")
        appendLine()
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
        appendLine(
            """
            ## 规则

            1. 第一人称叙事，保留完整的来龙去脉，不要只剩干巴巴的结论。例：
               - "$userName 今天加班到九点还没吃饭，我让她别硬撑，她说回家煮面。"
               - "$userName 说起小时候学画画被老师夸，语气一下子亮了，我挺高兴她愿意跟我讲这些。"
            2. 重要性 importance 1-10，越重要写得越完整：
               - 1-5：15-50 字，事实为主
               - 6-7：60-120 字，带上我的感受
               - 8-10：100-200 字，起因、经过、我的感受和反应
            3. 房间 room：
               - living_room：纯日常琐事，吃什么、天气、随口吐槽
               - bedroom：我和$userName 之间的亲密情感、深层羁绊、感动的时刻
               - study：工作、学习、技能、她在做的项目
               - user_room：关于$userName 的个人信息、习惯、喜好、经历、情绪模式，以及她家人朋友同事的一切事，哪怕只发生一次
               - self_room：我自己的成长、认同的变化
               - attic：没解决的矛盾、困惑、受到的伤害
               - windowsill：我的期盼、我们的目标、对以后的憧憬
            4. 情绪 mood，从这 12 个里选一个：${PalaceMood.extractionMoods.joinToString(", ")}
            5. 情绪坐标 valence（-1 极痛苦到 1 极愉悦）、arousal（-1 极平静到 1 极激烈）。参考：开心约 (0.7, 0.5)，平静约 (0.5, -0.6)，失落约 (-0.5, -0.4)，焦虑约 (-0.6, 0.7)，生气约 (-0.7, 0.8)。
            6. 标签 tags：2-5 个关键词，人名、地名、作品名照原样写。
            7. 日期 date：每条消息前面有 [YYYY-MM-DD HH:MM]，按这件事实际发生的那天填 "YYYY-MM-DD"，聊天跨了几天就分别标。
            8. 不要漏掉重要的事，也不要把每句话都变成记忆。
            9. 不要记：写代码、命令、报错、调试过程，游戏一步步的流水账，已经写在背景里的东西。
            10. 系统提示、工具调用、记忆整理本身的说明，都不是聊天内容，不要记。

            ## 输出

            只输出一个 JSON 数组，不要 markdown 代码块，不要别的文字。没有值得记的就输出 []。
            [{"content": "...", "room": "living_room", "importance": 5, "mood": "neutral", "valence": 0.0, "arousal": 0.0, "tags": ["标签1", "标签2"], "date": "2026-11-14"}]
            """.trimIndent()
        )
    }

    class ParseException(message: String) : Exception(message)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 解析模型输出。整个数组必须完整（被截断的输出直接判失败重试，不抢救半截）；
     * 单条缺正文的跳过，房间认不出放客厅，重要性夹到 1-10。
     */
    fun parse(reply: String, batch: List<PalaceMessage>, zone: ZoneId = ZoneId.systemDefault()): List<ExtractedMemory> {
        val text = reply
            .replace(Regex("(?s)<think>.*?</think>"), "")
            .replace(Regex("^\\s*```(?:json)?\\s*"), "")
            .replace(Regex("\\s*```\\s*$"), "")
            .trim()
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end < start) throw ParseException("记忆提取没有返回 JSON 数组")
        val array = try {
            json.parseToJsonElement(text.substring(start, end + 1)).jsonArray
        } catch (e: Exception) {
            throw ParseException("记忆提取返回的 JSON 不完整：${e.message?.take(120)}")
        }
        val fallbackTime = batch.map { it.time }.sorted().let { if (it.isEmpty()) System.currentTimeMillis() else it[it.size / 2] }
        return array.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val content = o.string("content")?.trim().orEmpty()
            if (content.isEmpty()) return@mapNotNull null
            val room = o.string("room")?.trim()
            ExtractedMemory(
                content = content,
                room = if (PalaceRoom.isValid(room)) room!! else PalaceRoom.LIVING,
                importance = (o.number("importance") ?: 5.0).let { if (it.isFinite()) Math.round(it).toInt() else 5 }.coerceIn(1, 10),
                mood = PalaceMood.normalize(o.string("mood")),
                valence = o.number("valence")?.takeIf { it.isFinite() }?.coerceIn(-1.0, 1.0)?.toFloat(),
                arousal = o.number("arousal")?.takeIf { it.isFinite() }?.coerceIn(-1.0, 1.0)?.toFloat(),
                tags = (o["tags"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                    .filter { it.isNotEmpty() }.distinct().take(5),
                createdAt = o.string("date")?.let { dateToMillis(it, zone) } ?: fallbackTime,
            )
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.number(key: String): Double? =
        (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

    private fun dateToMillis(raw: String, zone: ZoneId): Long? = runCatching {
        LocalDate.parse(raw.trim().take(10)).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()
}
