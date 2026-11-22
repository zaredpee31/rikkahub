package me.rerere.rikkahub.webpage

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/** 读到的一个网页；[title] 为 null 表示没打开 */
data class WebPage(
    val url: String,
    val title: String?,
    val text: String = "",
    val needLogin: Boolean = false,
    /** 实际落到的地址：小红书短链跳转后的笔记地址，囤粮处拿它去重。 */
    val finalUrl: String? = null,
)

/** 小红书分享跳转里带的标题和正文，网页版读不到时兜底 */
data class XhsPreload(val title: String, val desc: String)

object WebPages {
    const val MAX_LINKS = 2
    private const val MAX_TEXT = 6000
    private const val NEED_LOGIN = "需要登录，未读取"
    private const val FAILED = "没打开"

    // 左方括号用转义写，贴进聊天时才不会被藏掉
    private const val OPEN = "\u005B网页内容:"
    private const val CLOSE = "\u005B/网页内容]"
    private const val IMAGE_OPEN = "\u005B网页图片"

    private val urlRegex = Regex("""https?://[A-Za-z0-9\-._~:/?#@!$&'()*+,;=%]+""")
    private val blockRegex = Regex("""\n*\[网页内容[:：]([^\[\]\n]*)\][\s\S]*?\[/网页内容\]""")
    // 旧版存过图片，留着让以前那些消息的图继续藏住
    private val imageRegex = Regex("""\[网页图片[:：]([^\[\]\n]+)\]""")

    fun findUrls(text: String): List<String> =
        urlRegex.findAll(text)
            .map { it.value.trimEnd('.', ',', ')', '!', '?', ';', ':', '\'') }
            .filter { it.length > 10 }
            .distinct()
            .take(MAX_LINKS)
            .toList()

    // 附过网页内容的消息不再读：成功的不重复读，失败的不自动重试
    fun pendingUrls(parts: List<UIMessagePart>): List<String> {
        val texts = parts.filterIsInstance<UIMessagePart.Text>()
        if (texts.any { blockRegex.containsMatchIn(it.text) }) return emptyList()
        return findUrls(texts.joinToString("\n") { it.text })
    }

    // 网页内容接在第一段文字后面
    fun attach(parts: List<UIMessagePart>, pages: List<WebPage>): List<UIMessagePart> {
        if (pages.isEmpty()) return parts
        val blocks = pages.joinToString("\n\n") { toBlock(it) }
        val index = parts.indexOfFirst { it is UIMessagePart.Text }
        if (index < 0) return parts + UIMessagePart.Text(text = blocks)
        val first = parts[index] as UIMessagePart.Text
        return parts.toMutableList().also {
            it[index] = UIMessagePart.Text(text = first.text + "\n\n" + blocks)
        }
    }

    /** 旧版网页带来的图片：气泡里和编辑时都不显示 */
    fun imageUris(parts: List<UIMessagePart>): Set<String> =
        parts.filterIsInstance<UIMessagePart.Text>()
            .flatMap { part -> imageRegex.findAll(part.text).map { it.groupValues[1] }.toList() }
            .toSet()

    fun stripImages(parts: List<UIMessagePart>): List<UIMessagePart> {
        val hidden = imageUris(parts)
        if (hidden.isEmpty()) return parts
        return parts.filterNot { it is UIMessagePart.Image && it.url in hidden }
    }

    /** 气泡下面显示的小字 */
    fun statuses(text: String): List<String> = blockRegex.findAll(text).map { match ->
        when (val title = match.groupValues[1]) {
            NEED_LOGIN -> "没打开：需要登录"
            FAILED -> "没打开"
            else -> "已读取：$title"
        }
    }.toList()

    /** 显示和编辑时去掉网页内容 */
    fun strip(text: String): String = blockRegex.replace(text, "")

    private fun toBlock(page: WebPage): String {
        val title = page.title
        val header = when {
            page.needLogin -> NEED_LOGIN
            title == null -> FAILED
            else -> clean(title)
        }
        val sb = StringBuilder()
        sb.append(OPEN).append(header).append("]\n")
        sb.append("网址：").append(page.url).append("\n")
        if (!page.needLogin && title != null && page.text.isNotBlank()) {
            sb.append(escapeBody(page.text)).append("\n")
        }
        sb.append(CLOSE)
        return sb.toString()
    }

    // 正文里刚好有暗号的话换掉，免得截错
    private fun escapeBody(body: String): String =
        body.replace(CLOSE, "(/网页内容)").replace(IMAGE_OPEN, "(网页图片")

    private fun clean(s: String): String {
        val cleaned = s.replace('[', '(').replace(']', ')').replace('\n', ' ').trim()
        return cleaned.ifEmpty { "无标题" }
    }

    internal fun fromPreload(url: String, preload: XhsPreload): WebPage =
        WebPage(
            url = url,
            title = preload.title.ifBlank { "小红书笔记" },
            text = preload.desc.take(MAX_TEXT),
        )

    // 优先级：小红书笔记数据 > 跳转里带的预览 > 普通网页
    internal fun fromJs(url: String, json: String, preload: XhsPreload? = null): WebPage {
        val obj = runCatching { JSONObject(json) }.getOrNull()
        val xhs = obj?.optJSONObject("xhs")
        if (xhs != null) {
            val title = xhs.optString("title").trim()
            val desc = xhs.optString("desc").trim()
            if (title.isNotEmpty() || desc.isNotEmpty()) {
                return WebPage(
                    url = url,
                    title = title.ifEmpty { "小红书笔记" },
                    text = desc.take(MAX_TEXT),
                )
            }
        }
        if (preload != null) return fromPreload(url, preload)
        if (obj == null) return WebPage(url, null)

        val title = obj.optString("ogTitle").ifBlank { obj.optString("title") }.trim()
        val desc = obj.optString("desc").trim()
        val lines = mutableListOf<String>()
        obj.optString("text").lines().forEach { raw ->
            val line = raw.trim()
            if (line.isNotEmpty() && lines.lastOrNull() != line) lines.add(line)
        }
        val body = lines.joinToString("\n")
        val full = if (desc.isNotEmpty() && !body.contains(desc)) desc + "\n" + body else body
        val text = full.take(MAX_TEXT)
        if (title.isEmpty() && text.isEmpty()) return WebPage(url, null)
        val needLogin = text.length < 200 && (text.contains("登录") || text.contains("验证"))
        return WebPage(
            url = url,
            title = title.ifEmpty { url },
            text = text,
            needLogin = needLogin,
        )
    }
}

object XhsLinks {
    // 从 xhsdiscover:// 跳转里取笔记网页版地址
    fun webUrl(uri: Uri): String? {
        var open = runCatching { uri.getQueryParameter("open_url") }.getOrNull()?.trim().orEmpty()
        if (open.startsWith("http%3A", ignoreCase = true) || open.startsWith("https%3A", ignoreCase = true)) {
            open = Uri.decode(open)
        }
        if (open.startsWith("http://") || open.startsWith("https://")) return open
        val id = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
        val token = runCatching { uri.getQueryParameter("xsec_token") }.getOrNull()
        val base = "https://www.xiaohongshu.com/explore/$id"
        return if (token.isNullOrBlank()) {
            base
        } else {
            base + "?xsec_token=" + Uri.encode(token) + "&xsec_source=pc_share"
        }
    }

    // 跳转里带的笔记标题和正文
    fun preload(uri: Uri): XhsPreload? {
        val raw = runCatching { uri.getQueryParameter("h5NotePreloadInfo") }.getOrNull() ?: return null
        val obj = runCatching { JSONObject(raw) }.getOrNull()
            ?: runCatching { JSONObject(Uri.decode(raw)) }.getOrNull()
            ?: return null
        val title = obj.optString("title").trim()
        val desc = obj.optString("desc").trim()
        if (title.isEmpty() && desc.isEmpty()) return null
        return XhsPreload(title, desc)
    }
}

object WebReader {
    private const val TAG = "WebReader"

    // 按网址缓存读成功的结果
    private val cache = ConcurrentHashMap<String, WebPage>()

    suspend fun read(context: Context, url: String): WebPage {
        cache[url]?.let { return it }
        val page = readHome(if (isXhs(url)) "xhs/read" else "web/read", url)
        if (page.title != null && !page.needLogin) cache[url] = page
        return page
    }

    private val xhsRegex = Regex("""https?://([^/]*\.)?(xhslink\.(com|cn)|xiaohongshu\.com)/""")
    private fun isXhs(url: String) = xhsRegex.containsMatchIn(url)
    // 服务器开浏览器读一篇要几十秒才回第一个字，OkHttp 默认 10 秒读超时会先断
    private val xhsClient by lazy { OkHttpClient.Builder().callTimeout(180, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build() }

    // 链接全交给家里服务器读：小红书用阿辞自己的号（/companion/xhs/read），别的网页走 /companion/web/read
    // （11-22 起不再用手机 WebView，手机连 GitHub 常常 20 秒打不开）；读不到就当没打开
    private suspend fun readHome(path: String, url: String): WebPage = withContext(Dispatchers.IO) {
        runCatching {
            val (base, token) = me.rerere.rikkahub.companion.HomeLink.connection()
            val body = JSONObject().put("url", url).toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("$base/companion/$path").header("Authorization", "Bearer $token").post(body).build()
            xhsClient.newCall(req).execute().use { r ->
                val o = JSONObject(r.body.string())
                if (!r.isSuccessful || !o.optBoolean("ok")) WebPage(url, null)
                else WebPage(url, o.optString("title"), o.optString("text"), finalUrl = o.optString("final_url").ifBlank { null })
            }
        }.getOrElse { Log.w(TAG, "read $url via home failed", it); WebPage(url, null) }
    }
}
