package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.MemoryRoom
import java.security.MessageDigest

/** 旧 Markdown 记忆里拆出来的一块 */
data class LegacyBlock(
    /** `legacy:<路径>:<内容哈希>`，同一文件里内容完全相同的第二块再加 `:2` */
    val origin: String,
    val path: String,
    val room: String,
    val content: String,
    /** 文件标题和所在小标题，给关键词兜底检索用 */
    val context: String,
)

/**
 * 把旧 Markdown 记忆拆成节点。
 *
 * 不假设正文都是 `- [stated]` 列表：顶层列表项一块，空行分开的段落一块，代码块整块，
 * 列表下缩进的续行、子项跟着上一块。只有小标题、底下什么都没有的，小标题本身也算一块，
 * 保证正文里的字都落进某一块。
 *
 * 来源键用内容哈希而不是行号：在文件里挪动、插入别的行不会让已导入的节点被当成新内容。
 */
object LegacyMemoryParser {
    /** 不拆成节点：profile 进用户门牌，preferences 原样常驻 */
    val residentPaths = setOf(MemoryFile.PROFILE_PATH, MemoryFile.PREFERENCES_PATH)

    private val headingRegex = Regex("^(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val topListItemRegex = Regex("^ ?([-*+]|\\d+[.)])\\s+")
    private val markerRegex = Regex("^\\s*([-*+]|\\d+[.)])\\s+")
    private val statedRegex = Regex("^\\[stated]\\s*")
    private val spaceRegex = Regex("\\s+")
    private val ruleRegex = Regex("^([-*_])(\\s*\\1){2,}$")

    fun parse(file: MemoryFile): List<LegacyBlock> {
        if (file.path in residentPaths) return emptyList()
        val room = roomOf(file.path)
        val title = file.title
        val seen = HashMap<String, Int>()
        return splitBlocks(file.editableBody).map { (heading, text) ->
            val hash = hashOf(text)
            val n = (seen[hash] ?: 0) + 1
            seen[hash] = n
            LegacyBlock(
                origin = "legacy:${file.path}:$hash" + if (n > 1) ":$n" else "",
                path = file.path,
                room = room,
                content = text,
                context = listOf(title, heading).filter { it.isNotBlank() }.joinToString(" / "),
            )
        }
    }

    fun roomOf(path: String): String = when {
        path == "/topics/appearance.md" || path.startsWith("/self") || path.contains("/self/") -> MemoryRoom.SELF
        path == "/topics/diary.md" -> MemoryRoom.LIVING
        path.startsWith("/areas/") -> MemoryRoom.STUDY
        else -> MemoryRoom.USER
    }

    /** 返回 (所在小标题路径, 清理后的内容) */
    internal fun splitBlocks(body: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val headings = arrayOfNulls<String>(7)
        var current: StringBuilder? = null
        var inFence = false
        var headingHasContent = true

        fun headingPath() = headings.filterNotNull().filter { it.isNotBlank() }.joinToString(" / ")

        fun flush() {
            val text = current?.toString()?.let(::cleanup)
            current = null
            if (!text.isNullOrEmpty()) {
                out += headingPath() to text
                headingHasContent = true
            }
        }

        fun flushBareHeading() {
            if (!headingHasContent) {
                val path = headingPath()
                if (path.isNotEmpty()) out += path to path
            }
            headingHasContent = true
        }

        for (line in body.lines()) {
            val trimmed = line.trim()
            if (inFence) {
                current!!.append('\n').append(line)
                if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                    inFence = false
                    flush()
                }
                continue
            }
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                flush()
                current = StringBuilder(line)
                inFence = true
                continue
            }
            val heading = headingRegex.matchEntire(trimmed)
            if (heading != null && line.length - line.trimStart().length < 4) {
                flush()
                flushBareHeading()
                val level = heading.groupValues[1].length
                headings[level] = heading.groupValues[2]
                for (i in level + 1..6) headings[i] = null
                headingHasContent = false
                continue
            }
            if (trimmed.isEmpty() || ruleRegex.matches(trimmed)) {
                flush()
                continue
            }
            if (topListItemRegex.containsMatchIn(line)) flush()
            val block = current
            if (block == null) current = StringBuilder(line) else block.append('\n').append(line)
        }
        // 没闭合的代码块也整块保留
        flush()
        flushBareHeading()
        return out
    }

    private fun cleanup(raw: String): String {
        val text = raw.trimEnd()
        if (text.trimStart().startsWith("```") || text.trimStart().startsWith("~~~")) return text.trim()
        val lines = text.lines()
        val first = lines.first().replaceFirst(markerRegex, "").replaceFirst(statedRegex, "")
        return (listOf(first) + lines.drop(1)).joinToString("\n").trim()
    }

    private fun hashOf(text: String): String {
        val normalized = text.replace(spaceRegex, " ").trim()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
