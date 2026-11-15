package me.rerere.rikkahub.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.companionMessagesIntent
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.repository.ConversationRepository
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
    /** 旧渠道（IMPORTANCE_DEFAULT、以及第一版 HIGH）已建在用户手机上，应用改不了渠道设置，所以换新 id 并删旧的。 */
    private val LEGACY_CHANNELS = listOf("companion_incoming", "companion_incoming_high")
    const val CHANNEL = "companion_incoming_v3"
    private const val NOTIFY_ID = 3411
    /** 导航栈顶是不是消息页，由 RouteActivity 按栈顶推过来。 */
    @Volatile private var inboxOnTop = false
    @Volatile private var coldSynced = false
    @Volatile private var observing = false

    /** 栈顶是消息页，并且整个 app 在前台，才算「正在看消息页」。盖着普通会话、或 app 在后台都不算。 */
    private fun inboxVisible() =
        inboxOnTop && ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    /** 导航栈顶变化时调用。变成可见时清掉已有的消息通知。 */
    fun setInboxOnTop(c: Context, top: Boolean) {
        inboxOnTop = top
        if (inboxVisible()) NotificationUtil.cancel(c.applicationContext, NOTIFY_ID)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null
    @Volatile private var app: Context? = null
    fun initialize(c: Context) {
        app = c.applicationContext
        if (!observing) {
            observing = true
            // app 回到前台且栈顶是消息页：清掉通知栏里已读的消息通知
            ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START && inboxOnTop) NotificationUtil.cancel(c.applicationContext, NOTIFY_ID)
            })
        }
    }

    /**
     * 进程被拉起 / 开机 / 前台服务启动时调用，幂等。开关开着就：排好 WorkManager 和闹钟兜底、起前台服务、
     * 在后台取一次消息（进程里的第一次会顺带上传开关和资料）。设置页不负责激活。
     */
    fun ensureRunning(c: Context) {
        val a = c.applicationContext
        if (!enabled(a) && !needsDisable(a)) return
        CompanionReceiverService.scheduleFallback(a)
        CompanionAlarm.schedule(a)
        ensureChannel(a)
        if (enabled(a)) {
            runCatching { ContextCompat.startForegroundService(a, Intent(a, CompanionReceiverService::class.java)) }
        }
        scope.launch { runCatching { poll(a) } }
    }

    /** 闹钟广播用：只取消息，最多等 [limitMs]，结束后调用 [done]（给 goAsync 收尾）。 */
    fun pollWithin(c: Context, limitMs: Long, done: () -> Unit) {
        val a = c.applicationContext
        scope.launch {
            try { withTimeoutOrNull(limitMs) { runCatching { poll(a) } } } finally { done() }
        }
    }

    /** 手机上次成功取到消息的时间（毫秒），从没成功过是 0。 */
    fun lastPullAt(c: Context) = prefs(c).getLong("pulled_at", 0L)

    /** 上次问到的服务器状态，打开设置页时先显示它，再在后台刷新。 */
    fun cachedServer(c: Context): Pair<JsonObject, Long>? {
        val raw = prefs(c).getString("server_cache", null) ?: return null
        val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        return obj to prefs(c).getLong("server_cache_at", 0L)
    }

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        LEGACY_CHANNELS.forEach { nm.deleteNotificationChannel(it) }
        val channel = NotificationChannel(CHANNEL, "阿辞的消息", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "阿辞主动发来的消息，会从屏幕顶部弹出"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    /** 渠道当前的重要性（用户在系统里改过会体现在这里）；渠道不存在是 null。 */
    fun channelImportance(c: Context): Int? =
        c.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance
    /** Debounce only the bound assistant. Never launch a scheduler for each conversation. */
    @Synchronized
    fun contextChanged(memoryChanged: Boolean = false) {
        val c = app ?: return
        if (memoryChanged) recallCache = null
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
    /** 服务器 48 小时没收到资料就不再主动发言；轮询只在距上次上传超过这个间隔时顺带保鲜一次。 */
    private const val KEEPALIVE_MS = 12 * 3600_000L
    /** 上一次召回的结果：消息页最近 3 条没变、记忆没变、向量模型没变时直接用，不再调向量接口。 */
    private class RecallCache(val key: List<String>, val text: String)
    @Volatile private var recallCache: RecallCache? = null
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
        CompanionAlarm.schedule(c)
    }
    suspend fun ensureInitialized(c: Context, conversationId: Uuid) {
        val targetId = target(c)
        if (targetId != conversationId.toString()) {
            require(!enabled(c) && !needsDisable(c)) { "消息页已绑定其他会话，请先关闭主动消息" }
            bind(c, conversationId)
        }
        if (!enabled(c)) setEnabled(c, true)
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
    private val slowClient by lazy { client.newBuilder().callTimeout(170, TimeUnit.SECONDS).readTimeout(170, TimeUnit.SECONDS).build() }

    /** 让服务器马上生成一条。先把最新资料传上去，失败时抛出看得懂的原因。 */
    suspend fun tryNow(c: Context): Unit = withContext(Dispatchers.IO) {
        uploadedHash = null
        sync(c)
        val link = mutex.withLock { connection() }
        val req = Request.Builder().url(link.first + "/companion/try")
            .header("Authorization", "Bearer " + link.second)
            .post("{}".toRequestBody("application/json".toMediaType())).build()
        val res = slowClient.newCall(req).execute().use { response ->
            require(response.isSuccessful) { "服务器返回 ${response.code}" }
            Json.parseToJsonElement(response.body.string()).jsonObject
        }
        val st = res["status"]?.jsonPrimitive?.contentOrNull
        if (st != "queued") error(res["reason"]?.jsonPrimitive?.contentOrNull ?: "服务器说：$st")
        sync(c)
    }

    suspend fun serverStatus(c: Context): JsonObject = withContext(Dispatchers.IO) {
        mutex.withLock { request(connection(), "status") }.also {
            prefs(c).edit().putString("server_cache", it.toString()).putLong("server_cache_at", System.currentTimeMillis()).apply()
        }
    }

    suspend fun weather(c: Context): JsonObject = withContext(Dispatchers.IO) {
        request(mutex.withLock { connection() }, "weather")
    }

    /** 绑定到消息页自己的会话并打开接收。 */
    fun activate(c: Context, conversationId: Uuid, assistantId: Uuid) {
        prefs(c).edit().putString("target", conversationId.toString())
            .putString("assistant", assistantId.toString()).putBoolean("disable_pending", false).commit()
        if (!enabled(c)) setEnabled(c, true)
    }

    /** 内容变了的触发（发消息、编辑、重新生成、设置、contextChanged）用：组装资料上传，再取消息。 */
    suspend fun sync(c: Context) = execute(c, upload = true)

    /** 前台服务每 60 秒、WorkManager 每 15 分钟用：只 GET 消息和 ack，不组装人设、门牌、召回。 */
    suspend fun poll(c: Context) = execute(c, upload = null)

    /** [upload] 为 null：本进程还没成功上传过就上传（冷启动、开机、进程被拉起），之后只取消息。 */
    private suspend fun execute(c: Context, upload: Boolean?): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val link = connection()
                if (!enabled(c)) {
                    request(link, "sync", buildJsonObject { put("enabled", false) })
                    uploadedHash = null
                    prefs(c).edit().putBoolean("disable_pending", false).apply()
                    prefs(c).edit().putString("status", "主动消息已关闭，服务器已确认").apply()
                    WorkManager.getInstance(c).cancelUniqueWork("companion_sync")
                    CompanionAlarm.cancel(c)
                    return@withLock
                }
                val koin = GlobalContext.get()
                val chat = koin.get<ChatService>()
                val stored = chat.getCompanionConversation() ?: error("还没建立消息会话，先选个模型")
                val conv = chat.getConversation(stored.id) ?: stored
                val id = conv.id
                if (target(c) != id.toString() || prefs(c).getString("assistant", "") != conv.assistantId.toString()) {
                    prefs(c).edit().putString("target", id.toString()).putString("assistant", conv.assistantId.toString()).commit()
                }
                val settings = koin.get<SettingsStore>().awaitLoaded()
                require(settings.assistants.any { it.id == conv.assistantId }) { "绑定的助手已删除" }
                val assistant = settings.getAssistantOf(conv)
                val model = settings.getChatModelOf(conv) ?: error("这个会话还没有模型")
                val lastUploadMs = prefs(c).getLong("uploaded_at", 0L)
                val doUpload = upload ?: !coldSynced
                if (doUpload || System.currentTimeMillis() - lastUploadMs >= KEEPALIVE_MS) {
                    uploadLocked(c, link, koin, chat, conv, settings, assistant, model)
                }
                if (!enabled(c) || target(c) != id.toString()) return@withLock
                pullLocked(c, link, id, conv.assistantId, assistant.name.ifBlank { "阿辞" }, assistant.avatar, model.id, chat, doUpload)
                if (doUpload) coldSynced = true
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                val why = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName.orEmpty()
                prefs(c).edit().putString("status", if (needsDisable(c)) "本机已停止接收，服务器尚未确认关闭" else "同步失败：$why").apply()
                throw e
            }
        }
    }

    private suspend fun uploadLocked(
        c: Context, link: Pair<String, String>, koin: org.koin.core.Koin, chat: ChatService, conv: Conversation,
        settings: me.rerere.rikkahub.data.datastore.Settings, assistant: me.rerere.rikkahub.data.model.Assistant,
        model: me.rerere.ai.provider.Model,
    ) {
        val id = conv.id
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
        // 主动消息走服务器，拿不到手机上的向量：门牌整份带上，再按消息页最近 3 条在手机上召回一次
        val palaceMemory = if (assistant.enableMemory) {
            runCatching {
                val palace = koin.get<me.rerere.rikkahub.data.palace.PalaceRepository>()
                palace.ensureMigrated(assistant.memoryId)
                val (user, self) = palace.core(assistant.memoryId)
                val plates = me.rerere.rikkahub.data.palace.PalacePrompt.plates(user, self, palace.plateEntries(assistant.memoryId))
                val recentText = conv.currentMessages
                    .filter { (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT) && !it.isContextCheckpoint }
                    .takeLast(3).map { it.toText() }
                val index = koin.get<me.rerere.rikkahub.data.palace.PalaceIndex>()
                // 召回要调向量接口：最近 3 条、记忆、向量模型、日期都没变就沿用上次的结果
                val cacheKey = listOf(assistant.memoryId.toString(), index.currentKey().orEmpty(), java.time.LocalDate.now().toString()) + recentText
                val recalled = recallCache?.takeIf { it.key == cacheKey }?.text
                    ?: me.rerere.rikkahub.data.palace.PalaceIndex.format(
                        index.recall(assistant.memoryId, recentText, mark = false)
                    ).also { recallCache = RecallCache(cacheKey, it) }
                listOf(plates, recalled).filter { it.isNotBlank() }.joinToString("\n\n")
            }.getOrDefault("")
        } else ""
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
            job?.isActive == true && cid == id
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
            put("memory", palaceMemory.take(480000))
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
        if (!enabled(c) || target(c) != id.toString()) return
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest((link.toString() + payload.toString()).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val nowMs = System.currentTimeMillis()
        if (uploadedHash != digest || nowMs - uploadedAt >= KEEPALIVE_MS) {
            request(link, "sync", payload)
            uploadedHash = digest
            uploadedAt = nowMs
            prefs(c).edit().putLong("uploaded_at", nowMs).apply()
        }
    }

    private suspend fun pullLocked(
        c: Context, link: Pair<String, String>, id: Uuid, assistantId: Uuid, name: String, avatar: Avatar, modelId: Uuid,
        chat: ChatService, uploaded: Boolean,
    ) {
        val received = request(link, "messages")["messages"]?.jsonArray.orEmpty()
        val ack = mutableListOf<String>()
        for (element in received) {
            val msg = element.jsonObject
            if (msg["conversation"]?.jsonPrimitive?.content != id.toString() ||
                msg["assistant"]?.jsonPrimitive?.content != assistantId.toString()) continue
            // Re-check opt-in and target after network I/O, before mutating live sessions.
            if (!enabled(c) || target(c) != id.toString()) break
            val messageId = Uuid.parse(msg.getValue("id").jsonPrimitive.content)
            val text = msg.getValue("text").jsonPrimitive.content
            val inserted = chat.receiveCompanionMessage(UIMessage.assistant(text).copy(
                id = messageId,
                createdAt = kotlin.time.Instant.fromEpochMilliseconds((msg.getValue("created").jsonPrimitive.double * 1000).toLong())
                    .toLocalDateTime(TimeZone.currentSystemDefault()),
                modelId = modelId,
            ))
            if (inserted) notify(c, name, avatar, text)
            ack += messageId.toString()
        }
        if (ack.isNotEmpty()) request(link, "ack", buildJsonObject { put("ids", JsonArray(ack.map { JsonPrimitive(it) })) })
        val now = java.time.LocalTime.now().withNano(0)
        prefs(c).edit().putString("status", (if (uploaded) "已同步人设、记忆和近期聊天 · " else "已取消息 · ") + now)
            .putLong("pulled_at", System.currentTimeMillis()).apply()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun notify(c: Context, name: String, avatar: Avatar, text: String) {
        if (inboxVisible()) return
        val nm = c.getSystemService(NotificationManager::class.java)
        ensureChannel(c)
        if (!NotificationUtil.hasNotificationPermission(c)) return
        val short = if (text.length > 200) text.take(200) + "…" else text
        val open = PendingIntent.getActivity(c, NOTIFY_ID, companionMessagesIntent(c), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val person = androidx.core.app.Person.Builder().setName(name).build()
        val style = androidx.core.app.NotificationCompat.MessagingStyle(person)
        // 同一个通知 id 叠加：把还没被清掉的旧消息带上，最多保留最近 4 条
        runCatching {
            nm.activeNotifications.firstOrNull { it.id == NOTIFY_ID }?.notification
                ?.let { androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
                ?.messages?.takeLast(4)?.forEach { style.addMessage(it) }
        }
        style.addMessage(short, System.currentTimeMillis(), person)
        val builder = androidx.core.app.NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rikkahub)
            .setContentTitle(name).setContentText(short).setStyle(style)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .setOnlyAlertOnce(false).setSilent(false).setShowWhen(true).setWhen(System.currentTimeMillis())
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true).setContentIntent(open)
        avatarBitmap(c, avatar)?.let { builder.setLargeIcon(it) }
        androidx.core.app.NotificationManagerCompat.from(c).notify(NOTIFY_ID, builder.build())
    }

    /** 只处理本地 file/content 头像；拿不到就不放大图标。 */
    private fun avatarBitmap(c: Context, avatar: Avatar): android.graphics.Bitmap? {
        val url = (avatar as? Avatar.Image)?.url ?: return null
        return runCatching {
            val uri = android.net.Uri.parse(url)
            if (uri.scheme != "file" && uri.scheme != "content") return null
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            c.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }
}
