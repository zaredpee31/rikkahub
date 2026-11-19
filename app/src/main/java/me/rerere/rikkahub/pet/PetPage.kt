package me.rerere.rikkahub.pet

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.companion.HomeLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PetBridge(private val context: Context, private val onExit: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile var web: WebView? = null
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()

    /** 崽崽自己说话：页面把事实打包发来，服务器问免费小模型，回来的话交给 window.petSaid(id, text)。不改任何数值。 */
    @JavascriptInterface
    fun say(id: String, payload: String) {
        Thread {
            val text = runCatching {
                val link = runBlocking { HomeLink.connection() }
                val req = Request.Builder().url(link.first + "/companion/pet/say")
                    .header("Authorization", "Bearer " + link.second)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(req).execute().use { r ->
                    if (r.isSuccessful) JSONObject(r.body.string()).optString("text") else ""
                }
            }.getOrDefault("")
            val js = "window.petSaid&&window.petSaid(" + JSONObject.quote(id) + "," + JSONObject.quote(text) + ")"
            main.post { web?.evaluateJavascript(js, null) }
        }.start()
    }

    @JavascriptInterface
    fun state(): String = PetGame.stateJson(context)

    @JavascriptInterface
    fun catalog(): String = PetGame.catalog().toString()

    @JavascriptInterface
    fun act(cmd: String): String = PetGame.act(context, cmd, PetGame.MOM).toString()

    @JavascriptInterface
    fun exit() {
        main.post { onExit() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PetPage(onBack: () -> Unit) {
    val back by rememberUpdatedState(onBack)
    val holder = remember { arrayOfNulls<WebView>(1) }

    BackHandler {
        val w = holder[0]
        if (w == null) {
            back()
        } else {
            w.evaluateJavascript("window.petBack ? window.petBack() : 'false'") { r ->
                if (r?.contains("true") != true) back()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF6EBDD))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    setBackgroundColor(android.graphics.Color.rgb(246, 235, 221))
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    // 长按交给页面自己处理（按住崽崽拎起来）；不吃掉的话 WebView 长按约 0.4 秒就发 pointercancel，页面的长按永远等不到
                    isLongClickable = false
                    isHapticFeedbackEnabled = false
                    setOnLongClickListener { true }
                    val bridge = PetBridge(ctx.applicationContext) { back() }
                    bridge.web = this
                    addJavascriptInterface(bridge, "PetBridge")
                    loadUrl("file:///android_asset/pet/index.html")
                    holder[0] = this
                }
            },
            onRelease = {
                holder[0] = null
                it.destroy()
            }
        )
    }
}
