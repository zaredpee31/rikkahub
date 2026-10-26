package me.rerere.rikkahub.ui.pages.chat

import android.content.Context
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random

// 小螃蟹的配色
private val ClawdOrange = Color(0xFFD97757)
private val ClawdInk = Color(0xFF1F1E1D)
private val WizardBlue = Color(0xFF5A7BB5)
private val StarYellow = Color(0xFFF2C14E)
private val HatBrown = Color(0xFF8A5A3B)
private val HatBand = Color(0xFFB5524A)

// 像素格：13 宽 12 高，上面 4 行留给帽子
private const val GRID_W = 13
private const val GRID_H = 12
private const val HAT_ROWS = 4
private val PIXEL = 3.dp

// 站在输入框上时往下沉多少；浮在半空就调大，陷进去就调小
private val FLOOR_SINK = 2.dp

private enum class ClawdEyes { Open, Closed, Dizzy }
private enum class ClawdArms { Side, Up }
private enum class ClawdOutfit { Plain, Wizard, Cowboy, Shades }

private class ClawdPixel(val x: Int, val y: Int, val color: Color)

/** 按当前表情、动作、装扮拼出小螃蟹的每个像素 */
private fun buildClawd(
    outfit: ClawdOutfit,
    eyes: ClawdEyes,
    arms: ClawdArms,
    legFrame: Int,
): List<ClawdPixel> {
    val pixels = ArrayList<ClawdPixel>(128)
    // 身体坐标从帽子下面开始算
    fun body(x: Int, y: Int, color: Color = ClawdOrange) {
        pixels += ClawdPixel(x, y + HAT_ROWS, color)
    }
    fun hat(x: Int, y: Int, color: Color) {
        pixels += ClawdPixel(x, y, color)
    }

    // 身体
    for (y in 0..5) for (x in 2..10) body(x, y)

    // 钳子
    when (arms) {
        ClawdArms.Side -> {
            body(0, 4); body(1, 4); body(11, 4); body(12, 4)
        }
        ClawdArms.Up -> {
            body(1, 3); body(0, 2); body(0, 1)
            body(11, 3); body(12, 2); body(12, 1)
        }
    }

    // 腿，走路时两帧交替
    val legs = if (legFrame == 0) intArrayOf(3, 5, 7, 9) else intArrayOf(2, 4, 8, 10)
    legs.forEach { body(it, 6); body(it, 7) }

    // 眼睛
    if (outfit == ClawdOutfit.Shades && eyes != ClawdEyes.Dizzy) {
        for (x in 3..9) body(x, 1, ClawdInk)
        for (x in listOf(3, 4, 5, 7, 8, 9)) body(x, 2, ClawdInk)
        body(4, 1, Color.White)
        body(8, 1, Color.White)
    } else {
        when (eyes) {
            ClawdEyes.Open -> {
                body(4, 1, ClawdInk); body(4, 2, ClawdInk)
                body(8, 1, ClawdInk); body(8, 2, ClawdInk)
            }
            ClawdEyes.Closed -> {
                body(3, 2, ClawdInk); body(4, 2, ClawdInk)
                body(8, 2, ClawdInk); body(9, 2, ClawdInk)
            }
            ClawdEyes.Dizzy -> {
                listOf(0 to 0, 2 to 0, 1 to 1, 0 to 2, 2 to 2).forEach { (dx, dy) ->
                    body(3 + dx, 1 + dy, ClawdInk)
                    body(7 + dx, 1 + dy, ClawdInk)
                }
            }
        }
    }

    // 帽子
    when (outfit) {
        ClawdOutfit.Wizard -> {
            for (x in 2..10) hat(x, 3, WizardBlue)
            for (x in 4..8) hat(x, 2, WizardBlue)
            for (x in 5..7) hat(x, 1, WizardBlue)
            hat(6, 0, WizardBlue)
            hat(5, 2, StarYellow)
            hat(8, 3, StarYellow)
        }
        ClawdOutfit.Cowboy -> {
            for (x in 0..12) hat(x, 3, HatBrown)
            for (x in 3..9) hat(x, 2, HatBand)
            for (x in 3..9) hat(x, 1, HatBrown)
            for (x in 4..8) hat(x, 0, HatBrown)
        }
        else -> {}
    }
    return pixels
}

/**
 * 聊天页里的小螃蟹桌宠
 * 平时站在输入框上，会自己走、爬到消息顶上再爬下来
 * 点一下随机反应，长按换装扮，可以拖动，松手会掉下去
 */
@Composable
fun ClawdPet(
    innerPadding: PaddingValues,
    listState: LazyListState,
    working: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("clawd_pet", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val crabW = with(density) { (PIXEL * GRID_W).toPx() }
        val crabH = with(density) { (PIXEL * GRID_H).toPx() }
        val areaW by rememberUpdatedState(constraints.maxWidth.toFloat())
        // 脚能到的最高处：顶栏下面
        val ceiling by rememberUpdatedState(
            with(density) { innerPadding.calculateTopPadding().toPx() } + crabH
        )
        // 脚的最低处：输入框顶边
        val floor by rememberUpdatedState(
            constraints.maxHeight.toFloat() -
                with(density) { (innerPadding.calculateBottomPadding() - FLOOR_SINK).toPx() }
        )
        val workingNow by rememberUpdatedState(working)

        var outfit by remember {
            mutableStateOf(ClawdOutfit.entries.getOrElse(prefs.getInt("outfit", 0)) { ClawdOutfit.Plain })
        }
        var eyes by remember { mutableStateOf(ClawdEyes.Open) }
        var arms by remember { mutableStateOf(ClawdArms.Side) }
        var legFrame by remember { mutableIntStateOf(0) }
        var bubble by remember { mutableStateOf<String?>(null) }
        var posX by remember { mutableFloatStateOf(-1f) }
        // 不为空时表示悬空（爬、掉、被拎着），值是脚的位置
        var freeY by remember { mutableStateOf<Float?>(null) }
        // 站在哪条消息上，为空就是站在输入框上
        var platformKey by remember { mutableStateOf<Any?>(null) }
        var hop by remember { mutableFloatStateOf(0f) }
        var shake by remember { mutableFloatStateOf(0f) }
        var busy by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }

        // 某条消息在屏幕上的顶边
        fun platformTop(key: Any?): Float? {
            if (key == null) return null
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return null
            return (item.offset - info.viewportStartOffset).toFloat()
        }

        // 现在屏幕上能站的消息顶边
        fun platforms(): List<Pair<Any, Float>> {
            val info = listState.layoutInfo
            return info.visibleItemsInfo
                .map { it.key to (it.offset - info.viewportStartOffset).toFloat() }
                .filter { (_, top) -> top > ceiling + crabH * 0.5f && top < floor - crabH * 1.5f }
        }

        // 站着时脚的位置，跟着消息一起滚
        val standY by remember {
            derivedStateOf {
                platformKey?.let { platformTop(it) }
                    ?.coerceAtLeast(ceiling)?.coerceAtMost(floor)
                    ?: floor
            }
        }

        // 脚下的消息滚走了：从下面滑走就站回输入框，从上面滑走就掉下来
        LaunchedEffect(Unit) {
            snapshotFlow {
                val key = platformKey ?: return@snapshotFlow false
                val top = platformTop(key)
                top == null || top <= ceiling || top >= floor
            }.collect { lost ->
                if (!lost) return@collect
                val top = platformTop(platformKey)
                platformKey = null
                if (top == null || top < floor) {
                    freeY = (top ?: ceiling).coerceAtLeast(ceiling).coerceAtMost(floor)
                }
            }
        }

        // 眨眼
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(2500, 6000))
                if (eyes == ClawdEyes.Open && !busy && !dragging) {
                    eyes = ClawdEyes.Closed
                    delay(140)
                    if (eyes == ClawdEyes.Closed && !busy && !dragging && bubble == null) {
                        eyes = ClawdEyes.Open
                    }
                }
            }
        }

        // 小螃蟹自己的行动
        LaunchedEffect(Unit) {
            while (areaW <= 0f) delay(50)
            if (posX < 0f) posX = Random.nextFloat() * (areaW - crabW).coerceAtLeast(0f)

            fun interrupted() = busy || dragging

            // 歇一会，被戳、被拎、脚下没了都会提前结束
            suspend fun rest(millis: Long) {
                var left = millis
                while (left > 0) {
                    if (interrupted() || freeY != null) return
                    delay(50)
                    left -= 50
                }
            }

            suspend fun walkTo(target: Float) {
                val speed = with(density) { 40.dp.toPx() }
                var last = 0L
                var legTimer = 0f
                while (abs(target - posX) > 1f) {
                    if (interrupted() || freeY != null) break
                    val now = withFrameNanos { it }
                    if (last != 0L) {
                        val dt = (now - last) / 1_000_000_000f
                        val step = min(speed * dt, abs(target - posX))
                        posX += step * sign(target - posX)
                        legTimer += dt
                        if (legTimer > 0.14f) {
                            legFrame = 1 - legFrame
                            legTimer = 0f
                        }
                    }
                    last = now
                }
                legFrame = 0
            }

            // 竖着爬到某条消息顶上，key 为空就是爬回输入框
            suspend fun climbTo(key: Any?) {
                val speed = with(density) { 50.dp.toPx() }
                freeY = standY
                platformKey = null
                arms = ClawdArms.Up
                var last = 0L
                var legTimer = 0f
                while (true) {
                    if (interrupted()) break
                    val target = if (key == null) {
                        floor
                    } else {
                        (platformTop(key)?.takeIf { it > ceiling && it < floor } ?: break)
                    }
                    val y = freeY ?: break
                    if (abs(target - y) <= 1f) {
                        platformKey = key
                        freeY = null
                        break
                    }
                    val now = withFrameNanos { it }
                    if (last != 0L) {
                        val dt = (now - last) / 1_000_000_000f
                        val step = min(speed * dt, abs(target - y))
                        freeY = y + step * sign(target - y)
                        legTimer += dt
                        if (legTimer > 0.12f) {
                            legFrame = 1 - legFrame
                            legTimer = 0f
                        }
                    }
                    last = now
                }
                if (!interrupted()) {
                    arms = ClawdArms.Side
                    legFrame = 0
                }
            }

            // 悬空时往下掉，落到下面最近的消息或者输入框上
            suspend fun settle() {
                val start = freeY ?: return
                val landing = platforms().filter { it.second >= start - 1f }.minByOrNull { it.second }
                val target = landing?.second ?: floor
                arms = ClawdArms.Up
                val duration = (160 + abs(target - start) * 0.4f).toInt().coerceAtMost(700)
                animate(
                    initialValue = start,
                    targetValue = target,
                    animationSpec = tween(duration, easing = FastOutLinearInEasing),
                ) { v, _ -> if (!dragging) freeY = v }
                if (dragging) return
                platformKey = landing?.first
                freeY = null
                arms = ClawdArms.Side
                // 落地轻轻弹一下
                val bounce = with(density) { 4.dp.toPx() }
                animate(0f, -bounce, animationSpec = tween(90)) { v, _ -> hop = v }
                animate(-bounce, 0f, animationSpec = tween(140)) { v, _ -> hop = v }
            }

            suspend fun nap() {
                eyes = ClawdEyes.Closed
                bubble = "z z"
                rest(Random.nextLong(3000, 6000))
                if (!busy) {
                    eyes = ClawdEyes.Open
                    bubble = null
                }
            }

            while (true) {
                if (interrupted()) {
                    delay(100)
                    continue
                }
                if (freeY != null) {
                    settle()
                    continue
                }
                val onFloor = platformKey == null
                val r = Random.nextFloat()
                when {
                    r < 0.12f && onFloor -> {
                        val target = platforms().randomOrNull()
                        if (target != null) climbTo(target.first) else rest(1500)
                    }
                    r < 0.2f && !onFloor -> climbTo(null)
                    // 我在回你消息的时候它走得勤一点
                    r < (if (workingNow) 0.75f else 0.55f) ->
                        walkTo(Random.nextFloat() * (areaW - crabW).coerceAtLeast(0f))
                    r > 0.94f && !workingNow -> nap()
                    else -> rest(Random.nextLong(1200, 3500))
                }
            }
        }

        // 点一下：随机一种反应
        fun react() {
            if (busy || dragging) return
            busy = true
            scope.launch {
                try {
                    when (Random.nextInt(4)) {
                        0 -> {
                            // 蹦两下
                            arms = ClawdArms.Up
                            eyes = ClawdEyes.Closed
                            val h = with(density) { 22.dp.toPx() }
                            repeat(2) {
                                animate(0f, -h, animationSpec = tween(170, easing = FastOutSlowInEasing)) { v, _ -> hop = v }
                                animate(-h, 0f, animationSpec = tween(200, easing = FastOutLinearInEasing)) { v, _ -> hop = v }
                            }
                        }
                        1 -> {
                            // 挥钳子
                            bubble = "hi"
                            repeat(4) {
                                arms = ClawdArms.Up
                                delay(180)
                                arms = ClawdArms.Side
                                delay(180)
                            }
                        }
                        2 -> {
                            // 被戳晕了
                            eyes = ClawdEyes.Dizzy
                            bubble = "?"
                            val s = with(density) { 3.dp.toPx() }
                            repeat(6) {
                                shake = s
                                delay(60)
                                shake = -s
                                delay(60)
                            }
                            shake = 0f
                            delay(600)
                        }
                        else -> {
                            // 冒小心心
                            eyes = ClawdEyes.Closed
                            arms = ClawdArms.Up
                            bubble = "♥"
                            val h = with(density) { 8.dp.toPx() }
                            animate(0f, -h, animationSpec = tween(150)) { v, _ -> hop = v }
                            animate(-h, 0f, animationSpec = tween(180)) { v, _ -> hop = v }
                            delay(900)
                        }
                    }
                } finally {
                    eyes = ClawdEyes.Open
                    arms = ClawdArms.Side
                    bubble = null
                    hop = 0f
                    shake = 0f
                    busy = false
                }
            }
        }

        // 长按：换下一套装扮，并记住
        fun changeOutfit() {
            outfit = ClawdOutfit.entries[(outfit.ordinal + 1) % ClawdOutfit.entries.size]
            prefs.edit().putInt("outfit", outfit.ordinal).apply()
        }

        val pixels = remember(outfit, eyes, arms, legFrame) {
            buildClawd(outfit, eyes, arms, legFrame)
        }

        if (posX >= 0f) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (posX + shake).roundToInt(),
                            ((freeY ?: standY) - crabH + hop).roundToInt(),
                        )
                    }
                    .size(PIXEL * GRID_W, PIXEL * GRID_H)
                    .semantics { contentDescription = "小螃蟹，点一下戳它，长按换装扮，可以拖动" }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { react() },
                            onLongPress = { changeOutfit() },
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                freeY = freeY ?: standY
                                platformKey = null
                                hop = 0f
                                shake = 0f
                                arms = ClawdArms.Up
                                eyes = ClawdEyes.Open
                                bubble = "!"
                            },
                            onDragEnd = {
                                dragging = false
                                bubble = null
                            },
                            onDragCancel = {
                                dragging = false
                                bubble = null
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                posX = (posX + amount.x)
                                    .coerceAtLeast(0f)
                                    .coerceAtMost((areaW - crabW).coerceAtLeast(0f))
                                freeY = ((freeY ?: standY) + amount.y)
                                    .coerceAtLeast(ceiling)
                                    .coerceAtMost(floor)
                            },
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val p = size.width / GRID_W
                    pixels.forEach { px ->
                        drawRect(
                            color = px.color,
                            topLeft = Offset(px.x * p, px.y * p),
                            // 多画半个像素，避免格子之间漏缝
                            size = Size(p + 0.5f, p + 0.5f),
                        )
                    }
                }
                bubble?.let {
                    Text(
                        text = it,
                        color = ClawdOrange,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-14).dp),
                    )
                }
            }
        }
    }
}
