@file:OptIn(ExperimentalMaterial3Api::class)

package me.rerere.rikkahub.jade

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.reading.ReadingIcons
import me.rerere.rikkahub.ui.context.LocalSettings
import java.io.ByteArrayInputStream
import java.io.File

private val Paper = Color(0xFFFAF9F5)
private val CardColor = Color.White
private val Ink = Color(0xFF1F1E1D)
private val Gray = Color(0xFF8A8885)
private val Accent = Color(0xFFD97757)
private val AccentSoft = Color(0xFFF7E7DF)
private val Line = Color(0xFFE8E6DC)

// 临时：页面读存档时弹提示，确认接上了再改成 false
private const val DEBUG_TOAST = true

private const val HOST = "appassets.androidplatform.net"
private const val START_URL = "https://appassets.androidplatform.net/index.html"

/*
 * 原版页面把存档存在浏览器的 localStorage 里（键名 dushi_xiaopu_v3）。
 * 这里在页面最前面插一段脚本，只把这个键的读写转给 app，页面本身一行不改。
 */
private const val SHIM = """<script>
(function(){
  var B = window.AoiJade; if (!B) return;
  var KEY = 'dushi_xiaopu_v3';
  var S = Storage.prototype, og = S.getItem, os = S.setItem, orm = S.removeItem;
  S.getItem = function(k){
    if (k === KEY) { var v = B.get(); return (v === undefined || v === null || v === '') ? null : String(v); }
    return og.apply(this, arguments);
  };
  S.setItem = function(k, v){
    if (k === KEY) { B.set(String(v)); return; }
    return os.apply(this, arguments);
  };
  S.removeItem = function(k){
    if (k === KEY) return;
    return orm.apply(this, arguments);
  };
})();
</script>"""

/** 读 assets 里的原版页面，把转接脚本插进 head 最前面 */
private object JadeHtml {
    @Volatile
    private var cached: ByteArray? = null

    fun get(c: Context): ByteArray = cached ?: synchronized(this) {
        cached ?: build(c).also { cached = it }
    }

    private fun build(c: Context): ByteArray {
        val html = c.assets.open("jade/index.html").use { it.readBytes() }.toString(Charsets.UTF_8)
        val i = html.indexOf("<head>", ignoreCase = true)
        val out = if (i >= 0) html.substring(0, i + 6) + SHIM + html.substring(i + 6) else SHIM + html
        return out.toByteArray(Charsets.UTF_8)
    }
}

private fun debugToast(app: Context, text: String) {
    if (!DEBUG_TOAST) return
    Handler(Looper.getMainLooper()).post { Toast.makeText(app, text, Toast.LENGTH_SHORT).show() }
}

private class JadeBridge(private val app: Context) {
    @JavascriptInterface
    fun get(): String {
        val r = JadeGame.storageGet(app)
        debugToast(app, if (r.isEmpty()) "赌石：app 里还没有存档，新开一局" else "赌石：读到 app 里的存档")
        return r
    }

    // 页面不在前台时（我在后台逛、宝宝切走了）晚到的保存不收，免得盖掉我刚切的
    @JavascriptInterface
    fun set(body: String) {
        if (!JadeGame.pageOpen) return
        JadeGame.storageSet(app, body)
    }
}

// 只放行我们自己的页面，别的网址一律不加载（谷歌字体国内也打不开，页面会用系统字体）
private class JadeClient(private val app: Context) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url ?: return null
        if (url.scheme == "data" || url.scheme == "blob") return null
        val path = url.path.orEmpty()
        if (url.host == HOST && (path == "/" || path == "/index.html" || path == "/jade/index.html" || path == "/jade/")) {
            val bytes = runCatching { JadeHtml.get(app) }.getOrElse {
                "<meta charset='utf-8'><p style='padding:24px'>没找到 assets/jade/index.html</p>".toByteArray()
            }
            return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(bytes))
        }
        return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        request?.url?.host != HOST
}

private fun ago(t: Long): String {
    if (t <= 0) return ""
    val m = (System.currentTimeMillis() - t) / 60000
    return when {
        m < 1 -> "刚刚"
        m < 60 -> "$m 分钟前"
        m < 1440 -> "${m / 60} 小时前"
        else -> "${m / 1440} 天前"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JadePage() {
    val context = LocalContext.current
    val app = context.applicationContext
    val backOwner = LocalOnBackPressedDispatcherOwner.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var webView by remember { mutableStateOf<WebView?>(null) }
    var resumed by remember { mutableStateOf(true) }
    var lent by remember { mutableStateOf(false) }
    var sawRunning by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    val running by JadeAuto.running.collectAsState()
    val note by JadeAuto.note.collectAsState()
    val noteTime by JadeAuto.noteTime.collectAsState()
    val error by JadeAuto.error.collectAsState()

    LaunchedEffect(Unit) { JadeAuto.load(app) }

    // 页面在前台时我不动存档；切走再回来，存档被我动过就刷新
    DisposableEffect(lifecycleOwner) {
        val saveFile = File(app.filesDir, "jade/save.json")
        var pausedAt = -1L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    webView?.onResume()
                    if (!lent) JadeGame.pageOpen = true
                    if (pausedAt >= 0 && saveFile.lastModified() != pausedAt && !JadeAuto.running.value) {
                        webView?.reload()
                    }
                    pausedAt = -1L
                }

                Lifecycle.Event.ON_PAUSE -> {
                    resumed = false
                    JadeGame.pageOpen = false
                    webView?.onPause()
                    pausedAt = saveFile.lastModified()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            JadeGame.pageOpen = false
        }
    }

    // 我逛完了，刷新页面
    LaunchedEffect(running) {
        if (running) {
            sawRunning = true
        } else if (sawRunning) {
            sawRunning = false
            lent = false
            if (resumed) JadeGame.pageOpen = true
            webView?.reload()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(ReadingIcons.Back, "返回") { backOwner?.onBackPressedDispatcher?.onBackPressed() }
            Text(
                text = "赌石小铺",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            )
            Surface(
                onClick = {
                    lent = true
                    JadeGame.pageOpen = false
                    JadeAuto.playNow(app)
                },
                enabled = !running,
                shape = CircleShape,
                color = if (running) Line else Accent,
            ) {
                Text(
                    text = if (running) "在逛了…" else "叫 Claude 来",
                    color = if (running) Gray else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            RoundButton(HugeIcons.Settings03, "设置") { showSettings = true }
        }

        if (note.isNotEmpty() || error != null) {
            val shape = RoundedCornerShape(14.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .clip(shape)
                    .background(CardColor)
                    .border(1.dp, Line, shape)
                    .clickable { showLog = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (note.isNotEmpty()) {
                    Text(
                        text = "Claude 的留言 · " + ago(noteTime),
                        color = Accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = note,
                        color = Ink,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                error?.let {
                    Text("上次没逛成：$it", color = Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.textZoom = 100
                        setBackgroundColor(android.graphics.Color.parseColor("#F4F1E8"))
                        addJavascriptInterface(JadeBridge(app), "AoiJade")
                        webViewClient = JadeClient(app)
                        loadUrl(START_URL)
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )

            if (running) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Paper.copy(alpha = 0.9f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent)
                        Spacer(Modifier.height(14.dp))
                        Text("Claude 正在店里逛…", color = Ink, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text("逛完页面会自己刷新", color = Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showSettings) SettingsSheet(onDismiss = { showSettings = false })
    if (showLog) LogSheet(onDismiss = { showLog = false })
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = CardColor,
        shadowElevation = 2.dp,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = Ink, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Ink, style = MaterialTheme.typography.bodyLarge)
            Text(desc, color = Gray, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
        )
    }
}

@Composable
private fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val settings = LocalSettings.current
    var auto by remember { mutableStateOf(JadeAuto.autoEnabled(app)) }
    var think by remember { mutableStateOf(JadeAuto.thinkEnabled(app)) }
    var modelId by remember { mutableStateOf(JadeAuto.modelId(app)) }
    val groups = remember(settings.providers) {
        settings.providers
            .filter { it.enabled }
            .map { p -> p.name to p.models.filter { it.type == ModelType.CHAT } }
            .filter { it.second.isNotEmpty() }
    }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Paper) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("赌石设置", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            SwitchRow("让 Claude 自己不定时来逛", "隔几个小时随机来一次，夜里不来", auto) {
                auto = it
                JadeAuto.setAuto(app, it)
            }
            Spacer(Modifier.height(14.dp))
            SwitchRow("逛店时让 Claude 思考", "挑石头更讲究；老是 524 就关掉", think) {
                think = it
                JadeAuto.setThink(app, it)
            }
            Spacer(Modifier.height(18.dp))
            Text("Claude 来逛用的模型", color = Ink, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "自己来逛和点「叫 Claude 来」都用这个。聊天里叫他去，用的是聊天的模型",
                color = Gray,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                item {
                    ModelRow("跟着当前助手", modelId.isBlank()) {
                        modelId = ""
                        JadeAuto.setModelId(app, "")
                    }
                }
                groups.forEach { (providerName, models) ->
                    item {
                        Text(
                            text = providerName,
                            color = Gray,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                    items(models) { m ->
                        val id = m.id.toString()
                        ModelRow(m.displayName.ifBlank { m.modelId }, id == modelId) {
                            modelId = id
                            JadeAuto.setModelId(app, id)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ModelRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = if (selected) Accent else Ink,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(ReadingIcons.Check, "已选", tint = Accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LogSheet(onDismiss: () -> Unit) {
    val note by JadeAuto.note.collectAsState()
    val log by JadeAuto.log.collectAsState()
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Paper) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Claude 这一趟", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(note, color = Accent, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = log.ifBlank { "还没有经过" },
                color = Ink,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
