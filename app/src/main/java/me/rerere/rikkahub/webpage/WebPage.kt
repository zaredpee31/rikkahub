package me.rerere.rikkahub.webpage

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.ai.ui.UIMessagePart
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** 读到的一个网页；[title] 为 null 表示没打开 */
data class WebPage(
    val url: String,
    val title: String?,
    val text: String = "",
    val needLogin: Boolean = false,
)

/** 小红书分享跳转里带的标题和正文，网页版读不到时兜底 */
data class XhsPreload(val title: String, val desc: String)

/** 百度搜到的一条 */
data class SearchHit(val title: String, val url: String, val snippet: String)

/** 百度搜索结果；[blocked] 是遇到了安全验证 */
data class SearchResult(
    val hits: List<SearchHit> = emptyList(),
    val blocked: Boolean = false,
    val failed: Boolean = false,
)

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
    private const val READ_TIMEOUT_MS = 20_000L
    private const val SEARCH_TIMEOUT_MS = 15_000L
    private const val MAX_HITS = 8
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // 按网址缓存读成功的结果
    private val cache = ConcurrentHashMap<String, WebPage>()

    suspend fun read(context: Context, url: String): WebPage {
        cache[url]?.let { return it }
        val page = withContext(Dispatchers.Main) { load(context.applicationContext, url) }
        if (page.title != null && !page.needLogin) cache[url] = page
        return page
    }

    suspend fun search(context: Context, query: String): SearchResult {
        val address = "https://m.baidu.com/s?word=" + Uri.encode(query)
        return withContext(Dispatchers.Main) { searchBaidu(context.applicationContext, address) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView? {
        val webView = runCatching { WebView(context) }.getOrElse {
            Log.w(TAG, "create WebView failed", it)
            return null
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        // 不显示出来，手动给一个屏幕大小，页面才会排版
        val metrics = context.resources.displayMetrics
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        return webView
    }

    private suspend fun load(context: Context, url: String): WebPage {
        val webView = createWebView(context) ?: return WebPage(url, null)
        var failed = false
        var jumpTo: String? = null
        var jumpStarted = false
        var preload: XhsPreload? = null
        val loaded = CompletableDeferred<Unit>()
        val jumpLoaded = CompletableDeferred<Unit>()
        return try {
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url
                    val scheme = target.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") return false
                    // 小红书短链会跳 xhsdiscover://，截下来改开笔记网页版，只截一次
                    if (scheme == "xhsdiscover" && jumpTo == null) {
                        val web = XhsLinks.webUrl(target)
                        if (web != null) {
                            jumpTo = web
                            preload = XhsLinks.preload(target)
                            view.settings.userAgentString = DESKTOP_UA
                            view.post { view.loadUrl(web) }
                        }
                    }
                    // 别的 App 链接一律不打开
                    return true
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    failed = false
                    if (jumpTo != null) jumpStarted = true
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) failed = true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    loaded.complete(Unit)
                    if (jumpStarted) jumpLoaded.complete(Unit)
                }
            }

            val page = withTimeoutOrNull(READ_TIMEOUT_MS) {
                webView.loadUrl(url)
                loaded.await()
                delay(1500)
                if (jumpTo != null) {
                    withTimeoutOrNull(8000) { jumpLoaded.await() }
                }
                // JS 渲染的页面多等一会，再往下滚一屏
                delay(2500)
                webView.evaluate("window.scrollTo(0, window.innerHeight);")
                delay(800)
                if (failed) null else WebPages.fromJs(url, webView.evaluate(EXTRACT_JS), preload)
            }
            page ?: preload?.let { WebPages.fromPreload(url, it) } ?: WebPage(url, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "read $url failed", e)
            preload?.let { WebPages.fromPreload(url, it) } ?: WebPage(url, null)
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private suspend fun searchBaidu(context: Context, address: String): SearchResult {
        val webView = createWebView(context) ?: return SearchResult(failed = true)
        var failed = false
        var lastUrl = address
        val loaded = CompletableDeferred<Unit>()
        return try {
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    return scheme != "http" && scheme != "https"
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    failed = false
                    if (url != null) lastUrl = url
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) failed = true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    if (url != null) lastUrl = url
                    loaded.complete(Unit)
                }
            }

            withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                webView.loadUrl(address)
                loaded.await()
                delay(1500)
                if (isBlocked(lastUrl)) {
                    SearchResult(blocked = true)
                } else if (failed) {
                    SearchResult(failed = true)
                } else {
                    parseSearch(webView.evaluate(SEARCH_JS))
                }
            } ?: SearchResult(failed = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "search failed", e)
            SearchResult(failed = true)
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private fun isBlocked(url: String): Boolean =
        url.contains("wappass.baidu.com") || url.contains("captcha")

    private fun parseSearch(json: String): SearchResult {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return SearchResult(failed = true)
        if (obj.optBoolean("blocked")) return SearchResult(blocked = true)
        val arr = obj.optJSONArray("items") ?: return SearchResult()
        val hits = mutableListOf<SearchHit>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            val link = item.optString("url").trim()
            val snippet = item.optString("snippet").trim()
            if (title.isEmpty() && snippet.isEmpty()) continue
            hits.add(SearchHit(title, link, snippet))
            if (hits.size >= MAX_HITS) break
        }
        return SearchResult(hits)
    }

    private suspend fun WebView.evaluate(script: String): String =
        suspendCancellableCoroutine { cont ->
            evaluateJavascript(script) { cont.resume(it ?: "null") }
        }

    private val EXTRACT_JS = """
        (function () {
          function meta(name) {
            var el = document.querySelector('meta[property="' + name + '"]')
              || document.querySelector('meta[name="' + name + '"]');
            return el ? (el.getAttribute('content') || '') : '';
          }
          function un(v) { return (v && v._value !== undefined) ? v._value : v; }
          var xhs = null;
          if (location.hostname.indexOf('xiaohongshu') >= 0) {
            try {
              var st = window.__INITIAL_STATE__;
              var map = st && st.note && un(st.note.noteDetailMap);
              if (map) {
                for (var k in map) {
                  var item = un(map[k]);
                  var n = item && un(item.note);
                  if (n && (n.title || n.desc)) {
                    xhs = { title: String(n.title || ''), desc: String(n.desc || '') };
                    break;
                  }
                }
              }
              if (!xhs) {
                var nd0 = st && un(st.noteData);
                var nd = nd0 && nd0.data && nd0.data.noteData;
                if (nd && (nd.title || nd.desc)) {
                  xhs = { title: String(nd.title || ''), desc: String(nd.desc || '') };
                }
              }
            } catch (e) {}
            if (!xhs) {
              var dt = document.querySelector('#detail-title');
              var dd = document.querySelector('#detail-desc');
              if (dt || dd) {
                xhs = { title: dt ? dt.innerText.trim() : '', desc: dd ? dd.innerText.trim() : '' };
              }
            }
          }
          var body = document.body ? document.body.innerText : '';
          return {
            title: document.title || '',
            ogTitle: meta('og:title'),
            desc: meta('og:description') || meta('description'),
            text: (body || '').slice(0, 20000),
            xhs: xhs
          };
        })();
    """.trimIndent()

    private val SEARCH_JS = """
        (function () {
          if ((document.title || '').indexOf('安全验证') >= 0 || location.href.indexOf('wappass') >= 0) {
            return { blocked: true, items: [] };
          }
          var nodes = document.querySelectorAll('.c-result');
          if (!nodes.length) nodes = document.querySelectorAll('.result');
          var items = [];
          for (var i = 0; i < nodes.length && items.length < 8; i++) {
            var node = nodes[i];
            var t = node.querySelector('h3') || node.querySelector('.c-title') || node.querySelector('[class*="title"]');
            var title = t ? t.innerText.trim() : '';
            var link = '';
            var logs = [node].concat(Array.prototype.slice.call(node.querySelectorAll('[data-log]')));
            for (var j = 0; j < logs.length && !link; j++) {
              var log = logs[j].getAttribute('data-log') || '';
              var m = log.match(/["']mu["']\s*:\s*["']([^"']+)["']/);
              if (m) link = m[1];
            }
            if (!link) {
              var a = node.querySelector('a[href]');
              if (a) link = a.href;
            }
            var text = (node.innerText || '').replace(/\s+/g, ' ').trim();
            if (title && text.indexOf(title) === 0) text = text.slice(title.length).trim();
            if (!title && !text) continue;
            items.push({ title: title, url: link, snippet: text.slice(0, 200) });
          }
          return { blocked: false, items: items };
        })();
    """.trimIndent()
}
