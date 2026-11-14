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

    fun parse(file: MemoryFile): List<LegacyBlock> = parseWithSegments(file).map { it.first }

    /** 拆出来的块和它在正文里的行范围，顺序一致 */
    internal fun parseWithSegments(file: MemoryFile): List<Pair<LegacyBlock, Segment>> =
        blocksOf(file, splitSegments(file.editableBody))

    private fun blocksOf(file: MemoryFile, segments: List<Segment>): List<Pair<LegacyBlock, Segment>> {
        if (file.path in residentPaths) return emptyList()
        val room = roomOf(file.path)
        val title = file.title
        val seen = HashMap<String, Int>()
        return segments.map { segment ->
            val hash = hashOf(segment.text)
            val n = (seen[hash] ?: 0) + 1
            seen[hash] = n
            LegacyBlock(
                origin = "legacy:${file.path}:$hash" + if (n > 1) ":$n" else "",
                path = file.path,
                room = room,
                content = segment.text,
                context = listOf(title, segment.heading).filter { it.isNotBlank() }.joinToString(" / "),
            ) to segment
        }
    }

    /** 改写或删掉原文里的一块之后的结果 */
    internal data class Rewrite(
        /** 整个文件的新内容，frontmatter 原样保留 */
        val content: String,
        /** 改写后这一块的新来源键；删除时为 null */
        val origin: String?,
        /** 改写后原文里这一块拆出来的内容（去掉了用户自己打的列表符号和 [stated]）；删除时为 null */
        val text: String? = null,
        /**
         * 同一文件里其他块的来源键变化（旧 -> 新）。内容一样的块靠 `:2`、`:3` 区分，
         * 删掉或改掉前面那块，后面同样内容的块编号会变；节点跟着改名，房间、日期等都留着。
         */
        val renames: Map<String, String> = emptyMap(),
    )

    /**
     * 在 [file] 里找到来源键为 [origin] 的那一块，换成 [newText]（为 null 时删掉）。
     * 找不到返回 null；改完之后这一块拆不回一整块（比如中间加了空行）就抛出，不写半截。
     */
    internal fun rewrite(file: MemoryFile, origin: String, newText: String?): Rewrite? {
        val body = file.editableBody
        val prefix = file.content.substring(0, file.content.length - body.length)
        val pairs = parseWithSegments(file)
        val index = pairs.indexOfFirst { it.first.origin == origin }
        if (index < 0) return null
        val segment = pairs[index].second
        val lines = body.lines()
        val before = lines.subList(0, segment.start)
        val after = lines.subList(segment.end + 1, lines.size)

        if (newText == null) {
            val kept = if ((before.isEmpty() || before.last().isBlank()) && after.firstOrNull()?.isBlank() == true) {
                before + after.drop(1)
            } else {
                before + after
            }
            val keptBody = kept.joinToString("\n")
            val reparsed = blocksOf(file.copy(content = prefix + keptBody), splitSegments(keptBody))
            val renames = align(pairs.filterIndexed { i, _ -> i != index }, reparsed)
            requireNotNull(renames) { "删掉这条之后前后的内容会粘在一起，去文件里删" }
            return Rewrite(prefix + keptBody, null, renames = renames)
        }

        val text = newText.trim().replace("\r\n", "\n")
        require(text.isNotEmpty()) { "内容不能为空" }
        val original = lines.subList(segment.start, segment.end + 1)
        val replaced: List<String> = when {
            segment.raw -> text.lines()
            segment.bareHeading -> {
                require(!text.contains('\n') && !segment.heading.contains(" / ")) { "这是一个小标题，去文件里改" }
                val marks = Regex("^\\s*#{1,6}\\s+").find(original.first())?.value.orEmpty()
                listOf(marks + text)
            }
            else -> {
                val lead = Regex("^\\s*(([-*+]|\\d+[.)])\\s+)?(\\[stated]\\s*)?").find(original.first())?.value.orEmpty()
                val newLines = text.lines()
                val first = newLines.first().replaceFirst(markerRegex, "").replaceFirst(statedRegex, "")
                listOf(lead + first) + newLines.drop(1)
            }
        }
        val newLinesAll = before + replaced + after
        val newBody = newLinesAll.joinToString("\n")
        val newFile = file.copy(content = prefix + newBody)
        val reparsed = blocksOf(newFile, splitSegments(newBody))
        val landed = reparsed.firstOrNull { it.second.start == segment.start }
        val expectedText = when {
            segment.bareHeading -> segment.heading.substringBeforeLast(" / ", "").let { if (it.isEmpty()) text else "$it / $text" }
            segment.raw -> text
            else -> cleanup(replaced.joinToString("\n"))
        }
        val expected = hashOf(expectedText)
        require(
            landed != null && landed.second.end == segment.start + replaced.size - 1 &&
                hashOf(landed.first.content) == expected
        ) { "改完之后在原文里会被拆成好几条或者跟上下文粘在一起。去掉中间的空行、行首的 - 再试" }
        val renames = align(pairs.filterIndexed { i, _ -> i != index }, reparsed.filter { it !== landed })
        requireNotNull(renames) { "改完之后前后的内容会粘在一起，去文件里改" }
        return Rewrite(newFile.content, landed.first.origin, landed.first.content, renames)
    }

    /**
     * 改写前后其余的块要一一对上（内容和顺序都不变），返回来源键有变化的那些；对不上返回 null。
     * 只有小标题的块不要求对上：删掉一个小标题下唯一的一条，小标题本身会变成单独一块，这是正常的。
     */
    private fun align(
        old: List<Pair<LegacyBlock, Segment>>,
        new: List<Pair<LegacyBlock, Segment>>,
    ): Map<String, String>? {
        val oldBody = old.filter { !it.second.bareHeading }.map { it.first }
        val newBody = new.filter { !it.second.bareHeading }.map { it.first }
        if (oldBody.size != newBody.size) return null
        val renames = LinkedHashMap<String, String>()
        oldBody.zip(newBody).forEach { (a, b) ->
            if (hashOf(a.content) != hashOf(b.content)) return null
            if (a.origin != b.origin) renames[a.origin] = b.origin
        }
        // 小标题块：同样文字的按先后对上，对不上的交给同步去删、去导入
        val newHeadings = new.filter { it.second.bareHeading }.map { it.first }.groupBy { hashOf(it.content) }
        old.filter { it.second.bareHeading }.map { it.first }.groupBy { hashOf(it.content) }
            .forEach { (hash, olds) ->
                olds.zip(newHeadings[hash].orEmpty()).forEach { (a, b) ->
                    if (a.origin != b.origin) renames[a.origin] = b.origin
                }
            }
        return renames
    }

    fun roomOf(path: String): String = when {
        path == "/topics/appearance.md" || path.startsWith("/self") || path.contains("/self/") -> MemoryRoom.SELF
        path == "/topics/diary.md" -> MemoryRoom.LIVING
        path.startsWith("/areas/") -> MemoryRoom.STUDY
        else -> MemoryRoom.USER
    }

    /** 正文里的一块：小标题路径、清理后的内容、在 body.lines() 里的首尾行号（含） */
    internal data class Segment(
        val heading: String,
        val text: String,
        val start: Int,
        val end: Int,
        /** 代码块，原样整块 */
        val raw: Boolean = false,
        /** 底下没内容的小标题本身 */
        val bareHeading: Boolean = false,
    )

    /** 返回 (所在小标题路径, 清理后的内容) */
    internal fun splitBlocks(body: String): List<Pair<String, String>> =
        splitSegments(body).map { it.heading to it.text }

    internal fun splitSegments(body: String): List<Segment> {
        val out = mutableListOf<Segment>()
        val headings = arrayOfNulls<String>(7)
        var current: StringBuilder? = null
        var start = 0
        var end = 0
        var raw = false
        var inFence = false
        var headingHasContent = true
        var headingLine = 0

        fun headingPath() = headings.filterNotNull().filter { it.isNotBlank() }.joinToString(" / ")

        fun flush() {
            val text = current?.toString()?.let(::cleanup)
            current = null
            if (!text.isNullOrEmpty()) {
                out += Segment(headingPath(), text, start, end, raw = raw)
                headingHasContent = true
            }
            raw = false
        }

        fun flushBareHeading() {
            if (!headingHasContent) {
                val path = headingPath()
                if (path.isNotEmpty()) out += Segment(path, path, headingLine, headingLine, bareHeading = true)
            }
            headingHasContent = true
        }

        fun begin(line: String, i: Int) {
            current = StringBuilder(line)
            start = i
            end = i
        }

        body.lines().forEachIndexed { i, line ->
            val trimmed = line.trim()
            if (inFence) {
                current!!.append('\n').append(line)
                end = i
                if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                    inFence = false
                    flush()
                }
                return@forEachIndexed
            }
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                flush()
                begin(line, i)
                raw = true
                inFence = true
                return@forEachIndexed
            }
            val heading = headingRegex.matchEntire(trimmed)
            if (heading != null && line.length - line.trimStart().length < 4) {
                flush()
                flushBareHeading()
                val level = heading.groupValues[1].length
                headings[level] = heading.groupValues[2]
                for (j in level + 1..6) headings[j] = null
                headingHasContent = false
                headingLine = i
                return@forEachIndexed
            }
            if (trimmed.isEmpty() || ruleRegex.matches(trimmed)) {
                flush()
                return@forEachIndexed
            }
            if (topListItemRegex.containsMatchIn(line)) flush()
            val block = current
            if (block == null) begin(line, i) else {
                block.append('\n').append(line)
                end = i
            }
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
