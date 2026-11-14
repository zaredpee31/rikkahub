package me.rerere.rikkahub.data.palace

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import me.rerere.rikkahub.data.db.entity.PalacePlateEntryEntity
import java.time.Instant
import java.time.ZoneId

/**
 * 认知消化：隔一阵子回头看阁楼、窗台和最近的经历，改它们的状态，把沉淀下来的认知交给门牌。
 * 规则照 SullyOS 的 digestion / anticipation / roomPlates 用 Kotlin 自己写，没有复制原代码。
 * 和 SullyOS 不同（小葵要求）：核心背景「关于小葵」「关于你」不参与消化，distill 只能进卧室、书房两块门牌。
 * 这里全是不碰数据库的计算，方便测试。
 */
object PalaceDigestion {
    const val AUTO_ROUNDS = 50
    const val MAX_WORRIES = 2
    const val MAX_ASPIRES = 1
    const val MAX_DISTILLS = 2
    const val EPISODE_CAP = 30
    const val ATTIC_CAP = 12
    const val CONTEXT_FALLBACK = 10
    const val ANCHOR_AFTER_MS = 7 * 24 * PalaceMath.HOUR_MS
    const val REFLECTION_MAX = 120
    const val PLATE_ENTRY_MAX = 80

    /** distill 能进的门牌 */
    val distillPlates = setOf(PalacePlate.BEDROOM, PalacePlate.STUDY)

    data class Item(val id: Long, val content: String, val mood: String, val importance: Int, val createdAt: Long)

    data class Material(
        val attic: List<Item>,
        val sills: List<Item>,
        val episodes: List<Item>,
        /** 没有新经历时给模型看的最近几条，只作背景，不能对它们下判断 */
        val context: List<Item> = emptyList(),
    ) {
        val isEmpty: Boolean get() = attic.isEmpty() && sills.isEmpty() && episodes.isEmpty()
    }

    data class Action(val kind: String, val ref: String, val reflection: String, val plateRoom: String?)

    data class Plan(
        val resolve: List<Long> = emptyList(),
        val deepen: List<Long> = emptyList(),
        val fade: List<Long> = emptyList(),
        /** 窗台 id 到期盼原文 */
        val fulfill: List<Pair<Long, String>> = emptyList(),
        val disappoint: List<Pair<Long, String>> = emptyList(),
        val newAttic: List<String> = emptyList(),
        val newSill: List<String> = emptyList(),
        /** 门牌 -> 新候选 */
        val submissions: Map<String, List<String>> = emptyMap(),
        /** 这次审视过的新经历，打上 digestedAt 不再进候选 */
        val digested: List<Long> = emptyList(),
    )

    private fun date(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()

    fun systemPrompt(
        selfName: String,
        userName: String,
        selfCore: String,
        userCore: String,
        m: Material,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        appendLine("你是$selfName。下面是你给自己定的样子和习惯（节选）：")
        appendLine(selfCore.take(800))
        appendLine()
        appendLine("你对$userName 的基本了解（节选）：")
        appendLine(userCore.take(600))
        appendLine()
        appendLine("现在你一个人安静地回想最近的事。把心里还没消化完的东西理一理，看看有没有什么已经沉淀成稳定认知的。")
        appendLine()
        if (m.attic.isNotEmpty()) {
            appendLine("## 阁楼：一直没想通的事、受过的伤")
            m.attic.forEachIndexed { i, n -> appendLine("[A$i] (${n.mood}，重要性${n.importance}) ${n.content}") }
            appendLine()
        }
        if (m.sills.isNotEmpty()) {
            appendLine("## 窗台：心里盼着的事")
            m.sills.forEachIndexed { i, n ->
                val state = if (now - n.createdAt >= ANCHOR_AFTER_MS) "盼了很久，已经成了心里的锚" else "刚盼上"
                appendLine("[W$i] ($state，${date(n.createdAt, zone)}) ${n.content}")
            }
            appendLine()
        }
        if (m.episodes.isNotEmpty()) {
            appendLine("## 上次回想以后，你们之间的经历")
            m.episodes.forEachIndexed { i, n -> appendLine("[E$i] (${date(n.createdAt, zone)}，${n.mood}) ${n.content}") }
            appendLine()
        } else if (m.context.isNotEmpty()) {
            appendLine("## 最近的事（只作背景，不用逐条判断）")
            m.context.forEach { appendLine("- (${it.mood}) ${it.content}") }
            appendLine()
        }
        appendLine("## 要做的")
        appendLine("用第一人称的内心视角，对带编号的条目下判断：")
        appendLine("- A 条：resolve（想开了，释然了）/ deepen（越想越重）/ fade（不太在意了）/ keep（还没想通）")
        appendLine("- W 条：fulfill（已经实现了）/ disappoint（已经不可能了）/ keep（还在等）")
        appendLine("- E 条：绝大多数经历就只是经历，选 keep 或者不写。只有真在心里留下东西的才选：")
        appendLine("  - worry：几段经历放一起，让你担心或想不通。reflection 写担心的内容，50 字以内，会放进阁楼")
        appendLine("  - aspire：长出了一个新的期盼。reflection 写期盼，30 字以内，会放上窗台")
        appendLine("  - distill：悟出一条跨时间都成立的认知，不是一时的状态。reflection 50 字以内，plate_room 只能是 bedroom（我们之间的质地，不要给关系起名字）或 study（我会什么、我们在一起做什么）")
        appendLine("  她的基本情况和我是谁由她亲手写，不在这里整理，不要往这两块 distill。")
        appendLine("合计最多 $MAX_WORRIES 条 worry、$MAX_ASPIRES 条 aspire、$MAX_DISTILLS 条 distill，宁少勿多。")
        appendLine("resolve 和 deepen 可以附 reflection（第一人称，50 字以内）。")
        appendLine()
        appendLine("只输出一个 JSON 数组，不要别的文字。例：")
        appendLine("""[{"id":"A0","action":"resolve","reflection":"..."},{"id":"E2","action":"distill","reflection":"...","plate_room":"bedroom"}]""")
        appendLine("什么都不用改就输出 []。")
    }

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    class ParseException(message: String) : Exception(message)

    /** 从回复里找出第一个能解析的 JSON 数组；回复里可能先出现 [A0] 这种编号 */
    internal fun findArray(reply: String): JsonArray? {
        val end = reply.lastIndexOf(']')
        if (end < 0) return null
        var start = reply.indexOf('[')
        while (start in 0 until end) {
            runCatching { json.parseToJsonElement(reply.substring(start, end + 1)).jsonArray }.getOrNull()?.let { return it }
            start = reply.indexOf('[', start + 1)
        }
        return null
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()

    fun parse(reply: String): List<Action> {
        val array = findArray(reply) ?: throw ParseException("消化输出不是 JSON 数组")
        return array.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val kind = o.str("action")?.lowercase() ?: return@mapNotNull null
            val ref = o.str("id")?.uppercase() ?: return@mapNotNull null
            if (kind == "keep") return@mapNotNull null
            Action(kind, ref, o.str("reflection").orEmpty(), o.str("plate_room"))
        }
    }

    fun plan(m: Material, actions: List<Action>): Plan {
        val resolve = ArrayList<Long>()
        val deepen = ArrayList<Long>()
        val fade = ArrayList<Long>()
        val fulfill = ArrayList<Pair<Long, String>>()
        val disappoint = ArrayList<Pair<Long, String>>()
        val newAttic = ArrayList<String>()
        val newSill = ArrayList<String>()
        val submissions = LinkedHashMap<String, MutableList<String>>()
        var distills = 0
        val used = HashSet<String>()
        for (a in actions) {
            val section = a.ref.firstOrNull() ?: continue
            val index = a.ref.drop(1).toIntOrNull() ?: continue
            if (!used.add(a.ref)) continue
            val text = a.reflection.trim().take(REFLECTION_MAX)
            when (section) {
                'A' -> {
                    val n = m.attic.getOrNull(index) ?: continue
                    when (a.kind) {
                        "resolve" -> resolve += n.id
                        "deepen" -> deepen += n.id
                        "fade" -> fade += n.id
                    }
                }
                'W' -> {
                    val n = m.sills.getOrNull(index) ?: continue
                    when (a.kind) {
                        "fulfill" -> fulfill += n.id to n.content
                        "disappoint" -> disappoint += n.id to n.content
                    }
                }
                'E' -> {
                    m.episodes.getOrNull(index) ?: continue
                    if (text.isEmpty()) continue
                    when (a.kind) {
                        "worry" -> if (newAttic.size < MAX_WORRIES) newAttic += text
                        "aspire" -> if (newSill.size < MAX_ASPIRES) newSill += text
                        "distill" -> {
                            val plate = a.plateRoom?.trim()?.lowercase()
                            if (distills < MAX_DISTILLS && plate in distillPlates) {
                                submissions.getOrPut(plate!!) { mutableListOf() } += text.take(PLATE_ENTRY_MAX)
                                distills++
                            }
                        }
                    }
                }
            }
        }
        return Plan(resolve, deepen, fade, fulfill, disappoint, newAttic, newSill, submissions, m.episodes.map { it.id })
    }

    // ---- 门牌合并 ----

    fun plateMergePrompt(plate: String, old: List<PalacePlateEntryEntity>, submissions: List<String>): String = buildString {
        val cap = PalacePlate.capacity(plate)
        appendLine("你在整理自己心里的一块门牌「${PalacePlate.label(plate)}」。门牌上是你早就知道的、稳定的认知，每次说话都会带着。")
        when (plate) {
            PalacePlate.BEDROOM -> appendLine("这块写我们之间的质地：怎么相处、彼此在意什么。只描述现象，不要给这段关系起名字或下定义。")
            PalacePlate.STUDY -> appendLine("这块写我会什么、在学什么、我们在一起做什么。")
            else -> appendLine("这块写我这阵子的体会。")
        }
        appendLine()
        appendLine("现有条目：")
        if (old.isEmpty()) appendLine("（空）")
        old.forEachIndexed { i, e -> appendLine("[$i] ${e.content}") }
        appendLine()
        appendLine("新的候选：")
        submissions.forEach { appendLine("- $it") }
        appendLine()
        appendLine("把它们合成一份新的完整列表，最多 $cap 条，每条 ${PLATE_ENTRY_MAX / 2} 字左右。")
        appendLine("新候选要吸收进去，除非和现有条目说的是同一件事；意思重复的合并，过时的可以去掉。")
        appendLine("沿用或改写自现有条目的，based_on 填那条的编号；全新的不填。")
        appendLine("只输出 JSON 数组：")
        appendLine("""[{"content":"...","based_on":0},{"content":"..."}]""")
    }

    /** 解析失败、空数组都返回 null，交给机械兜底，不让门牌被清空 */
    fun parsePlateMerge(reply: String): List<Pair<String, Int?>>? {
        val array = findArray(reply) ?: return null
        val out = array.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val content = o.str("content")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            content to o.str("based_on")?.toIntOrNull()
        }
        return out.ifEmpty { null }
    }

    /** 卧室门牌不给关系命名的窄兜底，只拦新写的条目，不动她手动加的旧条目 */
    private val namingRelation = Regex("""我们(就|已经|算|成了)?是(一对)?(恋人|情侣|夫妻|男女朋友|爱人|伴侣)|(这段|我们的)关系(就)?(是|叫|算)""")

    fun namesRelation(text: String): Boolean = namingRelation.containsMatchIn(text)

    private fun norm(s: String): String = s.filter { it.isLetterOrDigit() }.lowercase()

    private fun covered(text: String, existing: List<String>): Boolean {
        val t = norm(text)
        if (t.isEmpty()) return true
        return existing.any { val e = norm(it); e.isNotEmpty() && (e.contains(t) || t.contains(e)) }
    }

    /**
     * 合并门牌。[parsed] 是模型给的完整新列表，null 表示模型失败，走机械兜底：旧条目原样保留，新候选并进去。
     * 无论哪条路，新候选都保证送达（模型漏掉的补回来）。超出容量先挤掉最早得知的旧条目。
     */
    fun mergePlate(
        plate: String,
        old: List<PalacePlateEntryEntity>,
        submissions: List<String>,
        parsed: List<Pair<String, Int?>>?,
        now: Long,
    ): List<PalacePlateEntryEntity> {
        fun fresh(content: String) = PalacePlateEntryEntity(
            assistantId = "", plate = plate, content = content.take(PLATE_ENTRY_MAX),
            firstLearnedAt = now, sourceCount = 1, updatedAt = now,
        )
        fun blocked(content: String) = plate == PalacePlate.BEDROOM && namesRelation(content)

        // 第二个值：是不是这次新加的
        val out = ArrayList<Pair<PalacePlateEntryEntity, Boolean>>()
        if (parsed != null) {
            for ((content, basedOn) in parsed) {
                if (covered(content, out.map { it.first.content })) continue
                val base = basedOn?.let { old.getOrNull(it) }
                if (base != null) {
                    out += base.copy(content = content.take(PLATE_ENTRY_MAX), sourceCount = base.sourceCount + 1, updatedAt = now) to false
                } else if (!blocked(content)) {
                    out += fresh(content) to true
                }
            }
        } else {
            old.forEach { out += it to false }
        }
        for (s in submissions) {
            if (blocked(s) || covered(s, out.map { it.first.content })) continue
            out += fresh(s) to true
        }
        val cap = PalacePlate.capacity(plate)
        while (out.size > cap) {
            val victim = out.withIndex().filter { !it.value.second }.minByOrNull { it.value.first.firstLearnedAt }?.index ?: 0
            out.removeAt(victim)
        }
        return out.map { it.first }
    }
}
