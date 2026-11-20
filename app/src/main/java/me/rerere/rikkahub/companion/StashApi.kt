package me.rerere.rikkahub.companion

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.webpage.WebPages
import me.rerere.rikkahub.webpage.WebReader
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

    /** 读链接的结果：finalUrl 是跳转后的地址（读不到就是原链接）；title / content 为 null 表示没读出来（没打开或要登录）。 */
    private class LinkRead(val finalUrl: String, val title: String?, val content: String?)

    /** 用 WebReader 读一个链接。读失败不抛错，title / content 给 null，由调用方退回自己的做法。 */
    private suspend fun readLink(context: Context, url: String): LinkRead {
        val page = runCatching { WebReader.read(context, url) }.getOrNull()
        val pt = page?.title
        val usable = page != null && pt != null && !page.needLogin
        return LinkRead(
            finalUrl = page?.finalUrl ?: url,
            title = if (usable) pt?.take(200) else null,
            content = if (usable && page != null && page.text.isNotBlank()) page.text.take(20000) else null,
        )
    }

    /**
     * 长按消息「囤一条」：进待归仓，同一条消息只囤一次（服务器按 meta.node 去重）。返回给她看的一句话。
     * 消息里有链接就先读链接（和分享一样，存跳转后的地址、网页标题和正文），读不出来就退回拿消息原文当内容；
     * 读之前先调 onReading，让界面提示一声要等一会儿。
     */
    suspend fun stashMessage(
        context: Context,
        conversationId: String,
        nodeId: String,
        fromAi: Boolean,
        text: String,
        onReading: () -> Unit = {},
    ): String {
        val clean = text.replace(Regex("""<think>[\s\S]*?</think>"""), "").trim()
        if (clean.isEmpty()) return "这条没有文字，囤不了"
        val first = clean.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        var title = (if (fromAi) "阿辞：" else "我：") + first.take(30) + if (first.length > 30) "…" else ""
        var content = clean.take(20000)
        val link = WebPages.findUrls(clean).firstOrNull()
        var finalUrl: String? = null
        if (link != null) {
            onReading()
            val read = readLink(context, link)
            finalUrl = read.finalUrl
            read.title?.let { title = it }
            read.content?.let { content = it }
        }
        val body = buildJsonObject {
            finalUrl?.let { put("url", it) }
            put("title", title)
            put("content", content)
            put("by", "her")
            put("src", "msg")
            putJsonObject("meta") {
                put("node", "$conversationId:$nodeId")
                put("role", if (fromAi) "assistant" else "user")
                if (link != null && finalUrl != link) put("shared_url", link)
            }
        }
        return runCatching {
            val r = call("POST", "stash/items", body)
            if ((r["dup"] as? JsonPrimitive)?.booleanOrNull == true) "这条早囤过了" else "囤进待归仓了"
        }.getOrElse { "没囤上：${it.message ?: it.javaClass.simpleName}" }
    }

    /**
     * 分享进来的东西囤进待归仓。有链接就先用 WebReader 读标题和正文（小红书靠 WebLogin 的登录状态），
     * 按跳转后的地址去重；读不出来就拿分享的文字当标题。返回给她看的一句话。
     */
    suspend fun stashShare(context: Context, shared: String): String {
        val text = shared.trim()
        if (text.isEmpty()) return "这次分享没有文字，囤不了"
        val url = WebPages.findUrls(text).firstOrNull()
        val rest = (if (url != null) text.replace(url, " ") else text)
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
        var finalUrl = url
        var title = rest.firstOrNull()?.take(60)
        var content: String? = if (url == null) text else rest.joinToString("\n").ifBlank { null }
        if (url != null) {
            val read = readLink(context, url)
            finalUrl = read.finalUrl
            read.title?.let { title = it }
            read.content?.let { content = it }
        }
        val body = buildJsonObject {
            put("url", finalUrl.orEmpty())
            put("title", title.orEmpty())
            put("content", content.orEmpty())
            put("by", "her")
            put("src", "share")
            if (url != null && finalUrl != url) putJsonObject("meta") { put("shared_url", url) }
        }
        return runCatching {
            val r = call("POST", "stash/items", body)
            val t = (r["item"] as? JsonObject)?.get("title")?.jsonPrimitive?.contentOrNull ?: title.orEmpty()
            if ((r["dup"] as? JsonPrimitive)?.booleanOrNull == true) "早囤过了：「$t」" else "囤进待归仓了：「$t」"
        }.getOrElse { "没囤上：${it.message ?: it.javaClass.simpleName}" }
    }
}
