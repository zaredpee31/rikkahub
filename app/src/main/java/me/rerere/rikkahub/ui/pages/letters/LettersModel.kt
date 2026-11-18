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

internal const val GRID_COLS = 4
internal const val GRID_ROWS = 5
internal const val SLOTS = GRID_COLS * GRID_ROWS

// 网页拼图板尺寸（dp）：208x288，内边距 3，格距 2
internal const val BOARD_W = 208f
internal const val BOARD_H = 288f
internal const val BOARD_PAD = 3f
internal const val BOARD_GAP = 2f
internal const val CELL_W = (BOARD_W - BOARD_PAD * 2 - BOARD_GAP * (GRID_COLS - 1)) / GRID_COLS
internal const val CELL_H = (BOARD_H - BOARD_PAD * 2 - BOARD_GAP * (GRID_ROWS - 1)) / GRID_ROWS

internal fun slotX(slot: Int): Float = BOARD_PAD + (slot % GRID_COLS) * (CELL_W + BOARD_GAP)
internal fun slotY(slot: Int): Float = BOARD_PAD + (slot / GRID_COLS) * (CELL_H + BOARD_GAP)

/** 右侧凸耳：规则同网页 tr。 */
internal fun hasRightKnob(slot: Int): Boolean {
    val r = slot / GRID_COLS
    val c = slot % GRID_COLS
    return c < GRID_COLS - 1 && (r + c) % 2 == 0
}

/** 下侧凸耳：规则同网页 tb。 */
internal fun hasBottomKnob(slot: Int): Boolean {
    val r = slot / GRID_COLS
    return r < GRID_ROWS - 1 && (r * GRID_COLS + slot % GRID_COLS) % 2 == 1
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
 * 按创建时间排好，20 封一板。
 * 锁定的信不管谁写的都漂在板外；阿辞写的、可读但她没读过的是「新来的」；
 * 她写的、阿辞还没读的是「等阿辞读」。
 */
internal fun buildPieces(rows: List<LetterRow>, readByUser: Set<String>): List<PieceItem> =
    rows.sortedBy { parseTime(it.created) ?: 0L }.mapIndexed { i, r ->
        val state = when {
            !r.canRead -> PieceState.Locked
            r.fromAi && r.id !in readByUser -> PieceState.Fresh
            !r.fromAi && !r.readByAi -> PieceState.Sent
            else -> PieceState.Placed
        }
        PieceItem(r, i / SLOTS, i % SLOTS, state, if (state == PieceState.Locked) daysLeft(r.unlockDate) else 0)
    }

/** 取正文第一句，没有句号就取前 24 字。 */
internal fun firstSentence(body: String): String {
    val t = body.trim().lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.endsWith("：") && !it.endsWith(":") }
        ?: body.trim()
    val end = t.indexOfFirst { it == '。' || it == '！' || it == '？' || it == '!' || it == '?' }
    val s = if (end in 0 until t.length) t.substring(0, end + 1) else t
    return if (s.length > 28) s.take(28) + "…" else s
}
