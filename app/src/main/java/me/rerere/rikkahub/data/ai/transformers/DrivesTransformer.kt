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
 * 服务器顺带补报我上一条回复，最多等 4 秒让 GLM 判完，把我这会儿的状态翻成几句话还回来；
 * 平静时是空串，什么都不贴。贴在她最后一条消息后面（在「开口之前」要求的前面），只进请求不进记录。
 * 同一句重新生成、工具调用的后续几步不会重复报（服务器按消息 id 去重）。
 * 连不上就这一条不带心情，原因记在服务器 ~/Drivesoid/data/turns.jsonl 和这里的日志。
 */
object DrivesTransformer : InputMessageTransformer {
    private const val TAG = "Drives"
    private val http = OkHttpClient.Builder().callTimeout(7, TimeUnit.SECONDS).build()
    private val thinkRe = Regex("<think>[\\s\\S]*?</think>")

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.assistant.feelDrives) return messages
        val real = messages.filter {
            !it.isSynthetic && (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT)
        }
        val lastUser = real.lastOrNull { it.role == MessageRole.USER } ?: return messages
        val text = lastUser.toText().trim()
        if (text.isEmpty()) return messages
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
                    if (r.isSuccessful) JSONObject(r.body.string()).optString("text") else {
                        Log.w(TAG, "drives turn http ${r.code}"); ""
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "drives turn failed", e)
            ""
        }
        if (block.isBlank()) return messages
        val idx = messages.indexOfLast { it.id == lastUser.id }
        if (idx < 0) return messages
        return messages.toMutableList().apply {
            this[idx] = this[idx].let { it.copy(parts = it.parts + UIMessagePart.Text("\n\n" + block)) }
        }
    }
}
