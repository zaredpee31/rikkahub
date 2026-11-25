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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

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
    /** 助手回复套不套气泡；不写就跟设置走 */
    val assistantBubble: Boolean? = null,
    /** 等回复时那个虚线胶囊 */
    val loadingText: String? = null,
    val loadingColor: String? = null,
    val loadingBorder: String? = null,
    val loadingBg: String? = null,
    /** 楼层、时间那条和按钮胶囊 */
    val floor: SkinFloor? = null,
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
    /** "capsule"：条纹顶 + 中间白胶囊（两边头像）+ 左右两个小图标；不写就是原来的排法 */
    val layout: String? = null,
    /** capsule 时状态栏下面那段的高度 */
    val height: Float = 64f,
    val shadow: Boolean = true,
    val capsuleText: String? = null,
    val capsuleTextColor: String? = null,
    val capsuleColor: String? = null,
    val capsuleBorder: String? = null,
    /** 左边图标图（点了开侧边栏）、右边图标图（点了新建对话） */
    val leftIcon: String? = null,
    val rightIcon: String? = null,
    val iconSize: Float = 26f,
    val capsuleWidth: Float = 190f,
    val capsuleTextSize: Float = 11f,
    val capsuleHeight: Float = 36f,
    /** 胶囊往下挪多少（负数往上） */
    val capsuleY: Float = 14f,
    val capsuleBorderWidth: Float = 1f,
    val capsuleShadow: Float = 3f,
    val leftAvatarSize: Float = 34f,
    val rightAvatarSize: Float = 30f,
    val avatarBorder: Float = 2f,
    /** 左右两个图标离顶多远 */
    val iconTop: Float = 4f,
    val iconBox: Float = 36f,
    val padX: Float = 14f,
    val shadowElevation: Float = 6f,
    val shadowAlpha: Float = 0.6f,
    /** layout="musi" 用：@名字、签名卡正文、签名卡右下角小字、拍立得底下的字、进度条两头的字 */
    val handle: String? = null,
    val quote: String? = null,
    val stats: String? = null,
    val coverLabel: String? = null,
    val progressLeft: String? = null,
    val progressRight: String? = null,
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
    /** true：靠头像那一侧上下两个角都用 tailRadius */
    val sideTail: Boolean = false,
    /** 靠头像那一侧的一条小色条 */
    val strip: String? = null,
    val stripWidth: Float = 3f,
    /** 头像旁边那行字，换掉名字；可以用 \n 分两行 */
    val name: String? = null,
    val nameColor: String? = null,
    val avatarSize: Float? = null,
    /** 头像下面、气泡上面那行小花字 */
    val caption: String? = null,
    val captionColor: String? = null,
    val captionSize: Float = 10f,
    /** "polaroid"：头像套拍立得框；avatarLabel 是框底下的小字 */
    val avatarFrame: String? = null,
    val avatarLabel: String? = null,
    /** true：气泡上半截叠一层白色高光 */
    val gloss: Boolean = false,
    /** 气泡里文字离边多远；不写用原来的 */
    val padX: Float? = null,
    val padY: Float? = null,
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
    /** "capsule"：加号、表情、发送挪到胶囊外面浮着，药盒和麦克风放到上面一排小方块 */
    val layout: String? = null,
    val shadow: Boolean = false,
    val plusIcon: String? = null,
    val stickerIcon: String? = null,
    val sendIcon: String? = null,
    val iconSize: Float = 30f,
    val chipColor: String? = null,
    val iconTint: String? = null,
    /** 上面那排小方块的图标：https 图片，或者直接写 emoji */
    val pillIcon: String? = null,
    val micIcon: String? = null,
    val keyboardIcon: String? = null,
    /** 三个浮着的图标各自大小，不写用 iconSize */
    val plusSize: Float? = null,
    val stickerSize: Float? = null,
    val sendSize: Float? = null,
    /** 各自往下挪多少（负数往上） */
    val plusY: Float = -10f,
    val stickerY: Float = 0f,
    val sendY: Float = 0f,
    /** 胶囊和两边图标的间距 */
    val gap: Float = 6f,
    /** 胶囊厚度：里面上下留白 */
    val padY: Float = 2f,
    val padX: Float = 8f,
    val shadowElevation: Float = 4f,
    val shadowAlpha: Float = 0.25f,
    val showChips: Boolean = true,
    /** true：加号、表情、发送也挪到上面那一排，胶囊独占一行 */
    val buttonsOnTop: Boolean = false,
    /** true：胶囊上半截叠一层白色渐变高光 */
    val gloss: Boolean = false,
    /** layout="musi" 用：胶囊上面那根进度条两头的字 */
    val progressLeft: String? = null,
    val progressRight: String? = null,
    val chipSize: Float = 34f,
    val chipRadius: Float = 10f,
    val chipGap: Float = 8f,
    val chipIconSize: Float = 22f,
    val chipEmojiSize: Float = 17f,
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

/** 下沿一排波浪，照马卡龙 CSS 的 mask：每 120 一个起伏，上下各 7。 */
class ScallopShape(private val period: Dp = 120.dp, private val amp: Dp = 7.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = with(density) { period.toPx() }
        val a = with(density) { amp.toPx() }
        val y0 = size.height - a
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, y0)
            var x = 0f
            while (x < size.width) {
                quadraticTo(x + p / 4, y0 - 2 * a, x + p / 2, y0)
                quadraticTo(x + p * 3 / 4, y0 + 2 * a, x + p, y0)
                x += p
            }
            lineTo(x, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/** 皮肤里的图标图，点了照原来的功能走。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SkinIconImage(
    url: String,
    size: Float,
    description: String,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    AsyncImage(
        model = url,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}

@Serializable
data class SkinFloor(
    val color: String? = null,
    val border: String? = null,
    val dashed: Boolean = true,
    val borderWidth: Float = 2f,
    val radius: Float = 999f,
    val text: String? = null,
    val subText: String? = null,
    val iconTint: String? = null,
    /** 助手那条的高度 */
    val height: Float = 48f,
    /** tok/s、上下文那行要不要 */
    val showStats: Boolean = true,
)

/** null：不在皮肤折叠模式；false：折着；true：点开了 */
val LocalSkinFold = staticCompositionLocalOf<Boolean?> { null }

/** 底色 + 虚线（或实线）圆角框 */
fun Modifier.skinDashed(bg: Color?, line: Color?, width: Float, radius: Float, dashed: Boolean = true): Modifier = this.drawBehind {
    val w = width.dp.toPx()
    val r = minOf(radius.dp.toPx(), size.height / 2)
    if (bg != null) drawRoundRect(bg, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
    if (line != null && w > 0f) {
        drawRoundRect(
            color = line,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w,
                pathEffect = if (dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null,
            ),
        )
    }
}
