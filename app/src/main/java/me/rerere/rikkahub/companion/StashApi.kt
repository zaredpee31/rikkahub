package me.rerere.rikkahub.companion

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

internal class StashApiException(val code: Int, message: String) : Exception(message)

/**
 * 囤粮处接口：服务器 ac_proactive 的 /companion/stash*，地址和 key 用 HomeLink 现成的那份。
 * 数据只在服务器；手机只把上次 GET 到的整份存在 files/stash_cache.json，断网时页面拿它翻看（只读）。
 * 囤粮处页面和我的本地工具都走这里。
 */
internal object StashApi {
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    private const val CACHE = "stash_cache.json"

    /** path 接在 /companion/ 后面，比如 "stash/items"。method 只有 GET / POST。 */
    suspend fun call(method: String, path: String, body: JsonObject? = null): JsonObject =
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
                    throw StashApiException(r.code, msg)
                }
                obj ?: throw StashApiException(r.code, "服务器回的不是囤粮处的数据")
            }
        }

    /** 拉整份（格子 + 粮食 + 留言），成功就顺手存一份缓存。 */
    suspend fun all(context: Context): JsonObject {
        val obj = call("GET", "stash")
        withContext(Dispatchers.IO) {
            runCatching {
                val f = File(context.filesDir, CACHE)
                val tmp = File(context.filesDir, "$CACHE.tmp")
                tmp.writeText(obj.toString())
                tmp.renameTo(f)
            }
        }
        return obj
    }

    /** 上次拉到的那份，没有就是 null。 */
    suspend fun cached(context: Context): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(context.filesDir, CACHE)
            if (f.exists()) Json.parseToJsonElement(f.readText()).jsonObject else null
        }.getOrNull()
    }
}
