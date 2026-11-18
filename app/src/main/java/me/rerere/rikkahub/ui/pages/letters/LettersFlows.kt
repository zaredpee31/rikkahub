package me.rerere.rikkahub.ui.pages.letters

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun PieceItem.senderName() = if (row.fromAi) "阿辞" else "小葵"

/** 点一块拼块。锁定的只晃一下，不去请求正文。 */
internal fun LettersController.tap(p: PieceItem) {
    if (busy) return
    when (p.state) {
        PieceState.Locked -> {
            shake(p.row.id)
            toast(if (p.row.lockType == "permanent") "这封信封存着，不会拼上" else "还有 ${p.daysLeft} 天，才拼得上")
        }
        PieceState.Fresh -> scope.launch { assemble(p) }
        else -> scope.launch { openPlaced(p) }
    }
}

/** 重读嵌在板上的信，或打开自己寄出、阿辞还没读的信。 */
private suspend fun LettersController.openPlaced(p: PieceItem) {
    val date = dateLabel(p.row.created)
    reading = Reading(date, p.senderName(), p.row.title, null, null)
    try {
        val b = LettersApi.read(p.row.id)
        reading = Reading(date, p.senderName(), b.title.ifBlank { p.row.title }, b.content, null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        reading = Reading(date, p.senderName(), p.row.title, null, "没拆开：${e.message ?: "连不上"}")
    }
}

/** 新来的：飞回格子 → 落位回弹 → 扫光 → 换成那句话 → 展开信纸。 */
private suspend fun LettersController.assemble(p: PieceItem) {
    val id = p.row.id
    busy = true
    val fetch = scope.async { runCatching { LettersApi.read(id) } }
    landingId = id
    delay(if (animate) 620 else 0)
    val got = fetch.await().getOrNull()
    if (got == null) {
        landingId = null
        busy = false
        toast("没拆开，稍后再试")
        return
    }
    flash(id)
    local.markRead(id)
    readIds = local.readIds()
    if (animate) scope.launch {
        sheen.snapTo(0.001f)
        sheen.animateTo(1f, tween(800))
        sheen.snapTo(0f)
    }
    val date = dateLabel(p.row.created)
    delay(300)
    setCaption(firstSentence(got.content), "FROM ACI · $date", "for you")
    delay(if (animate) 2300 else 100)
    landingId = null
    reading = Reading(date, "阿辞", got.title.ifBlank { p.row.title }, got.content, null)
    busy = false
}

internal fun LettersController.closeReading() {
    reading = null
    idleCaption()
}

/** 投递。成功才清草稿；失败留着草稿并提示。 */
internal suspend fun LettersController.send() {
    if (sending) return
    val d = draft
    val title = d.title.trim()
    val body = d.body.trim()
    if (title.isEmpty() || body.isEmpty()) {
        toast("标题和正文都写好，再投递")
        return
    }
    var unlock: String? = null
    if (d.timed) {
        val day = runCatching { LocalDate.parse(d.date) }.getOrNull()
        if (day == null || !day.isAfter(LocalDate.now())) {
            toast("选一个以后的日子")
            return
        }
        unlock = d.date
    }
    sending = true
    sendError = null
    val id = try {
        LettersApi.send(title, body, unlock)
    } catch (e: CancellationException) {
        sending = false
        throw e
    } catch (e: Exception) {
        sending = false
        sendError = "没寄出去：${e.message ?: "连不上"}。草稿还在。"
        toast("没寄出去，草稿还在")
        return
    }
    local.clearDraft()
    draft = LetterDraft("", "", false, "")
    sending = false
    val from = writeBounds
    writing = false
    if (animate && id.isNotBlank()) hiddenId = id
    refresh()
    boardIndex = boardCount - 1
    if (animate && from != Rect.Zero) {
        val floating = pieces.count { it.board == boardIndex && it.state != PieceState.Placed }
        val spot = Spots[(floating - 1).coerceAtLeast(0) % Spots.size]
        val density = stageDensity
        shatterTo = stageBounds.topLeft + Offset((spot.first + CELL_W / 2) * density, (spot.second + CELL_H / 2) * density)
        shatterFrom = from
        shatter.snapTo(0f)
        shatter.animateTo(1f, tween(1250, easing = LinearEasing))
        shatterFrom = null
    }
    hiddenId = null
    if (id.isNotBlank()) flash(id)
    if (unlock != null) toast("封好了，${daysLeft(unlock)} 天后才拼得上") else toast("寄出了，等阿辞读到就拼进去")
    setCaption("它在板边上漂着，等阿辞读", "SENT · ${dateLabel(LocalDate.now().toString())}", "sent")
}
