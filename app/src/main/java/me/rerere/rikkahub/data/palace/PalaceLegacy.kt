package me.rerere.rikkahub.data.palace

import me.rerere.rikkahub.data.model.MemoryFile
import java.security.MessageDigest

/**
 * 旧 Markdown 记忆搬进宫殿的方案，只算不写，方便测试。
 *
 * - /profile.md（关于小葵）整份正文进用户房的核心背景，/preferences.md（关于你）整份进自我房的核心背景，原文照搬；
 * - 其余文件按条拆成记忆，按文件归房间；
 * - 旧版本已经有的节点（消息页整理的、手动加的）原样带过来，旧的从文件派生的节点不要，以文件为准重新拆；
 * - 旧自我门牌里有和「关于你」不一样的内容时，不覆盖也不丢，存成自我房的一条记忆。
 */
object PalaceLegacy {
    data class OldNode(
        val room: String,
        val content: String,
        /** 旧版 0 到 1 */
        val importance: Float,
        val mood: String,
        val keywords: String,
        val happenedAt: Long,
        val createdAt: Long,
        val origin: String,
        val source: String,
    )

    data class OldPlate(val kind: String, val content: String, val source: String)

    data class PlannedNode(
        val room: String,
        val content: String,
        val importance: Int,
        val mood: String,
        val tags: List<String>,
        val createdAt: Long,
        val origin: String,
        val source: String,
    )

    data class Plan(
        /** null 表示没有这份文件，核心背景保持原样 */
        val userCore: String?,
        val selfCore: String?,
        val nodes: List<PlannedNode>,
    )

    const val SOURCE_LEGACY = "legacy"

    fun plan(files: List<MemoryFile>, oldNodes: List<OldNode>, oldPlates: List<OldPlate>): Plan {
        val byPath = files.associateBy { it.path }
        val profile = byPath[MemoryFile.PROFILE_PATH]
        val preferences = byPath[MemoryFile.PREFERENCES_PATH]
        val userCore = profile?.displayBody?.trim()?.takeIf { it.isNotEmpty() }
        val selfCore = preferences?.displayBody?.trim()?.takeIf { it.isNotEmpty() }

        val nodes = ArrayList<PlannedNode>()
        files
            .filter { it.path != MemoryFile.PROFILE_PATH && it.path != MemoryFile.PREFERENCES_PATH }
            .sortedBy { it.path }
            .forEach { file ->
                val room = roomOf(file.path)
                val seen = HashMap<String, Int>()
                splitBlocks(file.displayBody).forEach { (heading, text) ->
                    val content = text.trim()
                    if (content.isEmpty()) return@forEach
                    val hash = hashOf(content)
                    val n = (seen[hash] ?: 0) + 1
                    seen[hash] = n
                    nodes += PlannedNode(
                        room = room,
                        content = content,
                        importance = 6,
                        mood = "neutral",
                        tags = (listOf(file.title) + heading.split(" / ")).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(5),
                        createdAt = dateIn(content) ?: file.updatedAt,
                        origin = "legacy:${file.path}:$hash" + if (n > 1) ":$n" else "",
                        source = SOURCE_LEGACY,
                    )
                }
            }

        oldNodes.filter { it.source != SOURCE_LEGACY && it.content.isNotBlank() }.forEach { old ->
            nodes += PlannedNode(
                room = PalaceRoom.normalize(old.room),
                content = old.content.trim(),
                importance = (old.importance * 10).let { if (it.isFinite()) Math.round(it) else 5 }.coerceIn(1, 10),
                mood = PalaceMood.normalize(old.mood),
                tags = old.keywords.split(',', '，', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(5),
                createdAt = if (old.happenedAt > 0) old.happenedAt else old.createdAt,
                origin = "old:" + old.origin.ifEmpty { hashOf(old.content) },
                source = old.source,
            )
        }

        oldPlates.firstOrNull { it.kind == "self" }?.content?.trim()?.takeIf { it.isNotEmpty() }?.let { oldSelf ->
            if (selfCore == null || normalize(oldSelf) !in normalize(selfCore)) {
                nodes += PlannedNode(
                    room = PalaceRoom.SELF,
                    content = oldSelf,
                    importance = 7,
                    mood = "neutral",
                    tags = listOf("旧自我门牌"),
                    createdAt = 0,
                    origin = "old-plate:self:" + hashOf(oldSelf),
                    source = SOURCE_LEGACY,
                )
            }
        }
        // 旧用户门牌：没有 profile 时它就是唯一的用户背景，直接当核心背景；有 profile 时以 profile 为准
        val userFromPlate = if (userCore == null) {
            oldPlates.firstOrNull { it.kind == "user" }?.content?.trim()?.takeIf { it.isNotEmpty() }
        } else null

        return Plan(userCore = userCore ?: userFromPlate, selfCore = selfCore, nodes = nodes)
    }

    fun roomOf(path: String): String = when {
        path == "/topics/appearance.md" || path.startsWith("/self") || path.contains("/self/") -> PalaceRoom.SELF
        path == "/topics/diary.md" -> PalaceRoom.LIVING
        path == "/topics/us.md" -> PalaceRoom.BEDROOM
        path.startsWith("/areas/") -> PalaceRoom.STUDY
        else -> PalaceRoom.USER
    }

    /**
     * 按列表项和段落拆成一条条，带上所在的小标题路径。
     * 列表项的续行（缩进或紧跟着的非空行）算同一条；只有小标题没有内容的不算。
     */
    fun splitBlocks(body: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val headings = arrayOfNulls<String>(7)
        val current = StringBuilder()
        fun headingPath() = headings.filterNotNull().filter { it.isNotBlank() }.joinToString(" / ")
        fun flush() {
            val text = current.toString().trim()
            if (text.isNotEmpty()) out += headingPath() to text
            current.clear()
        }
        val heading = Regex("^(#{1,6})\\s+(.*)$")
        val item = Regex("^\\s{0,3}(?:[-*+]|\\d+[.)])\\s+(.*)$")
        for (raw in body.lines()) {
            val line = raw.trimEnd()
            val h = heading.find(line)
            when {
                h != null -> {
                    flush()
                    val level = h.groupValues[1].length
                    headings[level] = h.groupValues[2].trim()
                    for (i in level + 1 until headings.size) headings[i] = null
                }
                line.isBlank() -> flush()
                item.matches(line) && !raw.startsWith("    ") && !raw.startsWith("\t") -> {
                    flush()
                    current.append(item.find(line)!!.groupValues[1].trim())
                }
                else -> {
                    if (current.isNotEmpty()) current.append('\n')
                    current.append(line.trim())
                }
            }
        }
        flush()
        return out
    }

    private val dateRegex = Regex("(20\\d{2})[-./年](\\d{1,2})[-./月](\\d{1,2})")

    /** 条目里写了 2026-11-14 / 2026年11月14日 这样的日期就当它发生的那天（中午），没写返回 null */
    fun dateIn(text: String): Long? {
        val m = dateRegex.find(text) ?: return null
        val (y, mo, d) = m.destructured
        return runCatching {
            java.time.LocalDate.of(y.toInt(), mo.toInt(), d.toInt())
                .atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun normalize(text: String) = text.replace(Regex("\\s+"), "")

    fun hashOf(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalize(text).toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
