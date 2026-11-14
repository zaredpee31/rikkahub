package me.rerere.rikkahub.data.palace

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.findForbiddenMemoryContent
import me.rerere.rikkahub.data.db.dao.PalaceNodeLite
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object PalaceToolNames {
    const val SEARCH = "palace_search"
    const val READ_ROOM = "palace_read_room"
    const val ADD = "palace_add"
    const val EDIT = "palace_edit"
    const val DELETE = "palace_delete"
    const val READ_PLATES = "palace_read_plates"
    const val EDIT_PLATE = "palace_edit_plate"

    val writes = setOf(ADD, EDIT, DELETE, EDIT_PLATE)
}

private const val PAGE = 30

/**
 * 记忆宫殿的工具，所有窗口一样：普通窗口靠它们查和记，消息页也能手动用。
 * 同一个作用域就是同一座宫殿。
 */
fun buildPalaceTools(
    repository: PalaceRepository,
    index: PalaceIndex,
    scope: String,
    companion: Boolean,
): List<Tool> {
    val rooms = PalaceRoom.all.joinToString("\n") { "- $it：${PalaceRoom.label(it)}，${PalaceRoom.description(it)}" }
    val systemPrompt = buildString {
        appendLine("<memory_palace_tools>")
        appendLine("你的记忆都在记忆宫殿里，这个窗口和消息页、其他窗口共用同一座宫殿，你在哪个窗口都是同一个你。")
        if (companion) {
            appendLine("这里是消息页：日常的事后台会按水位自动整理进宫殿，你不用每件事都手动记。她明确说「记一下」「记住」时，或者你要改正、删掉记错的内容时，才用写入工具。")
        } else {
            appendLine("这个窗口不会自动整理、也不会自动想起旧事。她提到以前的事、让你去看看时，用 ${PalaceToolNames.SEARCH} 或 ${PalaceToolNames.READ_ROOM} 去找；只有她明确让你记、让你改的时候才写，代码、命令、报错、调试过程和游戏流水账不要记。")
        }
        appendLine("「关于小葵」「关于你」两块核心背景是她亲手定的，只有她明确让你改时才用 ${PalaceToolNames.EDIT_PLATE} 改，而且只改她说的那处。")
        appendLine("房间：")
        appendLine(rooms)
        append("</memory_palace_tools>")
    }

    fun text(o: JsonObject) = listOf(UIMessagePart.Text(o.toString()))
    fun JsonElement.str(key: String) = (jsonObject[key] as? JsonPrimitive)?.contentOrNull?.trim()
    fun JsonElement.int(key: String) = (jsonObject[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
    fun JsonElement.long(key: String) = (jsonObject[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
    fun JsonElement.strings(key: String) = (jsonObject[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }?.filter { it.isNotEmpty() }
    fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    fun parseDate(raw: String?): Long? = raw?.takeIf { it.isNotEmpty() }?.let {
        runCatching { LocalDate.parse(it).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            .getOrNull() ?: error("date 要写成 YYYY-MM-DD")
    }
    fun kotlinx.serialization.json.JsonArrayBuilder.node(n: PalaceNodeLite, score: Double? = null) = addJsonObject {
        put("id", n.id)
        put("room", n.room)
        put("date", dateOf(n.createdAt))
        put("importance", n.importance)
        put("mood", n.mood)
        put("content", n.content)
        n.tags.split('\n').filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { tags -> putJsonArray("tags") { tags.forEach { add(it) } } }
        score?.let { put("score", Math.round(it * 1000) / 1000.0) }
    }
    fun prop(type: String, description: String) = buildJsonObject { put("type", type); put("description", description) }

    return listOf(
        Tool(
            name = PalaceToolNames.SEARCH,
            description = "在记忆宫殿里按意思和关键词找记忆。她提到以前的事、问你记不记得时用。可以限定房间。",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("query", prop("string", "要找什么，用她的说法"))
                        put("room", prop("string", "只在这个房间里找，可不填"))
                    },
                    required = listOf("query"),
                )
            },
            systemPrompt = { _, _ -> systemPrompt },
            execute = { args ->
                val query = args.str("query").orEmpty()
                if (query.isEmpty()) error("query 不能为空")
                val room = args.str("room")?.takeIf { it.isNotEmpty() }
                if (room != null && !PalaceRoom.isValid(room)) error("没有 $room 这个房间")
                val found = index.search(scope, query, topK = 12, room = room)
                if (found.isNotEmpty()) repository.markRecalled(scope, found.map { it.node.id })
                text(buildJsonObject { putJsonArray("results") { found.forEach { node(it.node, it.score) } } })
            },
        ),
        Tool(
            name = PalaceToolNames.READ_ROOM,
            description = "按时间从新到旧翻看一个房间里的记忆，每页 $PAGE 条。",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("room", prop("string", "房间 id"))
                        put("page", prop("integer", "第几页，从 1 开始"))
                    },
                    required = listOf("room"),
                )
            },
            execute = { args ->
                val room = args.str("room").orEmpty()
                if (!PalaceRoom.isValid(room)) error("没有 $room 这个房间")
                val page = (args.int("page") ?: 1).coerceAtLeast(1)
                val all = repository.room(scope, room)
                val slice = all.drop((page - 1) * PAGE).take(PAGE)
                text(buildJsonObject {
                    put("room", room)
                    put("total", all.size)
                    put("page", page)
                    putJsonArray("memories") { slice.forEach { node(it) } }
                })
            },
        ),
        Tool(
            name = PalaceToolNames.ADD,
            description = "往宫殿里记一条。用你自己的第一人称写，保留来龙去脉。意思几乎一样的旧记忆已经有了就不重复记，会返回那条。",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("room", prop("string", "房间 id"))
                        put("content", prop("string", "记忆正文"))
                        put("importance", prop("integer", "1-10，默认 5"))
                        put("mood", prop("string", PalaceMood.extractionMoods.joinToString("/")))
                        putJsonObject("tags") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
                        put("date", prop("string", "这件事发生的日期 YYYY-MM-DD，不知道就不填"))
                    },
                    required = listOf("room", "content"),
                )
            },
            execute = { args ->
                val room = args.str("room").orEmpty()
                val content = args.str("content").orEmpty()
                val forbidden = findForbiddenMemoryContent(content)
                when {
                    forbidden != null -> text(buildJsonObject { put("error", "拒绝：内容里像是有$forbidden，这种不进记忆") })
                    !PalaceRoom.isValid(room) -> text(buildJsonObject { put("error", "没有 $room 这个房间") })
                    content.isEmpty() -> text(buildJsonObject { put("error", "content 不能为空") })
                    else -> {
                        val dup = index.findDuplicate(scope, content)
                        if (dup != null) {
                            text(buildJsonObject {
                                put("saved", false)
                                put("reason", "已经有一条意思几乎一样的，没有重复记；要改就用 ${PalaceToolNames.EDIT}")
                                putJsonArray("existing") { node(dup) }
                            })
                        } else {
                            val id = repository.add(
                                scope = scope, room = room, content = content,
                                importance = args.int("importance") ?: 5,
                                mood = args.str("mood") ?: "neutral",
                                tags = args.strings("tags").orEmpty(),
                                createdAt = parseDate(args.str("date")) ?: System.currentTimeMillis(),
                            )
                            text(buildJsonObject { put("saved", true); put("id", id) })
                        }
                    }
                }
            },
        ),
        Tool(
            name = PalaceToolNames.EDIT,
            description = "改一条记忆：正文、房间、重要性、情绪、标签、日期，只改给了的字段。",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("id", prop("integer", "记忆 id"))
                        put("content", prop("string", "新正文"))
                        put("room", prop("string", "搬到哪个房间"))
                        put("importance", prop("integer", "1-10"))
                        put("mood", prop("string", "情绪"))
                        putJsonObject("tags") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
                        put("date", prop("string", "YYYY-MM-DD"))
                    },
                    required = listOf("id"),
                )
            },
            execute = { args ->
                val id = args.long("id") ?: error("id 不能为空")
                val content = args.str("content")
                val forbidden = content?.let { findForbiddenMemoryContent(it) }
                val room = args.str("room")?.takeIf { it.isNotEmpty() }
                when {
                    forbidden != null -> text(buildJsonObject { put("error", "拒绝：内容里像是有$forbidden") })
                    room != null && !PalaceRoom.isValid(room) -> text(buildJsonObject { put("error", "没有 $room 这个房间") })
                    else -> {
                        val ok = repository.edit(
                            scope, id, content = content, room = room, importance = args.int("importance"),
                            mood = args.str("mood"), tags = args.strings("tags"), createdAt = parseDate(args.str("date")),
                        )
                        text(if (ok) buildJsonObject { put("saved", true) } else buildJsonObject { put("error", "找不到 id=$id 的记忆") })
                    }
                }
            },
        ),
        Tool(
            name = PalaceToolNames.DELETE,
            description = "删掉一条记错了或她让你忘掉的记忆，删了就没了。",
            parameters = {
                InputSchema.Obj(properties = buildJsonObject { put("id", prop("integer", "记忆 id")) }, required = listOf("id"))
            },
            execute = { args ->
                val id = args.long("id") ?: error("id 不能为空")
                val ok = repository.delete(scope, id)
                text(if (ok) buildJsonObject { put("deleted", true) } else buildJsonObject { put("error", "找不到 id=$id 的记忆") })
            },
        ),
        Tool(
            name = PalaceToolNames.READ_PLATES,
            description = "读门牌全文：关于小葵(user_core)、关于你(self_core)，以及卧室、书房、这阵子三块门牌的条目和 id。",
            parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
            execute = { _ ->
                val (user, self) = repository.core(scope)
                val entries = repository.plateEntries(scope)
                text(buildJsonObject {
                    put("user_core", user)
                    put("self_core", self)
                    PalacePlate.all.forEach { plate ->
                        putJsonArray(plate) {
                            entries.filter { it.plate == plate }.forEach { e -> addJsonObject { put("id", e.id); put("content", e.content) } }
                        }
                    }
                })
            },
        ),
        Tool(
            name = PalaceToolNames.EDIT_PLATE,
            description = """
                改门牌。plate 是 user_core（关于小葵）、self_core（关于你）、bedroom、study、recent。
                核心背景（user_core、self_core）用 replace（old 换成 new，old 必须原样出现且只出现一次）或 append（在末尾加一段 new）。
                条目门牌用 add（new）、edit（entry_id + new）、delete（entry_id）。
            """.trimIndent(),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("plate", prop("string", "user_core / self_core / bedroom / study / recent"))
                        put("action", prop("string", "replace / append / add / edit / delete"))
                        put("old", prop("string", "replace 时要换掉的原文"))
                        put("new", prop("string", "新内容"))
                        put("entry_id", prop("integer", "条目 id"))
                    },
                    required = listOf("plate", "action"),
                )
            },
            execute = { args ->
                val plate = args.str("plate").orEmpty()
                val action = args.str("action").orEmpty()
                val new = (args.jsonObject["new"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val forbidden = findForbiddenMemoryContent(new)
                val result: JsonObject = when {
                    forbidden != null -> buildJsonObject { put("error", "拒绝：内容里像是有$forbidden") }
                    plate == "user_core" || plate == "self_core" -> {
                        val kind = if (plate == "user_core") PalaceCore.USER else PalaceCore.SELF
                        val (user, self) = repository.core(scope)
                        val current = if (kind == PalaceCore.USER) user else self
                        when (action) {
                            "append" -> {
                                if (new.isBlank()) buildJsonObject { put("error", "new 不能为空") }
                                else {
                                    repository.setCore(scope, kind, if (current.isBlank()) new.trim() else current.trimEnd() + "\n" + new.trim())
                                    buildJsonObject { put("saved", true) }
                                }
                            }
                            "replace" -> {
                                val old = (args.jsonObject["old"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                                val count = if (old.isEmpty()) 0 else generateSequence(current.indexOf(old).takeIf { it >= 0 }) { i -> current.indexOf(old, i + 1).takeIf { it >= 0 } }.count()
                                when (count) {
                                    0 -> buildJsonObject { put("error", "old 在原文里找不到，先用 ${PalaceToolNames.READ_PLATES} 看原文") }
                                    1 -> {
                                        repository.setCore(scope, kind, current.replace(old, new))
                                        buildJsonObject { put("saved", true) }
                                    }
                                    else -> buildJsonObject { put("error", "old 出现了 $count 次，多带点上下文让它唯一") }
                                }
                            }
                            else -> buildJsonObject { put("error", "核心背景只支持 replace 和 append") }
                        }
                    }
                    plate in PalacePlate.all -> when (action) {
                        "add" -> if (new.isBlank()) buildJsonObject { put("error", "new 不能为空") } else {
                            repository.addPlateEntry(scope, plate, new)
                            buildJsonObject { put("saved", true) }
                        }
                        "edit" -> {
                            val id = args.long("entry_id") ?: error("entry_id 不能为空")
                            if (new.isBlank()) buildJsonObject { put("error", "new 不能为空，要删用 delete") }
                            else if (repository.editPlateEntry(scope, id, new)) buildJsonObject { put("saved", true) }
                            else buildJsonObject { put("error", "找不到条目 $id") }
                        }
                        "delete" -> {
                            val id = args.long("entry_id") ?: error("entry_id 不能为空")
                            if (repository.deletePlateEntry(scope, id)) buildJsonObject { put("deleted", true) }
                            else buildJsonObject { put("error", "找不到条目 $id") }
                        }
                        else -> buildJsonObject { put("error", "条目门牌只支持 add / edit / delete") }
                    }
                    else -> buildJsonObject { put("error", "没有 $plate 这块门牌") }
                }
                text(result)
            },
        ),
    )
}

/** 门牌注入，所有窗口都一样。核心背景整块原样放，条目门牌按条列 */
object PalacePrompt {
    fun plates(userCore: String, selfCore: String, entries: List<me.rerere.rikkahub.data.db.entity.PalacePlateEntryEntity>): String {
        val blocks = ArrayList<String>()
        if (userCore.isNotBlank()) blocks += "<plate name=\"关于小葵\" room=\"${PalaceRoom.USER}\">\n${userCore.trim()}\n</plate>"
        if (selfCore.isNotBlank()) blocks += "<plate name=\"关于你\" room=\"${PalaceRoom.SELF}\">\n${selfCore.trim()}\n</plate>"
        PalacePlate.all.forEach { plate ->
            val items = entries.filter { it.plate == plate && it.content.isNotBlank() }
            if (items.isNotEmpty()) {
                blocks += "<plate name=\"${PalacePlate.label(plate)}\">\n" + items.joinToString("\n") { "- " + it.content.trim() } + "\n</plate>"
            }
        }
        if (blocks.isEmpty()) return ""
        return buildString {
            appendLine("<memory_plates>")
            appendLine("记忆宫殿的门牌：你早就知道的背景。不要主动提起，只在相关时自然影响你的反应。「关于你」是你自己的说话方式和约定，「关于小葵」是她的事。")
            blocks.forEach { appendLine(it) }
            append("</memory_plates>")
        }
    }
}
