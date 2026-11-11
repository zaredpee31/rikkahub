package me.rerere.rikkahub.ui.pages.chat

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.ceil

// 每一帧里：前 7 个是整只的平移和弹性，后面每个部件 7 个数
internal const val RIG_HEAD = 7
internal const val RIG_STRIDE = 7

internal class RigClip(
    val id: String,
    val dur: Int,
    val loop: Boolean,
    val intro: Int,
    val pool: Boolean,
    val weight: Double,
    val cool: Int,
    val hold: Boolean,
    val clipGround: Boolean,
    val rootSmooth: Boolean,
    val smooth: BooleanArray,
    val fParts: BooleanArray,
    val times: IntArray,
    val ticks: Array<FloatArray>,
)

internal class ClawdRigData(
    val palette: IntArray,
    val keys: List<String>,
    val parts: List<String>,
    val partSheet: IntArray,
    val origin: Array<IntArray>,
    val restF: IntArray,
    val restZ: IntArray,
    val frameNames: List<List<String>>,
    val sprites: List<List<IntArray>>,
    val clips: Map<String, RigClip>,
) {
    val poseSize = RIG_HEAD + parts.size * RIG_STRIDE
    val shadowIndex = parts.indexOf("shadow")
    val symIndex = parts.indexOf("sym")
    fun part(name: String) = parts.indexOf(name)
    fun frame(part: Int, name: String) = if (part < 0) -1 else frameNames[partSheet[part]].indexOf(name)

    private val extents = HashMap<String, IntArray>()

    /** 这个动作最多往左、往右伸出 16 格的框多少格 */
    fun extent(id: String): IntArray = extents.getOrPut(id) {
        val c = clips[id] ?: return@getOrPut intArrayOf(0, 0)
        var lo = 0f
        var hi = 15f
        for (row in c.ticks) {
            for (p in parts.indices) {
                if (p == shadowIndex) continue
                val b = RIG_HEAD + p * RIG_STRIDE
                val f = row[b].toInt()
                if (f < 0) continue
                val spr = sprites[partSheet[p]].getOrNull(f) ?: continue
                var i = 0
                while (i < spr.size) {
                    val x = origin[p][0] + spr[i] + row[b + 1] + row[0]
                    if (x < lo) lo = x
                    if (x > hi) hi = x
                    i += 3
                }
            }
        }
        intArrayOf(ceil(-lo).toInt(), ceil(hi - 15f).toInt())
    }
}

internal object ClawdRigStore {
    @Volatile
    private var cached: ClawdRigData? = null

    fun peek(): ClawdRigData? = cached

    suspend fun load(context: Context): ClawdRigData? {
        cached?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val text = context.assets.open("clawd/clawd.json").bufferedReader().use { it.readText() }
                parse(text)
            }.getOrNull()?.also { cached = it }
        }
    }

    private fun parse(text: String): ClawdRigData {
        val o = JSONObject(text)
        val palA = o.getJSONArray("palette")
        val palette = IntArray(palA.length()) { i ->
            java.lang.Long.parseLong(palA.getString(i).removePrefix("#"), 16).toInt()
        }
        val keysA = o.optJSONArray("keys")
        val keys = if (keysA == null) emptyList() else List(keysA.length()) { i -> keysA.getString(i) }
        val partsA = o.getJSONArray("parts")
        val parts = List(partsA.length()) { i -> partsA.getString(i) }

        val namesO = o.getJSONObject("names")
        val spritesO = o.getJSONObject("sprites")
        val sheetList = ArrayList<String>()
        val frameNames = ArrayList<List<String>>()
        val sprites = ArrayList<List<IntArray>>()
        for (s in namesO.keys()) {
            sheetList += s
            val na = namesO.getJSONArray(s)
            frameNames += List(na.length()) { i -> na.getString(i) }
            val sa = spritesO.getJSONArray(s)
            sprites += List(sa.length()) { i ->
                val f = sa.getJSONArray(i)
                IntArray(f.length()) { j -> f.getInt(j) }
            }
        }
        val sheetA = o.getJSONArray("sheet")
        val partSheet = IntArray(parts.size) { i -> sheetList.indexOf(sheetA.getString(i)) }
        val originA = o.getJSONArray("origin")
        val origin = Array(parts.size) { i ->
            val a = originA.getJSONArray(i)
            intArrayOf(a.getInt(0), a.getInt(1))
        }
        val rfA = o.getJSONArray("restF")
        val restF = IntArray(parts.size) { i -> rfA.getInt(i) }
        val rzA = o.getJSONArray("restZ")
        val restZ = IntArray(parts.size) { i -> rzA.getInt(i) }

        val clipsO = o.getJSONObject("clips")
        val clips = HashMap<String, RigClip>()
        for (id in clipsO.keys()) {
            val c = clipsO.getJSONObject(id)
            val sm = c.getJSONArray("smooth")
            val fp = c.getJSONArray("fParts")
            val tm = c.getJSONArray("times")
            val tk = c.getJSONArray("ticks")
            clips[id] = RigClip(
                id = id,
                dur = c.getInt("dur"),
                loop = c.optBoolean("loop"),
                intro = c.optInt("intro"),
                pool = c.optBoolean("pool"),
                weight = c.optDouble("weight", 1.0),
                cool = c.optInt("cool"),
                hold = c.optBoolean("hold"),
                clipGround = c.optBoolean("clipGround"),
                rootSmooth = c.optBoolean("rootSmooth"),
                smooth = BooleanArray(sm.length()) { i -> sm.getBoolean(i) },
                fParts = BooleanArray(fp.length()) { i -> fp.getBoolean(i) },
                times = IntArray(tm.length()) { i -> tm.getInt(i) },
                ticks = Array(tk.length()) { i ->
                    val r = tk.getJSONArray(i)
                    FloatArray(r.length()) { j -> r.getDouble(j).toFloat() }
                },
            )
        }
        return ClawdRigData(palette, keys, parts, partSheet, origin, restF, restZ, frameNames, sprites, clips)
    }
}

/** 待机的样子 */
internal fun ClawdRigData.rest(out: FloatArray) {
    out.fill(0f)
    out[2] = 1f
    out[3] = 1f
    for (p in parts.indices) {
        val b = RIG_HEAD + p * RIG_STRIDE
        out[b] = restF[p].toFloat()
        out[b + 3] = restZ[p].toFloat()
        out[b + 4] = 1f
        out[b + 6] = 1f
    }
}

/** 动作在 t 毫秒时的样子；换帧、层级按 100ms 一格跳，标了平滑的部件、缩放、旋转、透明度在两格之间插值 */
internal fun ClawdRigData.sample(c: RigClip, t: Int, out: FloatArray) {
    val ts = c.times
    val last = ts.size - 1
    var i = (t / 100).coerceIn(0, last)
    while (i > 0 && ts[i] > t) i--
    while (i < last && ts[i + 1] <= t) i++
    val a = c.ticks[i]
    val b = if (i < last) c.ticks[i + 1] else a
    val k = if (i < last) ((t - ts[i]).toFloat() / (ts[i + 1] - ts[i])).coerceIn(0f, 1f) else 0f
    fun lerp(j: Int) = a[j] + (b[j] - a[j]) * k
    out[0] = if (c.rootSmooth) lerp(0) else a[0]
    out[1] = if (c.rootSmooth) lerp(1) else a[1]
    for (j in 2..6) out[j] = a[j]
    for (p in parts.indices) {
        val base = RIG_HEAD + p * RIG_STRIDE
        out[base] = a[base]
        out[base + 1] = if (c.smooth[p]) lerp(base + 1) else a[base + 1]
        out[base + 2] = if (c.smooth[p]) lerp(base + 2) else a[base + 2]
        out[base + 3] = a[base + 3]
        out[base + 4] = lerp(base + 4)
        out[base + 5] = lerp(base + 5)
        out[base + 6] = lerp(base + 6)
    }
}

/** 彩棒每次换颜色 */
internal fun ClawdRigData.paletteWithSticks(a: Int, b: Int): IntArray {
    val p = palette.copyOf()
    fun set(k: String, v: Int) {
        val i = keys.indexOf(k)
        if (i >= 0) p[i] = v
    }
    set("G", a)
    set("t", (a and 0xFFFFFF) or (0x59 shl 24))
    set("N", b)
    set("u", (b and 0xFFFFFF) or (0x59 shl 24))
    return p
}

/** 画一整只：left/top 是 16×10 格框的左上角，脚底在 top + 10 格 */
internal fun DrawScope.drawRig(
    d: ClawdRigData,
    pose: FloatArray,
    left: Float,
    top: Float,
    cell: Float,
    mirror: Boolean,
    clipGround: Boolean,
    palette: IntArray,
) {
    val shadow = d.shadowIndex
    val rx = if (mirror) -pose[0] else pose[0]
    val ry = pose[1]
    val sx = pose[2]
    val sy = pose[3]
    val r = if (mirror) -pose[4] else pose[4]
    val tx = if (mirror) -pose[5] else pose[5]
    val ty = pose[6]
    val baseX = left + rx * cell
    val baseY = top + ry * cell
    val pivot = Offset(baseX + 8 * cell, baseY + 10 * cell)
    val order = d.parts.indices.filter { it != shadow }.sortedBy { pose[RIG_HEAD + it * RIG_STRIDE + 3] }
    val shadowZ = if (shadow >= 0) pose[RIG_HEAD + shadow * RIG_STRIDE + 3] else 0f

    val all: DrawScope.() -> Unit = {
        if (shadow >= 0 && shadowZ <= 2f) rigPart(d, pose, shadow, baseX, baseY, cell, mirror, palette)
        withTransform({
            translate(tx * cell, ty * cell)
            rotate(r, pivot)
            scale(sx, sy, pivot)
        }) {
            order.forEach { rigPart(d, pose, it, baseX, baseY, cell, mirror, palette) }
        }
        if (shadow >= 0 && shadowZ > 2f) rigPart(d, pose, shadow, baseX, baseY, cell, mirror, palette)
    }
    if (clipGround) {
        clipRect(left = -100000f, top = -100000f, right = 100000f, bottom = top + 10 * cell) { all() }
    } else {
        all()
    }
}

private fun DrawScope.rigPart(
    d: ClawdRigData,
    pose: FloatArray,
    p: Int,
    baseX: Float,
    baseY: Float,
    cell: Float,
    mirror: Boolean,
    palette: IntArray,
) {
    val b = RIG_HEAD + p * RIG_STRIDE
    val f = pose[b].toInt()
    if (f < 0) return
    val alpha = pose[b + 6]
    if (alpha <= 0.01f) return
    val spr = d.sprites[d.partSheet[p]].getOrNull(f) ?: return
    val ox = d.origin[p][0]
    val oy = d.origin[p][1]
    val dx = pose[b + 1]
    val dy = pose[b + 2]
    val isSym = p == d.symIndex
    val sc = pose[b + 4]
    val rot = pose[b + 5]
    val sz = Size(cell + 0.5f, cell + 0.5f)

    val cells: DrawScope.() -> Unit = {
        var i = 0
        while (i < spr.size) {
            val cx = ox + spr[i]
            val cy = oy + spr[i + 1]
            val x = when {
                !mirror -> cx + dx
                isSym -> cx + 1 - dx
                else -> 15 - (cx + dx)
            }
            val col = palette[spr[i + 2]]
            val ca = ((col ushr 24) and 0xFF) / 255f * alpha
            drawRect(Color(col).copy(alpha = ca.coerceIn(0f, 1f)), Offset(baseX + x * cell, baseY + (cy + dy) * cell), sz)
            i += 3
        }
    }
    if (sc != 1f || rot != 0f) {
        val pvx = when {
            !mirror -> ox + 0.5f + dx
            isSym -> ox + 0.5f + 1 - dx
            else -> 15 - (ox + 0.5f + dx)
        }
        val pv = Offset(baseX + pvx * cell, baseY + (oy + 2.5f + dy) * cell)
        withTransform({
            rotate(rot, pv)
            scale(sc, sc, pv)
        }) { cells() }
    } else {
        cells()
    }
}

/** 大崽崽和小崽崽互相让路用的 */
internal object ClawdShared {
    var bigLeft = 0f
    var bigRight = 0f
    var bigGrounded = false
    var pileRects: List<FloatArray> = emptyList()
}
