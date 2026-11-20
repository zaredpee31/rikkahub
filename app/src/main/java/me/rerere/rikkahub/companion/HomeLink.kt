package me.rerere.rikkahub.companion

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.CoreProfileRepository
import me.rerere.rikkahub.data.repository.memoryId
import me.rerere.rikkahub.service.ChatService
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.koin.core.context.GlobalContext
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

/**
 * 家里的主动消息：服务器按时叫醒阿辞，他说了话就放在服务器上，这里每分钟取一次，写进置顶会话并弹通知。
 * 同时每 5 分钟把置顶会话最近 30 条（每条截到 80 字）、门牌和模型传上去，内容没变就 30 分钟保活一次。
 * 地址和 key 取自 MCP 设置里那个 https://43-166-23-196.sslip.io/mcp，不另存密钥。
 */
object HomeLink {
    private const val HOST = "43-166-23-196.sslip.io"
    private const val BUILD_EVERY_MS = 5 * 60_000L
    private const val KEEPALIVE_MS = 30 * 60_000L
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val mutex = Mutex()
    private var builtAt = 0L
    private var uploadedAt = 0L
    private var uploadedHash: String? = null

    @Volatile var status: String = "还没连上"
        private set

    @Volatile private var qqPrompt: String = ""
    @Volatile private var qqFetchedAt: String = ""
    @Volatile private var homePrompt: String = ""
    @Volatile private var lastUse: String = ""

    /** 每次拼请求时记一笔：哪个会话、记忆开没开、带了多少字，下次拿 QQ 记录时顺带报给服务器 */
    fun noteUse(conv: String, memory: Boolean, total: Int) {
        lastUse = "${java.time.LocalTime.now().withNano(0)} conv=$conv memory=$memory total=$total qq=${qqPrompt.length} fetched=$qqFetchedAt"
    }

    @Volatile private var typingAt = 0L

    /** 她打了字又删光了：报给服务器（转给 Drivesoid 记 typing_deleted），两分钟内只报一次，失败不管 */
    suspend fun typingDeleted() {
        val now = System.currentTimeMillis()
        if (now - typingAt < 120_000L) return
        typingAt = now
        try {
            withContext(Dispatchers.IO) {
                val link = connection()
                val req = okhttp3.Request.Builder().url(link.first + "/companion/drives/typing")
                    .header("Authorization", "Bearer " + link.second)
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("HomeLink", "typing report failed", e)
        }
    }

    /** 服务器给的 QQ 最近聊天，空串表示没有。 */
    fun qqPrompt(): String = qqPrompt

    /** 服务器给的 AC 日常最近几句，只给看书页拼进人设，不进聊天 */
    fun homePrompt(): String = homePrompt

    fun start(c: Context) {
        HomeNotify.ensureChannels(c)
        HomeService.start(c)
        HomeAlarm.schedule(c)
    }

    /** 置顶会话有新回复后调用：下一次 tick 立刻重新打包上传。 */
    fun contextChanged() { builtAt = 0L }

    suspend fun tick(c: Context) = withContext(Dispatchers.IO) { mutex.withLock { tickLocked(c.applicationContext) } }

    private suspend fun tickLocked(c: Context) {
        try {
            val link = connection()
            var upErr: String? = null
            if (System.currentTimeMillis() - builtAt >= BUILD_EVERY_MS) {
                try { upload(c, link) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    upErr = e.message ?: e::class.simpleName
                }
            }
            pull(c, link)
            try {
                val used = lastUse
                val path = if (used.isBlank()) "context" else "context?used=" + java.net.URLEncoder.encode(used, "UTF-8")
                val ctx = request(link, path)
                qqPrompt = ctx["prompt"]?.jsonPrimitive?.content.orEmpty()
                homePrompt = ctx["home"]?.jsonPrimitive?.content.orEmpty()
                qqFetchedAt = java.time.LocalTime.now().withNano(0).toString()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("HomeLink", "fetch qq context failed", e)
            }
            status = if (upErr != null) "上传失败：$upErr" else "已连上 · " + java.time.LocalTime.now().withNano(0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = "连不上：" + (e.message ?: e::class.simpleName)
        }
    }

    /** 信箱页也用这个地址和 key，所以不再是 private。 */
    internal suspend fun connection(): Pair<String, String> {
        val settings = GlobalContext.get().get<SettingsStore>().awaitLoaded()
        for (server in settings.mcpServers) {
            val address = when (server) {
                is McpServerConfig.StreamableHTTPServer -> server.url
                is McpServerConfig.SseTransportServer -> server.url
            }
            val url = runCatching { address.toHttpUrl() }.getOrNull() ?: continue
            if (url.host != HOST || url.encodedPath.trimEnd('/') != "/mcp") continue
            val header = server.commonOptions.headers.lastOrNull { it.first.equals("Authorization", true) }?.second
            val token = url.queryParameter("key")?.takeIf { it.isNotBlank() }
                ?: header?.takeIf { it.startsWith("Bearer ", true) }?.substringAfter(' ')?.trim()?.takeIf { it.isNotBlank() }
                ?: continue
            return "https://$HOST" to token
        }
        error("MCP 设置里找不到 https://$HOST/mcp 的 key")
    }

    private fun request(link: Pair<String, String>, path: String, body: JsonObject? = null): JsonObject {
        val req = Request.Builder().url(link.first + "/companion/" + path)
            .header("Authorization", "Bearer " + link.second)
        if (body != null) req.post(body.toString().toRequestBody("application/json".toMediaType()))
        client.newCall(req.build()).execute().use { r ->
            require(r.isSuccessful) { "服务器返回 ${r.code}" }
            return Json.parseToJsonElement(r.body.string()).jsonObject
        }
    }

    private suspend fun upload(c: Context, link: Pair<String, String>) {
        builtAt = System.currentTimeMillis()
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsStore>().awaitLoaded()
        val chat = koin.get<ChatService>()
        val pinned = koin.get<ConversationRepository>().getPinnedConversations().first().firstOrNull()
        val payload = if (pinned == null) buildJsonObject { } else {
            val conv = chat.getConversation(pinned.id) ?: pinned
            val assistant = settings.getAssistantOf(conv)
            val model = settings.getChatModelOf(conv)
            val provider = model?.findProvider(settings.providers)
            val plates = runCatching { koin.get<CoreProfileRepository>().promptFor(assistant.memoryId) }.getOrDefault("")
            val messages = conv.currentMessages
                .filter { (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT) && !it.isContextCheckpoint }
            val recent = messages.takeLast(30).mapNotNull { m ->
                val text = m.toText().trim().replace('\n', ' ')
                if (text.isBlank()) null
                else Triple(if (m.role == MessageRole.USER) "user" else "assistant", m.createdAt.toString(),
                    if (text.length > 80) text.take(80) + "…" else text)
            }
            val lastUser = messages.lastOrNull { it.role == MessageRole.USER }?.createdAt?.toString()
            val busy = chat.getConversationJobs().first()[conv.id]?.isActive == true
            val headers = provider?.customHeaders.orEmpty() + assistant.customHeaders + model?.customHeaders.orEmpty()
            val providerJson = providerJson(provider, model?.modelId, headers)
            buildJsonObject {
                put("conversation_id", conv.id.toString())
                put("plates", plates)
                put("busy", busy)
                if (lastUser != null) put("last_user_at", lastUser)
                put("recent", buildJsonArray {
                    recent.forEach { (r, a, t) -> add(buildJsonObject { put("role", r); put("at", a); put("text", t) }) }
                })
                if (providerJson != null) put("provider", providerJson)
                // 崽崽摘要：服务器看它有没有事要「敲敲」阿辞（pet_knock.py）
                runCatching { Json.parseToJsonElement(me.rerere.rikkahub.pet.PetGame.summary(c).toString()) }
                    .getOrNull()?.let { put("pet", it) }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val now = System.currentTimeMillis()
        if (digest == uploadedHash && now - uploadedAt < KEEPALIVE_MS) return
        request(link, "sync", payload)
        uploadedHash = digest
        uploadedAt = now
    }

    private fun providerJson(p: ProviderSetting?, modelId: String?, headers: List<me.rerere.ai.provider.CustomHeader>): JsonObject? {
        if (p == null || modelId == null) return null
        val (type, url, key) = when (p) {
            is ProviderSetting.OpenAI -> Triple("openai", p.baseUrl.trimEnd('/') + "/" + p.chatCompletionsPath.trimStart('/'), p.apiKey)
            is ProviderSetting.Claude -> Triple("claude", p.baseUrl.trimEnd('/') + "/messages", p.apiKey)
            else -> return null
        }
        if (key.isBlank()) return null
        return buildJsonObject {
            put("type", type); put("url", url); put("key", key); put("model", modelId)
            put("headers", buildJsonObject { headers.forEach { h -> put(h.name, h.value.replace("{{apiKey}}", key)) } })
        }
    }

    private suspend fun pull(c: Context, link: Pair<String, String>) {
        val list = request(link, "messages")["messages"]?.jsonArray.orEmpty()
        if (list.isEmpty()) return
        val koin = GlobalContext.get()
        val chat = koin.get<ChatService>()
        val settings = koin.get<SettingsStore>().awaitLoaded()
        val ack = mutableListOf<String>()
        for (el in list) {
            val m = el.jsonObject
            val id = m["id"]?.jsonPrimitive?.content ?: continue
            val text = m["text"]?.jsonPrimitive?.content.orEmpty()
            val convId = m["conversation"]?.jsonPrimitive?.content?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            val msgId = runCatching { Uuid.parse(id) }.getOrNull()
            if (convId == null || msgId == null || text.isBlank()) { ack += id; continue }
            // 这个会话正在生成回复，插进去会打乱它，下一分钟再放
            if (chat.getConversationJobs().first()[convId]?.isActive == true) continue
            val conv = chat.getConversation(convId)
            if (conv == null) { ack += id; continue }
            val model = settings.getChatModelOf(conv)
            val inserted = chat.receiveHomeMessage(convId, UIMessage.assistant(text).copy(id = msgId, modelId = model?.id))
            if (inserted) HomeNotify.message(c, convId, settings.getAssistantOf(conv).name.ifBlank { "阿辞" }, text)
            ack += id
        }
        if (ack.isNotEmpty()) request(link, "ack", buildJsonObject { put("ids", JsonArray(ack.map { JsonPrimitive(it) })) })
        contextChanged()
    }
}
