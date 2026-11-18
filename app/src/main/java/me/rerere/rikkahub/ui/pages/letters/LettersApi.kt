package me.rerere.rikkahub.ui.pages.letters

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.rikkahub.companion.HomeLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal class LetterApiException(val code: Int, message: String) : Exception(message)

/** 一封可读信的正文。 */
internal data class LetterBody(val title: String, val content: String, val sender: String, val created: String)

/**
 * 信箱接口：服务器 ac_proactive 的 /companion/letters*，地址和 key 用 HomeLink 现成的那份。
 * 锁定的信服务器不返回正文，这里也不会去请求它们的正文。
 */
internal object LettersApi {
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()

    private suspend fun call(method: String, path: String, body: JsonObject? = null): JsonObject =
        withContext(Dispatchers.IO) {
            val (base, token) = HomeLink.connection()
            val req = Request.Builder().url("$base/companion/$path").header("Authorization", "Bearer $token")
            if (method == "POST") {
                req.post((body ?: JsonObject(emptyMap())).toString().toRequestBody("application/json".toMediaType()))
            }
            client.newCall(req.build()).execute().use { r ->
                val text = r.body.string()
                val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
                if (!r.isSuccessful) {
                    val msg = obj?.get("error")?.jsonPrimitive?.contentOrNull ?: "服务器返回 ${r.code}"
                    throw LetterApiException(r.code, msg)
                }
                obj ?: throw LetterApiException(r.code, "服务器回的不是信")
            }
        }

    suspend fun list(): List<LetterRow> =
        call("GET", "letters")["letters"]?.jsonArray.orEmpty().map { el ->
            val o = el.jsonObject
            LetterRow(
                id = o["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                fromAi = o["author"]?.jsonPrimitive?.contentOrNull == "ai",
                title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                created = o["created"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                lockType = o["lock_type"]?.jsonPrimitive?.contentOrNull ?: "none",
                unlockDate = o["unlock_date"]?.jsonPrimitive?.contentOrNull,
                canRead = o["can_read"]?.jsonPrimitive?.booleanOrNull == true,
                readByAi = o["read_by_ai"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }.filter { it.id.isNotBlank() }

    /** 只在 canRead 时调用。 */
    suspend fun read(id: String): LetterBody {
        val o = call("GET", "letters/$id")
        return LetterBody(
            title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            content = o["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            sender = o["sender"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            created = o["created"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    /** 写信。unlockDate 为 null 就是直接寄出，否则是 yyyy-MM-dd 的定时打开。成功返回信 id。 */
    suspend fun send(title: String, content: String, unlockDate: String?): String {
        val o = call("POST", "letters", buildJsonObject {
            put("title", title)
            put("content", content)
            put("lock_type", if (unlockDate == null) "none" else "timed")
            if (unlockDate != null) put("unlock_date", unlockDate)
        })
        return o["id"]?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    }
}
