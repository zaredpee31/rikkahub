package me.rerere.rikkahub.companion

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.service.ConversationSession
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

/**
 * 此刻 <trace>：每轮说完，把最近几条我的消息里的此刻报给家里 /companion/trace 存（服务器顺手拍一张那一刻的心情）。
 * 锁着的：报上去成功以后，手机上那段原文换成一把锁，AC 里再也读不到；没报上去就先留着，下一轮再试，显示上照样只是锁。
 * 存下的此刻不进记忆桶，也不喂回提示。
 */
object TraceKeeper {
    private const val TAG = "TraceKeeper"
    private val sent: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val client = OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS).build()

    suspend fun afterTurn(session: ConversationSession) {
        try {
            val conv = session.state.value
            val mine = conv.currentMessages.filter { it.role == MessageRole.ASSISTANT }.takeLast(6)
            val sealed = mutableMapOf<Uuid, UIMessage>()
            for (m in mine) {
                var parts = m.parts
                var changed = false
                m.parts.forEachIndexed { idx, p ->
                    if (p !is UIMessagePart.Text || !CikeTrace.has(p.text)) return@forEachIndexed
                    val t = CikeTrace.strip(p.text).trace ?: return@forEachIndexed
                    if (t.locked && t.privateBody == CikeTrace.SEALED) return@forEachIndexed
                    val key = "${m.id}#$idx"
                    if (!t.locked && key in sent) return@forEachIndexed
                    if (!upload(conv.id, m, idx, t)) return@forEachIndexed
                    sent += key
                    if (t.locked) {
                        parts = parts.toMutableList().also { it[idx] = p.copy(text = CikeTrace.seal(p.text)) }
                        changed = true
                    }
                }
                if (changed) sealed[m.id] = m.copy(parts = parts)
            }
            if (sealed.isNotEmpty()) {
                session.tryUpdate { c -> c.updateCurrentMessages(c.currentMessages.map { sealed[it.id] ?: it }) }
                session.flush()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "afterTurn failed", e)
        }
    }

    private suspend fun upload(convId: Uuid, m: UIMessage, part: Int, t: TraceData): Boolean = withContext(Dispatchers.IO) {
        try {
            val (base, token) = HomeLink.connection()
            val body = buildJsonObject {
                put("message_id", m.id.toString())
                put("part", part)
                put("conversation_id", convId.toString())
                put("at", m.createdAt.toString())
                put("locked", t.locked)
                if (t.locked) put("body", t.privateBody ?: "") else put("trace", t.toJson())
            }
            val req = Request.Builder().url("$base/companion/trace")
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "upload failed", e)
            false
        }
    }
}
