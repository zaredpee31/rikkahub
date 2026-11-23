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
import org.json.JSONObject
import kotlin.coroutines.resume

/** 百度搜到的一条 */
data class SearchHit(val title: String, val url: String, val snippet: String)

/** 百度搜索结果；[blocked] 是遇到了安全验证 */
data class SearchResult(
    val hits: List<SearchHit> = emptyList(),
    val blocked: Boolean = false,
    val failed: Boolean = false,
)

/** 在手机上用看不见的 WebView 搜百度（手机在国内，不会像首尔服务器那样被拦） */
object BaiduSearch {
    private const val TAG = "BaiduSearch"
    private const val SEARCH_TIMEOUT_MS = 15_000L
    private const val MAX_HITS = 8

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
