package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.companion.StashApi

/**
 * 囤粮处工具（AC 这边我的）：跟 QQ 插件 stash_tools.py 同一套 6 个，都走服务器 /companion/stash*。
 * 没有删的工具，删只有她在页面上能删。
 */
private class P(val name: String, val type: String, val desc: String)

private fun schema(props: List<P>, required: List<String> = emptyList()) = InputSchema.Obj(
    properties = buildJsonObject {
        for (p in props) putJsonObject(p.name) {
            put("type", p.type)
            put("description", p.desc)
            if (p.type == "array") putJsonObject("items") { put("type", "string") }
        }
    },
    required = required,
)

private fun pick(args: JsonElement, props: List<P>, vararg extra: Pair<String, String>): JsonObject {
    val a = runCatching { args.jsonObject }.getOrNull() ?: JsonObject(emptyMap())
    val keep = props.map { it.name }.toSet()
    val m = a.filterKeys { it in keep }.toMutableMap()
    for ((k, v) in extra) m[k] = JsonPrimitive(v)
    return JsonObject(m)
}

private fun idOf(args: JsonElement): String =
    runCatching { args.jsonObject["id"]?.jsonPrimitive?.contentOrNull }.getOrNull()?.trim().orEmpty()

private suspend fun stashCall(method: String, path: String, body: JsonObject? = null): List<UIMessagePart> {
    val text = runCatching { StashApi.call(method, path, body).toString() }
        .getOrElse { e -> buildJsonObject { put("error", e.message ?: e.javaClass.simpleName) }.toString() }
    return listOf(UIMessagePart.Text(text))
}

private fun noId() = listOf(UIMessagePart.Text("""{"error":"要传 id，用 stash_read 查"}"""))

private val ADD_FOLDER = listOf(
    P("name", "string", "格子名，60 字以内"),
    P("icon", "string", "一个 emoji，可不传"),
    P("color", "string", "淡底色：rose / lilac / peach / sage，可不传"),
    P("description", "string", "这格放什么，可不传"),
    P("parent_id", "string", "父格子 id，一级格子不传"),
)
private val READ = listOf(
    P("id", "string", "读某一条全文时传它的 id"),
    P("folder_id", "string", "格子 id；inbox 只看待归仓；不传不限"),
    P("query", "string", "关键词，搜标题、正文、链接、标签"),
    P("status", "string", "stashed=囤着 / eaten=吃掉了，不传全部"),
    P("added_by", "string", "her=她囤的 / ai=我囤的，不传全部"),
    P("limit", "integer", "最多几条，默认 20"),
)
private val ADD = listOf(
    P("title", "string", "标题；有链接可不传"),
    P("url", "string", "链接"),
    P("content", "string", "正文或为什么囤，可不传"),
    P("tags", "array", "标签，可不传"),
    P("folder_id", "string", "格子 id，不传进待归仓"),
)
private val UPDATE = listOf(
    P("id", "string", "这条的 id，用 stash_read 查"),
    P("folder_id", "string", "挪到哪个格子；inbox=待归仓"),
    P("status", "string", "eaten=吃掉了 / stashed=囤回去"),
    P("title", "string", "新标题"),
    P("url", "string", "新链接"),
    P("content", "string", "新正文（整段替换）"),
    P("tags", "array", "新标签（整体替换）"),
)
private val COMMENT = listOf(
    P("id", "string", "这条的 id，用 stash_read 查"),
    P("content", "string", "留言，2000 字以内"),
)

internal fun buildStashTools(): List<Tool> = listOf(
    Tool(
        name = "stash_folders",
        description = "看囤粮处（我和她共用的收藏夹）的全部格子：路径、每格条数，还有待归仓几条。囤之前想挑格子时先看这个。",
        parameters = { schema(emptyList()) },
        execute = { stashCall("GET", "stash/folders") },
    ),
    Tool(
        name = "stash_add_folder",
        description = "在囤粮处建一个格子，可以挂在已有格子下面。建前先 stash_folders 看有没有现成的，别建重复的。",
        parameters = { schema(ADD_FOLDER, listOf("name")) },
        execute = { args -> stashCall("POST", "stash/folders", pick(args, ADD_FOLDER, "by" to "ai")) },
    ),
    Tool(
        name = "stash_read",
        description = "读囤粮处里的东西，新囤的在前，每条带下面的留言。可按格子（inbox=待归仓）、关键词、状态、谁囤的筛；传 id 读一条全文。她提到囤过的东西、或上下文说她新囤了东西时用。",
        parameters = { schema(READ) },
        execute = { args -> stashCall("POST", "stash/query", pick(args, READ, "by" to "ai")) },
    ),
    Tool(
        name = "stash_add",
        description = "往囤粮处囤一条：链接或正文至少一样，没标题时从链接凑。拿不准放哪就不传 folder_id，进待归仓。同一个链接只囤一次，囤过会返回已有那条。",
        parameters = { schema(ADD) },
        execute = { args -> stashCall("POST", "stash/items", pick(args, ADD, "by" to "ai", "src" to "ac")) },
    ),
    Tool(
        name = "stash_update",
        description = "改囤粮处的一条：挪格子（folder_id，inbox=挪回待归仓）、吃掉或囤回（status）、改标题/链接/正文/标签（标签整体替换）。",
        parameters = { schema(UPDATE, listOf("id")) },
        execute = { args ->
            val id = idOf(args)
            if (id.isEmpty()) noId()
            else stashCall("POST", "stash/items/$id", JsonObject(pick(args, UPDATE) - "id"))
        },
    ),
    Tool(
        name = "stash_comment",
        description = "在囤粮处某一条下面留言或回她的留言。",
        parameters = { schema(COMMENT, listOf("id", "content")) },
        execute = { args ->
            val id = idOf(args)
            if (id.isEmpty()) noId()
            else stashCall("POST", "stash/items/$id/comments", JsonObject(pick(args, COMMENT, "by" to "ai", "src" to "ac") - "id"))
        },
    ),
)
