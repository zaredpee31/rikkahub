package me.rerere.rikkahub.reading

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.backgroundTextGenerationParams
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.MemoryWriteResult
import me.rerere.rikkahub.data.repository.memoryId
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.uuid.Uuid

const val ROLE_AI = "ai"
const val ROLE_USER = "user"

data class Book(
    val id: Long,
    val title: String,
    val chapterCount: Int,
    val totalChars: Int,
    val lastChapter: Int,
    val lastItem: Int,
    val lastOffset: Int,
    val opened: Long,
    val review: String,
)

data class ChapterInfo(val id: Long, val idx: Int, val title: String, val annotated: Boolean)

data class Chapter(
    val id: Long,
    val bookId: Long,
    val idx: Int,
    val title: String,
    val content: String,
    val summary: String,
    val annotated: Boolean,
) {
    val paragraphs: List<String> by lazy {
        content.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

data class NoteReply(val id: Long, val role: String, val text: String, val created: Long)

/** 一条段评楼：楼主可能是 Claude 的批注，也可能是我长按写的评论 */
data class NoteThread(
    val id: Long,
    val chapterId: Long,
    val para: Int,
    val author: String,
    val text: String,
    val created: Long,
    val replies: List<NoteReply>,
)

/** 阅读设置：字号、行距、批注开关在页面里读写，这里放看书用的模型 */
object ReadingPrefs {
    const val NAME = "reading"
    private const val KEY_MODEL = "model_id"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 空字符串表示跟着当前助手的模型 */
    fun modelId(context: Context): String = prefs(context).getString(KEY_MODEL, "").orEmpty()

    fun setModelId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_MODEL, id).apply()
    }
}

/** 看书功能单独的数据库，不碰聊天记录 */
class ReadingDb private constructor(context: Context) :
    SQLiteOpenHelper(context, "reading.db", null, 2) {

    companion object {
        @Volatile
        private var instance: ReadingDb? = null

        fun get(context: Context): ReadingDb =
            instance ?: synchronized(this) {
                instance ?: ReadingDb(context.applicationContext).also { instance = it }
            }

        private const val BOOK_COLS =
            "id, title, chapter_count, total_chars, last_chapter, last_item, last_offset, opened, review"
        private const val CHAPTER_COLS = "id, book_id, idx, title, content, summary, annotated"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE books (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, " +
                "chapter_count INTEGER NOT NULL, total_chars INTEGER NOT NULL, " +
                "last_chapter INTEGER NOT NULL DEFAULT 0, last_item INTEGER NOT NULL DEFAULT 0, " +
                "last_offset INTEGER NOT NULL DEFAULT 0, opened INTEGER NOT NULL DEFAULT 0, " +
                "review TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE chapters (id INTEGER PRIMARY KEY AUTOINCREMENT, book_id INTEGER NOT NULL, " +
                "idx INTEGER NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, " +
                "summary TEXT NOT NULL DEFAULT '', annotated INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX idx_chapters_book ON chapters(book_id, idx)")
        db.execSQL(
            "CREATE TABLE threads (id INTEGER PRIMARY KEY AUTOINCREMENT, chapter_id INTEGER NOT NULL, " +
                "para INTEGER NOT NULL, author TEXT NOT NULL, text TEXT NOT NULL, created INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_threads_chapter ON threads(chapter_id)")
        db.execSQL(
            "CREATE TABLE replies (id INTEGER PRIMARY KEY AUTOINCREMENT, thread_id INTEGER NOT NULL, " +
                "role TEXT NOT NULL, text TEXT NOT NULL, created INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_replies_thread ON replies(thread_id)")
    }

    // 第 2 版：书多一列读后感，旧数据都留着
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE books ADD COLUMN review TEXT NOT NULL DEFAULT ''")
        }
    }

    private fun Cursor.toBook() = Book(
        id = getLong(0),
        title = getString(1),
        chapterCount = getInt(2),
        totalChars = getInt(3),
        lastChapter = getInt(4),
        lastItem = getInt(5),
        lastOffset = getInt(6),
        opened = getLong(7),
        review = getString(8) ?: "",
    )

    private fun Cursor.toChapter() = Chapter(
        id = getLong(0),
        bookId = getLong(1),
        idx = getInt(2),
        title = getString(3),
        content = getString(4),
        summary = getString(5) ?: "",
        annotated = getInt(6) == 1,
    )

    fun books(): List<Book> =
        readableDatabase.rawQuery("SELECT $BOOK_COLS FROM books ORDER BY opened DESC, id DESC", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.toBook()) } }

    fun book(id: Long): Book? =
        readableDatabase.rawQuery("SELECT $BOOK_COLS FROM books WHERE id = ?", arrayOf(id.toString()))
            .use { c -> if (c.moveToFirst()) c.toBook() else null }

    fun insertBook(title: String, chapters: List<Pair<String, String>>): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val bookId = db.insertOrThrow("books", null, ContentValues().apply {
                put("title", title)
                put("chapter_count", chapters.size)
                put("total_chars", chapters.sumOf { it.second.length })
                put("opened", System.currentTimeMillis())
            })
            chapters.forEachIndexed { i, (chapterTitle, content) ->
                db.insertOrThrow("chapters", null, ContentValues().apply {
                    put("book_id", bookId)
                    put("idx", i)
                    put("title", chapterTitle)
                    put("content", content)
                })
            }
            db.setTransactionSuccessful()
            return bookId
        } finally {
            db.endTransaction()
        }
    }

    // 书、章节、批注、回复一起删
    fun deleteBook(id: Long) {
        val db = writableDatabase
        val arg = arrayOf(id.toString())
        db.beginTransaction()
        try {
            db.execSQL(
                "DELETE FROM replies WHERE thread_id IN (SELECT t.id FROM threads t " +
                    "JOIN chapters c ON t.chapter_id = c.id WHERE c.book_id = ?)",
                arg
            )
            db.execSQL("DELETE FROM threads WHERE chapter_id IN (SELECT id FROM chapters WHERE book_id = ?)", arg)
            db.delete("chapters", "book_id = ?", arg)
            db.delete("books", "id = ?", arg)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setReview(bookId: Long, text: String) {
        writableDatabase.update("books", ContentValues().apply {
            put("review", text)
        }, "id = ?", arrayOf(bookId.toString()))
    }

    fun chapterList(bookId: Long): List<ChapterInfo> =
        readableDatabase.rawQuery(
            "SELECT id, idx, title, annotated FROM chapters WHERE book_id = ? ORDER BY idx",
            arrayOf(bookId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(ChapterInfo(c.getLong(0), c.getInt(1), c.getString(2), c.getInt(3) == 1))
            }
        }

    fun chapter(bookId: Long, idx: Int): Chapter? =
        readableDatabase.rawQuery(
            "SELECT $CHAPTER_COLS FROM chapters WHERE book_id = ? AND idx = ?",
            arrayOf(bookId.toString(), idx.toString())
        ).use { c -> if (c.moveToFirst()) c.toChapter() else null }

    fun chapterById(id: Long): Chapter? =
        readableDatabase.rawQuery("SELECT $CHAPTER_COLS FROM chapters WHERE id = ?", arrayOf(id.toString()))
            .use { c -> if (c.moveToFirst()) c.toChapter() else null }

    fun saveProgress(bookId: Long, chapterIdx: Int, item: Int, offset: Int) {
        writableDatabase.update("books", ContentValues().apply {
            put("last_chapter", chapterIdx)
            put("last_item", item)
            put("last_offset", offset)
            put("opened", System.currentTimeMillis())
        }, "id = ?", arrayOf(bookId.toString()))
    }

    // 之前章节的小结，从最近的往前取，总共不超过 maxChars，返回时按章节顺序
    fun previousSummaries(bookId: Long, idx: Int, maxChars: Int): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        var total = 0
        readableDatabase.rawQuery(
            "SELECT title, summary FROM chapters WHERE book_id = ? AND idx < ? AND summary != '' ORDER BY idx DESC",
            arrayOf(bookId.toString(), idx.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val summary = c.getString(1)
                if (out.isNotEmpty() && total + summary.length > maxChars) break
                out.add(c.getString(0) to summary)
                total += summary.length
            }
        }
        return out.reversed()
    }

    // 整本书每章的小结，写读后感用
    fun allSummaries(bookId: Long): List<Pair<String, String>> =
        readableDatabase.rawQuery(
            "SELECT title, summary FROM chapters WHERE book_id = ? AND summary != '' ORDER BY idx",
            arrayOf(bookId.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) } }

    // 我们真正聊过的楼：我写的评论，或者下面有回复的批注
    fun discussions(bookId: Long, limit: Int): List<Pair<String, NoteThread>> {
        val ids = readableDatabase.rawQuery(
            "SELECT t.id, c.title FROM threads t JOIN chapters c ON t.chapter_id = c.id " +
                "WHERE c.book_id = ? AND (t.author = 'user' OR EXISTS " +
                "(SELECT 1 FROM replies r WHERE r.thread_id = t.id)) " +
                "ORDER BY c.idx, t.para, t.id LIMIT $limit",
            arrayOf(bookId.toString())
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) } }
        return ids.mapNotNull { (id, title) -> thread(id)?.let { title to it } }
    }

    private fun repliesOf(sql: String, arg: String): Map<Long, List<NoteReply>> =
        readableDatabase.rawQuery(sql, arrayOf(arg)).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(c.getLong(1) to NoteReply(c.getLong(0), c.getString(2), c.getString(3), c.getLong(4)))
                }
            }
        }.groupBy({ it.first }, { it.second })

    fun threads(chapterId: Long): List<NoteThread> {
        val replies = repliesOf(
            "SELECT r.id, r.thread_id, r.role, r.text, r.created FROM replies r " +
                "JOIN threads t ON r.thread_id = t.id WHERE t.chapter_id = ? ORDER BY r.created, r.id",
            chapterId.toString()
        )
        return readableDatabase.rawQuery(
            "SELECT id, chapter_id, para, author, text, created FROM threads WHERE chapter_id = ? " +
                "ORDER BY para, created, id",
            arrayOf(chapterId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    add(
                        NoteThread(
                            id, c.getLong(1), c.getInt(2), c.getString(3), c.getString(4), c.getLong(5),
                            replies[id].orEmpty()
                        )
                    )
                }
            }
        }
    }

    fun thread(id: Long): NoteThread? {
        val replies = repliesOf(
            "SELECT id, thread_id, role, text, created FROM replies WHERE thread_id = ? ORDER BY created, id",
            id.toString()
        )
        return readableDatabase.rawQuery(
            "SELECT id, chapter_id, para, author, text, created FROM threads WHERE id = ?",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                NoteThread(
                    c.getLong(0), c.getLong(1), c.getInt(2), c.getString(3), c.getString(4), c.getLong(5),
                    replies[id].orEmpty()
                )
            } else null
        }
    }

    fun insertThread(chapterId: Long, para: Int, author: String, text: String): Long =
        writableDatabase.insertOrThrow("threads", null, ContentValues().apply {
            put("chapter_id", chapterId)
            put("para", para)
            put("author", author)
            put("text", text)
            put("created", System.currentTimeMillis())
        })

    fun insertReply(threadId: Long, role: String, text: String): Long =
        writableDatabase.insertOrThrow("replies", null, ContentValues().apply {
            put("thread_id", threadId)
            put("role", role)
            put("text", text)
            put("created", System.currentTimeMillis())
        })

    // 新批注读成功了才换掉旧的；我自己写的评论不动
    fun replaceAiNotes(chapterId: Long, notes: List<Pair<Int, String>>, summary: String) {
        val db = writableDatabase
        val arg = arrayOf(chapterId.toString())
        db.beginTransaction()
        try {
            db.execSQL(
                "DELETE FROM replies WHERE thread_id IN (SELECT id FROM threads WHERE chapter_id = ? AND author = 'ai')",
                arg
            )
            db.delete("threads", "chapter_id = ? AND author = 'ai'", arg)
            val now = System.currentTimeMillis()
            notes.forEachIndexed { i, (para, text) ->
                db.insertOrThrow("threads", null, ContentValues().apply {
                    put("chapter_id", chapterId)
                    put("para", para)
                    put("author", ROLE_AI)
                    put("text", text)
                    put("created", now + i)
                })
            }
            db.update("chapters", ContentValues().apply {
                put("summary", summary)
                put("annotated", 1)
            }, "id = ?", arg)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

/** 导入 txt：认编码、切章 */
object BookImporter {
    private const val MAX_BYTES = 60 * 1024 * 1024
    private const val CHUNK = 5000
    private const val MAX_LINE = 3000

    private val strongTitle = Regex("""^第[零〇一二三四五六七八九十百千万两0-9０-９]+[章节回卷集].*""")
    private val englishTitle = Regex("""^[Cc][Hh][Aa][Pp][Tt][Ee][Rr]\s*[0-9]+.*""")
    private val weakTitle = Regex("""^(序章|楔子|引子|番外|尾声|后记)([\s:：·、.\-—_（(【].*|[0-9一二三四五六七八九十]+.*)?""")

    private fun isTitle(line: String): Boolean =
        line.length <= 40 && (strongTitle.matches(line) || englishTitle.matches(line) || weakTitle.matches(line))

    suspend fun import(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("打不开这个文件")
        if (bytes.size > MAX_BYTES) error("文件太大了")
        val chapters = split(decode(bytes))
        if (chapters.isEmpty()) error("这个文件里没有文字")
        ReadingDb.get(context).insertBook(titleOf(context, uri), chapters)
    }

    private fun titleOf(context: Context, uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return name?.substringBeforeLast('.')?.trim()?.removePrefix("《")?.removeSuffix("》")
            ?.ifBlank { null } ?: "未命名"
    }

    // 有 BOM 按 BOM，没有先试 UTF-8，不行再用 GB18030（包含 GBK）
    fun decode(bytes: ByteArray): String {
        fun b(i: Int) = bytes[i].toInt() and 0xFF
        if (bytes.size >= 3 && b(0) == 0xEF && b(1) == 0xBB && b(2) == 0xBF) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && b(0) == 0xFF && b(1) == 0xFE) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && b(0) == 0xFE && b(1) == 0xFF) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            val gb = runCatching { Charset.forName("GB18030") }.getOrElse { Charset.forName("GBK") }
            String(bytes, gb)
        }
    }

    fun split(text: String): List<Pair<String, String>> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { if (it.length > MAX_LINE) it.chunked(MAX_LINE) else listOf(it) }
        if (lines.isEmpty()) return emptyList()

        val result = mutableListOf<Pair<String, String>>()
        var title: String? = null
        val body = StringBuilder()
        var found = false
        for (line in lines) {
            if (isTitle(line)) {
                found = true
                val current = title
                if (current != null && body.isEmpty()) {
                    // 卷名后面紧跟章名，合成一个标题
                    title = "$current $line"
                    continue
                }
                if (body.isNotEmpty()) result.add((current ?: "开篇") to body.toString())
                title = line
                body.clear()
            } else {
                if (body.isNotEmpty()) body.append('\n')
                body.append(line)
            }
        }
        if (found) {
            if (body.isNotEmpty()) result.add((title ?: "开篇") to body.toString())
            if (result.isNotEmpty()) return result
        }
        return chunk(lines)
    }

    // 一个章节标题都没有时，按 5000 字左右切
    private fun chunk(lines: List<String>): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val sb = StringBuilder()
        for (line in lines) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(line)
            if (sb.length >= CHUNK) {
                out.add("第 ${out.size + 1} 部分" to sb.toString())
                sb.clear()
            }
        }
        if (sb.isNotEmpty()) out.add("第 ${out.size + 1} 部分" to sb.toString())
        return out
    }
}

/** 读章节、写批注、回段评、写读后感。单独请求模型，不进聊天记录 */
object Reading {
    private const val TAG = "Reading"

    // 往前带的前情总长度
    private const val SUMMARY_CHARS = 1500

    // 每章小结最多存这么长
    private const val SUMMARY_MAX = 80

    // 读后感
    private const val REVIEW_MAX = 200
    private const val REVIEW_SUMMARY_CHARS = 6000
    private const val REVIEW_TALK_CHARS = 4000
    private const val REVIEW_TALK_LIMIT = 40
    private const val MEMORY_PATH = "/topics/books.md"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 数据有变化就加一，页面跟着重新读 */
    val version = MutableStateFlow(0)
    val annotating = MutableStateFlow<Set<Long>>(emptySet())
    val annotateErrors = MutableStateFlow<Map<Long, String>>(emptyMap())
    val replying = MutableStateFlow<Set<Long>>(emptySet())
    val replyErrors = MutableStateFlow<Map<Long, String>>(emptyMap())

    /** 正在写或正在存读后感的书 */
    val reviewing = MutableStateFlow<Set<Long>>(emptySet())

    /** 写好了、还没决定存不存的读后感 */
    val reviewDrafts = MutableStateFlow<Map<Long, String>>(emptyMap())
    val reviewErrors = MutableStateFlow<Map<Long, String>>(emptyMap())

    fun changed() {
        version.update { it + 1 }
    }

    private class AiContext(
        val persona: String,
        val userName: String,
        val ask: suspend (system: String, user: String) -> String,
    )

    // 人设用当前助手的；模型优先用看书单独选的，没选或者被删了就用当前助手的
    private suspend fun aiContext(app: Context): AiContext {
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsStore>().awaitLoaded()
        val conversation = Conversation.ofId(id = Uuid.random(), assistantId = settings.getCurrentAssistant().id)
        val assistant = settings.getAssistantOf(conversation)
        val savedId = ReadingPrefs.modelId(app)
        val picked = if (savedId.isBlank()) null else {
            settings.providers.flatMap { it.models }.firstOrNull { it.id.toString() == savedId }
        }
        val model = picked ?: settings.getChatModelOf(conversation) ?: error("还没有选模型")
        val provider = model.findProvider(settings.providers) ?: error("找不到模型的提供商")
        val client = koin.get<ProviderManager>().getProviderByType(provider)
        return AiContext(
            persona = assistant.systemPrompt,
            userName = settings.displaySetting.userNickname.ifBlank { "我" },
        ) { system, user ->
            val messages = buildList {
                if (system.isNotBlank()) add(UIMessage.system(prompt = system))
                add(UIMessage.user(user))
            }
            val result = client.generateText(
                providerSetting = provider,
                messages = messages,
                params = backgroundTextGenerationParams(model, conversation.id),
            )
            result.message.toText()
        }
    }

    private val thinkRegex = Regex("<think>[\\s\\S]*?</think>")

    private fun errorText(e: Throwable): String =
        (e.message ?: e.javaClass.simpleName).take(80)

    // ---- 读一章 ----

    fun annotate(context: Context, chapterId: Long) {
        val app = context.applicationContext
        if (chapterId in annotating.value) return
        annotating.update { it + chapterId }
        annotateErrors.update { it - chapterId }
        scope.launch {
            try {
                val db = ReadingDb.get(app)
                val chapter = db.chapterById(chapterId) ?: error("找不到这一章")
                val book = db.book(chapter.bookId) ?: error("找不到这本书")
                if (chapter.paragraphs.isEmpty()) error("这一章没有正文")
                val previous = db.previousSummaries(book.id, chapter.idx, SUMMARY_CHARS)
                val ai = aiContext(app)
                val raw = ai.ask(ai.persona, annotatePrompt(book, chapter, previous, ai.userName))
                val parsed = parseAnnotations(raw, chapter.paragraphs.size) ?: error("没看懂模型的回复")
                db.replaceAiNotes(chapterId, parsed.first, parsed.second.take(SUMMARY_MAX))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "annotate failed", e)
                annotateErrors.update { it + (chapterId to errorText(e)) }
            } finally {
                annotating.update { it - chapterId }
                changed()
            }
        }
    }

    private fun annotatePrompt(
        book: Book,
        chapter: Chapter,
        previous: List<Pair<String, String>>,
        userName: String,
    ): String {
        val paragraphs = chapter.paragraphs
        val count = (chapter.content.length / 700).coerceIn(5, 12).coerceAtMost(paragraphs.size)
        return buildString {
            append("书名：《").append(book.title).append("》\n")
            append("本章：").append(chapter.title).append("\n\n")
            if (previous.isNotEmpty()) {
                append("前情（之前每章一句话）：\n")
                previous.forEach { (title, summary) -> append(title).append("：").append(summary).append("\n") }
                append("\n")
            }
            append("本章正文（每段前面是段号）：\n")
            paragraphs.forEachIndexed { i, p -> append("[P").append(i + 1).append("] ").append(p).append("\n") }
            append("\n")
            append(
                """
                你正在陪${userName}一起看这本小说，刚读完这一章。请在段落上写段评，像看书时随手发在段落下面的评论。
                要求：
                1. 写 ${count} 条左右，挑你真有想法的段落，不用平均分布。
                2. 用你平时跟${userName}说话的口吻，短，口语化，一两句话。可以吐槽、说感想、猜后面的剧情、评价角色。
                3. 不复述原文，不总结，不要每条都用问句结尾。
                4. 另外用一句话概括本章剧情，30 字左右，只写发生了什么，读后面的章节时当前情用。
                只输出 JSON，不要输出别的文字：
                {"annotations":[{"p":段号,"text":"段评内容"}],"summary":"一句话概括本章"}
                """.trimIndent()
            )
        }
    }

    // 去掉 ```json 包裹再解析，段号越界的那条不要
    internal fun parseAnnotations(raw: String, paragraphCount: Int): Pair<List<Pair<Int, String>>, String>? {
        val s = raw.replace(thinkRegex, "").replace(Regex("```(?:json|JSON)?"), "").trim()
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { JSONObject(s.substring(start, end + 1)) }.getOrNull() ?: return null
        val arr = obj.optJSONArray("annotations") ?: return null
        val notes = mutableListOf<Pair<Int, String>>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val v = item.opt("p")
            val p = when (v) {
                is Number -> v.toInt()
                else -> Regex("\\d+").find(v?.toString() ?: "")?.value?.toIntOrNull()
            } ?: continue
            val text = item.optString("text").trim()
            if (p < 1 || p > paragraphCount || text.isEmpty()) continue
            notes.add(p to text)
        }
        if (notes.isEmpty()) return null
        return notes to obj.optString("summary").trim()
    }

    // ---- 段评楼 ----

    fun reply(context: Context, threadId: Long, text: String) {
        if (threadId in replying.value) return
        launchReply(context.applicationContext, threadId) { it.insertReply(threadId, ROLE_USER, text) }
    }

    fun retryReply(context: Context, threadId: Long) {
        if (threadId in replying.value) return
        launchReply(context.applicationContext, threadId)
    }

    /** 长按段落自己发起一条评论，Claude 来回 */
    fun newComment(context: Context, chapterId: Long, para: Int, text: String, onCreated: (Long) -> Unit) {
        val app = context.applicationContext
        scope.launch {
            val id = try {
                ReadingDb.get(app).insertThread(chapterId, para, ROLE_USER, text)
            } catch (e: Exception) {
                Log.w(TAG, "insert comment failed", e)
                return@launch
            }
            changed()
            withContext(Dispatchers.Main) { onCreated(id) }
            launchReply(app, id)
        }
    }

    private fun launchReply(app: Context, threadId: Long, before: (ReadingDb) -> Unit = {}) {
        replying.update { it + threadId }
        replyErrors.update { it - threadId }
        scope.launch {
            try {
                val db = ReadingDb.get(app)
                before(db)
                changed()
                val thread = db.thread(threadId) ?: error("找不到这条评论")
                val chapter = db.chapterById(thread.chapterId) ?: error("找不到这一章")
                val book = db.book(chapter.bookId) ?: error("找不到这本书")
                val ai = aiContext(app)
                val system = ai.persona + "\n\n" +
                    "你们在一起看小说，正在段评区聊天。回复要短，像微信聊天，一两句话，口语化。" +
                    "直接输出回复内容，不要带名字前缀，不要加引号。"
                val answer = clean(ai.ask(system, replyPrompt(book, chapter, thread, ai.userName)))
                if (answer.isEmpty()) error("模型没有回复")
                db.insertReply(threadId, ROLE_AI, answer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "reply failed", e)
                replyErrors.update { it + (threadId to errorText(e)) }
            } finally {
                replying.update { it - threadId }
                changed()
            }
        }
    }

    private fun replyPrompt(book: Book, chapter: Chapter, thread: NoteThread, userName: String): String {
        val paragraphs = chapter.paragraphs
        return buildString {
            append("书名：《").append(book.title).append("》\n")
            append("本章：").append(chapter.title).append("\n")
            append("本章一句话：").append(chapter.summary.ifBlank { "（还没有）" }).append("\n\n")
            paragraphs.getOrNull(thread.para - 2)?.let { append("上一段：").append(it).append("\n") }
            append("这一段：").append(paragraphs.getOrNull(thread.para - 1).orEmpty()).append("\n")
            paragraphs.getOrNull(thread.para)?.let { append("下一段：").append(it).append("\n") }
            append("\n这一段下面的段评楼：\n")
            append(if (thread.author == ROLE_AI) "你" else userName).append("：").append(thread.text).append("\n")
            thread.replies.forEach { r ->
                append(if (r.role == ROLE_AI) "你" else userName).append("：").append(r.text).append("\n")
            }
            append("\n接着${userName}最后说的话，回一句。")
        }
    }

    private fun clean(raw: String): String {
        var s = raw.replace(thinkRegex, "").trim()
        for (prefix in listOf("你：", "你:", "Claude：", "Claude:")) {
            if (s.startsWith(prefix)) s = s.removePrefix(prefix).trim()
        }
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') s = s.substring(1, s.length - 1).trim()
        return s
    }

    // ---- 读后感 ----

    /** 先写一份草稿给我看，我点了存才进记忆 */
    fun writeReview(context: Context, bookId: Long) {
        val app = context.applicationContext
        if (bookId in reviewing.value) return
        reviewing.update { it + bookId }
        reviewErrors.update { it - bookId }
        reviewDrafts.update { it - bookId }
        scope.launch {
            try {
                val db = ReadingDb.get(app)
                val book = db.book(bookId) ?: error("找不到这本书")
                val summaries = db.allSummaries(bookId)
                val talks = db.discussions(bookId, REVIEW_TALK_LIMIT)
                if (summaries.isEmpty() && talks.isEmpty()) error("还没一起看过这本书的章节")
                val ai = aiContext(app)
                val raw = ai.ask(ai.persona, reviewPrompt(book, summaries, talks, ai.userName))
                val text = limitReview(clean(raw))
                if (text.isEmpty()) error("模型没有回复")
                reviewDrafts.update { it + (bookId to text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "review failed", e)
                reviewErrors.update { it + (bookId to errorText(e)) }
            } finally {
                reviewing.update { it - bookId }
                changed()
            }
        }
    }

    fun discardReview(bookId: Long) {
        reviewDrafts.update { it - bookId }
    }

    /** 存进当前助手的记忆，同一本书再存会换掉旧的那行 */
    fun saveReview(context: Context, bookId: Long, text: String) {
        val app = context.applicationContext
        if (bookId in reviewing.value) return
        reviewing.update { it + bookId }
        reviewErrors.update { it - bookId }
        scope.launch {
            try {
                val db = ReadingDb.get(app)
                val book = db.book(bookId) ?: error("找不到这本书")
                val koin = GlobalContext.get()
                val settings = koin.get<SettingsStore>().awaitLoaded()
                val memoryId = settings.getCurrentAssistant().memoryId
                val userName = settings.displaySetting.userNickname.ifBlank { "我" }
                writeMemory(koin.get<MemoryRepository>(), memoryId, book.title, text, userName)
                db.setReview(bookId, text)
                reviewDrafts.update { it - bookId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "save review failed", e)
                reviewErrors.update { it + (bookId to errorText(e)) }
            } finally {
                reviewing.update { it - bookId }
                changed()
            }
        }
    }

    // 小结太多时隔几章取一章，保证带过去的不会太长
    private fun fitSummaries(list: List<Pair<String, String>>): List<Pair<String, String>> {
        val trimmed = list.map { it.first to it.second.take(60) }
        val total = trimmed.sumOf { it.first.length + it.second.length + 2 }
        if (total <= REVIEW_SUMMARY_CHARS) return trimmed
        val step = (total + REVIEW_SUMMARY_CHARS - 1) / REVIEW_SUMMARY_CHARS
        return trimmed.filterIndexed { i, _ -> i % step == 0 || i == trimmed.lastIndex }
    }

    private fun reviewPrompt(
        book: Book,
        summaries: List<Pair<String, String>>,
        talks: List<Pair<String, NoteThread>>,
        userName: String,
    ): String {
        fun who(role: String) = if (role == ROLE_AI) "你" else userName
        return buildString {
            append("书名：《").append(book.title).append("》，共 ").append(book.chapterCount).append(" 章\n\n")
            if (summaries.isNotEmpty()) {
                append("各章剧情（每章一句话）：\n")
                fitSummaries(summaries).forEach { (title, s) -> append(title).append("：").append(s).append("\n") }
                append("\n")
            }
            if (talks.isNotEmpty()) {
                append("你们在段评里聊过的：\n")
                var used = 0
                for ((title, t) in talks) {
                    val block = buildString {
                        append("在「").append(title).append("」：\n")
                        append(who(t.author)).append("：").append(t.text.take(100)).append("\n")
                        t.replies.take(6).forEach { r ->
                            append(who(r.role)).append("：").append(r.text.take(100)).append("\n")
                        }
                    }
                    if (used + block.length > REVIEW_TALK_CHARS) break
                    append(block)
                    used += block.length
                }
                append("\n")
            }
            append(
                """
                你和${userName}一起看完了这本书。用你自己的口吻写一段读后感，第一人称，写给以后的自己看：
                1. 剧情一两句带过，重点写你的感受、你们聊过的印象深的地方、${userName}的看法。
                2. 200 字以内，一段话，不分点，不加标题，不用引号包起来。
                这段会存进你的长期记忆，以后用来想起你们一起读过这本书。只输出读后感本身。
                """.trimIndent()
            )
        }
    }

    // 合成一段，超过 200 字就在句号处截断
    private fun limitReview(s: String): String {
        val one = s.replace(Regex("\\s*\\n+\\s*"), "").trim()
        if (one.length <= REVIEW_MAX) return one
        val cut = one.take(REVIEW_MAX)
        val end = cut.indexOfLast { it in "。！？…" }
        return if (end >= REVIEW_MAX / 2) cut.substring(0, end + 1) else cut
    }

    private fun memoryHeader(userName: String): String =
        "---\n" +
            "name: books\n" +
            "title: 一起读过的书\n" +
            "description: ${userName}和助手一起看完的书，和助手写的读后感\n" +
            "aliases: [一起看书, 读后感, 读过的书]\n" +
            "---\n\n"

    private suspend fun writeMemory(
        repo: MemoryRepository,
        memoryId: String,
        title: String,
        text: String,
        userName: String,
    ) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val prefix = "- 《$title》"
        val line = "$prefix（$date 一起看完）：$text"
        repeat(3) {
            val current = repo.getFile(memoryId, MEMORY_PATH)
            val content = if (current == null) {
                memoryHeader(userName) + line + "\n"
            } else {
                val kept = current.content.trimEnd('\n').split('\n').filterNot { it.startsWith(prefix) }
                kept.joinToString("\n") + "\n" + line + "\n"
            }
            val result = repo.writeFile(
                assistantId = memoryId,
                path = MEMORY_PATH,
                content = content,
                ifVersion = current?.version ?: MemoryRepository.NEW_FILE_VERSION,
            )
            when (result) {
                is MemoryWriteResult.Success -> return
                is MemoryWriteResult.Rejected -> error("记忆存不下：" + result.reason.take(40))
                is MemoryWriteResult.Conflict -> Unit
            }
        }
        error("记忆正在被改，等会再试")
    }
}
