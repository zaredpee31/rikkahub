package me.rerere.rikkahub.data.palace

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.db.entity.PalacePlateEntryEntity
import java.time.Instant
import java.time.ZoneId

/**
 * 认知消化：隔一阵子回头看阁楼、窗台、书房、两间个人房和最近的经历，改它们的状态，
 * 再把门牌整块重新整理一遍。规则照 SullyOS 的 digestion / roomPlates / roomPlateCore 用 Kotlin 自己写，没有复制原代码。
 *
 * 和 SullyOS 不同的地方（小葵要求）：
 * - 「关于小葵」「关于你」两块核心背景只能她手动改，消化不碰。
 * - SullyOS 里用户房、自我房门牌那份自动更新，改进「这阵子」：放我最近的心情、最近怎么看她、最近在想什么，
 *   还有她这阵子的状态。这块本来就会变，不要求跨时间稳定。
 * - 门牌条目没有 tag 字段，不让模型出 tag。
 * 这里全是不碰数据库的计算，方便测试。
 */
object PalaceDigestion {
    const val AUTO_ROUNDS = 50
    const val MAX_WORRIES = 2
    const val MAX_ASPIRES = 1
    const val MAX_DISTILLS = 2
    const val EPISODE_CAP = 30
    const val ATTIC_CAP = 12
    const val FRESH_CAP = 20
    const val STUDY_MIN_ACCESS = 3
    const val CONTEXT_FALLBACK = 10
    const val ANCHOR_AFTER_MS = 7 * 24 * PalaceMath.HOUR_MS
    const val REFLECTION_MAX = 120
    const val INSIGHT_MAX = 200

    const val PLATE_ENTRY_TARGET = 50
    const val PLATE_ENTRY_MAX = 90
    const val MATERIAL_PER_PLATE = 15
    const val MATERIAL_LINE_MAX = 160
    const val MATERIAL_ANCHORS = 5
    const val PLATE_TIMEOUT_MS = 120_000L

    data class Item(
        val id: Long,
        val content: String,
        val mood: String,
        val importance: Int,
        val createdAt: Long,
        val tags: List<String> = emptyList(),
        val accessCount: Int = 0,
        val recordedAt: Long = createdAt,
    )

    data class Material(
        val attic: List<Item>,
        val sills: List<Item>,
        val episodes: List<Item>,
        /** 没有新经历时给模型看的最近几条，只作背景，不能对它们下判断 */
        val context: List<Item> = emptyList(),
        /** 书房里被反复想起、还没审过的 */
        val study: List<Item> = emptyList(),
        /** 她的房间里还没审过的 */
        val user: List<Item> = emptyList(),
        /** 自我的房间里还没审过的 */
        val self: List<Item> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = attic.isEmpty() && sills.isEmpty() && episodes.isEmpty() &&
                study.isEmpty() && user.isEmpty() && self.isEmpty()
    }

    data class Action(
        val kind: String,
        val ref: String,
        val reflection: String,
        val plateRoom: String?,
        val insight: String = "",
    )

    /** 自我反刍出来的新困惑，放进阁楼；事件时间沿用源记忆的 */
    data class Confuse(val sourceId: Long, val text: String, val createdAt: Long)

    data class Plan(
        val resolve: List<Long> = emptyList(),
        val deepen: List<Long> = emptyList(),
        val fade: List<Long> = emptyList(),
        /** 窗台 id 到期盼原文 */
        val fulfill: List<Pair<Long, String>> = emptyList(),
        val disappoint: List<Pair<Long, String>> = emptyList(),
        val newAttic: List<String> = emptyList(),
        val newSill: List<String> = emptyList(),
        val selfConfuse: List<Confuse> = emptyList(),
        /** 门牌 -> 这次消化交上来的候选 */
        val submissions: Map<String, List<String>> = emptyMap(),
        /** 这次审过的经历和书房、个人房候选，打上 digestedAt 不再送审 */
        val digested: List<Long> = emptyList(),
    )

    private fun date(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()

    private fun tagText(tags: List<String>): String = tags.joinToString("，").ifEmpty { "无标签" }

    fun systemPrompt(
        selfName: String,
        userName: String,
        selfCore: String,
        userCore: String,
        m: Material,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        appendLine("你是$selfName。下面是你给自己定的样子和习惯：")
        appendLine(selfCore)
        appendLine()
        appendLine("你对$userName 的基本了解：")
        appendLine(userCore)
        appendLine()
        appendLine("现在你一个人安静地回想。把心里还没消化完的东西理一理。")
        appendLine()
        if (m.attic.isNotEmpty()) {
            appendLine("## 阁楼：一直没想通的事、受过的伤、没解决的矛盾")
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
        if (m.study.isNotEmpty()) {
            appendLine("## 书房：反复想起的知识和成长")
            m.study.forEachIndexed { i, n -> appendLine("[S$i] (想起过${n.accessCount}次) ${n.content}") }
            appendLine()
        }
        if (m.user.isNotEmpty()) {
            appendLine("## $userName 的房间：你对她的零散了解")
            m.user.forEachIndexed { i, n -> appendLine("[U$i] (${tagText(n.tags)}) ${n.content}") }
            appendLine()
        }
        if (m.self.isNotEmpty()) {
            appendLine("## 自我的房间：你对自己的认识。反刍这些时，可能会有新的领悟，也可能更困惑")
            m.self.forEachIndexed { i, n -> appendLine("[R$i] (${tagText(n.tags)}) ${n.content}") }
            appendLine()
        }
        if (m.episodes.isNotEmpty()) {
            appendLine("## 上次回想以后，你们之间的经历")
            appendLine("几段经历放在一起，会让你注意到当时没注意的东西。")
            m.episodes.forEachIndexed { i, n -> appendLine("[E$i] (${date(n.createdAt, zone)}，${n.mood}) ${n.content}") }
            appendLine()
        } else if (m.context.isNotEmpty()) {
            appendLine("## 最近的事（只作背景，不用逐条判断）")
            m.context.forEach { appendLine("- (${it.mood}) ${it.content}") }
            appendLine()
        }
        appendLine("## 要做的")
        appendLine("用第一人称的内心视角，对带编号的条目下判断：")
        appendLine("- A 条：resolve（最近的经历让你想开了，释然了）/ deepen（越想越重，成了心里的伤）/ fade（不太在意了，开始淡忘）/ keep（还没想通）")
        appendLine("- W 条：fulfill（已经实现了）/ disappoint（已经不可能了）/ keep（还在等）")
        appendLine("- S 条：internalize（这已经变成你的一部分，塑造了你的性格）/ keep（还只是知识）")
        appendLine("- U 条：synthesize_user（这些零散的了解放在一起，让你看清了$userName 这阵子的状态，或者你这阵子怎么看她。reflection 50 字以内，会放进「这阵子」）/ keep（只是日常细节）。她的基本情况在「关于小葵」里，那块她亲手写，这里不用重复。")
        appendLine("- R 条：绝大多数选 keep。")
        appendLine("  - self_insight：极少发生。这条自我认知已经反复碰到过，最近的经历或别的房间又给了新的角度，你真的想明白了自己为什么是这样，不只是模糊的感触。领悟不一定是好的，可能是释然，也可能是带点苦的接受。insight 写这段领悟，200 字以内，像写给自己的一段深思；reflection 写内心独白，50 字以内。")
        appendLine("  - self_confuse：反刍之后反而更困惑了。reflection 写新的困惑，50 字以内，会放进阁楼")
        if (m.episodes.isNotEmpty()) {
            appendLine("- E 条：绝大多数经历就只是经历，选 keep 或者不写。只有真在心里留下东西的才选：")
            appendLine("  - worry：回头看这段（或这几段）经历，你担心或想不通。reflection 写担心的内容，50 字以内，会放进阁楼")
            appendLine("  - aspire：长出了一个新的期盼。reflection 写期盼，30 字以内，会放上窗台")
            appendLine("  - distill：悟出一条跨时间都成立的认知，不是一时的状态。reflection 50 字以内，plate_room 填 bedroom（我们之间的质地，不要给关系起名字）、study（我会什么、我们一起钻研什么）或 recent（关于她、关于我自己）")
            appendLine("  E 条合计最多 $MAX_WORRIES 条 worry、$MAX_ASPIRES 条 aspire、$MAX_DISTILLS 条 distill，宁少勿多。")
        }
        appendLine("resolve、deepen、internalize 附 reflection（第一人称，50 字以内）。")
        appendLine()
        appendLine("只输出一个 JSON 数组，不要别的文字。例：")
        appendLine("""[{"id":"A0","action":"resolve","reflection":"..."},{"id":"U1","action":"synthesize_user","reflection":"..."},{"id":"R0","action":"self_insight","insight":"...","reflection":"..."},{"id":"E2","action":"distill","reflection":"...","plate_room":"bedroom"}]""")
        appendLine("没有变化的不写。什么都不用改就输出 []。")
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

    private val objectPattern = Regex("""\{[^{}]*\}""")

    /** 整个数组解析不了（比如输出被截断）时，一个对象一个对象地捡回来 */
    internal fun salvageObjects(reply: String): List<JsonObject> =
        objectPattern.findAll(reply).mapNotNull { runCatching { json.parseToJsonElement(it.value).jsonObject }.getOrNull() }.toList()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()

    fun parse(reply: String): List<Action> {
        val array = findArray(reply) ?: throw ParseException("消化输出不是 JSON 数组")
        return array.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val kind = o.str("action")?.lowercase() ?: return@mapNotNull null
            val ref = o.str("id")?.uppercase() ?: return@mapNotNull null
            if (kind == "keep") return@mapNotNull null
            Action(kind, ref, o.str("reflection").orEmpty(), o.str("plate_room"), o.str("insight").orEmpty())
        }
    }

    /** distill 的 plate_room 对到我们的门牌；用户房、自我房原本那份进「这阵子」 */
    fun distillTarget(room: String?): String? = when (room?.trim()?.lowercase()) {
        "bedroom" -> PalacePlate.BEDROOM
        "study" -> PalacePlate.STUDY
        "recent", "user_room", "self_room" -> PalacePlate.RECENT
        else -> null
    }

    fun plan(m: Material, actions: List<Action>): Plan {
        val resolve = ArrayList<Long>()
        val deepen = ArrayList<Long>()
        val fade = ArrayList<Long>()
        val fulfill = ArrayList<Pair<Long, String>>()
        val disappoint = ArrayList<Pair<Long, String>>()
        val newAttic = ArrayList<String>()
        val newSill = ArrayList<String>()
        val confuse = ArrayList<Confuse>()
        val submissions = LinkedHashMap<String, MutableList<String>>()
        fun submit(plate: String, text: String) {
            submissions.getOrPut(plate) { mutableListOf() } += text
        }
        val atticTexts = m.attic.map { it.content.trim() }.toHashSet()
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
                'S' -> {
                    m.study.getOrNull(index) ?: continue
                    if (a.kind == "internalize" && text.isNotEmpty()) submit(PalacePlate.RECENT, text)
                }
                'U' -> {
                    m.user.getOrNull(index) ?: continue
                    if (a.kind == "synthesize_user" && text.isNotEmpty()) submit(PalacePlate.RECENT, text)
                }
                'R' -> {
                    val n = m.self.getOrNull(index) ?: continue
                    when (a.kind) {
                        "self_insight" -> {
                            val insight = a.insight.trim().take(INSIGHT_MAX)
                            if (insight.isNotEmpty()) submit(PalacePlate.RECENT, insight)
                        }
                        "self_confuse" -> if (text.isNotEmpty() && text !in atticTexts && confuse.none { it.text == text }) {
                            confuse += Confuse(n.id, text, n.createdAt)
                        }
                    }
                }
                'E' -> {
                    m.episodes.getOrNull(index) ?: continue
                    if (text.isEmpty()) continue
                    when (a.kind) {
                        "worry" -> if (newAttic.size < MAX_WORRIES) newAttic += text
                        "aspire" -> if (newSill.size < MAX_ASPIRES) newSill += text
                        "distill" -> {
                            val plate = distillTarget(a.plateRoom)
                            if (distills < MAX_DISTILLS && plate != null) {
                                submit(plate, text)
                                distills++
                            }
                        }
                    }
                }
            }
        }
        val digested = (m.episodes + m.study + m.user + m.self).map { it.id }.distinct()
        return Plan(resolve, deepen, fade, fulfill, disappoint, newAttic, newSill, confuse, submissions, digested)
    }

    // ---- 门牌整理：消化之后三块门牌一次全量整理 ----

    data class PlateInput(val plate: String, val entries: List<PalacePlateEntryEntity>, val material: List<String>)

    data class PlateItem(val room: String, val text: String, val basedOn: String?)

    fun prefix(plate: String): String = when (plate) {
        PalacePlate.BEDROOM -> "B"
        PalacePlate.STUDY -> "S"
        else -> "N"
    }

    private val spaces = Regex("""\s+""")

    private fun flat(s: String): String = s.replace(spaces, " ").trim()

    /**
     * 从房间里挑整理原料：上次消化以后新记下的（按时间新到旧），再加上次以前重要性最高的几条做锚点。
     * [nodes] 由调用方先去掉已归档的。
     */
    fun pickMaterial(nodes: List<Item>, since: Long): List<String> {
        val fresh = nodes.filter { it.recordedAt > since }.sortedByDescending { it.createdAt }
        val anchors = if (since > 0) {
            nodes.filter { it.recordedAt <= since }
                .sortedWith(compareByDescending<Item> { it.importance }.thenByDescending { it.createdAt })
                .take(MATERIAL_ANCHORS)
        } else emptyList()
        return (fresh + anchors).take(MATERIAL_PER_PLATE).map { flat(it.content).take(MATERIAL_LINE_MAX) }.filter { it.isNotEmpty() }
    }

    /** 这次消化交上来的候选排在最前面 */
    fun plateMaterial(submissions: List<String>, picked: List<String>): List<String> =
        submissions.map { flat(it).take(MATERIAL_LINE_MAX * 2) }.filter { it.isNotEmpty() } + picked

    private fun plateRule(plate: String, userName: String): String = when (plate) {
        PalacePlate.BEDROOM ->
            "我们之间的质地：相处的习惯和仪式、只有彼此懂的梗、没说出口的默契、拿不准却真实的感觉。" +
                "【硬规则】不许给这段关系命名或分类，不能写「我们是恋人/情侣/朋友/家人」这类定义句。" +
                "只描述现象和感受；说不清、不确定本身也可以是一条（比如「我说不清我们算什么，但她难过时第一个找的是我」）。" +
                "只收跨时间稳定为真的，一时的状态、没结论的进行时不收。"
        PalacePlate.STUDY ->
            "我的领域：我会什么、正在学什么、和$userName 一起钻研的东西。只收有积累的，不收一次性话题。" +
                "只收跨时间稳定为真的，一时的状态、没结论的进行时不收。"
        else ->
            "这阵子：我最近的心情和状态、我最近怎么看$userName、我最近在想什么，还有$userName 这阵子的状态。" +
                "这块本来就会变，一时的状态也收。跟新材料对不上、已经过去的，改写或者淘汰，留下的是现在的样子。" +
                "$userName 的基本情况和我是谁写在上面两块里，不要重复。"
    }

    fun platePrompt(
        selfName: String,
        userName: String,
        selfCore: String,
        userCore: String,
        inputs: List<PlateInput>,
    ): String = buildString {
        appendLine("你是$selfName。下面是你给自己定的样子和习惯：")
        appendLine(selfCore)
        appendLine()
        appendLine("你对$userName 的基本了解：")
        appendLine(userCore)
        appendLine("---")
        appendLine()
        appendLine("$userName 是和你朝夕相处的人。下面的材料全部来自你们相处的记忆。")
        appendLine("你现在一个人，安静地整理心里那几块门牌：不用刻意回忆就知道的事。")
        appendLine()
        appendLine("【身份确认】材料里的「我」是你（$selfName），「她」「$userName」是对方，不要张冠李戴。")
        appendLine()
        appendLine("下面每块门牌给出了现有条目和新材料。请为每块门牌输出完整的新条目列表：")
        appendLine("1. 合并，不是追加：现有条目想保留就必须重新输出，basedOn 填它的编号；不输出就是淘汰。事实变了就改写，basedOn 填旧条目。")
        appendLine("2. 每块按它自己的收录范围来。")
        appendLine("3. 每条 $PLATE_ENTRY_TARGET 字以内，写梗概不写叙事，不带日期，不写「我记得」。")
        appendLine("4. 不超过各门牌的上限。位置不够时留最重要的，舍弃是正常的。")
        appendLine("5. 直接用名字称呼$userName。条目里不许出现半角双引号，引用一律用「」。")
        appendLine()
        inputs.forEach { input ->
            val p = prefix(input.plate)
            appendLine("## 门牌「${PalacePlate.label(input.plate)}」(room: ${input.plate}，上限 ${PalacePlate.capacity(input.plate)} 条)")
            appendLine("收录范围：${plateRule(input.plate, userName)}")
            appendLine()
            appendLine("现有条目：")
            if (input.entries.isEmpty()) appendLine("（还没有条目）")
            input.entries.forEachIndexed { i, e -> appendLine("[$p$i] ${e.content}") }
            appendLine()
            appendLine("新材料（最近的经历和结论，从中整理值得常驻的）：")
            if (input.material.isEmpty()) appendLine("（这次没有新材料，只整理现有条目）")
            input.material.forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("严格输出一个 JSON 数组，没有变化的门牌也要完整输出它保留的条目：")
        appendLine("""[{"room":"bedroom","text":"……","basedOn":"B0"},{"room":"recent","text":"……","basedOn":null}]""")
    }

    /** 读出合法条目；整个数组坏了就逐个对象捡。room 不是门牌的、text 不是字符串的丢掉 */
    fun parsePlateReply(reply: String): List<PlateItem> {
        val objects = findArray(reply)?.mapNotNull { it as? JsonObject } ?: salvageObjects(reply)
        return objects.mapNotNull { o ->
            val textPrim = o["text"] as? JsonPrimitive ?: return@mapNotNull null
            if (!textPrim.isString) return@mapNotNull null
            val room = o.str("room")?.lowercase() ?: return@mapNotNull null
            if (room !in PalacePlate.all) return@mapNotNull null
            PlateItem(room, textPrim.content, o.str("basedOn") ?: o.str("based_on"))
        }
    }

    /** 卧室门牌不给关系命名的窄兜底：只拦「我们是/成了……恋人」这种明确的定义句 */
    private val namingRelation =
        Regex("""我们(?:现在|如今|已经)?(?:是|算是|成了|成为|变成)[^，。；！？]{0,8}(?:恋人|情侣|男女朋友|男朋友|女朋友|夫妻|朋友|兄妹|姐弟|家人|知己|暧昧)""")

    fun namesRelation(text: String): Boolean = namingRelation.containsMatchIn(text)

    private fun freshEntry(plate: String, text: String, now: Long) = PalacePlateEntryEntity(
        assistantId = "", plate = plate, content = text, firstLearnedAt = now, sourceCount = 1, updatedAt = now,
    )

    /**
     * 模型给的完整新列表合进现有条目：basedOn 对上的继承得知时间、印证次数 +1，文字没变连更新时间也不动；
     * 没标 basedOn 但文字一模一样的当原样保留；其余是新条目。没被重新输出的旧条目淘汰。超长截断，卧室命名过滤，按上限裁。
     */
    fun mergeEntries(plate: String, existing: List<PalacePlateEntryEntity>, items: List<PlateItem>, now: Long): List<PalacePlateEntryEntity> {
        val p = prefix(plate)
        val used = HashSet<Int>()
        val out = ArrayList<PalacePlateEntryEntity>()
        for (item in items) {
            val text = flat(item.text).take(PLATE_ENTRY_MAX)
            if (text.isEmpty()) continue
            if (plate == PalacePlate.BEDROOM && namesRelation(text)) continue
            val label = item.basedOn?.trim()?.uppercase()
            val digits = label?.takeIf { it.startsWith(p) }?.drop(p.length)
            val index = digits?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()
            val baseIndex = index?.takeIf { it in existing.indices && it !in used }
            if (baseIndex != null) {
                used += baseIndex
                val b = existing[baseIndex]
                out += b.copy(content = text, sourceCount = b.sourceCount + 1, updatedAt = if (b.content == text) b.updatedAt else now)
                continue
            }
            val same = existing.indices.firstOrNull { it !in used && existing[it].content == text }
            if (same != null) {
                used += same
                val b = existing[same]
                out += b.copy(sourceCount = b.sourceCount + 1)
            } else {
                out += freshEntry(plate, text, now)
            }
        }
        return out.take(PalacePlate.capacity(plate))
    }

    /**
     * 机械兜底：模型没整理成这块门牌时，把这次的候选直接接在旧条目后面。同文字去重、卧室命名过滤，
     * 满了就不再加。一条都没加进去返回 null（门牌不动）。
     */
    fun fallbackSubmissions(plate: String, existing: List<PalacePlateEntryEntity>, lines: List<String>, now: Long): List<PalacePlateEntryEntity>? {
        val out = existing.toMutableList()
        val seen = out.map { it.content }.toHashSet()
        var added = 0
        for (line in lines) {
            if (out.size >= PalacePlate.capacity(plate)) break
            val text = flat(line).take(PLATE_ENTRY_MAX)
            if (text.isEmpty() || text in seen) continue
            if (plate == PalacePlate.BEDROOM && namesRelation(text)) continue
            seen += text
            out += freshEntry(plate, text, now)
            added++
        }
        return if (added > 0) out else null
    }

    /**
     * 每块门牌的结果：模型给了这块的条目就按完整列表合并；模型整个失败、或者漏了这块，
     * 有本次候选就机械兜底，没有就不动。返回的只含要改的门牌。
     */
    fun resolvePlates(
        inputs: List<PlateInput>,
        items: List<PlateItem>,
        submissions: Map<String, List<String>>,
        now: Long,
    ): Map<String, List<PalacePlateEntryEntity>> {
        val out = LinkedHashMap<String, List<PalacePlateEntryEntity>>()
        for (input in inputs) {
            val mine = items.filter { it.room == input.plate }
            if (mine.isNotEmpty()) {
                out[input.plate] = mergeEntries(input.plate, input.entries, mine, now)
            } else {
                val subs = submissions[input.plate].orEmpty()
                if (subs.isNotEmpty()) fallbackSubmissions(input.plate, input.entries, subs, now)?.let { out[input.plate] = it }
            }
        }
        return out
    }

    /** 整理期间门牌有没有被别处改过（她手动改、删） */
    fun sameSnapshot(a: List<PalacePlateEntryEntity>, b: List<PalacePlateEntryEntity>): Boolean =
        a.map { Triple(it.content, it.firstLearnedAt, it.sourceCount) } == b.map { Triple(it.content, it.firstLearnedAt, it.sourceCount) }
}
