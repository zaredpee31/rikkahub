package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.MemoryRoom
import me.rerere.rikkahub.data.model.MemorySource
import me.rerere.rikkahub.data.repository.MemoryIndex
import me.rerere.rikkahub.data.repository.MemoryNodeRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object MemoryNodeToolNames {
    const val SEARCH = "memory_node_search"
    const val ADD = "memory_node_add"
}

private const val SEARCH_LIMIT = 10

/**
 * 记忆节点的手动查、手动记。普通窗口不自动召回，要查就靠 [MemoryNodeToolNames.SEARCH]；
 * 用户明确说"记一下"时用 [MemoryNodeToolNames.ADD] 写进共享的记忆库。
 */
fun buildMemoryNodeTools(
    nodeRepository: MemoryNodeRepository,
    memoryIndex: MemoryIndex,
    memoryId: String,
): List<Tool> = listOf(
    Tool(
        name = MemoryNodeToolNames.SEARCH,
        description = """
            Search remembered moments: small daily things, things done together, what the user has been busy with.
            Use it when the user refers to something from before that is not in profile or the memory files.
            Returns matching entries with their room and date.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "What to look for, in the user's language")
                    })
                },
                required = listOf("query")
            )
        },
        execute = { args ->
            val query = (args.jsonObject["query"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (query.isEmpty()) error("query is required")
            val terms = query.split(Regex("\\s+")).filter { it.isNotBlank() }
            val matches = nodeRepository.summaries(memoryId)
                .filter { node ->
                    val text = node.content + " " + node.keywords
                    terms.any { text.contains(it, ignoreCase = true) }
                }
                .take(SEARCH_LIMIT)
            val related = memoryIndex.recallPrompt(memoryId, query)
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("matches", buildJsonArray {
                            matches.forEach { node ->
                                add(buildJsonObject {
                                    put("id", node.id)
                                    put("room", MemoryRoom.label(node.room))
                                    put("content", node.content)
                                    if (node.happenedAt > 0) put("date", dateOf(node.happenedAt))
                                })
                            }
                        })
                        if (related.isNotBlank()) put("related", related)
                    }.toString()
                )
            )
        }
    ),
    Tool(
        name = MemoryNodeToolNames.ADD,
        description = """
            Save one remembered moment. Only when the user explicitly asks you to remember something
            that is a concrete event or a small daily thing rather than a stable fact for the memory files.
            room: living_room (daily small things), bedroom (the two of you, things done together),
            study (projects, work, skills), user_room (the user's habits and info),
            self_room (your own settings and self-view; never facts about the user).
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("room", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { MemoryRoom.all.forEach { add(it) } })
                    })
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "One or two sentences in the user's own words")
                    })
                    put("date", buildJsonObject {
                        put("type", "string")
                        put("description", "When it happened, YYYY-MM-DD. Leave out if unknown; do not guess")
                    })
                },
                required = listOf("room", "content")
            )
        },
        execute = { args ->
            val obj = args.jsonObject
            val room = (obj["room"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val content = (obj["content"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val date = (obj["date"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val forbidden = findForbiddenMemoryContent(content)
            val output = when {
                forbidden != null -> buildJsonObject {
                    put("error", "Refused: the content looks like it contains $forbidden, which is never stored in memory.")
                }
                !MemoryRoom.isValid(room) -> buildJsonObject { put("error", "Unknown room \"$room\".") }
                content.isBlank() -> buildJsonObject { put("error", "content must not be empty") }
                else -> {
                    val happenedAt = date.takeIf { it.isNotEmpty() }?.let {
                        runCatching {
                            LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }.getOrNull() ?: error("date must be YYYY-MM-DD")
                    } ?: 0L
                    val id = nodeRepository.add(
                        memoryId = memoryId,
                        room = room,
                        content = content,
                        happenedAt = happenedAt,
                        source = MemorySource.EXPLICIT,
                    )
                    buildJsonObject {
                        put("saved", id != null)
                        id?.let { put("id", it) }
                    }
                }
            }
            listOf(UIMessagePart.Text(output.toString()))
        }
    ),
)

private fun dateOf(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
