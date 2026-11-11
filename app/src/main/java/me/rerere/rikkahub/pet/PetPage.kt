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

class PetBridge(private val context: Context, private val onExit: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())

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
                    addJavascriptInterface(PetBridge(ctx.applicationContext) { back() }, "PetBridge")
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
