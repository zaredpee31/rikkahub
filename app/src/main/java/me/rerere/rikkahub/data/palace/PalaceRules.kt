package me.rerere.rikkahub.data.palace

import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 记忆宫殿的房间、门牌、情绪和链接规则。
 *
 * 参数照 SullyOS 记忆宫殿（参考版本 5c4aa52）的设计取值，用 Kotlin 重写，没有复制原代码。
 * 只有门牌 [PalacePlate] 一处按小葵的要求改过：用户房、自我房的核心背景不在这里，
 * 放在 memory_plate 表里整块保存，不设上限、自动整理不能动；另外多了「这阵子」一块。
 */
object PalaceRoom {
    const val LIVING = "living_room"
    const val BEDROOM = "bedroom"
    const val STUDY = "study"
    const val USER = "user_room"
    const val SELF = "self_room"
    const val ATTIC = "attic"
    const val WINDOWSILL = "windowsill"

    val all = listOf(LIVING, BEDROOM, STUDY, USER, SELF, ATTIC, WINDOWSILL)

    fun isValid(room: String?): Boolean = room != null && room in all

    /** 认不出的房间按客厅算，只影响打分，不改写原记录 */
    fun normalize(room: String?): String = if (isValid(room)) room!! else LIVING

    fun label(room: String): String = when (room) {
        LIVING -> "客厅"
        BEDROOM -> "卧室"
        STUDY -> "书房"
        USER -> "小葵的房间"
        SELF -> "自我的房间"
        ATTIC -> "阁楼"
        WINDOWSILL -> "窗台"
        else -> room
    }

    fun description(room: String): String = when (room) {
        LIVING -> "日常闲聊、近期互动"
        BEDROOM -> "亲密情感、深层羁绊"
        STUDY -> "工作学习、技能成长"
        USER -> "她的个人信息、习惯、家人朋友"
        SELF -> "我自己的认同和变化"
        ATTIC -> "没消化的困惑、伤害"
        WINDOWSILL -> "期盼、目标、憧憬"
        else -> ""
    }

    /** 容量上限，null 是不限。只有客厅有 */
    fun capacity(room: String): Int? = if (room == LIVING) 200 else null

    /** 每小时的衰减底数，null 是永不衰减 */
    fun decayRate(room: String): Double? = when (normalize(room)) {
        LIVING -> 0.9972
        BEDROOM, STUDY, USER -> 0.9995
        else -> null
    }

    /** 衰减到原重要性的这个比例就不再往下掉 */
    fun floorRatio(room: String): Double = when (normalize(room)) {
        LIVING -> 0.80
        BEDROOM, STUDY, USER -> 0.90
        else -> 1.0
    }

    data class Weights(val similarity: Double, val recency: Double, val importance: Double)

    fun weights(room: String): Weights = when (normalize(room)) {
        LIVING -> Weights(0.50, 0.30, 0.20)
        BEDROOM -> Weights(0.60, 0.10, 0.30)
        ATTIC -> Weights(0.70, 0.00, 0.30)
        else -> Weights(0.55, 0.15, 0.30)
    }
}

/**
 * 门牌里按条目存的三块。用户房、自我房的核心背景不在这里（见 MemoryPlateEntity）。
 */
object PalacePlate {
    const val BEDROOM = "bedroom"
    const val STUDY = "study"

    /** 认知消化产出的阶段性体会，可以随时间变化 */
    const val RECENT = "recent"

    val all = listOf(BEDROOM, STUDY, RECENT)

    fun capacity(plate: String): Int = when (plate) {
        BEDROOM -> 10
        STUDY -> 8
        RECENT -> 10
        else -> 8
    }

    fun label(plate: String): String = when (plate) {
        BEDROOM -> "我们之间"
        STUDY -> "我的领域"
        RECENT -> "这阵子"
        else -> plate
    }
}

/** Russell 情绪环：效价 v（-1 痛苦到 1 愉悦）、唤醒度 a（-1 平静到 1 激烈） */
data class EmotionVA(val v: Double, val a: Double) {
    val magnitude: Double get() = hypot(v, a)
    fun distanceTo(other: EmotionVA): Double = hypot(v - other.v, a - other.a)

    companion object {
        val NEUTRAL = EmotionVA(0.0, 0.0)
    }
}

object PalaceMood {
    /** 提取时让模型从这 12 种里选 */
    val extractionMoods = listOf(
        "happy", "sad", "angry", "anxious", "tender", "excited",
        "peaceful", "confused", "hurt", "grateful", "nostalgic", "neutral",
    )

    private val table: Map<String, EmotionVA> = mapOf(
        "happy" to EmotionVA(0.7, 0.5),
        "sad" to EmotionVA(-0.7, -0.5),
        "angry" to EmotionVA(-0.7, 0.8),
        "anxious" to EmotionVA(-0.6, 0.7),
        "tender" to EmotionVA(0.6, -0.2),
        "excited" to EmotionVA(0.8, 0.8),
        "peaceful" to EmotionVA(0.5, -0.6),
        "confused" to EmotionVA(-0.2, 0.2),
        "hurt" to EmotionVA(-0.7, 0.3),
        "grateful" to EmotionVA(0.6, 0.3),
        "nostalgic" to EmotionVA(0.2, -0.3),
        "neutral" to EmotionVA(0.0, 0.0),
        "lonely" to EmotionVA(-0.6, -0.3),
        "disappointed" to EmotionVA(-0.6, -0.2),
        "proud" to EmotionVA(0.7, 0.4),
        "relieved" to EmotionVA(0.5, -0.3),
        "开心" to EmotionVA(0.7, 0.5),
        "难过" to EmotionVA(-0.7, -0.5),
        "生气" to EmotionVA(-0.7, 0.8),
        "焦虑" to EmotionVA(-0.6, 0.7),
        "温柔" to EmotionVA(0.6, -0.2),
        "平静" to EmotionVA(0.5, -0.6),
        "委屈" to EmotionVA(-0.7, 0.3),
        "怀念" to EmotionVA(0.2, -0.3),
        "孤独" to EmotionVA(-0.6, -0.3),
    )

    fun normalize(mood: String?): String {
        val key = mood?.trim()?.lowercase().orEmpty()
        return if (key in extractionMoods) key else if (key.isEmpty()) "neutral" else key.take(20)
    }

    /** 记录里有坐标就用坐标，没有就按情绪词查表，查不到当中性 */
    fun va(mood: String?, valence: Float? = null, arousal: Float? = null): EmotionVA {
        if (valence != null && arousal != null && valence.isFinite() && arousal.isFinite()) {
            return EmotionVA(valence.toDouble().coerceIn(-1.0, 1.0), arousal.toDouble().coerceIn(-1.0, 1.0))
        }
        val key = mood?.trim().orEmpty()
        return table[key] ?: table[key.lowercase()] ?: EmotionVA.NEUTRAL
    }
}

object PalaceLinkType {
    const val TEMPORAL = "temporal"
    const val EMOTIONAL = "emotional"
    const val CAUSAL = "causal"
    const val PERSON = "person"
    const val METAPHOR = "metaphor"

    val all = listOf(TEMPORAL, EMOTIONAL, CAUSAL, PERSON, METAPHOR)

    /** 交给模型判断的三种 */
    val deep = listOf(CAUSAL, PERSON, METAPHOR)

    /** 叙事型：先顺着时间、人物想起来。小葵选的 */
    val narrativeWeights: Map<String, Double> = mapOf(
        TEMPORAL to 1.0, PERSON to 0.8, CAUSAL to 0.4, EMOTIONAL to 0.3, METAPHOR to 0.2,
    )
}

/** 打分、巩固、链接、扩散这些不碰数据库的计算，全部放这里方便测试 */
object PalaceMath {
    const val HOUR_MS = 3_600_000L
    const val TEMPORAL_WINDOW_MS = 24 * HOUR_MS

    const val VECTOR_WEIGHT = 0.85
    const val BM25_WEIGHT = 0.15
    const val RECENCY_DECAY = 0.999
    const val FAMILIARITY_WEIGHT = 0.05

    const val VECTOR_MIN_SIMILARITY = 0.3
    const val CANDIDATES_PER_SOURCE = 30
    const val TOP_K = 15
    const val MAX_INJECTED = 12
    const val MAX_SPREAD = 5
    const val SPREAD_DECAY = 0.3
    const val CO_ACTIVATION_STEP = 0.05
    const val DUPLICATE_SIMILARITY = 0.9

    const val PRIMING_MAX_BOOST = 1.3
    const val PRIMING_RADIUS = 0.5
    const val RUMINATION_TENDENCY = 0.3

    private const val EMOTIONAL_LINK_DIST = 0.35
    private const val EMOTIONAL_MIN_MAGNITUDE = 0.2

    private fun hours(from: Long, now: Long): Double = (now - from).toDouble() / HOUR_MS

    /** 重要性按房间衰减，掉到下限为止；不衰减的房间原样返回 */
    fun effectiveImportance(importance: Int, room: String, createdAt: Long, now: Long): Double {
        val rate = PalaceRoom.decayRate(room) ?: return importance.toDouble()
        val h = hours(createdAt, now)
        if (h <= 0) return importance.toDouble()
        val decayed = importance * rate.pow(h)
        return max(decayed, importance * PalaceRoom.floorRatio(room))
    }

    /** 客厅里的记忆什么时候搬进卧室 */
    fun shouldPromote(room: String, importance: Int, createdAt: Long, accessCount: Int, now: Long): Boolean {
        if (room != PalaceRoom.LIVING) return false
        if (importance >= 8) return true
        if (importance >= 6 && hours(createdAt, now) >= 24) return true
        return accessCount >= 3
    }

    data class EvictCandidate(val id: Long, val importance: Int, val createdAt: Long)

    /** 客厅超出容量时，有效重要性最低的那几条（之后挪进阁楼，不删除） */
    fun pickEvictions(living: List<EvictCandidate>, now: Long): List<Long> {
        val cap = PalaceRoom.capacity(PalaceRoom.LIVING) ?: return emptyList()
        if (living.size <= cap) return emptyList()
        return living
            .sortedWith(compareBy<EvictCandidate> { effectiveImportance(it.importance, PalaceRoom.LIVING, it.createdAt, now) }.thenBy { it.createdAt })
            .take(living.size - cap)
            .map { it.id }
    }

    fun familiarityBonus(accessCount: Int): Double {
        val n = max(0, accessCount - 1)
        if (n == 0) return 0.0
        return min(1.0, n.toDouble().pow(0.3) / 4)
    }

    data class ScoreInput(
        val room: String,
        val importance: Int,
        val createdAt: Long,
        val lastAccessedAt: Long,
        val accessCount: Int,
        val vectorSimilarity: Double,
        /** 已经除以本轮最高分，0 到 1 */
        val bm25Normalized: Double,
    )

    /** 混合相似度 + 时近 + 重要性，按房间权重合成；太久远时把时近的份额分给另外两项 */
    fun score(input: ScoreInput, now: Long): Double {
        val hybrid = VECTOR_WEIGHT * input.vectorSimilarity + BM25_WEIGHT * input.bm25Normalized
        val recency = RECENCY_DECAY.pow(max(0.0, hours(input.lastAccessedAt, now)))
        val importance = effectiveImportance(input.importance, input.room, input.createdAt, now) / 10.0
        val w = PalaceRoom.weights(input.room)
        var sim = w.similarity
        var rec = w.recency
        var imp = w.importance
        if (rec > 0 && recency < 0.1) {
            sim += rec / 2
            imp += rec / 2
            rec = 0.0
        }
        return sim * hybrid + rec * recency + imp * importance + FAMILIARITY_WEIGHT * familiarityBonus(input.accessCount)
    }

    /** 和当前情绪越近加得越多，最多乘 1.3；当前情绪中性就不加 */
    fun primingBoost(memory: EmotionVA, current: EmotionVA?): Double {
        if (current == null || (current.v == 0.0 && current.a == 0.0)) return 1.0
        val d = memory.distanceTo(current)
        if (d >= PRIMING_RADIUS) return 1.0
        return 1 + (PRIMING_MAX_BOOST - 1) * (1 - d / PRIMING_RADIUS)
    }

    /** 两条都有明显情绪且足够接近时连情绪链，强度 0.25 到 0.55；否则 0 */
    fun emotionalLinkStrength(a: EmotionVA, b: EmotionVA): Double {
        if (a.magnitude < EMOTIONAL_MIN_MAGNITUDE || b.magnitude < EMOTIONAL_MIN_MAGNITUDE) return 0.0
        val d = a.distanceTo(b)
        if (d >= EMOTIONAL_LINK_DIST) return 0.0
        return 0.25 + (0.55 - 0.25) * (1 - d / EMOTIONAL_LINK_DIST)
    }

    fun withinTemporalWindow(a: Long, b: Long): Boolean = kotlin.math.abs(a - b) < TEMPORAL_WINDOW_MS

    data class Edge(val a: Long, val b: Long, val type: String, val strength: Double)

    /**
     * 从已经选中的记忆顺着链接多带几条。每个邻居取能到它的最高激活分，
     * 已经选中的不重复带，最多 [maxExpand] 条。
     */
    fun spread(
        seeds: Map<Long, Double>,
        edges: List<Edge>,
        weights: Map<String, Double> = PalaceLinkType.narrativeWeights,
        maxExpand: Int = MAX_SPREAD,
    ): List<Pair<Long, Double>> {
        val activated = HashMap<Long, Double>()
        for (edge in edges) {
            for ((from, to) in listOf(edge.a to edge.b, edge.b to edge.a)) {
                val seedScore = seeds[from] ?: continue
                if (to in seeds) continue
                val s = seedScore * edge.strength * (weights[edge.type] ?: 0.2) * SPREAD_DECAY
                if (s > (activated[to] ?: 0.0)) activated[to] = s
            }
        }
        return activated.entries.sortedByDescending { it.value }.take(maxExpand).map { it.key to it.value }
    }

    /** 反刍的概率：倾向 × 0.2 */
    fun ruminationProbability(tendency: Double = RUMINATION_TENDENCY): Double = min(tendency, 1.0) * 0.2

    fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }
}

/** 关键词检索。英文数字按整词，中文按相邻两个字，单个汉字的片段保留单字 */
object PalaceBm25 {
    private const val K1 = 1.2
    private const val B = 0.75
    private val latin = Regex("[A-Za-z0-9]+")
    private val dropped = Regex("[\\s\\p{P}\\p{S}]")

    fun tokenize(text: String): List<String> {
        val out = ArrayList<String>()
        var last = 0
        fun cjk(segment: String) {
            val cleaned = segment.replace(dropped, "")
            if (cleaned.length == 1) out += cleaned
            for (i in 0 until cleaned.length - 1) out += cleaned.substring(i, i + 2)
        }
        for (m in latin.findAll(text)) {
            cjk(text.substring(last, m.range.first))
            out += m.value.lowercase()
            last = m.range.last + 1
        }
        cjk(text.substring(last))
        return out
    }

    /** 返回 (id, 分数)，分数从高到低，只含命中的 */
    fun search(query: String, docs: List<Pair<Long, String>>, topK: Int = PalaceMath.CANDIDATES_PER_SOURCE): List<Pair<Long, Double>> {
        if (docs.isEmpty()) return emptyList()
        val q = tokenize(query).distinct()
        if (q.isEmpty()) return emptyList()
        val tokens = docs.map { tokenize(it.second) }
        val avg = tokens.sumOf { it.size }.toDouble() / tokens.size
        if (avg == 0.0) return emptyList()
        val sets = tokens.map { it.toHashSet() }
        val idf = q.associateWith { t ->
            val df = sets.count { t in it }
            ln((docs.size - df + 0.5) / (df + 0.5) + 1)
        }
        val results = ArrayList<Pair<Long, Double>>()
        docs.forEachIndexed { i, (id, _) ->
            val dl = tokens[i].size
            if (dl == 0) return@forEachIndexed
            val tf = tokens[i].groupingBy { it }.eachCount()
            var score = 0.0
            for (t in q) {
                val f = tf[t] ?: continue
                score += idf.getValue(t) * (f * (K1 + 1)) / (f + K1 * (1 - B + B * dl / avg))
            }
            if (score > 0) results += id to score
        }
        return results.sortedByDescending { it.second }.take(topK)
    }
}
