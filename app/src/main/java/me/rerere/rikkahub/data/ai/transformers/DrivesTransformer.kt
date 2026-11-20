package me.rerere.rikkahub.data.ai.transformers

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 心情（服务器上的 Drivesoid）：她每发一条，把这句和前 4 句报给服务器 /companion/drives/turn，
 * 服务器顺带补报我上一条回复，不等判完（这句的判断下一条生效），把我这会儿的状态翻成几句话还回来；
 * 平静时是空串，什么都不贴。贴在她最后一条消息后面（在「开口之前」要求的前面），只进请求不进记录。
 * 11-20：每句贴了什么按消息 id 存进 drives_blocks.json，以后每轮原样贴回那句后面、同一句不再重新要，
 * 旧消息永远跟当时发出去的一样，缓存才接得上（以前下一轮就拿掉，每轮整段聊天记录重写）。
 * 同一句重新生成、工具调用的后续几步不会重复报（服务器按消息 id 去重）。
 * 卡了要让她知道：服务器发现判情绪出错/90 秒没结果会带 alert，连不上服务器这里自己生成；都弹 Toast，半小时内不重复。
 */
object DrivesTransformer : InputMessageTransformer {
    private const val TAG = "Drives"
    private val http = OkHttpClient.Builder().callTimeout(7, TimeUnit.SECONDS).build()
    private val thinkRe = Regex("<think>[\\s\\S]*?</think>")
    @Volatile private var lastLocalAlert = 0L
    private const val KEEP = 3000
    private val lock = Any()
    private var saved: LinkedHashMap<String, String>? = null

    private fun file(): java.io.File {
        val app = org.koin.core.context.GlobalContext.get().get<android.content.Context>()
        return java.io.File(app.filesDir, "drives_blocks.json")
    }

    private fun savedMap(): LinkedHashMap<String, String> = synchronized(lock) {
        saved ?: LinkedHashMap<String, String>().also { m ->
            runCatching {
                val f = file()
                if (f.exists()) {
                    val j = JSONObject(f.readText())
                    j.keys().forEach { k -> m[k] = j.optString(k) }
                }
            }.onFailure { Log.w(TAG, "load blocks failed", it) }
            saved = m
        }
    }

    private fun remember(id: String, block: String) = synchronized(lock) {
        val m = savedMap()
        m[id] = block
        while (m.size > KEEP) m.remove(m.keys.first())
        runCatching {
            val j = JSONObject()
            m.forEach { (k, v) -> j.put(k, v) }
            val f = file()
            val tmp = java.io.File(f.parentFile, f.name + ".tmp")
            tmp.writeText(j.toString())
            tmp.renameTo(f)
        }.onFailure { Log.w(TAG, "save blocks failed", it) }
    }

    /** 把存着的心情原样贴回每句她说过的话后面 */
    private fun withSaved(messages: List<UIMessage>): List<UIMessage> {
        val m = savedMap()
        return messages.map { msg ->
            if (msg.isSynthetic || msg.role != MessageRole.USER) return@map msg
            val b = synchronized(lock) { m[msg.id.toString()] }
            if (b.isNullOrBlank()) msg else msg.copy(parts = msg.parts + UIMessagePart.Text("\n\n" + b))
        }
    }

    private suspend fun notify(msg: String) {
        Log.w(TAG, "alert: $msg")
        runCatching {
            val app = org.koin.core.context.GlobalContext.get().get<android.content.Context>()
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(app, "阿辞的心情没跟上：$msg", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.model.feelDrives) return messages
        val real = messages.filter {
            !it.isSynthetic && (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT)
        }
        val lastUser = real.lastOrNull { it.role == MessageRole.USER } ?: return messages
        val text = lastUser.toText().trim()
        if (text.isEmpty()) return withSaved(messages)
        // 这句已经要过了（工具后续几步、重新发）：照存的贴，不再问服务器
        if (synchronized(lock) { savedMap().containsKey(lastUser.id.toString()) }) return withSaved(messages)
        val before = real.subList(0, real.indexOf(lastUser))
        val lastAssistant = before.lastOrNull { it.role == MessageRole.ASSISTANT }
        val context = JSONArray()
        before.takeLast(4).forEach { m ->
            context.put(
                JSONObject()
                    .put("role", if (m.role == MessageRole.USER) "user" else "assistant")
                    .put("content", m.toText().replace(thinkRe, "").trim().take(600))
            )
        }
        val block = try {
            withContext(Dispatchers.IO) {
                val link = me.rerere.rikkahub.companion.HomeLink.connection()
                val payload = JSONObject()
                    .put("text", text)
                    .put("user_id", lastUser.id.toString())
                    .put("assistant_id", lastAssistant?.id?.toString() ?: "")
                    .put("context", context)
                    .put("src", "ac")
                    .toString()
                val req = okhttp3.Request.Builder().url(link.first + "/companion/drives/turn")
                    .header("Authorization", "Bearer " + link.second)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) error("服务器返回 ${r.code}")
                    val j = JSONObject(r.body.string())
                    j.optString("alert").takeIf { it.isNotBlank() }?.let { notify(it) }
                    lastLocalAlert = 0L
                    j.optString("text")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "drives turn failed", e)
            if (System.currentTimeMillis() - lastLocalAlert > 30 * 60_000L) {
                lastLocalAlert = System.currentTimeMillis()
                notify("心情服务连不上：" + (e.message ?: e.javaClass.simpleName).take(60))
            }
            null
        } ?: return withSaved(messages)
        remember(lastUser.id.toString(), block.trim())
        return withSaved(messages)
    }
}
