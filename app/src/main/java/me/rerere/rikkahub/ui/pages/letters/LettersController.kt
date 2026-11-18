package me.rerere.rikkahub.ui.pages.letters

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** 正在读的一封信。body 为 null 表示还在取，error 不为 null 表示没取到。 */
internal data class Reading(val date: String, val from: String, val title: String, val body: String?, val error: String?)

internal const val IDLE_ZH = "点一块漂着的碎片，拆开它"
internal const val IDLE_EN = "EVERY FRAGMENT KEEPS A LETTER"

/** 信箱页的全部状态和动作；界面只读它、调它。 */
internal class LettersController(private val context: Context, val scope: CoroutineScope, val animate: Boolean) {
    val local = LettersLocal(context)

    var rows by mutableStateOf<List<LetterRow>?>(null)
    var loadError by mutableStateOf<String?>(null)
    var readIds by mutableStateOf(local.readIds())
    var image by mutableStateOf<ImageBitmap?>(null)
    var boardIndex by mutableIntStateOf(0)
    var hiddenId by mutableStateOf<String?>(null)

    /** 已经被叫回格子的已读拼块。不在这里的已读拼块还漂在两边。 */
    var homeIds by mutableStateOf<Set<String>>(emptySet())
    private var introDone = false
    private var landWait: CompletableDeferred<Unit>? = null
    private var landLeft = 0

    var landingId by mutableStateOf<String?>(null)
    var snapId by mutableStateOf<String?>(null)
    var snapTick by mutableIntStateOf(0)
    var shakeId by mutableStateOf<String?>(null)
    var shakeTick by mutableIntStateOf(0)
    val sheen = Animatable(0f)

    var capZh by mutableStateOf(IDLE_ZH)
    var capEn by mutableStateOf(IDLE_EN)
    var capWm by mutableStateOf("Letters")
    val capAlpha = Animatable(1f)

    var busy = false
    var reading by mutableStateOf<Reading?>(null)
    var writing by mutableStateOf(false)
    var draft by mutableStateOf(local.draft())
    var sending by mutableStateOf(false)
    var sendError by mutableStateOf<String?>(null)

    val shatter = Animatable(0f)
    var shatterFrom by mutableStateOf<Rect?>(null)
    var shatterTo by mutableStateOf(Offset.Zero)
    var stageBounds: Rect = Rect.Zero
    var writeBounds: Rect = Rect.Zero

    val pieces: List<PieceItem> get() = buildPieces(rows.orEmpty(), readIds)
    val boardCount: Int get() = maxOf(1, (pieces.size + SLOTS - 1) / SLOTS)

    val stageDensity: Float get() = context.resources.displayMetrics.density

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    suspend fun loadImage() {
        image = local.loadImage()
    }

    /** 取信。失败只记一句话，不丢掉已经显示的信。 */
    suspend fun refresh() {
        try {
            rows = LettersApi.list()
            loadError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message ?: "连不上"
        }
        boardIndex = boardIndex.coerceIn(0, boardCount - 1)
        if (introDone) homeIds = homeIds + placedIds()
    }

    private fun placedIds(): Set<String> = pieces.filter { it.state == PieceState.Placed }.map { it.row.id }.toSet()

    /** 拼块飞到格子、动画真正结束时由拼块自己回调。 */
    fun landed(id: String) {
        flash(id)
        if (landWait != null && --landLeft <= 0) landWait?.complete(Unit)
    }

    /**
     * 进页面：全部先散在两边，停 600ms，已读的按随机顺序一块块叫回格子，
     * 每块间隔 180~260ms；落位由各块完成回调计数，全部落完再扫光。只播一次。
     */
    suspend fun playIntro() {
        if (introDone || rows == null) return
        introDone = true
        val all = placedIds()
        if (!animate) {
            homeIds = all
            return
        }
        val here = pieces.filter { it.board == boardIndex && it.state == PieceState.Placed }.map { it.row.id }.shuffled()
        homeIds = all - here.toSet()
        if (here.isEmpty()) return
        delay(600)
        val wait = CompletableDeferred<Unit>()
        landLeft = here.size
        landWait = wait
        for ((i, id) in here.withIndex()) {
            homeIds = homeIds + id
            if (i < here.lastIndex) delay(Random.nextLong(180, 261))
        }
        withTimeoutOrNull(3000) { wait.await() }
        landWait = null
        sheen.snapTo(0.001f)
        sheen.animateTo(1f, tween(800))
        sheen.snapTo(0f)
    }

    /** 文字区换一句：旧的淡出，换字，新的淡入。 */
    fun setCaption(zh: String, en: String, wm: String? = null) {
        scope.launch {
            if (animate) capAlpha.animateTo(0f, tween(450))
            capZh = zh
            capEn = en
            if (wm != null) capWm = wm
            if (animate) capAlpha.animateTo(1f, tween(450)) else capAlpha.snapTo(1f)
        }
    }

    fun idleCaption() = setCaption(IDLE_ZH, IDLE_EN, "Letters")

    fun saveDraft(d: LetterDraft) {
        draft = d
        local.saveDraft(d)
    }

    fun shake(id: String) {
        shakeId = id
        shakeTick++
    }

    fun flash(id: String) {
        snapId = id
        snapTick++
    }

    suspend fun pickImage(uri: Uri) {
        if (local.saveImage(uri)) {
            loadImage()
            toast("拼图换好了")
        } else {
            toast("这张图读不出来")
        }
    }

    suspend fun resetImage() {
        local.resetImage()
        loadImage()
        toast("已恢复默认拼图")
    }
}
