package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import kotlin.math.floor
import kotlin.random.Random

// 开屏先打招呼用的动作，按顺序找第一个有的
private val SPLASH_HELLO = listOf("wave", "hello", "hi", "greet", "hop", "jump", "happy")

/** 开屏崽崽：进来先打个招呼，之后站着，隔一会儿做个小动作 */
private class SplashClawd(val d: ClawdRigData) {
    val pose = FloatArray(d.poseSize).also { d.rest(it) }
    var clip: RigClip? = null
        private set
    private var start = 0L
    private var nextIdle = 0L
    private var greeted = false
    private val pool = d.clips.values.filter { it.pool && it.dur > 0 && it.times.isNotEmpty() }
    private val hello = SPLASH_HELLO
        .firstNotNullOfOrNull { id -> d.clips[id]?.takeIf { it.dur > 0 && it.times.isNotEmpty() } }
        ?: pool.maxByOrNull { it.weight }

    fun update(now: Long) {
        if (!greeted) {
            greeted = true
            if (hello != null) {
                clip = hello
                start = now + 250
            } else {
                nextIdle = now + 800
            }
        }
        val c = clip
        if (c == null) {
            d.rest(pose)
            if (now >= nextIdle && pool.isNotEmpty()) {
                clip = pool.random()
                start = now
            }
            return
        }
        val t = (now - start).toInt()
        if (t < 0) {
            d.rest(pose)
            return
        }
        if (t >= c.dur) {
            clip = null
            nextIdle = now + Random.nextLong(900L, 2200L)
            d.rest(pose)
            return
        }
        d.sample(c, t, pose)
        // 别跑出框
        pose[0] = pose[0].coerceIn(-3f, 3f)
        pose[1] = pose[1].coerceIn(-5f, 2f)
    }
}

@Composable
fun ClawdSplash(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var rig by remember { mutableStateOf(ClawdRigStore.peek()) }
    LaunchedEffect(Unit) {
        if (rig == null) rig = ClawdRigStore.load(context)
    }
    val d = rig
    if (d == null) {
        Box(modifier = modifier)
        return
    }
    val anim = remember(d) { SplashClawd(d) }
    val fade = remember { Animatable(0f) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(d) { fade.animateTo(1f, tween(220)) }
    LaunchedEffect(d) {
        while (true) {
            withFrameNanos { n ->
                val ms = n / 1_000_000L
                anim.update(ms)
                frame = ms
            }
        }
    }
    Canvas(modifier = modifier.alpha(fade.value)) {
        if (frame < 0L) return@Canvas
        // 16×10 格的崽崽，上面留出蹦起来的地方，脚站在底边
        val cell = floor(size.width / 20f).coerceAtLeast(1f)
        val left = floor((size.width - 16f * cell) / 2f)
        val top = floor(size.height - 11f * cell)
        drawRig(
            d = d,
            pose = anim.pose,
            left = left,
            top = top,
            cell = cell,
            mirror = false,
            clipGround = anim.clip?.clipGround == true,
            palette = d.palette,
        )
    }
}
