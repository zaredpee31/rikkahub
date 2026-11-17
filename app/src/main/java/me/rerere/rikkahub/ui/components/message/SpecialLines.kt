package me.rerere.rikkahub.ui.components.message

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import kotlin.math.roundToInt
import kotlin.random.Random

/* 聊天里除了文字和表情包以外的两种东西：骰子、语音条。
 * 存的都是纯文本暗号，模型看到的也是这段文本，只有显示的时候才画出来：
 *   [骰子:4]（不带点数时按消息算一个固定点数）   [语音:5]转写的文字
 * 表情包 [崽崽:名] 不在这里处理，普通窗口原来就有（rememberStickerSegments）。 */

/** 一条消息拆开后的一段：连续的普通文字、一个骰子、一条语音。 */
sealed interface SpecialSegment {
    data class Text(val text: String) : SpecialSegment
    data class Dice(val value: Int) : SpecialSegment
    data class Voice(val seconds: Int, val text: String) : SpecialSegment
}

private val diceTag = Regex("""\[骰子(?:[:：]([1-6]))?\]""")
private val voiceLine = Regex("""^\[语音(?:[:：](\d{1,3}))?\]\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)

/** 文字里有没有骰子或语音暗号，没有就走原来的显示，不拆。 */
fun hasSpecial(text: String): Boolean =
    text.contains("[骰子") || text.contains("[语音")

/**
 * 按行拆：以 [语音 开头的整行是一条语音（后面的文字是转写）；
 * 其余行里的 [骰子] 单独拆出来，前后的文字各自合并成 Text 段。
 * 骰子不带点数时，用 [seed] 算一个固定点数，同一条消息每次显示一样。
 */
fun splitSpecialText(text: String, seed: String): List<SpecialSegment> {
    val out = mutableListOf<SpecialSegment>()
    val plain = StringBuilder()
    fun flushPlain() {
        val s = plain.toString().trim('\n')
        if (s.isNotBlank()) out += SpecialSegment.Text(s)
        plain.clear()
    }
    var diceIndex = 0
    text.split('\n').forEach { line ->
        val voice = voiceLine.matchEntire(line.trim())
        val body = voice?.groupValues?.get(2)?.trim().orEmpty()
        if (voice != null && body.isNotEmpty()) {
            flushPlain()
            val secs = voice.groupValues[1].toIntOrNull() ?: (body.length / 4.5f).roundToInt()
            out += SpecialSegment.Voice(secs.coerceIn(1, 60), body)
            return@forEach
        }
        var start = 0
        diceTag.findAll(line).forEach { m ->
            plain.append(line, start, m.range.first)
            flushPlain()
            val v = m.groupValues[1].toIntOrNull()
                ?: (Math.floorMod("$seed#${diceIndex}".hashCode(), 6) + 1)
            diceIndex++
            out += SpecialSegment.Dice(v)
            start = m.range.last + 1
        }
        plain.append(line, start, line.length).append('\n')
    }
    flushPlain()
    return out
}

/* ---------------- 骰子 ---------------- */

private val pipRed = Color(0xFFC9483F)
private val pipInk = Color(0xFF2F3237)
private val diceEdge = Color(0x14000000)

private fun pipsOf(v: Int): List<Pair<Int, Int>> = when (v) {
    1 -> listOf(1 to 1)
    2 -> listOf(0 to 0, 2 to 2)
    3 -> listOf(0 to 0, 1 to 1, 2 to 2)
    4 -> listOf(0 to 0, 2 to 0, 0 to 2, 2 to 2)
    5 -> listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2)
    else -> listOf(0 to 0, 0 to 1, 0 to 2, 2 to 0, 2 to 1, 2 to 2)
}

/** 白色小骰子；roll 为 true 时先乱滚一阵再停在 value 上。1 和 4 的点是红的。深色模式下也是白底。 */
@Composable
fun DiceFace(value: Int, roll: Boolean, modifier: Modifier = Modifier, size: Dp = 50.dp) {
    var shown by remember { mutableIntStateOf(if (roll) Random.nextInt(1, 7) else value) }
    val tilt = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    LaunchedEffect(value, roll) {
        if (!roll) {
            shown = value
            return@LaunchedEffect
        }
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
            .shadow(
                3.dp,
                RoundedCornerShape(size * 0.24f),
                ambientColor = Color(0x22000000),
                spotColor = Color(0x33000000),
            )
            .clip(RoundedCornerShape(size * 0.24f))
            .background(Color.White)
            .border(0.6.dp, diceEdge, RoundedCornerShape(size * 0.24f))
    ) {
        val s = this.size.minDimension
        val color = if (shown == 1 || shown == 4) pipRed else pipInk
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
    Canvas(Modifier.size(18.dp).graphicsLayer { scaleX = if (flip) -1f else 1f }) {
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

/** 微信那样的语音条：高度固定 40dp，宽度随内容、最小 72dp 最大 160dp；自己的在右边，波纹朝左。颜色跟着外面气泡的文字色走。 */
@Composable
fun VoiceBar(
    seconds: Int,
    mine: Boolean,
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .widthIn(min = 72.dp, max = 160.dp)
            .height(40.dp)
            .padding(horizontal = 12.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (mine) {
            VoiceSeconds(seconds, color)
            Spacer(Modifier.width(8.dp))
            Waves(flip = true, playing = playing, color = color)
        } else {
            Waves(flip = false, playing = playing, color = color)
            Spacer(Modifier.width(8.dp))
            VoiceSeconds(seconds, color)
        }
    }
}

@Composable
private fun VoiceSeconds(seconds: Int, color: Color) {
    Text("$seconds″", fontSize = 13.sp, color = color, maxLines = 1, softWrap = false)
}

/* ---------------- 按住说话 ---------------- */

/**
 * 按住开始，松开结束；手指往上滑出一段算取消。
 * onDown 返回 false 表示这次没开始（没权限、没配识别），后面的就不管了。
 * 手势被系统打断时也按取消收尾，不会一直录着。
 */
@Composable
fun HoldToTalk(
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
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (recording) colors.surfaceContainerHighest else colors.surfaceContainerHigh)
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
                            if (up != cancelled) {
                                cancelled = up
                                slide0(up)
                            }
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
            color = if (cancelling) colors.error else colors.onSurfaceVariant,
        )
    }
}

/** 录音时浮在输入栏上面的小卡片：音量点、听到的字、怎么取消。 */
@Composable
fun RecordingCard(heard: String, amplitudes: List<Float>, cancelling: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHighest,
        shadowElevation = 6.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.height(22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val recent = amplitudes.takeLast(14)
                repeat(14) { i ->
                    val a = recent.getOrNull(i - (14 - recent.size))?.coerceIn(0f, 1f) ?: 0f
                    Box(
                        Modifier.width(3.dp).height((4 + a * 18).dp).clip(RoundedCornerShape(2.dp))
                            .background(if (cancelling) colors.error else colors.onSurfaceVariant)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                heard.ifBlank { "在听…" }, fontSize = 13.sp, lineHeight = 18.sp,
                color = if (heard.isBlank()) colors.outline else colors.onSurface, maxLines = 4,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (cancelling) "松开取消" else "上滑取消",
                fontSize = 11.sp,
                color = if (cancelling) colors.error else colors.outline,
            )
        }
    }
}
