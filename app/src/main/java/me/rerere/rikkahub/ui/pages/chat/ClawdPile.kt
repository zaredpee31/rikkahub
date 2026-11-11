package me.rerere.rikkahub.ui.pages.chat

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// 一格 2dp，比大崽崽小一号
private val PILE_CELL = 2.dp
private const val PW = 22          // 每只一张 22×18 格的图，地面在图的下边
private const val PH = 18
private const val PTOP = 3
private const val BODY_C = 16
private const val LIFT_C = 7       // 叠上去抬高 7 格
private const val GAP_C = 2
private const val EDGE_C = 7
private const val STEP_MS = 160L
private val LEG_X = intArrayOf(3, 5, 10, 12)
private val NAMES = listOf("cowboy", "shades", "wizard")

private val PILE_PAL = mapOf(
    '#' to Color(0xFFD97757), 's' to Color(0xFFBF684C), 'o' to Color(0xFF2B1D17),
    'Z' to Color(0xFF1F1D1B), 'w' to Color(0xFFFBF8F1), 'B' to Color(0xFF5B8FB9),
    'b' to Color(0xFF3F6F99), 'y' to Color(0xFFE8B64A), 'Y' to Color(0xFFFFF3C4),
    'n' to Color(0xFF8B5E3C), 'l' to Color(0xFFCC4F45), 'k' to Color(0xFF6B6560),
)
private val PILE_SHADOW = Color(0x24141413)

private val BODY = listOf(
    "..############..", "..############..", "..############..", "################",
    "################", "..############..", "..############..", "..############..",
)
private val PILE_LEGS = mapOf(
    "stand" to listOf("...#.#....#.#...", "...#.#....#.#..."),
    "dangleA" to listOf("...#.#....#.#...", "...#.#..........", "...#.#.........."),
    "dangleB" to listOf("...#.#....#.#...", "..........#.#...", "..........#.#..."),
)
private typealias Sp = Triple<Int, Int, String>
private val EYES_SHUT = listOf(Sp(3, 2, "oo"), Sp(11, 2, "oo"))
private val EYES_OPEN = listOf(Sp(4, 1, "o"), Sp(4, 2, "o"), Sp(11, 1, "o"), Sp(11, 2, "o"))
private val EYES_HALF = listOf(Sp(4, 2, "o"), Sp(11, 2, "o"))
private val GLASSES_ON = listOf(Sp(3, 1, "ZZZZ"), Sp(9, 1, "ZZZZ"), Sp(7, 1, "ZZ"), Sp(3, 2, "ZZZ"), Sp(10, 2, "ZZZ"), Sp(4, 1, "w"), Sp(10, 1, "w"))
private val GLASSES_DOWN = listOf(Sp(3, 2, "ZZZZ"), Sp(9, 2, "ZZZZ"), Sp(7, 2, "ZZ"), Sp(3, 3, "ZZZ"), Sp(10, 3, "ZZZ"), Sp(4, 2, "w"), Sp(10, 2, "w"))
private val COWBOY = listOf(".....nnnnnn.....", "....nnnnnnnn....", "....llllllll....", ".nnnnnnnnnnnnnn.")
private val WIZARD = listOf(
    "...........b....", "..........Bb....", ".........BBB....", ".......BBBBB....",
    "......ByBBBBB...", ".....BBBBBByBB..", "..BBBBBBBBBBBB..",
)
private val WAND_REST = listOf(Sp(0, 3, "n"), Sp(1, 2, "n"), Sp(2, 1, "n"), Sp(2, -1, ".y."), Sp(2, 0, "yYy"), Sp(2, 1, ".y."))
private val WAND_SHINE = listOf(Sp(0, 3, "n"), Sp(1, 2, "n"), Sp(2, 1, "n"), Sp(2, -1, ".Y."), Sp(2, 0, "YwY"), Sp(2, 1, ".Y."))
private val Z_BIG = listOf(Sp(0, 0, "kkkk"), Sp(2, 1, "k"), Sp(1, 2, "k"), Sp(0, 3, "kkkk"))
private val Z_SMALL = listOf(Sp(0, 0, "kkk"), Sp(1, 1, "k"), Sp(0, 2, "kkk"))
private val STAR = listOf(Sp(1, 0, "Y"), Sp(0, 1, "YwY"), Sp(1, 2, "Y"))
private val SPARK_AT = arrayOf(intArrayOf(19, -3, 0), intArrayOf(21, -1, 120), intArrayOf(18, -1, 220), intArrayOf(20, -5, 320))

// 呼吸、蹦、落地压扁的关键帧：[时刻, 上下位移(格), 横向缩放, 纵向缩放]
private val BREATHE = arrayOf(
    floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(.42f, 0f, 1.07f, .91f), floatArrayOf(.58f, 0f, .96f, 1.05f),
    floatArrayOf(.72f, 0f, 1.015f, .985f), floatArrayOf(.84f, 0f, 1f, 1f), floatArrayOf(1f, 0f, 1f, 1f),
)
private val HOP = arrayOf(
    floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(.18f, 0f, 1.08f, .9f), floatArrayOf(.5f, -3f, .95f, 1.07f),
    floatArrayOf(.78f, 0f, 1.07f, .92f), floatArrayOf(1f, 0f, 1f, 1f),
)
private val SQUASH = arrayOf(
    floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(.35f, 0f, 1.12f, .86f), floatArrayOf(.7f, 0f, .97f, 1.04f), floatArrayOf(1f, 0f, 1f, 1f),
)

private class PCell(val x: Int, val y: Int, val c: Color)

private fun grid(rows: List<String>, ox: Int, oy: Int, out: MutableList<PCell>, dark: Int = -1) {
    rows.forEachIndexed { y, r ->
        r.forEachIndexed { x, ch ->
            if (ch != '.') out += PCell(x + ox, y + oy, PILE_PAL[if (x == dark) 's' else ch]!!)
        }
    }
}

private fun spans(l: List<Sp>, ox: Int, oy: Int, out: MutableList<PCell>) {
    for ((x, y, s) in l) s.forEachIndexed { i, ch -> if (ch != '.') out += PCell(x + i + ox, y + oy, PILE_PAL[ch]!!) }
}

private val cellCache = HashMap<String, List<PCell>>()

/** eyes: shut / half / open；pose: crouch / stand / walk / dangleA / dangleB；face: 0 正面 / 1 朝右 / -1 朝左 */
private fun pileCells(name: String, eyes: String, pose: String, face: Int): List<PCell> =
    cellCache.getOrPut("$name|$eyes|$pose|$face") {
        val up = when {
            pose == "crouch" -> 0
            pose.startsWith("dangle") -> 3
            else -> 2
        }
        val by = PTOP + 7 - up
        val out = ArrayList<PCell>()
        if (name == "cowboy") grid(COWBOY, 0, by - 4, out)
        if (name == "wizard") grid(WIZARD, 0, by - 7, out)
        grid(BODY, 0, by, out, if (face > 0) 2 else if (face < 0) 13 else -1)
        PILE_LEGS[pose]?.let { grid(it, 0, by + 8, out) }
        if (name == "shades") {
            if (eyes == "open") {
                spans(EYES_OPEN, face, by, out)
                spans(GLASSES_DOWN, face, by, out)
            } else {
                spans(GLASSES_ON, face, by, out)
            }
        } else {
            spans(
                when (eyes) {
                    "open" -> EYES_OPEN
                    "half" -> EYES_HALF
                    else -> EYES_SHUT
                },
                face, by, out,
            )
        }
        if (name == "wizard") spans(if (eyes == "open") WAND_SHINE else WAND_REST, 15, by, out)
        out
    }

private fun keyAt(p: Float, k: Array<FloatArray>, out: FloatArray) {
    var i = 0
    while (i < k.size - 2 && p > k[i + 1][0]) i++
    val a = k[i]
    val b = k[i + 1]
    val t = ((p - a[0]) / (b[0] - a[0])).coerceIn(0f, 1f)
    val e = t * t * (3 - 2 * t)
    for (j in 1..3) out[j - 1] = a[j] + (b[j] - a[j]) * e
}

private class Mini(val name: String, val seed: Long) {
    var x = 0f
    var on: List<String> = emptyList()
    var mode = "sleep"
    var eyes = "shut"
    var pose = "crouch"
    var face = 0
    var gen = 0
    var job: Job? = null

    var glideFromX = 0f
    var glideFromY = 0f
    var glideArc = 0f
    var glideAt = 0L
    var glideDur = 0L
    var glideIn = false
    var stepDir = 0
    var stepAt = -1000L
    var hopAt = -1000L
    var squashAt = -1000L
    var sparkAt = -1000L
    var pokeUntil = 0L

    var heldX = 0f
    var heldY = 0f
    var lastLeft = -10000f
    var lastTop = -10000f
}

private class PileBrain(val prefs: SharedPreferences, val scope: CoroutineScope) {
    val minis = linkedMapOf(
        "cowboy" to Mini("cowboy", 0),
        "shades" to Mini("shades", 1300),
        "wizard" to Mini("wizard", 2600),
    )
    var cell = 0f
    var areaW = 0f
    var floor = 0f
    var now = 0L
    var laid = false
    var lastW = 0f
    var atHome = false
    var peek = 0f
    var zLast = 0L
    var zStart = -100000L
    var zX = 0f
    var zY = 0f
    var zBig = false

    val bodyW get() = BODY_C * cell
    val gapW get() = GAP_C * cell
    private val tmp = FloatArray(3)

    fun lift(k: Mini): Float = if (k.on.isEmpty()) 0f else k.on.maxOf { n -> minis[n]?.let { lift(it) + LIFT_C * cell } ?: 0f }
    fun carriers(k: Mini) = minis.values.filter { k.name in it.on }
    fun grounded(except: String) = minis.values.filter { it.name != except && it.on.isEmpty() && it.mode != "held" }
    private fun minX() = EDGE_C * cell
    private fun maxX() = max(minX(), areaW - EDGE_C * cell - bodyW)
    fun snap(x: Float) = (x / cell).roundToInt() * cell
    fun clampX(x: Float) = snap(x.coerceIn(minX(), maxX()))

    private fun bigClash(x: Float) = ClawdShared.bigGrounded &&
        x < ClawdShared.bigRight + gapW && ClawdShared.bigLeft < x + bodyW + gapW

    fun freeAt(x: Float, except: String): Boolean {
        if (x < minX() - 0.5f || x > maxX() + 0.5f || bigClash(x)) return false
        return grounded(except).all { abs(it.x - x) >= bodyW + gapW }
    }

    fun nearestFree(x: Float, except: String): Float? {
        val x0 = clampX(x)
        if (freeAt(x0, except)) return x0
        var d = cell
        while (d < areaW) {
            for (s in intArrayOf(-1, 1)) {
                val t = x0 + s * d
                if (t >= minX() && t <= maxX() && freeAt(t, except)) return t
            }
            d += cell
        }
        return null
    }

    private fun clearPath(a: Float, b: Float, except: String): Boolean {
        val lo = min(a, b)
        val hi = max(a, b)
        fun apart(o: Float, w: Float) = o + w + gapW <= lo || o >= hi + bodyW + gapW
        if (ClawdShared.bigGrounded && !apart(ClawdShared.bigLeft, ClawdShared.bigRight - ClawdShared.bigLeft)) return false
        return grounded(except).all { apart(it.x, bodyW) }
    }

    fun stackX() {
        fun depth(k: Mini): Int = if (k.on.isEmpty()) 0 else 1 + k.on.maxOf { n -> minis[n]?.let { depth(it) } ?: 0 }
        minis.values.filter { it.on.isNotEmpty() }.sortedBy { depth(it) }.forEach { k ->
            val sup = k.on.mapNotNull { minis[it] }
            if (sup.isEmpty()) {
                k.on = emptyList()
            } else {
                k.x = snap(sup.sumOf { (it.x + bodyW / 2).toDouble() }.toFloat() / sup.size - bodyW / 2)
            }
        }
    }

    // ---------- 位置 ----------

    private fun home() {
        val r = areaW - (EDGE_C + 30) * cell
        minis["cowboy"]!!.apply { x = snap(r); on = emptyList() }
        minis["shades"]!!.apply { x = snap(r + 14 * cell); on = emptyList() }
        minis["wizard"]!!.apply { x = snap(r + 7 * cell); on = listOf("cowboy", "shades") }
        stackX()
        atHome = true
    }

    fun save() {
        if (areaW <= 0f) return
        val o = JSONObject()
        minis.values.forEach { k ->
            o.put(k.name, JSONObject().put("dx", ((k.x - areaW / 2) / cell).toDouble()).put("on", JSONArray(k.on)))
        }
        prefs.edit().putString("pile_v1", o.toString()).apply()
    }

    private fun load(): Boolean {
        val s = prefs.getString("pile_v1", null) ?: return false
        return runCatching {
            val o = JSONObject(s)
            minis.values.forEach { k ->
                val e = o.getJSONObject(k.name)
                k.x = clampX(areaW / 2 + e.getDouble("dx").toFloat() * cell)
                val a = e.getJSONArray("on")
                k.on = List(a.length()) { a.getString(it) }.filter { it in minis && it != k.name }
            }
            stackX()
            true
        }.getOrDefault(false)
    }

    private fun layout() {
        if (!laid) {
            if (!load()) home() else atHome = false
            laid = true
            lastW = areaW
            minis.values.forEach { startLife(it, first = true) }
            return
        }
        if (abs(areaW - lastW) < 1f) return
        if (atHome) {
            home()
        } else {
            val d = snap((areaW - lastW) / 2)
            grounded("").forEach { it.x = clampX(it.x + d) }
            stackX()
            save()
        }
        lastW = areaW
    }

    fun frame(t: Long, dt: Long, scrolling: Boolean) {
        now = t
        if (areaW <= 0f || cell <= 0f) return
        layout()
        val target = if (scrolling) 1f else 0f
        peek += (target - peek) * (dt / 120f).coerceAtMost(1f)
        if (!scrolling && t - zLast > 5000) {
            zLast = t
            val s = minis.values.filter { it.mode == "sleep" && carriers(it).isEmpty() }
            if (s.isNotEmpty()) {
                val k = s.random()
                zStart = t
                zX = k.x + 12 * cell
                zY = floor - PH * cell - lift(k) + (PTOP + 5) * cell
                zBig = !zBig
            }
        }
        ClawdShared.pileRects = grounded("").map { floatArrayOf(it.x, it.x + bodyW) }
    }

    // ---------- 作息：睡 → 醒 → 走走停停 → 坐下睡 ----------

    fun startLife(k: Mini, first: Boolean = false, fromSit: Boolean = false) {
        k.job?.cancel()
        k.job = scope.launch {
            var firstRun = first
            if (fromSit) {
                k.mode = "sit"
                delay(rnd(1500, 3000))
                doze(k)
            }
            while (true) {
                k.mode = "sleep"
                k.eyes = "shut"
                k.pose = "crouch"
                k.face = 0
                delay(if (firstRun) rnd(8000, 60000) else rnd(40000, 110000))
                firstRun = false
                k.mode = "sit"
                k.eyes = "half"
                delay(700)
                k.eyes = "open"
                delay(rnd(1200, 2500))
                if (carriers(k).isNotEmpty()) {
                    delay(rnd(5000, 9000))
                    doze(k)
                    continue
                }
                if (k.on.isNotEmpty() && !hopDown(k)) {
                    doze(k)
                    continue
                }
                repeat(2 + Random.nextInt(3)) { if (!wanderOnce(k)) pause(k) }
                k.pose = "crouch"
                k.face = 0
                k.mode = "sit"
                save()
                delay(rnd(1200, 2500))
                doze(k)
            }
        }
    }

    private suspend fun doze(k: Mini) {
        k.eyes = "half"
        delay(900)
    }

    private suspend fun wanderOnce(k: Mini): Boolean {
        var target: Float? = null
        repeat(12) {
            if (target == null) {
                val t = snap(k.x + (if (Random.nextBoolean()) -1 else 1) * (15 + Random.nextFloat() * 35) * cell)
                if (freeAt(t, k.name) && clearPath(k.x, t, k.name)) target = t
            }
        }
        val t = target ?: return false
        walk(k, t)
        pause(k)
        return true
    }

    private suspend fun walk(k: Mini, target: Float) {
        atHome = false
        k.mode = "walk"
        k.eyes = "open"
        val dir = if (target > k.x) 1 else -1
        k.face = dir
        if (k.pose != "stand") {
            k.pose = "crouch"
            delay(300)
        }
        k.pose = "stand"
        delay(350)
        k.pose = "walk"
        while (abs(target - k.x) >= cell * 0.5f) {
            val nx = k.x + dir * cell
            if (nx < minX() - 0.5f || nx > maxX() + 0.5f) break
            val hit = grounded(k.name).firstOrNull { (it.x - k.x) * dir > 0 && abs(it.x - nx) < bodyW + gapW }
            if (hit != null) {
                if (hit.mode == "sleep") {
                    hit.eyes = "half"
                    scope.launch {
                        delay(700)
                        if (hit.mode == "sleep") hit.eyes = "shut"
                    }
                }
                break
            }
            if (bigClash(nx) && (ClawdShared.bigLeft - k.x) * dir > 0) break
            k.x = nx
            k.stepDir = dir
            k.stepAt = now
            delay(STEP_MS)
        }
        k.pose = "stand"
        delay(300)
        k.face = 0
        delay(300)
    }

    private suspend fun pause(k: Mini) {
        k.mode = "sit"
        if (k.pose != "crouch") k.pose = "stand"
        k.eyes = "open"
        if (Random.nextFloat() < 0.5f) {
            val side = if (Random.nextBoolean()) -1 else 1
            delay(rnd(500, 1200))
            k.face = side
            delay(rnd(800, 1200))
            k.face = 0
        }
        delay(rnd(1500, 4000))
    }

    private suspend fun hopDown(k: Mini): Boolean {
        val side = if (Random.nextBoolean()) -1 else 1
        val t = nearestFree(k.x + side * (bodyW + 4 * cell), k.name) ?: return false
        val fromY = lift(k)
        val fromX = k.x
        k.on = emptyList()
        k.x = t
        stackX()
        k.mode = "walk"
        k.pose = "stand"
        k.eyes = "open"
        glide(k, fromX - t, -fromY, 520, arc = 7 * cell, easeIn = false)
        delay(520)
        k.squashAt = now
        k.pose = "crouch"
        delay(400)
        return true
    }

    private fun glide(k: Mini, dx: Float, dy: Float, dur: Long, arc: Float = 0f, easeIn: Boolean = false) {
        k.glideFromX = dx
        k.glideFromY = dy
        k.glideArc = arc
        k.glideAt = now
        k.glideDur = dur
        k.glideIn = easeIn
    }

    // ---------- 手指 ----------

    fun poke(k: Mini) {
        k.pokeUntil = now + 1000
        k.hopAt = now
        if (k.name == "wizard") k.sparkAt = now
    }

    fun pickUp(k: Mini) {
        atHome = false
        k.gen++
        k.job?.cancel()
        k.heldX = k.lastLeft
        k.heldY = k.lastTop
        k.glideDur = 0
        k.mode = "held"
        k.pose = "dangleA"
        k.eyes = "open"
        k.face = 0
        k.x = snap(k.x)
        val above = carriers(k)
        k.on = emptyList()
        lowerFrom(above, k.name)
    }

    /** 下面那只被拿走：上面的平滑地降下来，还有别的撑着就落到它头上，没有就落回输入框上 */
    private fun lowerFrom(list: List<Mini>, gone: String) {
        if (list.isEmpty()) return
        val before = list.associateWith { lift(it) to it.x }
        for (t in list) {
            t.on = t.on - gone
            if (t.on.isEmpty()) t.x = nearestFree(t.x, t.name) ?: clampX(t.x)
        }
        stackX()
        for (t in list) {
            val (bl, bx) = before[t]!!
            glide(t, bx - t.x, -(bl - lift(t)), 400)
        }
        save()
    }

    fun moveHeld(k: Mini, d: Offset) {
        k.heldX = (k.heldX + d.x).coerceIn(-5 * cell, areaW - PW * cell + 5 * cell)
        k.heldY = (k.heldY + d.y).coerceAtMost(floor - PH * cell)
    }

    fun drop(k: Mini) {
        if (k.mode != "held") return
        val fromLeft = k.heldX
        val fromTop = k.heldY
        val feet = fromTop + PH * cell
        val below = minis.values
            .filter { it !== k && it.mode != "held" && carriers(it).isEmpty() && abs(it.x - fromLeft) < bodyW * 0.6f }
            .minByOrNull { abs(it.x - fromLeft) }
        if (below != null && feet < floor - lift(below) - 6 * cell) {
            if (below.mode == "walk") {
                below.job?.cancel()
                below.mode = "sit"
                below.pose = "crouch"
                below.face = 0
                startLife(below, fromSit = true)
            }
            k.on = listOf(below.name)
            k.x = below.x
        } else {
            k.on = emptyList()
            k.x = clampX(fromLeft)
        }
        stackX()
        val restTop = floor - PH * cell - lift(k)
        val dy = fromTop - restTop
        val dur = (180 + abs(dy) / cell * 4).toLong().coerceAtMost(420L)
        glide(k, fromLeft - k.x, dy, dur, easeIn = true)
        k.mode = "sit"
        k.pose = "dangleA"
        val g = k.gen
        scope.launch {
            delay(dur)
            if (k.gen != g) return@launch
            k.squashAt = now
            k.pose = "crouch"
            k.eyes = "open"
            if (k.on.isEmpty()) {
                val t = nearestFree(k.x, k.name)
                if (t != null && abs(t - k.x) > 0.5f) {
                    glide(k, k.x - t, 0f, 300)
                    k.x = t
                }
            }
            save()
            if (k.gen == g) startLife(k, fromSit = true)
        }
    }

    // ---------- 画 ----------

    fun draw(s: DrawScope, t: Long) = with(s) {
        if (!laid || cell <= 0f) return@with
        val c = cell
        val peekOff = peek * 4 * c
        val sz = Size(c + 0.5f, c + 0.5f)
        val list = minis.values.sortedWith(compareBy({ if (it.mode == "held") 1 else 0 }, { lift(it) }))
        for (k in list) {
            val held = k.mode == "held"
            val l = lift(k)
            var left: Float
            var top: Float
            if (held) {
                left = k.heldX
                top = k.heldY
            } else {
                left = k.x
                top = floor - PH * c - l
                if (k.glideDur > 0) {
                    val p = (t - k.glideAt).toFloat() / k.glideDur
                    if (p < 1f) {
                        val e = if (k.glideIn) p * p else 1 - (1 - p) * (1 - p)
                        left += k.glideFromX * (1 - e)
                        top += k.glideFromY * (1 - e) - k.glideArc * sin(PI * p).toFloat()
                    } else {
                        k.glideDur = 0
                    }
                }
                val sp = (t - k.stepAt).toFloat() / STEP_MS
                if (sp < 1f) left -= k.stepDir * c * (1 - sp)
                top += peekOff
            }
            k.lastLeft = left
            k.lastTop = top

            if (!held && k.on.isEmpty() && peek < 0.95f) {
                drawOval(PILE_SHADOW.copy(alpha = PILE_SHADOW.alpha * (1 - peek)), Offset(left + 1.5f * c, floor - 2f), Size(13 * c, 3f))
            }

            var sx = 1f
            var sy = 1f
            var ty = 0f
            if (k.mode == "sleep" && k.pose == "crouch" && !held) {
                val dur = when (k.name) { "shades" -> 3500L; "wizard" -> 2800L; else -> 3100L }
                keyAt(((t + k.seed) % dur).toFloat() / dur, BREATHE, tmp)
                sx *= tmp[1]; sy *= tmp[2]
            }
            val hp = (t - k.hopAt).toFloat() / 460f
            if (hp in 0f..1f) {
                keyAt(hp, HOP, tmp)
                ty += tmp[0] * c; sx *= tmp[1]; sy *= tmp[2]
            }
            val qp = (t - k.squashAt).toFloat() / 320f
            if (qp in 0f..1f) {
                keyAt(qp, SQUASH, tmp)
                sx *= tmp[1]; sy *= tmp[2]
            }

            var eyes = when {
                t < k.pokeUntil -> "open"
                k.mode == "sleep" && t < k.pokeUntil + 400 -> "half"
                else -> k.eyes
            }
            if (k.mode != "sleep" && eyes == "open" && t >= k.pokeUntil && (t + k.seed) % 3400 < 140) eyes = "shut"
            val pose = if (held) (if ((t / 360) % 2 == 0L) "dangleA" else "dangleB") else k.pose
            val cells = pileCells(k.name, eyes, pose, k.face)
            val pivot = Offset(left + 8 * c, top + PH * c)

            val body: DrawScope.() -> Unit = {
                withTransform({
                    translate(0f, ty)
                    scale(sx, sy, pivot)
                }) {
                    for (pc in cells) drawRect(pc.c, Offset(left + pc.x * c, top + pc.y * c), sz)
                    if (pose == "walk") {
                        val dir = if (k.face != 0) k.face else 1
                        for (i in 0..3) {
                            val ph = (((t % (2 * STEP_MS)) + (if (i % 2 == 1) STEP_MS else 0L)) % (2 * STEP_MS)).toFloat() / (2 * STEP_MS)
                            val lp: Float
                            val lx: Float
                            if (ph < 0.28f) {
                                val e = ph / 0.28f
                                lp = 1f - 0.6f * e; lx = dir * 0.6f * c * e
                            } else if (ph < 0.5f) {
                                val e = (ph - 0.28f) / 0.22f
                                lp = 0.4f + 0.6f * e; lx = dir * 0.6f * c * (1 - e)
                            } else {
                                lp = 1f; lx = 0f
                            }
                            drawRect(
                                PILE_PAL[if (i % 2 == 1) 's' else '#']!!,
                                Offset(left + LEG_X[i] * c + lx, top + (PTOP + 13) * c),
                                Size(c + 0.5f, 2 * c * lp + 0.5f),
                            )
                        }
                    }
                }
            }
            if (!held && k.on.isEmpty()) {
                clipRect(left = -100000f, top = -100000f, right = 100000f, bottom = floor) { body() }
            } else {
                body()
            }

            // 巫师被戳：魔杖边上闪几下
            if (k.name == "wizard") {
                for (a in SPARK_AT) {
                    val q = (t - k.sparkAt - a[2]).toFloat() / 500f
                    if (q < 0f || q > 1f) continue
                    val qs = floor(q * 4) / 4
                    val ox = left + (a[0] - 1) * c
                    val oy = top + (PTOP + 7 + a[1] - 1) * c
                    val ctr = Offset(ox + 1.5f * c, oy + 1.5f * c)
                    withTransform({ scale(1 + 0.6f * qs, 1 + 0.6f * qs, ctr) }) {
                        val out = ArrayList<PCell>()
                        spans(STAR, 0, 0, out)
                        for (pc in out) drawRect(pc.c.copy(alpha = 1 - qs), Offset(ox + pc.x * c, oy + pc.y * c), sz)
                    }
                }
            }
        }

        // 睡着的头上飘一个 Z
        val zp = (t - zStart).toFloat() / 4200f
        if (zp in 0f..1f) {
            val a = when {
                zp < 0.12f -> zp / 0.12f
                zp < 0.8f -> 1f - 0.1f * (zp - 0.12f) / 0.68f
                else -> 0.9f * (1 - (zp - 0.8f) / 0.2f)
            }
            val out = ArrayList<PCell>()
            spans(if (zBig) Z_BIG else Z_SMALL, 0, 0, out)
            val ox = zX + 6 * c * zp
            val oy = zY - 14 * c * zp
            for (pc in out) drawRect(pc.c.copy(alpha = a.coerceIn(0f, 1f)), Offset(ox + pc.x * c, oy + pc.y * c), sz)
        }
    }
}

/**
 * 输入框上的三只小崽崽：牛仔帽、墨镜、巫师
 * 照 TT 里 Claude Web 的 clawd-pile.js：睡一阵 → 醒 → 走走停停 → 坐下睡
 * 戳一下蹦一下睁一秒眼；按住拖起来，放到别人头上就叠上去；滑聊天的时候缩下去只露头
 */
@Composable
internal fun ClawdPileLayer(
    areaW: Float,
    floor: Float,
    scrolling: Boolean,
    now: () -> Long,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val brain = remember { PileBrain(context.getSharedPreferences("clawd_pile", Context.MODE_PRIVATE), scope) }
    brain.cell = with(density) { PILE_CELL.toPx() }
    brain.areaW = areaW
    brain.floor = floor
    val scrollingNow by rememberUpdatedState(scrolling)

    LaunchedEffect(brain) {
        var last = 0L
        while (true) {
            val t = withFrameMillis { it }
            val dt = if (last == 0L) 16L else (t - last).coerceIn(0L, 50L)
            last = t
            brain.frame(t, dt, scrollingNow)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) { brain.draw(this, now()) }

    val w = with(density) { (PW * brain.cell).toDp() }
    val h = with(density) { (PH * brain.cell).toDp() }
    brain.minis.values.forEach { k ->
        Box(
            modifier = Modifier
                .offset {
                    @Suppress("UNUSED_VARIABLE") val f = now()
                    IntOffset(k.lastLeft.roundToInt(), k.lastTop.roundToInt())
                }
                .size(w, h)
                .pointerInput(k) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val slop = viewConfiguration.touchSlop
                        var total = Offset.Zero
                        var drag = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) {
                                ch.consume()
                                if (drag) brain.drop(k) else brain.poke(k)
                                break
                            }
                            val d = ch.positionChange()
                            if (!drag) {
                                total += d
                                if (total.getDistance() > slop) {
                                    drag = true
                                    brain.pickUp(k)
                                    brain.moveHeld(k, total)
                                }
                            } else {
                                brain.moveHeld(k, d)
                            }
                            ch.consume()
                        }
                        if (drag && k.mode == "held") brain.drop(k)
                    }
                },
        )
    }
}
