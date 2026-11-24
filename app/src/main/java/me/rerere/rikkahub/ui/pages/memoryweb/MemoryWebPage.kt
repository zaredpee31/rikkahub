package me.rerere.rikkahub.ui.pages.memoryweb

import android.annotation.SuppressLint
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import me.rerere.rikkahub.ui.components.nav.BackButton

private const val MEMORY_URL = "https://ombre.43-166-23-196.sslip.io/lite/"
const val MOON_URL = "https://43-166-23-196.sslip.io/moon/"  // 月相梦境，有登录口令（用户名 aoi）
const val LUTOPIA_URL = "https://phos.43-166-23-196.sslip.io/"  // 侧边栏这个按钮现在开 Phosphene 积分

/** 记忆：全屏 WebView 加载记忆后台，cookie 持久保存，新窗口链接在本页内打开 */
@Composable
fun MemoryWebPage() = HomeWebPage(title = "记忆", url = MEMORY_URL)

/** 全屏网页（记忆、积分、月相梦境共用）：cookie 持久保存，登录一次就记住，新窗口链接在本页内打开 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeWebPage(title: String, url: String) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    // 能后退就在网页里后退，退到头再由系统处理（关闭本页）
    BackHandler(enabled = canGoBack) { webView?.goBack() }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            webView?.destroy()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { BackButton() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        val main = this
                        // 撑满给它的位置：不写的话网页量到的高度是 0，100vh/100dvh 的页面会白屏、弹窗被压扁
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        // 让输入框能拿到焦点、弹出键盘
                        isFocusable = true
                        isFocusableInTouchMode = true
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportMultipleWindows(true)
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val scheme = request.url.scheme?.lowercase()
                                // 只放行 http(s)，其他协议不拉起别的 App
                                return scheme != "http" && scheme != "https"
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                canGoBack = view.canGoBack()
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                CookieManager.getInstance().flush()
                            }

                            // 页面要账号密码（月相梦境）：先用存过的，不对或没有就弹框问一次，填对了存在本机
                            private val tried = HashSet<String>()

                            override fun onReceivedHttpAuthRequest(
                                view: WebView,
                                handler: HttpAuthHandler,
                                host: String,
                                realm: String,
                            ) {
                                val sp = view.context.getSharedPreferences("home_web_auth", android.content.Context.MODE_PRIVATE)
                                val user = sp.getString("$host.user", null)
                                val pass = sp.getString("$host.pass", null)
                                if (user != null && pass != null && tried.add(host)) {
                                    handler.proceed(user, pass)
                                    return
                                }
                                val ctx = view.context
                                val pad = (16 * ctx.resources.displayMetrics.density).toInt()
                                val u = android.widget.EditText(ctx).apply { hint = "用户名"; setText(user ?: "aoi"); isSingleLine = true }
                                val p = android.widget.EditText(ctx).apply {
                                    hint = "密码"; isSingleLine = true
                                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                                }
                                val box = android.widget.LinearLayout(ctx).apply {
                                    orientation = android.widget.LinearLayout.VERTICAL
                                    setPadding(pad, pad / 2, pad, 0)
                                    addView(u); addView(p)
                                }
                                android.app.AlertDialog.Builder(ctx)
                                    .setTitle("登录")
                                    .setView(box)
                                    .setPositiveButton("好") { _, _ ->
                                        val nu = u.text.toString().trim()
                                        val np = p.text.toString()
                                        sp.edit().putString("$host.user", nu).putString("$host.pass", np).apply()
                                        tried.add(host)
                                        handler.proceed(nu, np)
                                    }
                                    .setNegativeButton("取消") { _, _ -> handler.cancel() }
                                    .setOnCancelListener { handler.cancel() }
                                    .show()
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }

                            // window.open / target=_blank：把目标地址转到本 WebView 里加载，进入历史栈
                            override fun onCreateWindow(
                                view: WebView,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message,
                            ): Boolean {
                                val temp = WebView(view.context)
                                temp.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        val scheme = request.url.scheme?.lowercase()
                                        if (scheme == "http" || scheme == "https") {
                                            main.loadUrl(request.url.toString())
                                        }
                                        v.destroy()
                                        return true
                                    }

                                    override fun onPageStarted(v: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                        if (!url.isNullOrBlank() && url != "about:blank") {
                                            main.loadUrl(url)
                                            v.stopLoading()
                                            v.destroy()
                                        }
                                    }
                                }
                                val transport = resultMsg.obj as WebView.WebViewTransport
                                transport.webView = temp
                                resultMsg.sendToTarget()
                                return true
                            }
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}
