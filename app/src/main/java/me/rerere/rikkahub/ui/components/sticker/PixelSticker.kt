package me.rerere.rikkahub.ui.components.sticker

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import me.rerere.rikkahub.ui.pages.chat.ClawdRigData
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// 像素崽崽表情：崽崽本体用桌宠的像素（身子、眼睛、钳子、腿），动作和道具是表情自己排的
// 崽崽格子：身子 x2..13 y0..7，眼睛在 x4、x11 的 y2..3，钳子在 y5..6，腿在 y8..9

// 道具颜色，'.' 是空
internal val PROP_COLORS: Map<Char, Color> = mapOf(
    'R' to Color(0xFFE8505B), 'r' to Color(0xFFB83A44),
    'P' to Color(0xFFF28B82), 'p' to Color(0xFFF7C1C6),
    'Y' to Color(0xFFF5C84C), 'y' to Color(0xFFD9A630),
    'O' to Color(0xFFF0A868),
    'W' to Color(0xFFFFFFFF), 'w' to Color(0xFFF4EFE6),
    'G' to Color(0xFFB8B0A6), 'g' to Color(0xFF7A726A),
    'K' to Color(0xFF1F1D1B),
    'B' to Color(0xFF7FC4E0), 'b' to Color(0xFF4A7FB0),
    'N' to Color(0xFF8CC07A), 'n' to Color(0xFF5E8A4E),
    'C' to Color(0xFFA0703C), 'c' to Color(0xFFD8B48C),
    'V' to Color(0xFFA99BD8),
    'S' to Color(0xFFCFE6F2), 'T' to Color(0xFF8FCFEA),
    'X' to Color(0xFFD97757), 'x' to Color(0xFFBF684C),
)

internal class Px(val x: Int, val y: Int, val c: Color)

/** 道具像素画：一行一个字符串 */
internal class PixArt(rows: List<String>) {
    val cells: List<Px> = rows.flatMapIndexed { y, row ->
        row.mapIndexedNotNull { x, ch -> PROP_COLORS[ch]?.let { Px(x, y, it) } }
    }
}

internal fun art(vararg rows: String) = PixArt(rows.toList())

/** 一帧：崽崽各部位用哪一格、挪多少，加上这一帧的道具。ms 是这帧停多久 */
internal class SK(
    val ms: Int,
    val body: String = "stand",
    val eyes: String = "open",
    val cl: String = "stub",
    val cr: String = "stub",
    val legs: String = "stand",
    val dx: Int = 0, val dy: Int = 0,
    val bx: Int = 0, val by: Int = 0,
    val ex: Int = 0, val ey: Int = 0,
    val lx: Int = 0, val ly: Int = 0,
    val rx: Int = 0, val ry: Int = 0,
    val gx: Int = 0, val gy: Int = 0,
    val blush: Boolean = false,
    val mirror: Boolean = false,
    val hide: Boolean = false,
    val props: List<SP> = emptyList(),
)

/** 道具：layer 0 在崽崽后面，1 在身子前面、钳子后面，2 盖在最上面 */
internal class SP(val art: PixArt, val x: Int, val y: Int, val layer: Int = 1)

/** 一直飘的小东西：爱心、Z、眼泪、彩纸。rise 是一圈往上飘几格，负的就是往下掉 */
internal class SFx(
    val art: PixArt,
    val x: Float,
    val y: Float,
    val rise: Float,
    val period: Int,
    val phase: Int = 0,
    val drift: Float = 0f,
    val sway: Float = 0f,
)

internal class StickerDef(val keys: List<SK>, val fx: List<SFx> = emptyList())

// ---------- 大家都用的小东西 ----------
internal val HEART = art(".R.R.", "RRRRR", ".RRR.", "..R..")
internal val HEART_S = art("P.P", "PPP", ".P.")
internal val HEART_BIG = art(".RR.RR.", "RWRRRRR", "RRRRRRR", ".RRRRR.", "..RRR..", "...R...")
internal val SPARK = art(".Y.", "YWY", ".Y.")
internal val SPARK_P = art(".P.", "PWP", ".P.")
internal val ZZ = art("VVVV", "..V.", ".V..", "VVVV")
internal val TEAR = art("T", "T")
internal val SWEAT = art(".T", "TT")
internal val NOTE = art(".bb", ".b.", "bb.")
internal val SMOKE = art(".GG.", "GGGG", ".GG.")
internal val SMOKE_S = art("GG", "GG")
internal val QMARK = art("KKK", "..K", ".KK", "...", ".K.")
internal val EXCL = art("R", "R", "R", ".", "R")
internal val ANGER = art(".R.R.", "RR.RR", ".....", "RR.RR", ".R.R.")
internal val MOTION = art("G", "G", "G")
internal val MOUSE = art("GG...GG", "GpGGGpG", "GGKGKGG", "GGGpGGG", ".GGGGG.", ".G...G.")
internal val MOUSE_RUN = art("GG...GG", "GpGGGpG", "GGKGKGG", "GGGpGGG", ".GGGGG.", "G.....G")
internal val MOUSE_HAPPY = art("GG...GG", "GpGGGpG", "GKKGKKG", "GPGpGPG", ".GGGGG.", ".G...G.")
internal val CONF_R = art("R")
internal val CONF_Y = art("Y")
internal val CONF_B = art("B")
internal val CONF_N = art("N")
internal val CONF_P = art("P")
internal val CONF_V = art("V")

// 三批合在一起，按名字找
internal val STICKER_DEFS_RAW: Map<String, StickerDef> by lazy {
    stickerDefsA() + stickerDefsB() + stickerDefsC()
}
internal val STICKER_DEFS: Map<String, StickerDef> by lazy {
    STICKER_DEFS_RAW.mapKeys { normalizeStickerName(it.key) }
}

// 画布 30×22 格，崽崽的 16×10 格放在 (7, 9)
private const val GRID_W = 30
private const val GRID_H = 22
private const val CRAB_X = 7
private const val CRAB_Y = 9
private val BLUSH = Color(0xFFF2A0A8)

internal class StickerPlayer(val d: ClawdRigData, val def: StickerDef) {
    val total = def.keys.sumOf { it.ms }.coerceAtLeast(1)

    fun keyAt(t: Long): SK {
        var r = (t % total).toInt()
        for (k in def.keys) {
            if (r < k.ms) return k
            r -= k.ms
        }
        return def.keys.last()
    }
}

internal fun DrawScope.drawSticker(p: StickerPlayer, t: Long) {
    val d = p.d
    val k = p.keyAt(t)
    val cell = floor(min(size.width / GRID_W, size.height / GRID_H)).coerceAtLeast(1f)
    val left = floor((size.width - GRID_W * cell) / 2f) + CRAB_X * cell
    val top = floor((size.height - GRID_H * cell) / 2f) + CRAB_Y * cell
    val sz = Size(cell + 0.5f, cell + 0.5f)

    fun dot(x: Int, y: Int, c: Color, mirror: Boolean) {
        val xx = if (mirror) 15 - x else x
        drawRect(c, Offset(left + xx * cell, top + y * cell), sz)
    }

    fun part(name: String, frame: String, ox: Int, oy: Int) {
        val pi = d.part(name)
        if (pi < 0) return
        var fi = d.frame(pi, frame)
        if (fi < 0) fi = d.restF[pi]
        if (fi < 0) return
        val spr = d.sprites[d.partSheet[pi]].getOrNull(fi) ?: return
        val bx = d.origin[pi][0] + ox
        val by = d.origin[pi][1] + oy
        var i = 0
        while (i + 2 < spr.size) {
            dot(bx + spr[i], by + spr[i + 1], Color(d.palette[spr[i + 2]]), k.mirror)
            i += 3
        }
    }

    fun prop(a: PixArt, x: Int, y: Int, mirror: Boolean, alpha: Float = 1f) {
        for (c in a.cells) {
            val col = if (alpha < 1f) c.c.copy(alpha = c.c.alpha * alpha) else c.c
            dot(x + c.x, y + c.y, col, mirror)
        }
    }

    part("shadow", "", k.dx, 0)
    k.props.filter { it.layer == 0 }.forEach { prop(it.art, it.x, it.y, k.mirror) }
    if (!k.hide) {
        part("legs", k.legs, k.dx + k.gx, k.dy + k.gy)
        part("body", k.body, k.dx + k.bx, k.dy + k.by)
        part("eyes", k.eyes, k.dx + k.bx + k.ex, k.dy + k.by + k.ey)
        if (k.blush) {
            val ox = k.dx + k.bx
            val oy = k.dy + k.by + k.ey
            for (x in listOf(3, 4, 11, 12)) dot(x + ox, 4 + oy, BLUSH, k.mirror)
        }
    }
    k.props.filter { it.layer == 1 }.forEach { prop(it.art, it.x, it.y, k.mirror) }
    if (!k.hide) {
        part("clawL", k.cl, k.dx + k.lx, k.dy + k.ly)
        part("clawR", k.cr, k.dx + k.rx, k.dy + k.ry)
    }
    k.props.filter { it.layer == 2 }.forEach { prop(it.art, it.x, it.y, k.mirror) }

    for (f in p.def.fx) {
        val ph = ((t + f.phase) % f.period).toFloat() / f.period
        val x = (f.x + f.drift * ph + f.sway * sin(ph * 4f * PI.toFloat())).roundToInt()
        val y = (f.y - f.rise * ph).roundToInt()
        val a = if (ph > .75f) (1f - ph) / .25f else 1f
        prop(f.art, x, y, false, a)
    }
}

@Composable
internal fun PixelSticker(d: ClawdRigData, def: StickerDef, modifier: Modifier = Modifier) {
    val player = remember(d, def) { StickerPlayer(d, def) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        var start = -1L
        var last = -1000L
        while (true) {
            withFrameNanos { n ->
                val ms = n / 1_000_000L
                if (start < 0L) start = ms
                val t = ms - start
                // 像素动作一格一格跳，20 帧就够，省电
                if (t - last >= 50L) {
                    last = t
                    now = t
                }
            }
        }
    }
    Canvas(modifier = modifier.clipToBounds()) {
        drawSticker(player, now)
    }
}
