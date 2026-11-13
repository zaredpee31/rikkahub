package me.rerere.rikkahub.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.memoryId
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.NotificationUtil
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

/** Only the explicit target receives proactive messages. The existing assistant owns all memory. */
object CompanionSync {
    private const val PREFS = "companion_messages"
    private const val CHANNEL = "companion_incoming"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null
    @Volatile private var app: Context? = null
    fun initialize(c: Context) { app = c.applicationContext }
    /** Debounce only the bound assistant. Never launch a scheduler for each conversation. */
    @Synchronized
    fun contextChanged() {
        val c = app ?: return
        if (!enabled(c)) return
        pending?.cancel()
        pending = scope.launch {
            delay(2_000)
            runCatching { sync(c) }
        }
    }
    private val mutex = Mutex()
    private var uploadedHash: String? = null
    private var uploadedAt = 0L
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(40, TimeUnit.SECONDS).build()
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun needsDisable(c: Context) = prefs(c).getBoolean("disable_pending", false)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun target(c: Context) = prefs(c).getString("target", "").orEmpty()
    fun status(c: Context) = prefs(c).getString("status", "尚未启用").orEmpty()
    fun morning(c: Context) = prefs(c).getBoolean("morning", true)
    fun proactive(c: Context) = prefs(c).getBoolean("proactive", true)
    fun options(c: Context, morning: Boolean, proactive: Boolean) {
        prefs(c).edit().putBoolean("morning", morning).putBoolean("proactive", proactive).apply()
    }
    suspend fun bind(c: Context, conversationId: Uuid) {
        require(!enabled(c) && !needsDisable(c)) { "请先关闭并等待服务器确认，再更换会话" }
        val chat = GlobalContext.get().get<ChatService>()
        val conversation = chat.getConversation(conversationId) ?: error("请先在这个窗口发一条消息")
        val settings = GlobalContext.get().get<SettingsStore>().awaitLoaded()
        require(settings.assistants.any { it.id == conversation.assistantId }) { "该助手已被删除" }
        prefs(c).edit().putString("target", conversationId.toString())
            .putString("assistant", conversation.assistantId.toString()).commit()
    }
    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("enabled", on).putBoolean("disable_pending", !on)
            .putString("status", if (on) "等待同步" else "本机已停止接收，等待服务器确认关闭").commit()
        CompanionReceiverService.scheduleFallback(c)
    }
    private suspend fun connection(): Pair<String, String> {
        val settings = GlobalContext.get().get<SettingsStore>().awaitLoaded()
        val candidates = settings.mcpServers.filter { it.commonOptions.enable }.mapNotNull { server ->
            val address = when (server) {
                is McpServerConfig.StreamableHTTPServer -> server.url
                is McpServerConfig.SseTransportServer -> server.url
            }
            val url = runCatching { address.toHttpUrl() }.getOrNull() ?: return@mapNotNull null
            if (!url.isHttps || url.encodedPath.trimEnd('/') !in setOf("/mcp", "/sse")) return@mapNotNull null
            val header = server.commonOptions.headers.lastOrNull { it.first.equals("Authorization", true) }?.second
            val token = url.queryParameter("key")?.takeIf { it.isNotBlank() }
                ?: header?.takeIf { it.startsWith("Bearer ", true) }?.substringAfter(' ')?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/') to token
        }.distinct()
        val home = candidates.filter { it.first.toHttpUrl().host == "43-166-23-196.sslip.io" }
        if (home.size == 1) return home.single()
        require(candidates.size == 1) { "需要唯一的小家 HTTPS MCP 地址及 key 或 Bearer 鉴权；请检查服务器配置" }
        return candidates.single()
    }

    private fun request(connection: Pair<String, String>, path: String, body: JsonObject? = null): JsonObject {
        val req = Request.Builder().url(connection.first + "/companion/" + path)
            .header("Authorization", "Bearer " + connection.second)
        if (body != null) req.post(body.toString().toRequestBody("application/json".toMediaType()))
        client.newCall(req.build()).execute().use { response ->
            require(response.isSuccessful) { "服务器返回 ${response.code}" }
            return Json.parseToJsonElement(response.body.string()).jsonObject
        }
    }
    suspend fun sync(c: Context): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val link = connection()
                if (!enabled(c)) {
                    request(link, "sync", buildJsonObject { put("enabled", false) })
                    uploadedHash = null
                    prefs(c).edit().putBoolean("disable_pending", false).apply()
                    prefs(c).edit().putString("status", "主动消息已关闭，服务器已确认").apply()
                    WorkManager.getInstance(c).cancelUniqueWork("companion_sync")
                    return@withLock
                }
                val id = Uuid.parse(target(c))
                val koin = GlobalContext.get()
                val chat = koin.get<ChatService>()
                val conv = chat.getConversation(id) ?: error("消息会话已删除，请重新选择")
                require(conv.assistantId.toString() == prefs(c).getString("assistant", "")) { "消息会话的助手已改变，请重新绑定" }
                val settings = koin.get<SettingsStore>().awaitLoaded()
                require(settings.assistants.any { it.id == conv.assistantId }) { "绑定的助手已删除" }
                val assistant = settings.getAssistantOf(conv)
                val model = settings.getChatModelOf(conv) ?: error("这个会话还没有模型")
                val provider = model.findProvider(settings.providers) ?: error("找不到模型提供商")
                val type: String
                val url: String
                val key: String
                when (provider) {
                    is ProviderSetting.OpenAI -> {
                        type = if (provider.useResponseApi) "responses" else "openai"
                        val path = if (provider.useResponseApi) provider.responsesPath else provider.chatCompletionsPath
                        url = provider.baseUrl.trimEnd('/') + "/" + path.trimStart('/')
                        key = provider.apiKey
                    }
                    is ProviderSetting.Claude -> {
                        type = "claude"; url = provider.baseUrl.trimEnd('/') + "/messages"; key = provider.apiKey
                    }
                    else -> error("第一版后台支持 OpenAI 兼容和 Claude 提供商，当前提供商暂不支持")
                }
                require(url.toHttpUrl().isHttps) { "模型接口必须使用 HTTPS" }
                require(key.isNotBlank()) { "模型 API key 为空" }
                val memories = if (assistant.enableMemory) koin.get<MemoryRepository>().getFiles(assistant.memoryId) else emptyList()
                val recent = koin.get<ConversationRepository>().getRecentConversations(conv.assistantId, 8)
                    .map { chat.getConversation(it.id) ?: it }
                val context = (listOf(conv) + recent.filter { it.id != id }).joinToString("\n\n") { item ->
                    "<conversation id=\"${item.id}\" title=\"${item.title.replace(Regex("[<>\\\"\\n]"), " ")}\">\n" +
                        item.currentMessages.takeLast(if (item.id == id) 40 else 12).joinToString("\n") {
                            "[${it.createdAt}] " + it.summaryAsText(2500)
                        } + "\n</conversation>"
                }.take(100000)
                val lastUser = (listOf(conv) + recent).flatMap { it.currentMessages }
                    .filter { it.role == MessageRole.USER }.maxOfOrNull {
                        it.createdAt.toInstant(TimeZone.currentSystemDefault()).epochSeconds
                    } ?: 0L
                val busy = chat.getConversationJobs().first().any { (cid, job) ->
                    job?.isActive == true && (cid == id || recent.any { it.id == cid })
                }
                val effectivePrompt = if (assistant.allowConversationSystemPrompt && !conv.customSystemPrompt.isNullOrBlank()) conv.customSystemPrompt!! else assistant.systemPrompt
                val rendered = me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer.transform(
                    me.rerere.rikkahub.data.ai.transformers.TransformerContext(c, model, assistant, settings),
                    listOf(UIMessage.system(effectivePrompt)),
                ).single().toText()
                val payload = buildJsonObject {
                    put("enabled", true); put("conversation_id", id.toString()); put("assistant_id", conv.assistantId.toString())
                    put("morning", morning(c)); put("proactive", proactive(c)); put("busy", busy); put("last_user_at", lastUser)
                    put("persona", rendered)
                    put("memory", memories.joinToString("\n\n") { "<memory path=\"${it.path}\">\n${it.content}\n</memory>" })
                    put("context", context)
                    put("provider", buildJsonObject {
                        put("type", type); put("url", url); put("key", key); put("model", model.modelId)
                        put("headers", buildJsonObject {
                            (provider.customHeaders + assistant.customHeaders + model.customHeaders).forEach { h ->
                                put(h.name, h.value.replace("{{apiKey}}", key))
                            }
                        })
                    })
                }
                if (!enabled(c) || target(c) != id.toString()) return@withLock
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((link.toString() + payload.toString()).toByteArray())
                    .joinToString("") { "%02x".format(it) }
                val nowMs = System.currentTimeMillis()
                if (uploadedHash != digest || nowMs - uploadedAt >= 15 * 60_000L) {
                    request(link, "sync", payload)
                    uploadedHash = digest
                    uploadedAt = nowMs
                }
                val received = request(link, "messages")["messages"]?.jsonArray.orEmpty()
                val ack = mutableListOf<String>()
                for (element in received) {
                    val msg = element.jsonObject
                    if (msg["conversation"]?.jsonPrimitive?.content != id.toString() ||
                        msg["assistant"]?.jsonPrimitive?.content != conv.assistantId.toString()) continue
                    // Re-check opt-in and target after network I/O, before mutating live sessions.
                    if (!enabled(c) || target(c) != id.toString()) break
                    val messageId = Uuid.parse(msg.getValue("id").jsonPrimitive.content)
                    val text = msg.getValue("text").jsonPrimitive.content
                    val inserted = chat.receiveCompanionMessage(id, conv.assistantId, UIMessage.assistant(text).copy(
                        id = messageId,
                        createdAt = kotlin.time.Instant.fromEpochMilliseconds((msg.getValue("created").jsonPrimitive.double * 1000).toLong())
                            .toLocalDateTime(TimeZone.currentSystemDefault()),
                        modelId = model.id,
                    ))
                    if (inserted) notify(c, id, messageId, assistant.name.ifBlank { "阿辞" }, text)
                    ack += messageId.toString()
                }
                if (ack.isNotEmpty()) request(link, "ack", buildJsonObject { put("ids", JsonArray(ack.map { JsonPrimitive(it) })) })
                prefs(c).edit().putString("status", "已同步人设、记忆和近期聊天 · " + java.time.LocalTime.now().withNano(0)).apply()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                prefs(c).edit().putString("status", if (needsDisable(c)) "本机已停止接收，服务器尚未确认关闭；联网后重试" else "同步失败，请检查服务器连接、模型和会话设置").apply()
                throw e
            }
        }
    }
    private fun notify(c: Context, conversationId: Uuid, messageId: Uuid, name: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "阿辞的消息", NotificationManager.IMPORTANCE_DEFAULT))
        if (!NotificationUtil.hasNotificationPermission(c)) return
        val intent = Intent(c, RouteActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("companionMessages", true).setAction("companion.$messageId")
        val pending = PendingIntent.getActivity(c, messageId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationUtil.notify(c, CHANNEL, messageId.hashCode()) {
            title = name; content = text; useBigTextStyle = true; autoCancel = true; contentIntent = pending
        }
    }
}
