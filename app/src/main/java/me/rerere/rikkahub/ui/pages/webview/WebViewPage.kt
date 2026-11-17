package me.rerere.rikkahub.ui.pages.webview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.rikkahub.ui.components.ui.PalaceButton
import me.rerere.rikkahub.ui.components.ui.PalaceGlassDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceBackdrop
import me.rerere.rikkahub.ui.pages.assistant.detail.PalaceTopBar
import me.rerere.rikkahub.ui.theme.palaceColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.ui.webview.WEB_VIEW_BASE_URL
import me.rerere.ui.webview.WebView
import me.rerere.ui.webview.WebViewContentCache
import me.rerere.ui.webview.rememberWebViewState

@Composable
fun WebViewPage(url: String, contentId: String) {
    val context = LocalContext.current
    val c = palaceColors()
    val navController = LocalNavController.current
    val state = if (url.isNotEmpty()) {
        rememberWebViewState(
            url = url,
            settings = {
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
            })
    } else {
        val content = remember(contentId) {
            WebViewContentCache.load(context.cacheDir, contentId).orEmpty()
        }
        rememberWebViewState(
            data = content,
            baseUrl = WEB_VIEW_BASE_URL,
            mimeType = "text/html",
            settings = {
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
            }
        )
    }

    var showMenu by remember { mutableStateOf(false) }
    var showConsoleSheet by remember { mutableStateOf(false) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    BackHandler(state.canGoBack) {
        state.goBack()
    }

    // 外壳换成雪天样式：磨砂顶栏 + 渐变底；WebView 本身和渲染内容不动
    PalaceBackdrop {
        Column(Modifier.fillMaxSize()) {
            PalaceTopBar(
                title = state.pageTitle?.takeIf { it.isNotEmpty() } ?: state.currentUrl ?: "",
                sub = "WEB VIEW",
                onBack = { navController.popBackStack() },
                rightIcon = HugeIcons.MoreVertical,
                onRight = { showMenu = true },
            )
            WebView(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // 原来 Scaffold 会给底部导航栏留位置
                    .navigationBarsPadding(),
            )
        }
    }

    // 原来顶栏上的刷新 / 前进 / 更多（浏览器打开、控制台），收进一个磨砂弹窗，功能一个不少
    if (showMenu) {
        val urlHandler = LocalUriHandler.current
        PalaceGlassDialog(
            title = "网页视图",
            onDismiss = { showMenu = false },
            buttons = {
                PalaceButton(text = "关闭", onClick = { showMenu = false })
            },
        ) {
            PalaceButton(
                text = "刷新",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    showMenu = false
                    state.reload()
                },
            )
            PalaceButton(
                text = "前进",
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canGoForward,
                onClick = {
                    showMenu = false
                    state.goForward()
                },
            )
            PalaceButton(
                text = "在浏览器中打开",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    showMenu = false
                    state.currentUrl?.let { current ->
                        if (current.isNotBlank()) {
                            urlHandler.openUri(current)
                        }
                    }
                },
            )
            PalaceButton(
                text = "控制台日志",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    showMenu = false
                    showConsoleSheet = true
                },
            )
        }
    }

    if (showConsoleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConsoleSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "控制台日志",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.ink,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                SelectionContainer {
                    LazyColumn {
                        items(state.consoleMessages) { message ->
                            Text(
                                text = "${message.messageLevel().name}: ${message.message()}\n" +
                                    "Source: ${message.sourceId()}:${message.lineNumber()}",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                fontFamily = JetbrainsMono,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                color = when (message.messageLevel().name) {
                                    "ERROR" -> c.error
                                    "WARNING" -> c.warnInk
                                    else -> c.ink
                                }
                            )
                        }
                    }
                }

                if (state.consoleMessages.isEmpty()) {
                    Text(
                        text = "没有控制台消息",
                        fontSize = 13.sp,
                        color = c.mute,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
