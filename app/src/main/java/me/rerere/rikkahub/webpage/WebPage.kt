package me.rerere.rikkahub.webpage

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
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

object WebPages {
    const val MAX_LINKS = 2
    private const val MAX_TEXT = 6000
    private const val NEED_LOGIN = "需要登录，未读取"
    private const val FAILED = "没打开"

    private val urlRegex = Regex("""https?://[A-Za-z0-9\-._~:/?#@!$&'()*+,;=%]+""")
    private val blockRegex = Regex("""\n*\[网页内容[:：]([^\[\]\n]*)\][\s\S]*?\[/网页内容\]""")

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
        val blocks = pages.joinToString("\n\n") { it.toBlock() }
        val index = parts.indexOfFirst { it is UIMessagePart.Text }
        if (index < 0) return parts + UIMessagePart.Text(text = blocks)
        val first = parts[index] as UIMessagePart.Text
        return parts.toMutableList().also {
            it[index] = UIMessagePart.Text(text = "${first.text}\n\n$blocks")
        }
    }

    /** 气泡下面显示的小字 */
    fun statuses(text: String): List<String> = blockRegex.findAll(text).map {
        when (val title = it.groupValues[1]) {
            NEED_LOGIN -> "没打开：需要登录"
            FAILED -> "没打开"
            else -> "已读取：$title"
        }
    }.toList()

    /** 显示和编辑时去掉网页内容 */
    fun strip(text: String): String = text.replace(blockRegex, "")

    private fun WebPage.toBlock(): String {
        val header = when {
            needLogin -> NEED_LOGIN
            title == null -> FAILED
            else -> clean(title.orEmpty())
        }
        return buildString {
            append("[网页内容:").append(header).append("]\n")
            append("网址：").append(url)
            if (title != null && !needLogin && text.isNotBlank()) {
                append("\n").append(text.replace("[/网页内容]", "(/网页内容)"))
            }
            append("\n[/网页内容]")
        }
    }

    private fun clean(s: String): String =
        s.replace('[', '(').replace(']', ')').replace('\n', ' ').trim().ifEmpty { "无标题" }

    internal fun fromJs(url: String, json: String): WebPage {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return WebPage(url, null)
        val title = obj.optString("ogTitle").ifBlank { obj.optString("title") }.trim()
        val desc = obj.optString("desc").trim()
        val lines = mutableListOf<String>()
        obj.optString("text").lines().forEach { raw ->
            val line = raw.trim()
            if (line.isNotEmpty() && lines.lastOrNull() != line) lines.add(line)
        }
        val body = lines.joinToString("\n")
        val text = (if (desc.isNotEmpty() && !body.contains(desc)) "$desc\n$body" else body).take(MAX_TEXT)
        if (title.isEmpty() && text.isEmpty()) return WebPage(url, null)
        val needLogin = text.length < 200 && (text.contains("登录") || text.contains("验证"))
        return WebPage(url = url, title = title.ifEmpty { url }, text = text, needLogin = needLogin)
    }
}

object WebReader {
    private const val TAG = "WebReader"
    private const val TIMEOUT_MS = 15_000L

    // 按网址缓存读成功的结果
    private val cache = ConcurrentHashMap<String, WebPage>()

    suspend fun read(context: Context, url: String): WebPage {
        cache[url]?.let { return it }
        val page = withContext(Dispatchers.Main) { load(context.applicationContext, url) }
        if (page.title != null && !page.needLogin) cache[url] = page
        return page
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun load(context: Context, url: String): WebPage {
        val webView = runCatching { WebView(context) }.getOrElse {
            Log.w(TAG, "create WebView failed", it)
            return WebPage(url, null)
        }
        return try {
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

            var failed = false
            val loaded = CompletableDeferred<Unit>()
            webView.webViewClient = object : WebViewClient() {
                // 拦掉 xhsdiscover:// 这类跳转，不让它拉起别的 App
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    return scheme != "http" && scheme != "https"
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    failed = false
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
                }
            }

            withTimeoutOrNull(TIMEOUT_MS) {
                webView.loadUrl(url)
                loaded.await()
                // 小红书这类页面是 JS 渲染的，多等一会，再往下滚一屏
                delay(2500)
                webView.evaluate("window.scrollTo(0, window.innerHeight);")
                delay(800)
                if (failed) return@withTimeoutOrNull WebPage(url, null)
                WebPages.fromJs(url, webView.evaluate(EXTRACT_JS))
            } ?: WebPage(url, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "read $url failed", e)
            WebPage(url, null)
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
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
          var body = document.body ? document.body.innerText : '';
          return {
            title: document.title || '',
            ogTitle: meta('og:title'),
            desc: meta('og:description') || meta('description'),
            text: (body || '').slice(0, 20000)
          };
        })();
    """.trimIndent()
}
