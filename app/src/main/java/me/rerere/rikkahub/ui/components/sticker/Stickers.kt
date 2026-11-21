package me.rerere.rikkahub.ui.components.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.ui.components.ai.GridTile
import me.rerere.rikkahub.ui.components.ai.GridTileItem
import me.rerere.rikkahub.ui.components.message.ZeroWidthChars
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceGlass
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.ui.pages.chat.ClawdRigStore
import java.text.Collator
import java.util.Locale

// 崽崽表情包：消息里存的是文本 [崽崽:表情名]，发给模型的也是这段文本，只有显示时才换成图
// 有像素动作的表情（PixelSticker.kt 和 StickerDefs*.kt）画成会动的；没有的才用 assets/stickers/ 里的图

// tag："崽崽" 是像素崽崽，"表情" 是 assets/stickers/ 子目录里的图片表情（fileName 是相对路径）
data class Sticker(val name: String, val fileName: String, val tag: String = "崽崽")

// 全角半角都认：「哼!」也能对上「哼！」
internal fun normalizeStickerName(name: String): String = name.trim()
    .replace('！', '!')
    .replace('？', '?')
    .replace('～', '~')

object Stickers {
    private const val DIR = "stickers"

    @Volatile
    private var cached: List<Sticker>? = null

    @Volatile
    private var index: Map<String, Sticker> = emptyMap()

    // 按字节算的缓存，最多占 16MB
    private val bitmapCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // 图片目录里的，加上只有像素动作、没有图片的
    fun all(context: Context): List<Sticker> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val collator = Collator.getInstance(Locale.CHINA)
            val pngs = runCatching { context.assets.list(DIR) }.getOrNull().orEmpty()
                .filter { it.endsWith(".png", ignoreCase = true) }
                .map { Sticker(name = it.dropLast(4), fileName = it) }
            val have = pngs.map { normalizeStickerName(it.name) }.toSet()
            val extra = STICKER_DEFS_RAW.keys
                .filter { normalizeStickerName(it) !in have }
                .map { Sticker(name = it, fileName = "") }
            val list = (pngs + extra).sortedWith(compareBy(collator) { it.name })
            index = list.associateBy { normalizeStickerName(it.name) }
            cached = list
            return list
        }
    }

    fun find(context: Context, name: String): Sticker? {
        all(context)
        return index[normalizeStickerName(name)]
    }

    fun cachedBitmap(fileName: String): ImageBitmap? = bitmapCache.get(fileName)?.asImageBitmap()

    suspend fun loadBitmap(context: Context, fileName: String): ImageBitmap? {
        if (fileName.isEmpty()) return null
        bitmapCache.get(fileName)?.let { return it.asImageBitmap() }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("$DIR/$fileName").use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        } ?: return null
        bitmapCache.put(fileName, bitmap)
        return bitmap.asImageBitmap()
    }
}

fun stickerTag(name: String): String = "[崽崽:$name]"

fun imageStickerTag(name: String): String = "[表情:$name]"

data class StickerGroup(val title: String, val stickers: List<Sticker>)

// 图片表情：assets/stickers/index.json 是 分类 -> {名字: 相对路径}，消息里写 [表情:名字]
// 匹配时忽略空白和全半角标点
private val imageKeyStrip = Regex("""[\s，,。.！!？?、…·:：（）()~～"“”'‘’]""")

internal fun imageStickerKey(name: String): String = name.replace(imageKeyStrip, "")

object ImageStickers {
    @Volatile
    private var groups: List<StickerGroup>? = null

    @Volatile
    private var index: Map<String, Sticker> = emptyMap()

    fun groups(context: Context): List<StickerGroup> {
        groups?.let { return it }
        synchronized(this) {
            groups?.let { return it }
            val list = runCatching {
                val raw = context.assets.open("stickers/index.json").bufferedReader().use { it.readText() }
                val root = org.json.JSONObject(raw)
                root.keys().asSequence().map { title ->
                    val obj = root.getJSONObject(title)
                    StickerGroup(
                        title = title,
                        stickers = obj.keys().asSequence()
                            .map { n -> Sticker(name = n, fileName = obj.getString(n), tag = "表情") }
                            .toList(),
                    )
                }.toList()
            }.getOrDefault(emptyList())
            index = list.flatMap { it.stickers }.associateBy { imageStickerKey(it.name) }
            groups = list
            return list
        }
    }

    fun find(context: Context, name: String): Sticker? {
        groups(context)
        val key = imageStickerKey(name)
        if (key.isEmpty()) return null
        index[key]?.let { return it }
        // 名字写得不全时，只有一个能对上才算
        return index.entries.filter { it.key.contains(key) || key.contains(it.key) }.singleOrNull()?.value
    }

    fun names(context: Context): List<String> = groups(context).flatMap { g -> g.stickers.map { it.name } }
}

// 冒号全角半角都认
private val stickerTagRegex = Regex("""\[(崽崽|表情)[:：]([^\[\]\n]+)\]""")

private val StickerSize = 120.dp

sealed interface StickerSegment {
    data class Plain(val text: String) : StickerSegment
    data class Picture(val sticker: Sticker) : StickerSegment
}

fun List<StickerSegment>.isStickerOnly(): Boolean =
    isNotEmpty() && all { it is StickerSegment.Picture }

@Composable
fun rememberStickerSegments(text: String): List<StickerSegment> {
    val context = LocalContext.current
    return remember(text) {
        splitStickerSegments(text.replace(ZeroWidthChars, "")) { tag, name ->
            if (tag == "表情") ImageStickers.find(context, name) else Stickers.find(context, name)
        }
    }
}

// 把文字按 [崽崽:xxx] 切开，对不上的标签留在原文里
private fun splitStickerSegments(text: String, find: (String, String) -> Sticker?): List<StickerSegment> {
    if (!text.contains("崽崽") && !text.contains("表情")) return listOf(StickerSegment.Plain(text))
    val segments = mutableListOf<StickerSegment>()
    var textStart = 0
    fun addText(end: Int) {
        val piece = text.substring(textStart, end).trim('\n', '\r').trimEnd()
        if (piece.isNotBlank()) segments += StickerSegment.Plain(piece)
    }
    stickerTagRegex.findAll(text).forEach { match ->
        val sticker = find(match.groupValues[1], match.groupValues[2]) ?: return@forEach
        addText(match.range.first)
        segments += StickerSegment.Picture(sticker)
        textStart = match.range.last + 1
    }
    // 一个都没对上，原文不动
    if (segments.isEmpty()) return listOf(StickerSegment.Plain(text))
    addText(text.length)
    return segments
}

@Composable
fun StickerImage(
    sticker: Sticker,
    modifier: Modifier = Modifier,
    contentDescription: String? = sticker.name,
) {
    // 图片表情走 Coil，GIF 会动（单例 ImageLoader 里装了 GIF 解码）
    if (sticker.tag == "表情") {
        AsyncImage(
            model = "file:///android_asset/stickers/${sticker.fileName}",
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
        return
    }
    val def = remember(sticker.name) { STICKER_DEFS[normalizeStickerName(sticker.name)] }
    if (def != null) {
        val context = LocalContext.current
        var rig by remember { mutableStateOf(ClawdRigStore.peek()) }
        var tried by remember { mutableStateOf(rig != null) }
        LaunchedEffect(Unit) {
            if (rig == null) rig = ClawdRigStore.load(context)
            tried = true
        }
        val d = rig
        if (d != null) {
            PixelSticker(d = d, def = def, modifier = modifier)
            return
        }
        // 还在读动作文件，先空着，免得闪一下旧图
        if (!tried) {
            Box(modifier = modifier)
            return
        }
    }
    if (sticker.fileName.isNotEmpty()) {
        PngSticker(sticker = sticker, modifier = modifier, contentDescription = contentDescription)
    } else {
        Box(modifier = modifier)
    }
}

@Composable
private fun PngSticker(
    sticker: Sticker,
    modifier: Modifier,
    contentDescription: String?,
) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = Stickers.cachedBitmap(sticker.fileName), sticker.fileName) {
        if (value == null) value = Stickers.loadBitmap(context, sticker.fileName)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                // 像素图关掉平滑缩放，放大了也清楚
                filterQuality = FilterQuality.None,
            )
        }
    }
}

// 只有表情没有字的消息：不套气泡，直接显示
@Composable
fun StickerOnlyBlock(
    segments: List<StickerSegment>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEach { segment ->
            if (segment is StickerSegment.Picture) {
                StickerImage(sticker = segment.sticker, modifier = Modifier.size(StickerSize))
            }
        }
    }
}

// 文字和表情混在一起的消息：文字照常走 Markdown，表情换成崽崽
@Composable
fun StickerMarkdownBlock(
    segments: List<StickerSegment>,
    onClickCitation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val single = segments.singleOrNull()
    if (single is StickerSegment.Plain) {
        // 没有表情的普通消息，跟原来完全一样
        MarkdownBlock(content = single.text, onClickCitation = onClickCitation, modifier = modifier)
        return
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is StickerSegment.Plain -> MarkdownBlock(
                    content = segment.text,
                    onClickCitation = onClickCitation,
                )

                is StickerSegment.Picture -> StickerImage(
                    sticker = segment.sticker,
                    modifier = Modifier.size(StickerSize),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StickerPickerSheet(
    onDismiss: () -> Unit,
    onPick: (tag: String) -> Unit,
    // 点一下：放进输入框，接着打字；长按：直接发
    onInsert: (tag: String) -> Unit,
) {
    val context = LocalContext.current
    val stickers = remember { Stickers.all(context) }
    val groups = remember { ImageStickers.groups(context) }
    val tabs = remember(groups) { listOf("崽崽") + groups.map { it.title } }
    var tab by remember { mutableIntStateOf(0) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        val c = palaceColors()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .padding(horizontal = 16.dp),
        ) {
            // 顶上一排分类：崽崽、小狗豹、chuguming、杂七杂八
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tabs.forEachIndexed { i, title ->
                    val selected = i == tab
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) c.ink2 else c.mute,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) c.ink2.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable { tab = i }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            val group = if (tab == 0) null else groups.getOrNull(tab - 1)
            if (group == null) {
                if (stickers.isEmpty()) {
                    Text(
                        text = "还没有表情",
                        fontSize = 13.sp,
                        color = c.mute,
                        modifier = Modifier.padding(4.dp),
                    )
                } else {
                    // 外壳和加号面板同一套：磨砂卡，里面 4 列方块
                    PalaceGlass(modifier = Modifier.weight(1f)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(stickers, key = { it.name }) { sticker ->
                                // 复用加号面板的方块：圆底只当背景，里面放崽崽图，不裁切
                                // 像素图本身在 PngSticker（FilterQuality.None）和 PixelSticker（Canvas 画格子）里保持硬边
                                GridTileItem(
                                    tile = GridTile(
                                        icon = StickerSmileIcon,
                                        title = sticker.name,
                                        status = null,
                                        leading = {
                                            StickerImage(
                                                sticker = sticker,
                                                modifier = Modifier.size(44.dp),
                                                // 名字就写在下面，读屏不用重复念
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = { onInsert(stickerTag(sticker.name)) },
                                    onLongClick = { onPick(stickerTag(sticker.name)) },
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            } else {
                PalaceGlass(modifier = Modifier.weight(1f)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(group.stickers, key = { it.name }) { sticker ->
                            StickerImage(
                                sticker = sticker,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = { onInsert(imageStickerTag(sticker.name)) },
                                        onLongClick = { onPick(imageStickerTag(sticker.name)) },
                                    ),
                                contentDescription = sticker.name,
                            )
                        }
                    }
                }
            }
        }
    }
}

// 表情包入口的笑脸，线条粗细跟 HugeIcons stroke 一致
val StickerSmileIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "StickerSmile",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 脸
            moveTo(22f, 12f)
            arcTo(10f, 10f, 0f, false, true, 2f, 12f)
            arcTo(10f, 10f, 0f, false, true, 22f, 12f)
            close()
            // 嘴
            moveTo(8f, 15f)
            curveTo(8.9f, 16.2f, 10.4f, 17f, 12f, 17f)
            curveTo(13.6f, 17f, 15.1f, 16.2f, 16f, 15f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // 眼睛
            moveTo(8.99f, 9f)
            lineTo(9.01f, 9f)
            moveTo(14.99f, 9f)
            lineTo(15.01f, 9f)
        }
    }.build()
}
