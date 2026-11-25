package me.rerere.rikkahub.companion

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 聊天页皮肤。只管聊天页的外观，功能不变。每一项都可以不写，不写就用原来冷淡风那套。
 * 颜色写 "#RRGGBB" 或 "#AARRGGBB"（透明度在前）。尺寸单位都是 dp。图片写 https 链接。
 * 说明书在服务器 /home/ubuntu/SKIN.md。
 */
@Serializable
data class ChatSkin(
    val name: String = "未命名皮肤",
    val author: String? = null,
    val background: SkinFill? = null,
    val topBar: SkinTopBar? = null,
    val bubbleMe: SkinBubble? = null,
    val bubbleThem: SkinBubble? = null,
    val input: SkinInput? = null,
    val stickers: List<SkinSticker> = emptyList(),
)

/** 一块底：底色 → 竖向渐变 → 竖条纹 → 圆点 → 图片，按这个顺序往上叠。 */
@Serializable
data class SkinFill(
    val color: String? = null,
    val gradient: List<String>? = null,
    val stripes: List<String>? = null,
    val stripeWidth: Float = 10f,
    val dots: String? = null,
    val dotGap: Float = 28f,
    val dotRadius: Float = 2.5f,
    val image: String? = null,
    val imageAlpha: Float = 1f,
)

@Serializable
data class SkinTopBar(
    val fill: SkinFill? = null,
    /** 下沿要不要波浪边 */
    val wave: Boolean = true,
    /** 大字（名字）颜色 */
    val text: String? = null,
    /** 小字、图标颜色 */
    val subText: String? = null,
)

@Serializable
data class SkinBubble(
    val color: String? = null,
    val text: String? = null,
    val border: String? = null,
    val borderWidth: Float = 0f,
    val radius: Float? = null,
    /** 靠头像那个角的圆角 */
    val tailRadius: Float? = null,
    /** true 时不用 color，保留原来的磨砂玻璃 */
    val glass: Boolean = false,
    val shadow: Boolean = true,
)

@Serializable
data class SkinInput(
    val color: String? = null,
    val border: String? = null,
    val borderWidth: Float = 1f,
    val radius: Float? = null,
    val text: String? = null,
    val placeholder: String? = null,
    val placeholderColor: String? = null,
)

/** 贴在聊天背景上的小图，在消息下面一层，不挡点击。 */
@Serializable
data class SkinSticker(
    val image: String,
    /** topStart topCenter topEnd centerStart center centerEnd bottomStart bottomCenter bottomEnd */
    val anchor: String = "topEnd",
    val x: Float = 0f,
    val y: Float = 0f,
    val size: Float = 80f,
    val alpha: Float = 1f,
)

val LocalChatSkin = staticCompositionLocalOf<ChatSkin?> { null }

fun skinColor(raw: String?): Color? {
    val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrNull()
}

object SkinStore {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }
    private val _current = MutableStateFlow<ChatSkin?>(null)
    val current: StateFlow<ChatSkin?> = _current
    @Volatile
    private var loaded = false

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "skins").apply { mkdirs() }
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("chat_skin", Context.MODE_PRIVATE)

    fun ensure(ctx: Context) {
        if (loaded) return
        loaded = true
        _current.value = selectedId(ctx)?.let { load(ctx, it) }
    }

    fun parse(text: String): Result<ChatSkin> = runCatching {
        json.decodeFromString(ChatSkin.serializer(), text.trim().removePrefix("\uFEFF"))
    }

    private fun load(ctx: Context, id: String): ChatSkin? {
        val f = File(dir(ctx), "$id.json")
        if (!f.exists()) return null
        return parse(f.readText()).getOrNull()
    }

    fun list(ctx: Context): List<Pair<String, ChatSkin>> =
        dir(ctx).listFiles { f -> f.extension == "json" }
            .orEmpty()
            .sortedBy { it.lastModified() }
            .mapNotNull { f -> parse(f.readText()).getOrNull()?.let { f.nameWithoutExtension to it } }

    fun selectedId(ctx: Context): String? = prefs(ctx).getString("selected", null)

    /** 同名皮肤直接覆盖，改过的版本再导一次就是更新。 */
    fun import(ctx: Context, text: String): Result<String> = parse(text).map { skin ->
        val id = skin.name.filter { it.isLetterOrDigit() }.take(40).ifBlank { "skin" }
        File(dir(ctx), "$id.json").writeText(text.trim().removePrefix("\uFEFF"))
        if (selectedId(ctx) == id) _current.value = skin
        id
    }

    fun select(ctx: Context, id: String?) {
        prefs(ctx).edit().apply { if (id == null) remove("selected") else putString("selected", id) }.apply()
        _current.value = id?.let { load(ctx, it) }
    }

    fun delete(ctx: Context, id: String) {
        File(dir(ctx), "$id.json").delete()
        if (selectedId(ctx) == id) select(ctx, null)
    }
}

@Composable
fun rememberChatSkin(): ChatSkin? {
    val ctx = LocalContext.current
    remember { SkinStore.ensure(ctx) }
    return SkinStore.current.collectAsState().value
}

fun Modifier.skinFill(fill: SkinFill): Modifier = this.drawBehind {
    skinColor(fill.color)?.let { drawRect(it) }
    fill.gradient?.mapNotNull(::skinColor)?.takeIf { it.size >= 2 }?.let {
        drawRect(Brush.verticalGradient(it))
    }
    fill.stripes?.mapNotNull(::skinColor)?.takeIf { it.isNotEmpty() }?.let { colors ->
        val w = fill.stripeWidth.coerceAtLeast(1f).dp.toPx()
        var x = 0f
        var i = 0
        while (x < size.width) {
            drawRect(colors[i % colors.size], Offset(x, 0f), Size(w, size.height))
            x += w
            i++
        }
    }
    skinColor(fill.dots)?.let { c ->
        val gap = fill.dotGap.coerceAtLeast(4f).dp.toPx()
        val r = fill.dotRadius.dp.toPx()
        var y = gap / 2
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) gap / 2 else gap
            while (x < size.width) {
                drawCircle(c, r, Offset(x, y))
                x += gap
            }
            y += gap
            row++
        }
    }
}

@Composable
fun SkinFillLayer(fill: SkinFill, modifier: Modifier = Modifier) {
    Box(modifier = modifier.skinFill(fill)) {
        fill.image?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(fill.imageAlpha.coerceIn(0f, 1f)),
            )
        }
    }
}

private fun skinAnchor(name: String): Alignment = when (name) {
    "topStart" -> Alignment.TopStart
    "topCenter" -> Alignment.TopCenter
    "centerStart" -> Alignment.CenterStart
    "center" -> Alignment.Center
    "centerEnd" -> Alignment.CenterEnd
    "bottomStart" -> Alignment.BottomStart
    "bottomCenter" -> Alignment.BottomCenter
    "bottomEnd" -> Alignment.BottomEnd
    else -> Alignment.TopEnd
}

/** 聊天页最底层：皮肤背景 + 贴纸。background 为空时只画贴纸，底下仍是原来的背景。 */
@Composable
fun SkinBackground(skin: ChatSkin, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        skin.background?.let { SkinFillLayer(it, Modifier.fillMaxSize()) }
        skin.stickers.forEach { s ->
            AsyncImage(
                model = s.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(skinAnchor(s.anchor))
                    .offset(s.x.dp, s.y.dp)
                    .size(s.size.dp)
                    .alpha(s.alpha.coerceIn(0f, 1f)),
            )
        }
    }
}
