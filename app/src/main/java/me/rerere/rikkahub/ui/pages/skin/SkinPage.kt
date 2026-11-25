package me.rerere.rikkahub.ui.pages.skin

import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.rikkahub.companion.SkinStore
import java.net.HttpURLConnection
import java.net.URL

private fun fetchText(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 15_000
    return try {
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    remember { SkinStore.ensure(ctx) }
    var skins by remember { mutableStateOf(SkinStore.list(ctx)) }
    var selected by remember { mutableStateOf(SkinStore.selectedId(ctx)) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun refresh() {
        skins = SkinStore.list(ctx)
        selected = SkinStore.selectedId(ctx)
    }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    fun importText(text: String?) {
        if (text.isNullOrBlank()) {
            toast("没读到内容")
            return
        }
        SkinStore.import(ctx, text)
            .onSuccess { id ->
                SkinStore.select(ctx, id)
                refresh()
                toast("已导入，换上了")
            }
            .onFailure { toast("皮肤文件读不懂：" + (it.message ?: "").take(80)) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                importText(text)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("美化") },
                navigationIcon = {
                    IconButton(onClick = { back?.onBackPressed() }) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "只换聊天页的样子，功能不变。点一下就换上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SkinRow(
                    title = "默认 · 冷淡风",
                    sub = "原来那套",
                    active = selected == null,
                    onClick = {
                        SkinStore.select(ctx, null)
                        refresh()
                    },
                    onDelete = null,
                )
            }
            items(skins, key = { it.first }) { (id, skin) ->
                SkinRow(
                    title = skin.name,
                    sub = skin.author ?: id,
                    active = selected == id,
                    onClick = {
                        SkinStore.select(ctx, id)
                        refresh()
                    },
                    onDelete = { pendingDelete = id to skin.name },
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            input = ""
                            dialog = "link"
                        },
                        enabled = !busy,
                    ) { Text(if (busy) "下载中…" else "链接导入") }
                    OutlinedButton(onClick = {
                        input = ""
                        dialog = "paste"
                    }) { Text("粘贴导入") }
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("文件导入") }
                }
            }
        }
    }

    dialog?.let { kind ->
        val isLink = kind == "link"
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (isLink) "皮肤链接" else "粘贴皮肤文件") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (isLink) 1 else 6,
                    maxLines = if (isLink) 3 else 12,
                    placeholder = { Text(if (isLink) "https://….json" else "{ \"name\": … }") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = input.trim()
                    dialog = null
                    if (isLink) {
                        busy = true
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { runCatching { fetchText(value) }.getOrNull() }
                            busy = false
                            if (text == null) toast("下载失败，检查一下链接") else importText(text)
                        }
                    } else {
                        importText(value)
                    }
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } },
        )
    }

    pendingDelete?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删掉「$name」？") },
            text = { Text("删了可以再导入。") },
            confirmButton = {
                TextButton(onClick = {
                    SkinStore.delete(ctx, id)
                    pendingDelete = null
                    refresh()
                }) { Text("删掉") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SkinRow(
    title: String,
    sub: String,
    active: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = if (active) 1.5.dp else 0.5.dp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (active) {
            Text("使用中", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        } else if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}
