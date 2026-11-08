package me.rerere.rikkahub.jade

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * 赌石规则，照 hual66209-max/dushi-xiaopu 的 v22 页面搬过来（原项目 PolyForm Noncommercial 1.0.0，自己玩，不商用）。
 * 石头的数据格式和页面完全一致，页面和我读写同一份存档。
 */

/** 原版的 mulberry32，和页面里一模一样，同一块石头的松花位置、打灯结果才对得上 */
internal class Mulberry32(seed: Int) {
    private var a = seed

    fun next(): Double {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

internal data class Vec(val x: Double, val y: Double, val z: Double) {
    fun lengthSq() = x * x + y * y + z * z

    fun normalized(): Vec {
        val d = sqrt(lengthSq()).let { if (it == 0.0) 1.0 else it }
        return Vec(x / d, y / d, z / d)
    }

    fun dist2(o: Vec) = (x - o.x) * (x - o.x) + (y - o.y) * (y - o.y) + (z - o.z) * (z - o.z)

    fun dist(o: Vec) = sqrt(dist2(o))

    fun toJson(): JSONObject = JSONObject().put("x", x).put("y", y).put("z", z)
}

internal object JadeEngine {
    val TIER_NAMES = listOf("垃圾铺", "小店", "商铺", "高档行", "拍卖场")
    val UNLOCK = longArrayOf(0, 15000, 20000, 50000, 100000)
    val RESTOCK_FEE = longArrayOf(100, 300, 1000, 3000, 8000)

    /** 一档货架上普通石头的块数，另外还有一块镇店石 */
    const val SHELF_SIZE = 8

    private val P_MIN = doubleArrayOf(200.0, 1000.0, 3000.0, 8000.0, 20000.0)
    private val P_MAX = doubleArrayOf(2000.0, 5000.0, 15000.0, 40000.0, 100000.0)

    private val RIND_COLOR_S = intArrayOf(6, 4, 2, 0, 0, -2, -4, -6)
    private val RIND_FINE_S = intArrayOf(12, 6, 0, -7, -13)
    private val RIND_CRACK_S = intArrayOf(10, 2, -9, -18)
    private val RIND_SONG_S = intArrayOf(-8, 3, 9, 15)
    private val RIND_MANG_S = intArrayOf(-4, 3, 8, 12)

    private val ZHONG_M = doubleArrayOf(0.3, 0.6, 1.0, 1.8, 3.0, 5.5)
    private val ZHONG_GLOW = doubleArrayOf(0.08, 0.25, 0.45, 0.65, 0.82, 1.0)
    private val COLOR_M = doubleArrayOf(0.4, 0.7, 1.0, 1.8, 2.6, 4.2, 6.0, 5.0, 5.6, 6.8, 9.5)
    private val FLAW_CRACK_M = doubleArrayOf(1.0, 0.7, 0.35)
    private val FLAW_COTTON_M = doubleArrayOf(1.0, 0.85, 0.6)
    private val FLAW_SPOT_M = doubleArrayOf(1.0, 0.8)
    private val TORCH_SIG = doubleArrayOf(0.0, 0.2, 0.4, 0.65, 0.85, 1.0, 1.0, 0.9, 0.9, 0.95, 1.0)

    // 稀有色：黄翡 / 蓝水 / 紫罗兰 / 春带彩，按石头所在档次累加解锁
    private const val RARE_YELLOW = 7
    private const val RARE_BLUE = 8
    private const val RARE_VIOLET = 9
    private const val RARE_SPRING = 10
    private val RARE_UNLOCK = listOf(RARE_YELLOW to 1, RARE_BLUE to 2, RARE_VIOLET to 3, RARE_SPRING to 4)
    private val RARE_MULT = mapOf(RARE_YELLOW to 1.5, RARE_BLUE to 2.0, RARE_VIOLET to 2.5, RARE_SPRING to 4.0)

    // v22：先抽内部，权重按铺子档次分行
    private class Weights(val z: IntArray, val c: IntArray, val k: IntArray, val t: IntArray, val s: IntArray)

    private val INNER_W = listOf(
        Weights(intArrayOf(34, 30, 18, 10, 5, 3), intArrayOf(36, 26, 16, 11, 6, 3, 2), intArrayOf(16, 34, 50), intArrayOf(20, 36, 44), intArrayOf(52, 48)),
        Weights(intArrayOf(28, 29, 20, 13, 7, 4), intArrayOf(29, 25, 18, 13, 8, 4, 2), intArrayOf(22, 36, 42), intArrayOf(26, 37, 37), intArrayOf(57, 43)),
        Weights(intArrayOf(22, 27, 22, 15, 8, 5), intArrayOf(23, 25, 20, 16, 9, 5, 3), intArrayOf(28, 38, 34), intArrayOf(32, 37, 31), intArrayOf(61, 39)),
        Weights(intArrayOf(20, 26, 24, 16, 9, 5), intArrayOf(20, 24, 22, 17, 10, 5, 2), intArrayOf(34, 38, 28), intArrayOf(38, 36, 26), intArrayOf(64, 36)),
        Weights(intArrayOf(18, 25, 25, 18, 9, 5), intArrayOf(18, 23, 24, 19, 10, 4, 2), intArrayOf(36, 38, 26), intArrayOf(42, 35, 23), intArrayOf(68, 32)),
    )
    private val JACKPOT_P = doubleArrayOf(0.032, 0.032, 0.032, 0.032, 0.045)
    private val VALUE_K = doubleArrayOf(407.0, 1039.0, 2501.0, 6094.0, 15128.0)
    private val VALUE_KZ = doubleArrayOf(900.0, 1995.0, 5420.0, 13544.0, 34297.0)

    // 皮壳跟着内部走的偏移量
    private val D_FINE = doubleArrayOf(3.5, 1.6, -0.6, -1.9, -2.6)
    private val D_CRACK = doubleArrayOf(-4.0, -0.8, 1.9, 2.9)
    private val D_SONG = doubleArrayOf(-4.8, -1.6, 1.6, 4.8)

    private val STONE_NAMES = listOf("老坑料", "河磨石", "山石料", "水石料", "半山半水", "角石料", "砾石料", "原生料")

    private var uid: Long = System.currentTimeMillis() % 100000

    private fun rnd() = Random.nextDouble()

    private fun u(a: Double, b: Double) = a + rnd() * (b - a)

    /** 和 JS 的 Math.round 一样：.5 往上进 */
    internal fun jsRound(x: Double) = floor(x + 0.5)

    private fun pickW(weights: DoubleArray): Int {
        val s = weights.sum()
        var x = rnd() * s
        for (i in weights.indices) {
            x -= weights[i]
            if (x <= 0) return i
        }
        return weights.size - 1
    }

    private fun pickW(weights: IntArray): Int = pickW(DoubleArray(weights.size) { weights[it].toDouble() })

    private fun rareChance(r: Double) = when {
        r >= 6 -> 0.30
        r >= 2.5 -> 0.12
        r >= 1 -> 0.05
        else -> 0.0
    }

    private fun rollRareColor(ti: Int, r: Double): Int {
        val chance = rareChance(r)
        if (chance <= 0 || rnd() >= chance) return -1
        val pool = RARE_UNLOCK.filter { ti >= it.second }.filter { it.first != RARE_SPRING || r >= 2.5 }
        if (pool.isEmpty()) return -1
        val weights = DoubleArray(pool.size) { if (pool[it].first == RARE_SPRING) 0.5 else 1.0 }
        return pool[pickW(weights)].first
    }

    private fun scoreRind(r: IntArray): Int {
        val s = 50 + RIND_COLOR_S[r[0]] + RIND_FINE_S[r[1]] + RIND_CRACK_S[r[2]] + RIND_SONG_S[r[3]] + RIND_MANG_S[r[4]]
        return s.coerceIn(5, 98)
    }

    // 很小概率直接抽到玻璃种满绿 / 帝王绿的好料
    private fun sampleInner(ti: Int, zhen: Boolean): IntArray {
        if (rnd() < JACKPOT_P[ti]) {
            return intArrayOf(5, if (rnd() < 0.5) 5 else 6, pickW(intArrayOf(80, 20, 0)), pickW(intArrayOf(75, 25, 0)), 0)
        }
        val w = INNER_W[if (zhen) min(4, ti + 1) else ti]
        return intArrayOf(pickW(w.z), pickW(w.c), pickW(w.k), pickW(w.t), pickW(w.s))
    }

    private fun shifted(base: IntArray, d: DoubleArray, amt: Double) =
        DoubleArray(base.size) { max(1.0, base[it] + d[it] * amt) }

    // 皮壳跟着内部走：色好松花蟒带倾向明显，种老皮倾向细，裂重皮上裂也多。只是倾向
    private fun drawRind(inner: IntArray, zhen: Boolean): IntArray {
        val cB = inner[1] - 2.0
        val zB = inner[0] - 2.0
        val kB = inner[2] - 1.0
        return if (zhen) {
            intArrayOf(
                pickW(intArrayOf(26, 22, 16, 12, 10, 6, 5, 3)),
                pickW(shifted(intArrayOf(42, 32, 16, 7, 3), D_FINE, zB)),
                pickW(shifted(intArrayOf(55, 28, 11, 6), D_CRACK, kB)),
                pickW(shifted(intArrayOf(4, 20, 34, 42), D_SONG, cB * 0.8)),
                pickW(shifted(intArrayOf(6, 22, 34, 38), D_SONG, cB * 0.5)),
            )
        } else {
            intArrayOf(
                floor(rnd() * 8).toInt(),
                pickW(shifted(intArrayOf(16, 26, 28, 18, 12), D_FINE, zB)),
                pickW(shifted(intArrayOf(22, 34, 26, 18), D_CRACK, kB)),
                pickW(shifted(intArrayOf(38, 30, 20, 12), D_SONG, cB * 0.8)),
                pickW(shifted(intArrayOf(40, 28, 19, 13), D_SONG, cB * 0.5)),
            )
        }
    }

    internal fun innerJson(i: IntArray): JSONObject = JSONObject()
        .put("zhong", i[0]).put("color", i[1]).put("crack", i[2]).put("cotton", i[3]).put("spot", i[4])

    private fun pocketJson(d: Vec, radius: Double, color: Int, zhong: Int): JSONObject = JSONObject()
        .put("x", d.x).put("y", d.y).put("z", d.z).put("radius", radius).put("color", color).put("zhong", zhong)

    /** 生成一块石头（v22）：先抽内部，皮壳跟着内部走，标价只看皮壳和档次，价值按内部品质算 */
    fun genStone(ti: Int, zhen: Boolean): JSONObject {
        uid += 1
        val seed = uid * 7919 + floor(rnd() * 1e9).toLong()
        val weight = jsRound(u(0.6, 4.8) * 10) / 10
        val inner = sampleInner(ti, zhen)
        val rind = drawRind(inner, zhen)
        val score = scoreRind(rind)

        val lp = ln(P_MIN[ti]) + (ln(P_MAX[ti]) - ln(P_MIN[ti])) * rnd()
        var p = exp(lp) * (0.72 + (score / 100.0) * 0.56)
        p = max(P_MIN[ti], min(P_MAX[ti], p))
        if (zhen) p = P_MAX[ti] * u(1.5, 2.3)
        val price = max(50.0, jsRound(p / 10) * 10)

        val med = sqrt(P_MIN[ti] * P_MAX[ti])
        val raw = (if (zhen) VALUE_KZ[ti] else VALUE_K[ti]) * weight *
            qualityOf(inner).pow(0.65) * (price / med).pow(0.35) * u(0.9, 1.1)
        val rare = rollRareColor(ti, raw / price)
        var value = raw
        if (rare >= 0) {
            inner[1] = rare
            value = raw * RARE_MULT.getValue(rare)
        }
        if (value > price * 24) value = price * 24
        // 裂纹封顶：细裂最高到大涨，大裂贯穿最高只到小亏
        val crackCap = doubleArrayOf(Double.POSITIVE_INFINITY, 3.9, 0.8)[inner[2]]
        if (value > price * crackCap) value = price * crackCap
        val finalValue = max(20.0, jsRound(value)).toLong()

        val name = STONE_NAMES[floor(rnd() * STONE_NAMES.size).toInt()] + "·" + (seed % 997 + 1) + "号"
        val torchLie = rnd() < 0.20

        // 局部团块：开窗、磨只能看到的那一小片
        val prng = Mulberry32((seed * 41 + 9).toInt())
        fun prandDir(): Vec {
            val th = acos(-1 + prng.next() * 2)
            val ph = prng.next() * PI * 2
            return Vec(sin(th) * cos(ph), cos(th), sin(th) * sin(ph))
        }
        val pocket: JSONObject? = if (inner[1] <= 2 && rnd() < 0.40) {
            val d = prandDir()
            pocketJson(d, u(0.45, 0.65), 3 + pickW(intArrayOf(30, 42, 28)), min(5, inner[0] + 1 + floor(rnd() * 2).toInt()))
        } else if (inner[1] >= 4 && rnd() < 0.40) {
            val d = prandDir()
            pocketJson(d, u(0.45, 0.65), floor(rnd() * 2).toInt(), max(0, inner[0] - 1 - floor(rnd() * 2).toInt()))
        } else if (rnd() < 0.25) {
            val d = prandDir()
            val shift = if (rnd() < 0.5) -2 else 2
            pocketJson(
                d, u(0.45, 0.65), (inner[1] + shift).coerceIn(0, 5),
                (inner[0] + if (shift > 0) 1 else -1).coerceIn(0, 5)
            )
        } else null

        return JSONObject().apply {
            put("id", "s${seed}_$uid")
            put("seed", seed)
            put("name", name)
            put(
                "rind", JSONObject()
                    .put("color", rind[0]).put("fine", rind[1]).put("crack", rind[2])
                    .put("song", rind[3]).put("mang", rind[4])
            )
            put("S", score)
            put("weight", weight)
            put("price", price.toLong())
            put("inner", innerJson(inner))
            put("value", finalValue)
            put("tier", ti)
            put("zhen", zhen)
            put("windowed", false)
            put("grindStep", 0)
            put("winDir", JSONObject.NULL)
            put("sliceDir", JSONObject.NULL)
            put("torchLie", torchLie)
            put("pocket", pocket ?: JSONObject.NULL)
        }
    }

    /** 一档货架：第一块是镇店石，后面 8 块编号 1 到 8（编号页面不认，原样带着） */
    fun genShop(ti: Int): JSONArray {
        val out = JSONArray().put(genStone(ti, true))
        for (i in 1..SHELF_SIZE) out.put(genStone(ti, false).put("shelfNo", i))
        return out
    }

    // ---- 结算 ----

    internal fun qualityOf(li: IntArray) =
        ZHONG_M[li[0]] * COLOR_M[li[1]] * FLAW_CRACK_M[li[2]] * FLAW_COTTON_M[li[3]] * FLAW_SPOT_M[li[4]]

    /** 五档：0 垮 / 1 小亏 / 2 小赚 / 3 大涨 / 4 真爆（真爆还要玻璃种加满绿以上） */
    fun outcomeOf(ratio: Double, inner: JSONObject): Int = when {
        ratio >= 6 && inner.optInt("zhong") == 5 && inner.optInt("color") >= 5 -> 4
        ratio < 0.5 -> 0
        ratio < 1 -> 1
        ratio < 2.5 -> 2
        else -> 3
    }

    /** 慢慢磨每一步的磨工费：标价的 1%，¥20 起、¥500 封顶 */
    fun grindFee(price: Long): Long = max(20L, min(500L, (jsRound(price * 0.01 / 10) * 10).toLong()))

    // ---- 读石头 ----

    private fun seedOf(st: JSONObject) = st.getLong("seed")

    private fun songOf(st: JSONObject) = st.getJSONObject("rind").getInt("song")

    private fun innerOf(st: JSONObject): IntArray = st.getJSONObject("inner").let {
        intArrayOf(it.getInt("zhong"), it.getInt("color"), it.getInt("crack"), it.getInt("cotton"), it.getInt("spot"))
    }

    private class Pocket(val at: Vec, val radius: Double, val color: Int, val zhong: Int)

    private fun pocketOf(st: JSONObject): Pocket? = st.optJSONObject("pocket")?.let {
        Pocket(Vec(it.getDouble("x"), it.getDouble("y"), it.getDouble("z")), it.getDouble("radius"), it.getInt("color"), it.getInt("zhong"))
    }

    private fun localInnerAt(st: JSONObject, dir: Vec?): IntArray {
        val li = innerOf(st)
        val p = pocketOf(st)
        if (p != null && dir != null && dir.dist(p.at) < p.radius) {
            li[0] = p.zhong
            li[1] = p.color
        }
        return li
    }

    private fun qNorm(q: Double) = (ln(q + 0.05) - ln(0.05)) / (ln(20.0) - ln(0.05))

    private fun songSpots(st: JSONObject): List<Vec> {
        val rng = Mulberry32((seedOf(st) * 3 + 11).toInt())
        fun u2(a: Double, b: Double) = a + rng.next() * (b - a)
        val count = intArrayOf(0, 6, 13, 24)[songOf(st)]
        return List(count) {
            val th = acos(u2(-1.0, 1.0))
            val ph = u2(0.0, PI * 2)
            u2(0.05, 0.13)
            Vec(sin(th) * cos(ph), cos(th), sin(th) * sin(ph))
        }
    }

    // ---- 打灯 ----

    private class Torch(val level: Double, val colored: Boolean, val label: String)

    private fun torchAt(st: JSONObject, local: Vec): Torch {
        val dir = local.normalized()
        val li = localInnerAt(st, dir)
        var songBoost = songOf(st) * 0.09
        for (p in songSpots(st)) {
            val d = dir.dist(p)
            if (d < 0.45) songBoost = max(songBoost, 0.34 * (1 - d / 0.45))
        }
        val seed = seedOf(st)
        val rng = Mulberry32(
            (seed + floor(local.x * 97) + floor(local.y * 57) * 3 + floor(local.z * 37) * 7).toLong().toInt()
        )
        val thickness = 0.75 + 0.25 * sin(local.x * 3.1 + seed % 5) * cos(local.y * 2.3)
        var sig = TORCH_SIG.getOrElse(li[1]) { 0.0 }
        var glow = ZHONG_GLOW[li[0]]
        if (st.optBoolean("torchLie")) {
            sig = if (sig > 0.05) 0.0 else 0.9
            glow = 1.02 - glow
        }
        val level = ((glow * 0.8 + 0.12 + songBoost * 0.3) * thickness * (0.78 + rng.next() * 0.44)).coerceIn(0.0, 1.0)
        val colored = (sig * 0.85 + songBoost * 0.6) * thickness * (0.78 + rng.next() * 0.44) > 0.30
        val label = when {
            level > 0.6 -> "强"
            level > 0.38 -> "中"
            level > 0.18 -> "弱"
            else -> "发暗不透"
        }
        return Torch(level, colored, label)
    }

    /** 我打灯、擦窗用的三个点：顶、腰，第三个点有松花照松花，没有照底 */
    private fun samplePoints(st: JSONObject): List<Pair<String, Vec>> {
        val top = Vec(0.0, 1.0, 0.0)
        val waist = Vec(1.0, 0.0, 0.0)
        val songs = songSpots(st)
        val third = if (songs.isNotEmpty()) {
            "松花" to songs.minBy { it.dist2(waist) }
        } else {
            "底" to Vec(0.0, -1.0, 0.0)
        }
        return listOf("顶" to top, "腰" to waist, third)
    }

    fun sampleLight(st: JSONObject): JSONArray {
        val pocket = pocketOf(st)
        val out = JSONArray()
        for ((name, point) in samplePoints(st)) {
            val r = torchAt(st, point)
            val dir = point.normalized()
            val hit = pocket != null && dir.dist(pocket.at) < pocket.radius
            out.put(
                JSONObject()
                    .put("name", name)
                    .put("level", if (r.label == "发暗不透") "暗" else r.label)
                    .put("colored", r.colored)
                    .put("hit", hit)
            )
        }
        return out
    }

    // ---- 擦窗、磨 ----

    /** 和页面一样：只按窗口那一小块的局部种色估价，擦在松花附近略加分 */
    fun windowResult(st: JSONObject, name: String): JSONObject {
        val wd = samplePoints(st).firstOrNull { it.first == name }?.second ?: error("invalid position")
        val noise = u(-0.12, 0.14)
        var songBonus = 0.0
        for (p in songSpots(st)) {
            val d = wd.dist(p)
            if (d < 0.5) songBonus = max(songBonus, 0.10 * (1 - d / 0.5))
        }
        val li = localInnerAt(st, wd)
        val patchQ = (qNorm(qualityOf(li)) + noise + songBonus).coerceIn(0.0, 1.0)
        val price = st.getDouble("price")
        val offer = max(30.0, jsRound(price * (0.25 + patchQ * 1.6) * u(0.9, 1.1) / 10) * 10).toLong()
        return JSONObject().put("dir", wd.toJson()).put("inner", innerJson(li)).put("offer", offer)
    }

    private fun windowDirFor(st: JSONObject): Vec {
        st.optJSONObject("winDir")?.let { w ->
            val v = Vec(w.optDouble("x", 0.0), w.optDouble("y", 0.0), w.optDouble("z", 0.0))
            if (v.lengthSq() > 0.01) return v.normalized()
        }
        val rng = Mulberry32((seedOf(st) * 7 + 5).toInt())
        fun u2(a: Double, b: Double) = a + rng.next() * (b - a)
        val th = acos(u2(-0.2, 0.85))
        val ph = u2(0.0, PI * 2)
        return Vec(sin(th) * cos(ph), cos(th), sin(th) * sin(ph)).normalized()
    }

    /** 磨开第 1 步看局部种水，第 2 步看局部颜色，报价只按磨口这一处估 */
    fun grindResult(st: JSONObject, step: Int): JSONObject {
        require(step == 0 || step == 1) { "invalid grind step" }
        val wd = windowDirFor(st)
        val li = localInnerAt(st, wd)
        val q = if (step == 0) ZHONG_M[li[0]] * 1.0 * 0.8 else ZHONG_M[li[0]] * COLOR_M[li[1]] * 0.8
        val pq = qNorm(q).coerceIn(0.0, 1.0)
        val lo = if (step == 0) 0.85 else 0.92
        val hi = if (step == 0) 1.15 else 1.08
        val offer = max(30.0, jsRound(st.getDouble("price") * (0.25 + pq * 1.6) * u(lo, hi) / 10) * 10).toLong()
        return JSONObject().put("dir", wd.toJson()).put("inner", innerJson(li)).put("offer", offer)
    }
}
