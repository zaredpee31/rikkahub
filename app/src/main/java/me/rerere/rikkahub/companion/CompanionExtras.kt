package me.rerere.rikkahub.companion

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.rikkahub.ui.components.sticker.Sticker
import me.rerere.rikkahub.ui.components.sticker.Stickers
import kotlin.math.roundToInt
import kotlin.random.Random

/* 消息页里除了文字以外的几种东西：崽崽表情、骰子、语音条。
 * 存的都是纯文本暗号，模型看到的也是这段文本，只有显示的时候才画出来：
 *   [崽崽:表情名]   [骰子:4]（不带点数时按消息算一个固定点数）   [语音:5]转写的文字 */

internal sealed interface LineKind {
    data class Plain(val text: String) : LineKind
    data class StickerLine(val sticker: Sticker) : LineKind
    data class Dice(val value: Int) : LineKind
    data class Voice(val seconds: Int, val text: String) : LineKind
}

private val inlineTag = Regex("""\[崽崽[:：][^\[\]\n]+\]|\[骰子(?:[:：][1-6])?\]""")
private val stickerOnly = Regex("""^\[崽崽[:：]([^\[\]\n]+)\]$""")
private val diceOnly = Regex("""^\[骰子(?:[:：]([1-6]))?\]$""")
private val voiceLine = Regex("""^\[语音(?:[:：](\d{1,3}))?\]\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)

/** 阿辞的一行里夹着表情或骰子时拆开，各占一条；语音行整行留着。 */
internal fun splitSpecial(line: String): List<String> {
    if (!line.contains('[') || line.startsWith("[语音")) return listOf(line)
    val out = mutableListOf<String>()
    var start = 0
    inlineTag.findAll(line).forEach { m ->
        line.substring(start, m.range.first).trim().takeIf { it.isNotEmpty() }?.let(out::add)
        out += m.value
        start = m.range.last + 1
    }
    line.substring(start).trim().takeIf { it.isNotEmpty() }?.let(out::add)
    return out.ifEmpty { listOf(line) }
}

internal fun lineKind(context: Context, text: String, seed: String): LineKind {
    val t = text.trim()
    stickerOnly.matchEntire(t)?.let { m ->
        Stickers.find(context, m.groupValues[1])?.let { return LineKind.StickerLine(it) }
    }
    diceOnly.matchEntire(t)?.let { m ->
        return LineKind.Dice(m.groupValues[1].toIntOrNull() ?: (Math.floorMod(seed.hashCode(), 6) + 1))
    }
    voiceLine.matchEntire(t)?.let { m ->
        val body = m.groupValues[2].trim()
        if (body.isNotEmpty()) {
            val secs = m.groupValues[1].toIntOrNull() ?: (body.length / 4.5f).roundToInt()
            return LineKind.Voice(secs.coerceIn(1, 60), body)
        }
    }
    return LineKind.Plain(text)
}

/** 给模型和自己看的纯文本：语音只留转写，骰子写成点数。 */
internal fun plainOf(text: String): String = text
    .replace(Regex("""\[语音(?:[:：]\d{1,3})?\]\s*"""), "")
    .replace(Regex("""\[骰子[:：]([1-6])\]"""), "🎲$1")

/* ---------------- 骰子 ---------------- */

private val pipRed = Color(0xFFC9483F)

private fun pipsOf(v: Int): List<Pair<Int, Int>> = when (v) {
    1 -> listOf(1 to 1)
    2 -> listOf(0 to 0, 2 to 2)
    3 -> listOf(0 to 0, 1 to 1, 2 to 2)
    4 -> listOf(0 to 0, 2 to 0, 0 to 2, 2 to 2)
    5 -> listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2)
    else -> listOf(0 to 0, 0 to 1, 0 to 2, 2 to 0, 2 to 1, 2 to 2)
}

/** 白色小骰子；roll 为 true 时先乱滚一阵再停在 value 上。1 和 4 的点是红的。 */
@Composable
internal fun DiceFace(value: Int, roll: Boolean, modifier: Modifier = Modifier, size: Dp = 50.dp) {
    var shown by remember { mutableIntStateOf(if (roll) Random.nextInt(1, 7) else value) }
    val tilt = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    LaunchedEffect(value, roll) {
        if (!roll) { shown = value; return@LaunchedEffect }
        repeat(9) { i ->
            shown = Random.nextInt(1, 7)
            hop.snapTo(if (i % 2 == 0) -5f else 0f)
            tilt.animateTo(if (i % 2 == 0) 16f else -16f, tween(85))
        }
        shown = value
        hop.animateTo(0f, spring(dampingRatio = 0.35f))
        tilt.animateTo(0f, spring(dampingRatio = 0.4f))
    }
    Canvas(
        modifier
            .graphicsLayer { rotationZ = tilt.value; translationY = hop.value * density }
            .size(size)
            .shadow(3.dp, RoundedCornerShape(size * 0.24f), ambientColor = Color(0x22000000), spotColor = Color(0x33000000))
            .clip(RoundedCornerShape(size * 0.24f))
            .background(Color.White)
            .border(0.6.dp, Snow.hairline, RoundedCornerShape(size * 0.24f))
    ) {
        val s = this.size.minDimension
        val color = if (shown == 1 || shown == 4) pipRed else Snow.ink
        val r = if (shown == 1) s * 0.12f else s * 0.085f
        pipsOf(shown).forEach { (cx, cy) ->
            drawCircle(color, r, Offset(s * (0.26f + cx * 0.24f), s * (0.26f + cy * 0.24f)))
        }
    }
}

/* ---------------- 语音条 ---------------- */

@Composable
private fun Waves(flip: Boolean, playing: Boolean, color: Color) {
    val t = rememberInfiniteTransition(label = "waves")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
    Canvas(Modifier.size(14.dp).graphicsLayer { scaleX = if (flip) -1f else 1f }) {
        val m = size.minDimension
        val c = Offset(m * 0.18f, m / 2)
        drawCircle(color, m * 0.08f, c)
        for (i in 1..2) {
            val radius = m * 0.32f * i
            val a = if (!playing || phase.toInt() >= i) 1f else 0.22f
            drawArc(
                color = color.copy(alpha = a), startAngle = -48f, sweepAngle = 96f, useCenter = false,
                topLeft = Offset(c.x - radius, c.y - radius), size = Size(radius * 2, radius * 2),
                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/** 微信那样的语音条：越长越宽；自己的在右边，波纹朝左。 */
@Composable
internal fun VoiceBar(seconds: Int, mine: Boolean, playing: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.width((66 + seconds * 4).coerceAtMost(220).dp).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (mine) {
            Text("$seconds″", fontSize = 13.sp, color = Snow.ink)
            Spacer(Modifier.width(8.dp))
            Waves(flip = true, playing = playing, color = Snow.ink)
        } else {
            Waves(flip = false, playing = playing, color = Snow.ink)
            Spacer(Modifier.width(8.dp))
            Text("$seconds″", fontSize = 13.sp, color = Snow.ink)
        }
    }
}

/* ---------------- 按住说话 ---------------- */

/**
 * 按住开始，松开结束；手指往上滑出一段算取消。
 * onDown 返回 false 表示这次没开始（没权限、没配识别），后面的就不管了。
 * 手势被系统打断时也按取消收尾，不会一直录着。
 */
@Composable
internal fun HoldToTalk(
    recording: Boolean,
    cancelling: Boolean,
    onDown: () -> Boolean,
    onSlide: (Boolean) -> Unit,
    onUp: (cancelled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val down0 by rememberUpdatedState(onDown)
    val slide0 by rememberUpdatedState(onSlide)
    val up0 by rememberUpdatedState(onUp)
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (recording) Color(0xFFE2E4E8) else Snow.chip)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    if (!down0()) return@awaitEachGesture
                    val limit = 70.dp.toPx()
                    var cancelled = false
                    var ended = false
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val up = ch.position.y < -limit
                            if (up != cancelled) { cancelled = up; slide0(up) }
                            ch.consume()
                        }
                        ended = true
                        up0(cancelled)
                    } finally {
                        if (!ended) up0(true)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when {
                !recording -> "按住 说话"
                cancelling -> "松开 取消"
                else -> "松开 发送"
            },
            fontSize = 14.sp,
            color = if (cancelling) Snow.error else Snow.ink2,
        )
    }
}

/** 录音时浮在输入栏上面的小卡片：音量点、听到的字、怎么取消。 */
@Composable
internal fun RecordingCard(heard: String, amplitudes: List<Float>, cancelling: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .snowGlass(RoundedCornerShape(20.dp), strong = true, elevation = 6.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val recent = amplitudes.takeLast(14)
            repeat(14) { i ->
                val a = recent.getOrNull(i - (14 - recent.size))?.coerceIn(0f, 1f) ?: 0f
                Box(
                    Modifier.width(3.dp).height((4 + a * 18).dp).clip(RoundedCornerShape(2.dp))
                        .background(if (cancelling) Snow.error else Snow.ink2)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            heard.ifBlank { "在听…" }, fontSize = 13.sp, lineHeight = 18.sp,
            color = if (heard.isBlank()) Snow.mute else Snow.ink, maxLines = 4,
        )
        Spacer(Modifier.height(6.dp))
        MonoText(if (cancelling) "RELEASE TO CANCEL" else "SLIDE UP TO CANCEL", size = 8f, color = if (cancelling) Snow.error else Snow.mute)
    }
}

/* ---------------- 小部件 ---------------- */

/** 多选时消息左边的小圆圈。 */
@Composable
internal fun CheckDot(checked: Boolean) {
    Box(
        Modifier.size(18.dp).clip(CircleShape)
            .background(if (checked) Snow.dark else Color.White)
            .border(1.dp, if (checked) Snow.dark else Snow.faint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(HugeIcons.Tick02, null, Modifier.size(11.dp), tint = Color.White)
    }
}

/** 键盘小图标（切回打字用）。 */
@Composable
internal fun KeyboardGlyph(color: Color = Snow.ink2, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.4.dp.toPx()
        drawRoundRect(
            color, topLeft = Offset(w * 0.06f, h * 0.2f), size = Size(w * 0.88f, h * 0.6f),
            cornerRadius = CornerRadius(w * 0.12f), style = Stroke(stroke),
        )
        val dot = w * 0.045f
        for (row in 0..1) for (col in 0..4) {
            drawCircle(color, dot, Offset(w * (0.22f + col * 0.14f), h * (0.36f + row * 0.13f)))
        }
        drawLine(color, Offset(w * 0.32f, h * 0.66f), Offset(w * 0.68f, h * 0.66f), stroke, StrokeCap.Round)
    }
}
