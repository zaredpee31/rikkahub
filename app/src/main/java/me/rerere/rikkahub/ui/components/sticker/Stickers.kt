package me.rerere.rikkahub.ui.components.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import java.text.Collator
import java.util.Locale

// 崽崽表情包：图片放在 assets/stickers/ 下，文件名就是表情名
// 消息里存的是文本 [崽崽:表情名]，发给模型的也是这段文本，只有显示时才换成图片

data class Sticker(val name: String, val fileName: String)

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

    // 读一次目录就记住，以后往目录里加图，重新打包就能看到
    fun all(context: Context): List<Sticker> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val collator = Collator.getInstance(Locale.CHINA)
            val list = runCatching { context.assets.list(DIR) }.getOrNull().orEmpty()
                .filter { it.endsWith(".png", ignoreCase = true) }
                .map { Sticker(name = it.dropLast(4), fileName = it) }
                .sortedWith(compareBy(collator) { it.name })
            index = list.associateBy { normalize(it.name) }
            cached = list
            return list
        }
    }

    fun find(context: Context, name: String): Sticker? {
        all(context)
        return index[normalize(name)]
    }

    // 全角半角都认：「哼!」也能对上「哼！」
    private fun normalize(name: String): String = name.trim()
        .replace('！', '!')
        .replace('？', '?')
        .replace('～', '~')

    fun cachedBitmap(fileName: String): ImageBitmap? = bitmapCache.get(fileName)?.asImageBitmap()

    suspend fun loadBitmap(context: Context, fileName: String): ImageBitmap? {
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

// 冒号全角半角都认
private val stickerTagRegex = Regex("""\[崽崽[:：]([^\[\]\n]+)\]""")

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
    return remember(text) { splitStickerSegments(text) { Stickers.find(context, it) } }
}

// 把文字按 [崽崽:xxx] 切开，对不上内置图片的标签留在原文里
private fun splitStickerSegments(text: String, find: (String) -> Sticker?): List<StickerSegment> {
    if (!text.contains("崽崽")) return listOf(StickerSegment.Plain(text))
    val segments = mutableListOf<StickerSegment>()
    var textStart = 0
    fun addText(end: Int) {
        val piece = text.substring(textStart, end).trim('\n', '\r').trimEnd()
        if (piece.isNotBlank()) segments += StickerSegment.Plain(piece)
    }
    stickerTagRegex.findAll(text).forEach { match ->
        val sticker = find(match.groupValues[1]) ?: return@forEach
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

// 只有表情没有字的消息：不套气泡，直接显示图
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

// 文字和表情混在一起的消息：文字照常走 Markdown，表情换成图
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

@Composable
fun StickerPickerSheet(
    onDismiss: () -> Unit,
    onPick: (name: String) -> Unit,
) {
    val context = LocalContext.current
    val stickers = remember { Stickers.all(context) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = "表情包",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            if (stickers.isEmpty()) {
                Text(
                    text = "还没有表情，图片放在 assets/stickers/ 里",
                    fontSize = 13.sp,
                    color = Color(0xFF8A8885),
                    modifier = Modifier.padding(4.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(stickers, key = { it.fileName }) { sticker ->
                        StickerCell(sticker = sticker, onClick = { onPick(sticker.name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerCell(
    sticker: Sticker,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "发送", onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StickerImage(
            sticker = sticker,
            modifier = Modifier.size(64.dp),
            // 名字就写在下面，读屏不用重复念
            contentDescription = null,
        )
        Text(
            text = sticker.name,
            color = Color(0xFF8A8885),
            // 名字长的自动缩小，放得下
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
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
