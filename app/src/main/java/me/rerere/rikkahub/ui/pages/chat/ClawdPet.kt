package me.rerere.rikkahub.ui.pages.chat

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

// 一格 3dp，和 TT 里输入框上那只一样大
private val RIG_CELL = 3.dp

// 站在输入框上时往下沉多少；浮在半空就调大，陷进去就调小
private val FLOOR_SINK = 2.dp

// 不在随机池里、但闲着时也会做的小动作
private val IDLE_EXTRA = mapOf(
    "around" to 1.0, "spin" to 0.8, "lean" to 0.7, "scratch" to 0.8, "crouch" to 0.6,
    "tramp" to 0.5, "heart" to 0.6, "point" to 0.5, "facepalm" to 0.4, "hide" to 0.4,
)

// 彩棒每次随机一种甩法
private val STICK_FORMS = mapOf(
    "glowstick" to listOf("glowstick", "glowstickPump"),
    "glowstick2" to listOf("glowstick2", "glowstick2Pump", "glowstick2Alt"),
)

private val NEON = intArrayOf(
    0xFF7DFF9B.toInt(), 0xFFFF6BD6.toInt(), 0xFF6BD8FF.toInt(),
    0xFFFFE66B.toInt(), 0xFFB98CFF.toInt(), 0xFFFF9B5C.toInt(),
)

private enum class BigMode { Idle, Gen, Sleep }

internal fun rnd(a: Long, b: Long): Long = a + (Random.nextDouble() * (b - a)).toLong()

/** 大崽崽：一只按 clawd.json 一帧一帧播动作的崽崽 */
private class BigClawd(val d: ClawdRigData) {
    var cell = 0f
    var areaW = 0f
    var maxLift = 0f
    var x = -1f
    var lift = 0f
    var lastNow = 0L

    var clip: RigClip? = null
    var mirror = false
    var clipStart = 0L
    var onEnd: ((Long) -> Unit)? = null
    var palette: IntArray = d.palette

    var mode = BigMode.Idle
    var walkTo: Float? = null
    var held = false
    var air = false
    var vx = 0f
    var vy = 0f
    var nextIdle = 0L
    var lastTouch = 0L
    var genSince = 0L
    var genSat = false
    var sleepAt = 0L
    var sleepSince = 0L
    var peeking = false
    var scrollStop = 0L
    val cooldown = HashMap<String, Long>()
    val pokes = ArrayList<Long>()
    val throws = ArrayList<Long>()

    val pose = FloatArray(d.poseSize)
    val eyesP = d.part("eyes")
    val clawL = d.part("clawL")
    val clawR = d.part("clawR")
    val eyeOpen = d.frame(eyesP, "open").toFloat()
    val eyeHalf = d.frame(eyesP, "half").toFloat()
    val eyeShut = d.frame(eyesP, "shut").toFloat()

    val boxW get() = 16 * cell
    private fun base(p: Int) = RIG_HEAD + p * RIG_STRIDE

    fun play(id: String, now: Long, mir: Boolean = mirror, end: ((Long) -> Unit)? = null): Boolean {
        val c = d.clips[id] ?: return false
        clip = c
        mirror = mir
        clipStart = now
        onEnd = end
        palette = d.palette
        return true
    }

    fun stop() {
        clip = null
        onEnd = null
        palette = d.palette
    }

    /** 靠边时换镜像版，免得道具伸出屏幕 */
    fun fitMirror(id: String): Boolean {
        val e = d.extent(id)
        val okN = x - e[0] * cell >= 0f && x + (16 + e[1]) * cell <= areaW
        val okM = x - e[1] * cell >= 0f && x + (16 + e[0]) * cell <= areaW
        return when {
            okN && okM -> Random.nextFloat() < 0.3f
            okN -> false
            okM -> true
            else -> false
        }
    }

    fun resume(now: Long) {
        if (mode == BigMode.Gen) {
            play(if (genSat) "sitWrite" else "write", now, fitMirror("write"))
        } else {
            stop()
            nextIdle = now + rnd(4000, 9000)
        }
    }

    fun share() {
        ClawdShared.bigLeft = x
        ClawdShared.bigRight = x + boxW
        ClawdShared.bigGrounded = !held && !air && lift < 1f
    }

    fun step(now: Long, dt: Float, working: Boolean, scrolling: Boolean) {
        lastNow = now
        if (areaW <= 0f || cell <= 0f) return
        if (x < 0f) {
            x = areaW * (0.12f + Random.nextFloat() * 0.3f)
            nextIdle = now + 2500
            lastTouch = now
        }
        val maxX = (areaW - boxW).coerceAtLeast(0f)
        x = x.coerceIn(0f, maxX)

        clip?.let { c ->
            if (!c.loop && !c.hold && now - clipStart >= c.dur) {
                val e = onEnd
                stop()
                e?.invoke(now)
            }
        }

        if (air) {
            vy -= 300f * cell * dt
            lift += vy * dt
            x += vx * dt
            if (x <= 0f || x >= maxX) {
                vx = -vx * 0.5f
                x = x.coerceIn(0f, maxX)
            }
            if (lift >= maxLift) {
                lift = maxLift
                if (vy > 0f) vy = 0f
            }
            if (lift <= 0f) {
                lift = 0f
                air = false
                vx = 0f
                vy = 0f
                land(now)
            }
        }
        if (held || air) {
            share()
            return
        }

        walkTo?.let { tx ->
            val sp = cell / 0.3f * dt
            if (abs(tx - x) <= sp) {
                x = tx
                walkTo = null
                stop()
                nextIdle = now + rnd(3000, 8000)
            } else {
                x += if (tx > x) sp else -sp
            }
        }

        if (working && mode != BigMode.Gen) startGen(now)
        else if (!working && mode == BigMode.Gen) endGen(now)

        when (mode) {
            BigMode.Gen -> {
                val id = clip?.id
                if (!genSat && now - genSince > 12_000 && (id == "writeSend" || id == "write")) {
                    genSat = true
                    play("sitWrite", now, mirror)
                }
            }

            BigMode.Sleep -> {
                if (clip?.id == "drowsy" && now >= sleepAt) {
                    play("sleep", now, mirror)
                    sleepSince = now
                }
                if (clip == null) play("sleep", now, mirror)
                if (sleepSince > 0 && now - sleepSince > 600_000) wake(now)
            }

            BigMode.Idle -> {
                if (peeking) {
                    if (scrolling) {
                        scrollStop = now
                    } else if (now - scrollStop > 400) {
                        peeking = false
                        play("peekOut", now, mirror) { t ->
                            stop()
                            nextIdle = t + rnd(2000, 5000)
                        }
                    }
                } else if (clip == null && walkTo == null) {
                    when {
                        scrolling -> {
                            peeking = true
                            scrollStop = now
                            play("peek", now, mirror)
                        }

                        now - lastTouch > 240_000 -> {
                            mode = BigMode.Sleep
                            sleepAt = now + 9000
                            sleepSince = 0
                            play("drowsy", now, mirror)
                        }

                        now >= nextIdle -> pickIdle(now)
                    }
                }
            }
        }
        share()
    }

    fun startGen(now: Long) {
        mode = BigMode.Gen
        walkTo = null
        peeking = false
        genSince = now
        genSat = false
        play("writeSend", now, fitMirror("writeSend"))
    }

    fun endGen(now: Long) {
        mode = BigMode.Idle
        lastTouch = now
        play("done", now, mirror) { t ->
            stop()
            nextIdle = t + rnd(4000, 8000)
        }
    }

    fun wake(now: Long) {
        mode = BigMode.Idle
        lastTouch = now
        sleepSince = 0
        play("wake", now, mirror) { t ->
            stop()
            nextIdle = t + rnd(4000, 8000)
        }
    }

    private fun walkRange(): Pair<Float, Float> {
        val gap = 2 * cell
        var lo = 0f
        var hi = (areaW - boxW).coerceAtLeast(0f)
        for (r in ClawdShared.pileRects) {
            if (r[0] >= x + boxW - 1f) hi = min(hi, r[0] - boxW - gap)
            else if (r[1] <= x + 1f) lo = max(lo, r[1] + gap)
        }
        if (hi < lo) hi = lo
        return lo to hi
    }

    fun pickIdle(now: Long) {
        if (Random.nextFloat() < 0.35f) {
            val (lo, hi) = walkRange()
            if (hi - lo > 14 * cell) {
                val t = lo + Random.nextFloat() * (hi - lo)
                if (abs(t - x) > 10 * cell) {
                    walkTo = t
                    play("walkLoop", now, t > x)
                    return
                }
            }
        }
        val cands = ArrayList<Pair<String, Double>>()
        d.clips.values.forEach { c -> if (c.pool) cands += c.id to c.weight }
        IDLE_EXTRA.forEach { (id, w) -> if (d.clips.containsKey(id)) cands += id to w }
        val ok = cands.filter { (id, _) -> (cooldown[id] ?: 0L) <= now }
        if (ok.isEmpty()) {
            nextIdle = now + 5000
            return
        }
        var r = Random.nextDouble() * ok.sumOf { it.second }
        var pick = ok.last().first
        for ((id, w) in ok) {
            r -= w
            if (r <= 0) {
                pick = id
                break
            }
        }
        val c = d.clips[pick] ?: return
        cooldown[pick] = now + max(c.cool.toLong(), 20_000L)
        val real = STICK_FORMS[pick]?.random()?.takeIf { d.clips.containsKey(it) } ?: pick
        play(real, now, fitMirror(real)) { t ->
            stop()
            nextIdle = t + rnd(5000, 12000)
        }
        if (real.startsWith("glowstick")) palette = d.paletteWithSticks(NEON.random(), NEON.random())
    }

    // ---------- 手指 ----------

    fun press(now: Long) {
        lastTouch = now
        walkTo = null
        peeking = false
        if (mode != BigMode.Idle || air) return
        play("press", now, mirror)
    }

    fun tap(now: Long) {
        lastTouch = now
        if (mode == BigMode.Sleep) {
            wake(now)
            return
        }
        pokes.removeAll { now - it > 2500 }
        pokes += now
        val id = when (pokes.size) {
            1 -> if (Random.nextFloat() < 0.4f) "poke1Shy" else "poke1"
            2 -> "poke2"
            3 -> "poke3"
            4 -> "poke4"
            else -> {
                pokes.clear()
                if (Random.nextBoolean()) "sulk" else "rage"
            }
        }
        play(id, now, mirror) { t -> resume(t) }
    }

    fun pet(now: Long) {
        lastTouch = now
        if (mode == BigMode.Sleep) {
            wake(now)
            return
        }
        play("pet", now, mirror) { t -> resume(t) }
    }

    fun cancelPress(now: Long) {
        if (clip?.id == "press") resume(now)
    }

    fun grab(now: Long) {
        lastTouch = now
        held = true
        air = false
        walkTo = null
        peeking = false
        if (mode == BigMode.Sleep) {
            mode = BigMode.Idle
            sleepSince = 0
        }
        play("grab", now, false) { t ->
            if (held) play(if (Random.nextBoolean()) "drag" else "dragSwing", t, false)
        }
    }

    fun dragBy(dx: Float, dy: Float) {
        x = (x + dx).coerceIn(0f, (areaW - boxW).coerceAtLeast(0f))
        lift = (lift - dy).coerceIn(0f, maxLift)
    }

    fun release(fvx: Float, fvy: Float, now: Long) {
        held = false
        lastTouch = now
        vx = fvx.coerceIn(-2500f, 2500f)
        vy = (-fvy).coerceIn(-2500f, 2500f)
        air = true
        throws.removeAll { now - it > 20_000 }
        throws += now
        play("fly", now, false)
    }

    private fun land(now: Long) {
        val id = if (throws.size >= 3) {
            throws.clear()
            if (Random.nextBoolean()) "stomp" else "stompSlap"
        } else {
            "land"
        }
        play(id, now, false) { t -> resume(t) }
    }

    // ---------- 这一帧的样子 ----------

    fun fillPose(now: Long) {
        val c = clip
        if (c == null) {
            d.rest(pose)
            // 呼吸：只有两只钳子平滑地抬 1 格再放下
            val bp = now % 3200
            val by = when {
                bp < 1400 -> -ease(bp / 1400f)
                bp < 2600 -> -(1f - ease((bp - 1400) / 1200f))
                else -> 0f
            }
            if (clawL >= 0) pose[base(clawL) + 2] += by
            if (clawR >= 0) pose[base(clawR) + 2] += by
            blink(now)
        } else {
            val e = now - clipStart
            val t = when {
                !c.loop -> min(e, c.dur.toLong())
                e < c.dur -> e
                else -> c.intro + (e - c.intro) % (c.dur - c.intro).coerceAtLeast(1)
            }
            d.sample(c, t.toInt(), pose)
            if (eyesP >= 0 && !c.fParts[eyesP]) blink(now)
        }
    }

    private fun blink(now: Long) {
        if (eyesP < 0 || eyeOpen < 0) return
        val ph = now % 4400
        pose[base(eyesP)] = when {
            ph < 4000 || ph >= 4300 -> eyeOpen
            ph < 4100 || ph >= 4200 -> eyeHalf
            else -> eyeShut
        }
    }

    private fun ease(p: Float): Float {
        val q = p.coerceIn(0f, 1f)
        return q * q * (3 - 2 * q)
    }
}

/**
 * 聊天页里的小螃蟹桌宠
 * 动作照搬 TT 里 Claude Web 那只（clawd-rig.js），GitHub 打包前导出成 assets/clawd/clawd.json，这里一帧一帧播
 * 站在输入框上，自己走走停停、擦杯子、吃饭、读信、追蝴蝶……回消息时写字，写完蹦一下
 * 点一下戳它（越戳越烦），按住不动是轻抚，拖动拎起来，松手会掉下去摔成饼
 */
@Composable
fun ClawdPet(
    innerPadding: PaddingValues,
    listState: LazyListState,
    working: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var rig by remember { mutableStateOf(ClawdRigStore.peek()) }
    LaunchedEffect(Unit) {
        if (rig == null) rig = ClawdRigStore.load(context)
    }
    val d = rig ?: return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cell = with(density) { RIG_CELL.toPx() }
        val areaW = constraints.maxWidth.toFloat()
        val ceiling = with(density) { innerPadding.calculateTopPadding().toPx() }
        val floor = constraints.maxHeight.toFloat() -
            with(density) { (innerPadding.calculateBottomPadding() - FLOOR_SINK).toPx() }

        val big = remember(d) { BigClawd(d) }
        big.cell = cell
        big.areaW = areaW
        big.maxLift = (floor - ceiling - 12 * cell).coerceAtLeast(0f)

        val workingNow by rememberUpdatedState(working)
        val scrollingNow by rememberUpdatedState(listState.isScrollInProgress)
        var frame by remember { mutableLongStateOf(0L) }

        LaunchedEffect(big) {
            var last = 0L
            while (true) {
                val now = withFrameMillis { it }
                val dt = if (last == 0L) 16L else (now - last).coerceIn(0L, 50L)
                last = now
                big.step(now, dt / 1000f, workingNow, scrollingNow)
                frame = now
            }
        }

        ClawdPileLayer(
            areaW = areaW,
            floor = floor,
            scrolling = scrollingNow,
            now = { frame },
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val now = frame
            if (big.x < 0f) return@Canvas
            big.fillPose(now)
            drawRig(
                d = d,
                pose = big.pose,
                left = big.x,
                top = floor - 10 * cell - big.lift,
                cell = cell,
                mirror = big.mirror,
                clipGround = big.clip?.clipGround == true,
                palette = big.palette,
            )
        }

        val hitW = with(density) { (20 * cell).toDp() }
        val hitH = with(density) { (14 * cell).toDp() }
        Box(
            modifier = Modifier
                .offset {
                    @Suppress("UNUSED_VARIABLE") val f = frame
                    IntOffset(
                        (big.x - 2 * cell).roundToInt(),
                        (floor - 13 * cell - big.lift).roundToInt(),
                    )
                }
                .size(hitW, hitH)
                .semantics { contentDescription = "小螃蟹，点一下戳它，按住不动摸摸它，拖动拎起来" }
                .pointerInput(big) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        big.press(big.lastNow)
                        val slop = viewConfiguration.touchSlop
                        val start = SystemClock.uptimeMillis()
                        var moved = false
                        var petting = false
                        var ended = false
                        var total = Offset.Zero
                        var vxs = 0f
                        var vys = 0f
                        while (true) {
                            val ev: PointerEvent? = if (!moved && !petting) {
                                val remain = 600L - (SystemClock.uptimeMillis() - start)
                                if (remain <= 0L) null else withTimeoutOrNull(remain) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }
                            if (ev == null) {
                                petting = true
                                big.pet(big.lastNow)
                                continue
                            }
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) {
                                ch.consume()
                                ended = true
                                when {
                                    moved -> big.release(vxs, vys, big.lastNow)
                                    petting -> Unit
                                    else -> big.tap(big.lastNow)
                                }
                                break
                            }
                            val delta = ch.positionChange()
                            if (!moved) {
                                total += delta
                                if (total.getDistance() > slop) {
                                    moved = true
                                    big.grab(big.lastNow)
                                }
                            }
                            if (moved) {
                                big.dragBy(delta.x, delta.y)
                                val dtm = (ch.uptimeMillis - ch.previousUptimeMillis).coerceAtLeast(1L)
                                vxs = vxs * 0.6f + delta.x / dtm * 1000f * 0.4f
                                vys = vys * 0.6f + delta.y / dtm * 1000f * 0.4f
                            }
                            ch.consume()
                        }
                        if (!ended) {
                            if (moved) big.release(0f, 0f, big.lastNow) else big.cancelPress(big.lastNow)
                        }
                    }
                },
        )
    }
}
