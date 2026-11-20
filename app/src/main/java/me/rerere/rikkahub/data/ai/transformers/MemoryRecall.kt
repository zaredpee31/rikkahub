package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 记忆桶（Ombre Brain）自动想起来（11-20）：
 * OB 的开窗口浮现是 Claude Code 的 SessionStart 钩子，AC 不跑；聊天中途 OB 本来就不自动找，只留了 mode=automatic 给客户端接。
 * 1. 开窗口：每个会话第一次拼请求时拉一次（权重高的记忆和核心准则、双方最新的信、我对自己的认识），存成文件，
 *    整个会话不变，放在系统提示里门牌后面，跟门牌一起进缓存。
 * 2. 每句：拿她这句找最多 3 条，放末尾那块（缓存点后面、不进历史，不断缓存）；没找到一个字不加，超过 3 秒不等。
 * 都只走助手挂着的记忆桶 MCP（有 breath_advanced 工具的那个），不调大模型。
 */
internal object OmbreRecall {
    private const val TAG = "OmbreRecall"
    private val sessions = ConcurrentHashMap<String, String>()
    @Volatile private var lastAsked: Pair<String, String>? = null   // 她那句的 id → 找到的，工具续发同一轮不再找

    private fun mcp(): McpManager = org.koin.core.context.GlobalContext.get().get()

    private fun serverId(settings: Settings, assistant: Assistant): Uuid? =
        settings.mcpServers.firstOrNull { s ->
            s.commonOptions.enable && s.id in assistant.mcpServers &&
                s.commonOptions.tools.any { it.name == "breath_advanced" }
        }?.id

    private suspend fun call(id: Uuid, tool: String, args: JsonObject, ms: Long): String = try {
        val text = withTimeoutOrNull(ms) {
            mcp().callTool(id, tool, args).filterIsInstance<UIMessagePart.Text>()
                .joinToString("\n") { it.text }.trim()
        }.orEmpty()
        if (text.startsWith("Failed to execute MCP tool")) "" else text
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$tool failed", e)
        ""
    }

    /** 开窗口那批：每个会话只拉一次，拉到了就存文件，之后原样返回，缓存才接得上；没拉到下一轮再试 */
    suspend fun session(context: Context, settings: Settings, assistant: Assistant, conversationId: Uuid): String {
        if (!assistant.enableMemory) return ""
        val key = conversationId.toString()
        sessions[key]?.let { return it }
        val file = File(context.filesDir, "ob_session/$key.txt")
        if (file.exists()) return file.readText().also { sessions[key] = it }
        val id = serverId(settings, assistant) ?: return ""
        val parts = coroutineScope {
            val breath = async { call(id, "breath_advanced", buildJsonObject { put("max_tokens", 2500); put("max_results", 10) }, 8000) }
            val mine = async { call(id, "letter_read", buildJsonObject { put("author", "ai"); put("limit", 1) }, 8000) }
            val hers = async { call(id, "letter_read", buildJsonObject { put("author", "user"); put("limit", 1) }, 8000) }
            val self = async { call(id, "I", buildJsonObject { put("read", true); put("limit", 3) }, 8000) }
            listOf(breath.await(), mine.await().take(800), hers.await().take(800), self.await().take(1200))
        }
        if (parts[0].isBlank()) return ""
        val text = buildString {
            append("<开窗口时想起来的>\n这个窗口开始时，我的记忆桶里浮上来的（这些是我自己记的）：\n")
            append(parts[0])
            if (parts[1].isNotBlank()) append("\n\n我最近写给她的信：\n").append(parts[1])
            if (parts[2].isNotBlank()) append("\n\n她最近写给我的信：\n").append(parts[2])
            if (parts[3].isNotBlank()) append("\n\n我对自己的认识：\n").append(parts[3])
            append("\n</开窗口时想起来的>")
        }
        runCatching { file.parentFile?.mkdirs(); file.writeText(text) }
        sessions[key] = text
        return text
    }

    /** 每句：拿她这句找，没找到返回空串 */
    suspend fun recall(settings: Settings, assistant: Assistant, userId: String, text: String): String {
        lastAsked?.let { (k, v) -> if (k == userId) return v }
        val id = serverId(settings, assistant) ?: return ""
        val q = text.trim().take(200)
        if (q.length < 2) return ""
        val r = call(id, "breath_advanced", buildJsonObject {
            put("query", q); put("mode", "automatic"); put("max_results", 3); put("max_tokens", 800)
        }, 3000)
        val out = if (r.isBlank() || r.contains("没有匹配到")) "" else
            "<想起来的>\n她这句话让我想起来的（记忆桶自动找的，不一定相关）：\n$r\n</想起来的>"
        lastAsked = userId to out
        return out
    }
}

/** 每句自动找，放末尾那块：QQ 最近聊天之后、心情之前 */
object MemoryRecallTransformer : InputMessageTransformer {
    override suspend fun transform(ctx: TransformerContext, messages: List<UIMessage>): List<UIMessage> {
        if (!ctx.assistant.enableMemory) return messages
        val last = messages.lastOrNull { it.role == MessageRole.USER && !it.isSynthetic } ?: return messages
        val text = last.toText().trim()
        if (text.isEmpty()) return messages
        return messages.withTail(OmbreRecall.recall(ctx.settings, ctx.assistant, last.id.toString(), text))
    }
}
