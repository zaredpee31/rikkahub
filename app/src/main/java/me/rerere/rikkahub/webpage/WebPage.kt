package me.rerere.rikkahub.webpage

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
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
import me.rerere.rikkahub.data.files.FilesManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

/** 读到的一个网页；[title] 为 null 表示没打开 */
data class WebPage(
    val url: String,
    val title: String?,
    val text: String = "",
    val needLogin: Boolean = false,
    /** 网页里找到的图片地址，og:image 排第一 */
    val imageUrls: List<String> = emptyList(),
    /** 下载压缩好、存进聊天文件的图片 */
    val images: List<String> = emptyList(),
)

object WebPages {
    const val MAX_LINKS = 2
    // 有几张看几张；小红书一篇最多 18 张，这个数只防普通网页一次塞太多
    const val MAX_IMAGES = 18
    private const val MAX_TEXT = 6000
    private const val NEED_LOGIN = "需要登录，未读取"
    private const val FAILED = "没打开"

    private val urlRegex = Regex("""https?://[A-Za-z0-9\-._~:/?#@!$&'()*+,;=%]+""")
    private val blockRegex = Regex("""\n*\[网页内容[:：]([^\[\]\n]*)\][\s\S]*?\[/网页内容\]""")
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

    // 网页内容接在第一段文字后面，图片放在消息最后
    fun attach(parts: List<UIMessagePart>, pages: List<WebPage>): List<UIMessagePart> {
        if (pages.isEmpty()) return parts
        val blocks = pages.joinToString("\n\n") { it.toBlock() }
        val index = parts.indexOfFirst { it is UIMessagePart.Text }
        val withText = if (index < 0) {
            parts + UIMessagePart.Text(text = blocks)
        } else {
            val first = parts[index] as UIMessagePart.Text
            parts.toMutableList().also {
                it[index] = UIMessagePart.Text(text = "${first.text}\n\n$blocks")
            }
        }
        return withText + pages.flatMap { it.images }.map { UIMessagePart.Image(url = it) }
    }

    /** 网页带来的图片：气泡里和编辑时都不显示 */
    fun imageUris(parts: List<UIMessagePart>): Set<String> =
        parts.filterIsInstance<UIMessagePart.Text>()
            .flatMap { text -> imageRegex.findAll(text.text).map { it.groupValues[1] }.toList() }
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
            else -> {
                val count = imageRegex.findAll(match.value).count()
                if (count > 0) "已读取：${title}（${count} 张图）" else "已读取：${title}"
            }
        }
    }.toList()

    /** 显示和编辑时去掉网页内容 */
    fun strip(text: String): String = text.replace(blockRegex, "")

    private fun WebPage.toBlock(): String {
        val ok = title != null && !needLogin
        val header = when {
            needLogin -> NEED_LOGIN
            title == null -> FAILED
            else -> clean(title.orEmpty())
        }
        return buildString {
            append("", "(/网页内容)").replace("[网页图片", "(网页图片")
                    )
                }
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
        val imageUrls = mutableListOf<String>()
        obj.optJSONArray("images")?.let { arr ->
            for (i in 0 until arr.length()) {
                val src = arr.optString(i).trim()
                if ((src.startsWith("http://") || src.startsWith("https://")) && src !in imageUrls) {
                    imageUrls.add(src)
                }
            }
        }
        return WebPage(
            url = url,
            title = title.ifEmpty { url },
            text = text,
            needLogin = needLogin,
            // 多留几个备用，有的下载失败就用下一个
            imageUrls = imageUrls.take(MAX_IMAGES + 10),
        )
    }
}

object WebReader {
    private const val TAG = "WebReader"
    private const val TIMEOUT_MS = 15_000L
    private const val MAX_SIDE = 1024
    private const val MIN_SIDE = 200
    private const val MAX_IMAGE_BYTES = 15 * 1024 * 1024

    // 按网址缓存读成功的结果（只存文字和图片地址，图片每次重新下载）
    private val cache = ConcurrentHashMap<String, WebPage>()

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun read(context: Context, url: String): WebPage {
        val app = context.applicationContext
        val page = cache[url] ?: withContext(Dispatchers.Main) { load(app, url) }.also {
            if (it.title != null && !it.needLogin) cache[url] = it
        }
        if (page.title == null || page.needLogin || page.imageUrls.isEmpty()) return page
        return page.copy(images = saveImages(app, page))
    }

    // 下载、压缩，再存成聊天文件，跟手动发的图一样
    private suspend fun saveImages(context: Context, page: WebPage): List<String> =
        withContext(Dispatchers.IO) {
            val filesManager = runCatching { GlobalContext.get().get<FilesManager>() }.getOrNull()
                ?: return@withContext emptyList()
            val userAgent = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
            val saved = mutableListOf<String>()
            for (src in page.imageUrls) {
                if (saved.size >= WebPages.MAX_IMAGES) break
                val tmp = download(src, page.url, userAgent, context.cacheDir) ?: continue
                try {
                    filesManager.createChatFilesByContents(listOf(Uri.fromFile(tmp)))
                        .firstOrNull()?.let { saved.add(it.toString()) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "save image failed", e)
                } finally {
                    tmp.delete()
                }
            }
            saved
        }

    private fun download(src: String, referer: String, userAgent: String?, dir: File): File? {
        return try {
            val request = Request.Builder().url(src).header("Referer", referer).apply {
                userAgent?.let { header("User-Agent", it) }
                CookieManager.getInstance().getCookie(src)?.let { header("Cookie", it) }
            }.build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                if (bytes.size > MAX_IMAGE_BYTES) return null
                compress(bytes, dir)
            }
        } catch (e: Exception) {
            Log.w(TAG, "download $src failed", e)
            null
        }
    }

    // 长边压到 1024，存成 JPEG
    private fun compress(bytes: ByteArray, dir: File): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w0 = bounds.outWidth
        val h0 = bounds.outHeight
        // 太小的是图标、头像
        if (w0 < MIN_SIDE && h0 < MIN_SIDE) return null
        var sample = 1
        while (max(w0, h0) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val scale = minOf(1f, MAX_SIDE.toFloat() / max(decoded.width, decoded.height))
        val w = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val h = (decoded.height * scale).roundToInt().coerceAtLeast(1)
        // 透明底的图转 JPEG 会变黑，先铺一层白底
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(decoded, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        decoded.recycle()
        val file = File(dir, "webpage_${System.nanoTime()}.jpg")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        out.recycle()
        return file
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
                // 小红书这类页面是 JS 渲染的，多等一会，再往下滚一屏让图片加载出来
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
          var images = [];
          var og = meta('og:image');
          if (og.indexOf('//') === 0) og = location.protocol + og;
          if (og) images.push(og);
          var list = document.images || [];
          for (var i = 0; i < list.length && images.length < 30; i++) {
            var img = list[i];
            var src = img.currentSrc || img.src || '';
            if (src.indexOf('http') !== 0) continue;
            if (img.naturalWidth > 200 && images.indexOf(src) < 0) images.push(src);
          }
          var body = document.body ? document.body.innerText : '';
          return {
            title: document.title || '',
            ogTitle: meta('og:title'),
            desc: meta('og:description') || meta('description'),
            text: (body || '').slice(0, 20000),
            images: images
          };
        })();
    """.trimIndent()
}
