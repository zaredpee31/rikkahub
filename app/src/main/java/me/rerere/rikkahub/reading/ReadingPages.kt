@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package me.rerere.rikkahub.reading

import android.content.Context
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import java.util.Locale
import kotlin.math.roundToInt

// 浅色纸张，跟聊天页一个色系
private val Paper = Color(0xFFFAF9F5)
private val CardColor = Color.White
private val Ink = Color(0xFF1F1E1D)
private val Gray = Color(0xFF8A8885)
private val Accent = Color(0xFFD97757)
private val AccentSoft = Color(0xFFF7E7DF)
private val Line = Color(0xFFE8E6DC)
private val QuoteBg = Color(0xFFF0EEE6)
private val QuoteInk = Color(0xFF5E5D59)
private val CoverColors = listOf(
    Color(0xFFD97757), Color(0xFF8C9A7B), Color(0xFF7C8DB0), Color(0xFFC49A6C), Color(0xFFB5838D)
)

private fun strokeIcon(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    paths.forEach { d ->
        builder.addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

object ReadingIcons {
    val Book: ImageVector by lazy {
        strokeIcon(
            "Book",
            "M3 5.5C5.5 4.5 8.5 4.6 12 6.5C15.5 4.6 18.5 4.5 21 5.5V19C18.5 18 15.5 18.1 12 20C8.5 18.1 5.5 18 3 19Z",
            "M12 6.5V20",
        )
    }
    val Back: ImageVector by lazy { strokeIcon("Back", "M15 5L8 12L15 19") }
    val Toc: ImageVector by lazy {
        strokeIcon("Toc", "M9 6H20", "M9 12H20", "M9 18H20", "M4.5 6H4.51", "M4.5 12H4.51", "M4.5 18H4.51")
    }
    val Bubble: ImageVector by lazy {
        strokeIcon(
            "Bubble",
            "M4 6C4 4.9 4.9 4 6 4H18C19.1 4 20 4.9 20 6V14.5C20 15.6 19.1 16.5 18 16.5H10.5L6.5 20V16.5H6C4.9 16.5 4 15.6 4 14.5Z",
        )
    }
    val Plus: ImageVector by lazy { strokeIcon("Plus", "M12 5V19", "M5 12H19") }
    val Send: ImageVector by lazy { strokeIcon("Send", "M12 19V5", "M6 11L12 5L18 11") }
}

@Composable
private fun rememberBack(): () -> Unit {
    val owner = LocalOnBackPressedDispatcherOwner.current
    return { owner?.onBackPressedDispatcher?.onBackPressed() }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = Ink) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = CardColor,
        shadowElevation = 2.dp,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Pill(
    text: String,
    filled: Boolean,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = CircleShape
    Row(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(if (filled) Accent else CardColor)
            .border(1.dp, if (filled) Accent else Line, shape)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (filled) Color.White else Ink, style = MaterialTheme.typography.labelLarge)
    }
}

private fun wordCount(n: Int): String =
    if (n >= 10000) String.format(Locale.getDefault(), "%.1f 万字", n / 10000f) else "${n} 字"

// ================= 书架 =================

@Composable
fun BookshelfPage() {
    val context = LocalContext.current
    val nav = LocalNavController.current
    val back = rememberBack()
    val scope = rememberCoroutineScope()
    val version by Reading.version.collectAsState()

    var books by remember { mutableStateOf<List<Book>?>(null) }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Book?>(null) }

    LaunchedEffect(version) {
        books = withContext(Dispatchers.IO) { ReadingDb.get(context).books() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val result = runCatching { BookImporter.import(context, uri) }
            importing = false
            result.onSuccess {
                Reading.changed()
                nav.navigate(Screen.Reader(it))
            }.onFailure {
                message = "导入失败：" + (it.message ?: "未知错误")
            }
        }
    }
    val pick: () -> Unit = { picker.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(ReadingIcons.Back, "返回", back)
                Text(
                    text = "一起看书",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                )
                RoundButton(ReadingIcons.Plus, "导入 txt", pick, tint = Accent)
            }

            val list = books
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyShelf(onImport = pick)
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            onClick = { nav.navigate(Screen.Reader(book.id)) },
                            onLongClick = { toDelete = book },
                        )
                    }
                    item {
                        Text(
                            text = "长按一本书可以删除",
                            color = Gray,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        if (importing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Paper.copy(alpha = 0.85f))
                    .clickable(onClick = {}),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(12.dp))
                    Text("正在导入…", color = Gray)
                }
            }
        }
    }

    toDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Paper,
            title = { Text("删除《${book.title}》") },
            text = { Text("这本书的批注和段评也会一起删掉。") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch {
                        withContext(Dispatchers.IO) { ReadingDb.get(context).deleteBook(book.id) }
                        Reading.changed()
                    }
                }) { Text("删除", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("取消", color = Gray) }
            },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            containerColor = Paper,
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) { Text("知道了", color = Accent) }
            },
        )
    }
}

@Composable
private fun EmptyShelf(onImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(ReadingIcons.Book, null, tint = Gray, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("书架还是空的", color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("导入一本 txt，我们一起看", color = Gray, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Pill("导入 txt", filled = true, onClick = onImport)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun BookCard(book: Book, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardColor)
            .border(1.dp, Line, shape)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 58.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CoverColors[(book.id % CoverColors.size).toInt()]),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = book.title.take(1),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "读到第 ${book.lastChapter + 1} 章 · 共 ${book.chapterCount} 章 · ${wordCount(book.totalChars)}",
                color = Gray,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ================= 阅读器 =================

private class RestoreHolder {
    var chapter = -1
    var item = 0
    var offset = 0
    private var used = false

    // 只有第一次打开的那一章回到上次的位置
    fun take(idx: Int): Pair<Int, Int> {
        if (used) return 0 to 0
        used = true
        return if (idx == chapter) item to offset else 0 to 0
    }
}

/** 底部弹窗看的是哪一段；[threadId] 为 null 表示我在写一条新评论 */
private data class SheetTarget(val para: Int, val threadId: Long?)

@Composable
fun ReaderPage(bookId: Long) {
    val context = LocalContext.current
    val back = rememberBack()
    val settings = LocalSettings.current
    val userName = settings.displaySetting.userNickname.ifBlank { "我" }
    val db = remember { ReadingDb.get(context) }
    val prefs = remember { context.getSharedPreferences("reading", Context.MODE_PRIVATE) }

    var fontSize by remember { mutableFloatStateOf(prefs.getFloat("font_size", 18f)) }
    var lineHeight by remember { mutableFloatStateOf(prefs.getFloat("line_height", 1.8f)) }
    var showNotes by remember { mutableStateOf(prefs.getBoolean("show_notes", true)) }

    val version by Reading.version.collectAsState()
    val annotating by Reading.annotating.collectAsState()
    val annotateErrors by Reading.annotateErrors.collectAsState()
    val replying by Reading.replying.collectAsState()
    val replyErrors by Reading.replyErrors.collectAsState()

    val restore = remember { RestoreHolder() }
    var loaded by remember { mutableStateOf(false) }
    var book by remember { mutableStateOf<Book?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterInfo>>(emptyList()) }
    var chapterIdx by remember { mutableStateOf<Int?>(null) }
    var chapter by remember { mutableStateOf<Chapter?>(null) }
    var threads by remember { mutableStateOf<List<NoteThread>>(emptyList()) }

    var target by remember { mutableStateOf<SheetTarget?>(null) }
    var showToc by remember { mutableStateOf(false) }
    var showFont by remember { mutableStateOf(false) }
    var confirmRegen by remember { mutableStateOf(false) }

    LaunchedEffect(bookId) {
        val b = withContext(Dispatchers.IO) { db.book(bookId) }
        book = b
        if (b != null) {
            restore.chapter = b.lastChapter
            restore.item = b.lastItem
            restore.offset = b.lastOffset
            chapterIdx = b.lastChapter
        }
        loaded = true
    }
    LaunchedEffect(bookId, version) {
        chapters = withContext(Dispatchers.IO) { db.chapterList(bookId) }
    }
    LaunchedEffect(chapterIdx, version) {
        val idx = chapterIdx ?: return@LaunchedEffect
        chapter = withContext(Dispatchers.IO) { db.chapter(bookId, idx) ?: db.chapter(bookId, 0) }
    }
    val chapterId = chapter?.id
    LaunchedEffect(chapterId, version) {
        val id = chapterId ?: return@LaunchedEffect
        threads = withContext(Dispatchers.IO) { db.threads(id) }
    }
    val chThreads = remember(threads, chapterId) { threads.filter { it.chapterId == chapterId } }

    val goTo: (Int) -> Unit = { idx ->
        if (idx in chapters.indices) {
            target = null
            chapterIdx = idx
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        ReaderTopBar(
            title = book?.title.orEmpty(),
            subtitle = chapter?.title.orEmpty(),
            showNotes = showNotes,
            onBack = back,
            onToggleNotes = {
                showNotes = !showNotes
                prefs.edit().putBoolean("show_notes", showNotes).apply()
            },
            onFont = { showFont = true },
            onToc = { showToc = true },
        )
        val ch = chapter
        when {
            loaded && book == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("这本书找不到了", color = Gray)
            }

            ch == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }

            else -> key(ch.id) {
                ChapterReader(
                    bookId = bookId,
                    chapter = ch,
                    total = chapters.size,
                    restore = restore,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    showNotes = showNotes,
                    threads = chThreads,
                    running = ch.id in annotating,
                    error = annotateErrors[ch.id],
                    highlighted = target?.para,
                    onAnnotate = { Reading.annotate(context, ch.id) },
                    onRegenerate = { confirmRegen = true },
                    onOpenPara = { p -> target = SheetTarget(p, chThreads.firstOrNull { it.para == p }?.id) },
                    onNewComment = { p -> target = SheetTarget(p, null) },
                    onPrev = { goTo(ch.idx - 1) },
                    onNext = { goTo(ch.idx + 1) },
                )
            }
        }
    }

    val t = target
    val ch = chapter
    if (t != null && ch != null) {
        NotesSheet(
            paragraph = ch.paragraphs.getOrNull(t.para - 1).orEmpty(),
            threads = chThreads.filter { it.para == t.para },
            selected = t.threadId,
            replying = replying,
            replyErrors = replyErrors,
            userName = userName,
            onSelect = { id -> target = t.copy(threadId = id) },
            onNew = { target = t.copy(threadId = null) },
            onSend = { text ->
                val id = t.threadId
                if (id == null) {
                    Reading.newComment(context, ch.id, t.para, text) { newId ->
                        if (target?.para == t.para) target = SheetTarget(t.para, newId)
                    }
                } else {
                    Reading.reply(context, id, text)
                }
            },
            onRetry = { id -> Reading.retryReply(context, id) },
            onDismiss = { target = null },
        )
    }

    if (showToc) {
        TocSheet(
            chapters = chapters,
            current = chapter?.idx ?: 0,
            onPick = { idx ->
                showToc = false
                goTo(idx)
            },
            onDismiss = { showToc = false },
        )
    }

    if (showFont) {
        FontSheet(
            fontSize = fontSize,
            lineHeight = lineHeight,
            onFontSize = { v ->
                fontSize = v.coerceIn(14f, 26f)
                prefs.edit().putFloat("font_size", fontSize).apply()
            },
            onLineHeight = { v ->
                lineHeight = ((v * 10).roundToInt() / 10f).coerceIn(1.4f, 2.4f)
                prefs.edit().putFloat("line_height", lineHeight).apply()
            },
            onDismiss = { showFont = false },
        )
    }

    if (confirmRegen) {
        AlertDialog(
            onDismissRequest = { confirmRegen = false },
            containerColor = Paper,
            title = { Text("重新生成批注") },
            text = { Text("会换掉这一章 Claude 写的批注，下面的回复也一起换掉。你自己写的评论不动。新的没读出来的话，旧的会留着。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRegen = false
                    chapter?.let { Reading.annotate(context, it.id) }
                }) { Text("重新生成", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegen = false }) { Text("取消", color = Gray) }
            },
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    subtitle: String,
    showNotes: Boolean,
    onBack: () -> Unit,
    onToggleNotes: () -> Unit,
    onFont: () -> Unit,
    onToc: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(ReadingIcons.Back, "返回", onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = Gray,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(shape = CircleShape, color = CardColor, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(onToggleNotes) {
                    Icon(
                        ReadingIcons.Bubble,
                        "批注开关",
                        tint = if (showNotes) Accent else Gray,
                        modifier = Modifier.size(20.dp),
                    )
                }
                BarIcon(onFont) {
                    Text("Aa", color = Ink, style = MaterialTheme.typography.labelLarge)
                }
                BarIcon(onToc) {
                    Icon(ReadingIcons.Toc, "目录", tint = Ink, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun BarIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ChapterReader(
    bookId: Long,
    chapter: Chapter,
    total: Int,
    restore: RestoreHolder,
    fontSize: Float,
    lineHeight: Float,
    showNotes: Boolean,
    threads: List<NoteThread>,
    running: Boolean,
    error: String?,
    highlighted: Int?,
    onAnnotate: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenPara: (Int) -> Unit,
    onNewComment: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val initial = remember { restore.take(chapter.idx) }
    val listState = rememberLazyListState(initial.first, initial.second)

    // 停下来半秒后记住读到哪
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (item, offset) ->
                delay(600)
                withContext(Dispatchers.IO) {
                    runCatching { ReadingDb.get(context).saveProgress(bookId, chapter.idx, item, offset) }
                }
            }
    }

    val counts = remember(threads) { threads.groupingBy { it.para }.eachCount() }
    val aiCount = remember(threads) { threads.count { it.author == ROLE_AI } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = chapter.title,
                    color = Ink,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = (fontSize + 4).sp,
                        lineHeight = ((fontSize + 4) * 1.4f).sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Spacer(Modifier.height(14.dp))
                TogetherButton(
                    running = running,
                    error = error,
                    annotated = chapter.annotated || aiCount > 0,
                    count = aiCount,
                    onStart = onAnnotate,
                    onRegenerate = onRegenerate,
                )
            }
        }

        itemsIndexed(chapter.paragraphs) { i, text ->
            val para = i + 1
            val count = counts[para] ?: 0
            ParagraphItem(
                text = text,
                count = count,
                showNotes = showNotes,
                fontSize = fontSize,
                lineHeight = lineHeight,
                highlighted = highlighted == para,
                onClick = { if (showNotes && count > 0) onOpenPara(para) },
                onLongClick = { onNewComment(para) },
            )
        }

        item(key = "footer") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 28.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill("上一章", filled = false, enabled = chapter.idx > 0, onClick = onPrev)
                Text(
                    text = "${chapter.idx + 1} / ${total}",
                    color = Gray,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Pill("下一章", filled = true, enabled = chapter.idx < total - 1, onClick = onNext)
            }
        }
    }
}

@Composable
private fun TogetherButton(
    running: Boolean,
    error: String?,
    annotated: Boolean,
    count: Int,
    onStart: () -> Unit,
    onRegenerate: () -> Unit,
) {
    when {
        running -> Pill("正在读…", filled = false, loading = true)

        error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Pill("没读完，点这里重试", filled = true, onClick = onStart)
            Spacer(Modifier.height(6.dp))
            Text(error, color = Gray, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }

        annotated -> Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("已批注 · ${count} 条", filled = false)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "重新生成",
                color = Accent,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRegenerate)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        else -> Pill("一起看", filled = true, onClick = onStart)
    }
}

@Composable
private fun ParagraphItem(
    text: String,
    count: Int,
    showNotes: Boolean,
    fontSize: Float,
    lineHeight: Float,
    highlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val showBadge = showNotes && count > 0
    val annotated = buildAnnotatedString {
        append("\u3000\u3000")
        append(text)
        if (showBadge) {
            append(" ")
            appendInlineContent("note", "(${count})")
        }
    }
    val inline = if (showBadge) {
        mapOf(
            "note" to InlineTextContent(
                Placeholder(
                    width = (1.6f + count.toString().length * 0.6f).em,
                    height = 1.1.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                )
            ) { NoteBadge(count) }
        )
    } else {
        emptyMap()
    }
    Text(
        text = annotated,
        inlineContent = inline,
        style = TextStyle(
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeight).sp,
            color = Ink,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlighted) AccentSoft else Color.Transparent)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun NoteBadge(count: Int) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(ReadingIcons.Bubble, null, tint = Accent, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(2.dp))
        Text(count.toString(), color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---- 底部弹窗：段评楼 ----

@Composable
private fun NotesSheet(
    paragraph: String,
    threads: List<NoteThread>,
    selected: Long?,
    replying: Set<Long>,
    replyErrors: Map<Long, String>,
    userName: String,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
    onSend: (String) -> Unit,
    onRetry: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    var input by remember(selected) { mutableStateOf("") }
    val busy = selected != null && selected in replying

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Paper,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 20.dp),
        ) {
            Text("段评", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(
                text = paragraph,
                color = QuoteInk,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(QuoteBg)
                    .padding(12.dp),
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (threads.isEmpty()) {
                    item {
                        Text(
                            text = "写下你对这段的想法，Claude 会来回你",
                            color = Gray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(threads, key = { it.id }) { thread ->
                    ThreadCard(
                        thread = thread,
                        selected = selected == thread.id,
                        replying = thread.id in replying,
                        error = replyErrors[thread.id],
                        userName = userName,
                        onClick = { onSelect(thread.id) },
                        onRetry = { onRetry(thread.id) },
                    )
                }
                if (threads.isNotEmpty() && selected != null) {
                    item {
                        Text(
                            text = "＋ 自己写一条新的",
                            color = Accent,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onNew)
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            InputBar(
                value = input,
                onValueChange = { input = it },
                placeholder = if (selected == null) "说说你对这段的想法…" else "接着聊…",
                enabled = input.isNotBlank() && !busy,
                onSend = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        onSend(text)
                        input = ""
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ThreadCard(
    thread: NoteThread,
    selected: Boolean,
    replying: Boolean,
    error: String?,
    userName: String,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardColor)
            .border(1.dp, if (selected) Accent.copy(alpha = 0.6f) else Line, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        NoteLine(name = if (thread.author == ROLE_AI) "Claude" else userName, text = thread.text, ai = thread.author == ROLE_AI)
        thread.replies.forEach { reply ->
            Spacer(Modifier.height(10.dp))
            Row {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp, end = 10.dp)
                        .width(2.dp)
                        .height(16.dp)
                        .background(Line)
                )
                NoteLine(name = if (reply.role == ROLE_AI) "Claude" else userName, text = reply.text, ai = reply.role == ROLE_AI)
            }
        }
        if (replying) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Accent, strokeWidth = 1.5.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在回复…", color = Gray, style = MaterialTheme.typography.bodySmall)
            }
        } else if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "没回出来，点这里再试",
                color = Accent,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRetry)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun NoteLine(name: String, text: String, ai: Boolean) {
    Column {
        Text(
            text = name,
            color = if (ai) Accent else Gray,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(text = text, color = Ink, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardColor)
            .border(1.dp, Line, shape)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            if (value.isEmpty()) {
                Text(placeholder, color = Gray, style = MaterialTheme.typography.bodyMedium)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                cursorBrush = SolidColor(Accent),
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (enabled) Accent else QuoteBg)
                .clickable(enabled = enabled, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ReadingIcons.Send,
                "发送",
                tint = if (enabled) Color.White else Gray,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ---- 目录 ----

@Composable
private fun TocSheet(
    chapters: List<ChapterInfo>,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val listState = rememberLazyListState((current - 3).coerceAtLeast(0))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Paper,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "目录 · ${chapters.size} 章",
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
            ) {
                items(chapters, key = { it.id }) { info ->
                    val isCurrent = info.idx == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCurrent) AccentSoft else Color.Transparent)
                            .clickable { onPick(info.idx) }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = info.title,
                            color = if (isCurrent) Accent else Ink,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (info.annotated) {
                            Icon(ReadingIcons.Bubble, "有批注", tint = Accent, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---- 字号、行距 ----

@Composable
private fun FontSheet(
    fontSize: Float,
    lineHeight: Float,
    onFontSize: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Paper,
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("阅读设置", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Stepper(
                label = "字号",
                value = fontSize.roundToInt().toString(),
                onMinus = { onFontSize(fontSize - 1f) },
                onPlus = { onFontSize(fontSize + 1f) },
            )
            Stepper(
                label = "行距",
                value = String.format(Locale.getDefault(), "%.1f", lineHeight),
                onMinus = { onLineHeight(lineHeight - 0.1f) },
                onPlus = { onLineHeight(lineHeight + 0.1f) },
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Pill("－", filled = false, onClick = onMinus)
        Text(
            text = value,
            color = Ink,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        Pill("＋", filled = false, onClick = onPlus)
    }
}
