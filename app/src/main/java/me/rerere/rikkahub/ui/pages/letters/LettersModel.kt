package me.rerere.rikkahub.ui.pages.letters

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 板上一块拼块的状态，对应网页里的 in / ready / locked / sent。 */
internal enum class PieceState { Placed, Fresh, Locked, Sent }

/** 服务器 GET /companion/letters 的一行。锁定的信没有标题和正文。 */
internal data class LetterRow(
    val id: String,
    val fromAi: Boolean,
    val title: String,
    val created: String,
    val lockType: String,
    val unlockDate: String?,
    val canRead: Boolean,
    val readByAi: Boolean,
)

/** 一块拼块：哪一板、哪一格、什么状态。 */
internal data class PieceItem(
    val row: LetterRow,
    val board: Int,
    val slot: Int,
    val state: PieceState,
    val daysLeft: Int,
)

/** 板外漂着的位置：左上角 (x, y) dp，相对拼图板左上角；旋转角；缩放。 */
internal data class Spot(val x: Float, val y: Float, val rot: Float, val scale: Float)

internal const val GRID_COLS = 4
internal const val GRID_ROWS = 5
internal const val SLOTS = GRID_COLS * GRID_ROWS

// 拼图板尺寸（dp）：208x288，内边距 3；拼块要咬合，格距为 0
internal const val BOARD_W = 208f
internal const val BOARD_H = 288f
internal const val BOARD_PAD = 3f
internal const val BOARD_GAP = 0f
internal const val CELL_W = (BOARD_W - BOARD_PAD * 2 - BOARD_GAP * (GRID_COLS - 1)) / GRID_COLS
internal const val CELL_H = (BOARD_H - BOARD_PAD * 2 - BOARD_GAP * (GRID_ROWS - 1)) / GRID_ROWS

internal fun slotX(slot: Int): Float = BOARD_PAD + (slot % GRID_COLS) * (CELL_W + BOARD_GAP)
internal fun slotY(slot: Int): Float = BOARD_PAD + (slot / GRID_COLS) * (CELL_H + BOARD_GAP)

/** 一块拼块四条边：0 平边（板外圈）、1 凸、-1 凹。 */
internal data class Edges(val top: Int, val right: Int, val bottom: Int, val left: Int)

private fun edgeBit(r: Int, c: Int, k: Int): Boolean {
    var h = (r * 73856093) xor (c * 19349663) xor (k * 83492791) xor 0x2026
    h = h xor (h ushr 13)
    h *= 0x5bd1e995
    h = h xor (h ushr 15)
    return h and 1 == 1
}

/** 内部边方向由 (行, 列, 边) 固定决定；相邻两块同一条边一凸一凹。 */
internal fun edgesOf(slot: Int): Edges {
    val r = slot / GRID_COLS
    val c = slot % GRID_COLS
    fun h(rr: Int, cc: Int) = if (edgeBit(rr, cc, 0)) 1 else -1 // (rr,cc) 右边
    fun v(rr: Int, cc: Int) = if (edgeBit(rr, cc, 1)) 1 else -1 // (rr,cc) 下边
    return Edges(
        top = if (r == 0) 0 else -v(r - 1, c),
        right = if (c == GRID_COLS - 1) 0 else h(r, c),
        bottom = if (r == GRID_ROWS - 1) 0 else v(r, c),
        left = if (c == 0) 0 else -h(r, c - 1),
    )
}

internal fun idHash(id: String): Int {
    var h = 0x811c9dc5.toInt()
    for (ch in id) h = (h xor ch.code) * 16777619
    return h and Int.MAX_VALUE
}

private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

/** 服务器时间字符串转毫秒，认不出来返回 null。 */
internal fun parseTime(s: String?): Long? {
    if (s.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(s.take(19)).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDate.parse(s.take(10)).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
}

/** 还剩几天，至少 1 天。 */
internal fun daysLeft(unlockDate: String?, nowMs: Long = System.currentTimeMillis()): Int {
    val t = parseTime(unlockDate) ?: return 1
    val now = java.time.Instant.ofEpochMilli(nowMs).atZone(zone)
    val end = java.time.Instant.ofEpochMilli(t).atZone(zone)
    val hours = ChronoUnit.HOURS.between(now, end)
    return ((hours + 23) / 24).toInt().coerceAtLeast(1)
}

/** 信的日期显示：2026 · 11 · 18 */
internal fun dateLabel(created: String): String {
    val d = created.take(10).split("-")
    return if (d.size == 3) "${d[0]} · ${d[1]} · ${d[2]}" else created.take(10)
}

/**
 * 按创建时间排好，20 封一板。格子由信 id 哈希决定，撞了顺延到下一个空格，
 * 同一封信每次都在同一格。
 * 锁定的信不管谁写的都漂在板外；阿辞写的、可读但她没读过的是「新来的」；
 * 她写的、阿辞还没读的是「等阿辞读」。
 */
internal fun buildPieces(rows: List<LetterRow>, readByUser: Set<String>): List<PieceItem> {
    val out = ArrayList<PieceItem>(rows.size)
    var used = BooleanArray(SLOTS)
    rows.sortedBy { parseTime(it.created) ?: 0L }.forEachIndexed { i, r ->
        if (i % SLOTS == 0) used = BooleanArray(SLOTS)
        var slot = idHash(r.id) % SLOTS
        while (used[slot]) slot = (slot + 1) % SLOTS
        used[slot] = true
        val state = when {
            !r.canRead -> PieceState.Locked
            r.fromAi && r.id !in readByUser -> PieceState.Fresh
            !r.fromAi && !r.readByAi -> PieceState.Sent
            else -> PieceState.Placed
        }
        out += PieceItem(r, i / SLOTS, slot, state, if (state == PieceState.Locked) daysLeft(r.unlockDate) else 0)
    }
    return out
}

/** 即时信漂左边，定时、永久封存漂右边。 */
internal fun PieceItem.leftSide(): Boolean = row.lockType.isBlank() || row.lockType == "none"

/** 一板上每块信漂着的位置。左右各自竖排，超过 5 块排两列并缩小，不叠成一团。 */
internal fun floatSpots(pieces: List<PieceItem>): Map<String, Spot> {
    val out = HashMap<String, Spot>()
    for (left in booleanArrayOf(true, false)) {
        val side = pieces.filter { it.leftSide() == left }.sortedBy { it.slot }
        if (side.isEmpty()) continue
        val cols = if (side.size > 5) 2 else 1
        val perCol = (side.size + cols - 1) / cols
        val scale = if (cols == 2) .6f else .8f
        val step = BOARD_H / perCol
        side.forEachIndexed { i, p ->
            val col = i % cols
            val h = idHash(p.row.id)
            val reach = (if (cols == 1) 36f else if (col == 0) 28f else 58f) + (h % 7 - 3)
            val cx = if (left) -reach else BOARD_W + reach
            val cy = step * (i / cols + if (col == 1) .85f else .5f) + (h / 7 % 9 - 4)
            val rot = (6 + h / 63 % 9).toFloat() * (if ((i / cols + col) % 2 == 0) -1f else 1f)
            out[p.row.id] = Spot(cx - CELL_W / 2, cy - CELL_H / 2, rot, scale)
        }
    }
    return out
}

/** 取正文第一句，没有句号就取前 24 字。 */
internal fun firstSentence(body: String): String {
    val t = body.trim().lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.endsWith("：") && !it.endsWith(":") }
        ?: body.trim()
    val end = t.indexOfFirst { it == '。' || it == '！' || it == '？' || it == '!' || it == '?' }
    val s = if (end in 0 until t.length) t.substring(0, end + 1) else t
    return if (s.length > 28) s.take(28) + "…" else s
}
