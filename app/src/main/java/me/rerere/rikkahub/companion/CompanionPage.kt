package me.rerere.rikkahub.companion

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun CompanionPage() {
    val context = LocalContext.current
    val chat: ChatService = koinInject()
    var showSettings by remember { mutableStateOf(false) }
    var conversationId by remember { mutableStateOf<Uuid?>(null) }
    LaunchedEffect(Unit) {
        val oldId = CompanionSync.target(context).takeIf { it.isNotBlank() }?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        conversationId = runCatching { chat.initializeCompanionConversation(oldId)?.id }.getOrNull()
    }
    LaunchedEffect(showSettings) {
        if (!showSettings) conversationId = runCatching { chat.initializeCompanionConversation()?.id }.getOrNull()
    }
    if (showSettings || conversationId == null) {
        CompanionSettingsPage(onDone = { showSettings = false })
    } else {
        CompanionInbox(conversationId = conversationId!!, onSettings = { showSettings = true })
    }
}

@Composable
private fun CompanionSettingsPage(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val chat: ChatService = koinInject()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val assistant = settings.getCurrentAssistant()
    var modelId by remember { mutableStateOf<Uuid?>(null) }
    var connected by remember { mutableStateOf(CompanionSync.status(context).contains("同步")) }
    var morning by remember { mutableStateOf(CompanionSync.morning(context)) }
    var proactive by remember { mutableStateOf(CompanionSync.proactive(context)) }
    var working by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val modelList = rememberModelListState(modelId, settings.providers, ModelType.CHAT)
    LaunchedEffect(Unit) {
        modelId = chat.initializeCompanionConversation()?.config?.chatModelId
    }
    LaunchedEffect(modelId) {
        val conversation = chat.getCompanionConversation()
        if (conversation != null && modelId != null) {
            runCatching {
                CompanionSync.ensureInitialized(context, conversation.id)
                if (CompanionSync.enabled(context)) {
                    ContextCompat.startForegroundService(context, Intent(context, CompanionReceiverService::class.java))
                }
            }
        }
    }
    fun syncNow() {
        scope.launch {
            working = true
            connected = runCatching { CompanionSync.sync(context); true }.getOrDefault(false) &&
                CompanionSync.status(context).startsWith("已同步")
            working = false
        }
    }
    fun choose(model: Model) {
        modelId = model.id
        scope.launch {
            working = true
            try {
                chat.updateCompanionModel(model.id)
                toaster.show("模型已保存", ToastType.Success)
                // 保存和同步分开：同步失败只更新连接状态，不把模型保存判成失败。
                runCatching { CompanionSync.sync(context) }
                connected = CompanionSync.status(context).startsWith("已同步")
            } catch (e: Exception) {
                toaster.show("模型保存失败：${e.message ?: e::class.simpleName}", ToastType.Error)
            } finally {
                working = false
            }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("消息设置") }, navigationIcon = { BackButton() })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Text("模型", modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 4.dp), color = Color.Gray) }
            item {
                ListItem(
                    headlineContent = { Text("模型") },
                    trailingContent = { Text(modelId?.let { settings.findModelById(it)?.displayName } ?: "未选择") },
                    modifier = Modifier.clickable(enabled = !working) { modelList.open() },
                )
            }
            item { HorizontalDivider() }
            item { Text("主动消息", modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 4.dp), color = Color.Gray) }
            item {
                ListItem(headlineContent = { Text("工作日早安") }, trailingContent = {
                    Switch(morning, onCheckedChange = { morning = it; CompanionSync.options(context, morning, proactive); syncNow() })
                })
            }
            item {
                ListItem(headlineContent = { Text("主动找我聊天") }, trailingContent = {
                    Switch(proactive, onCheckedChange = { proactive = it; CompanionSync.options(context, morning, proactive); syncNow() })
                })
            }
            item { HorizontalDivider() }
            item { Text("其他", modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 4.dp), color = Color.Gray) }
            item {
                ListItem(
                    headlineContent = { Text("连接状态") },
                    trailingContent = { Text(if (connected) "已连接" else "连不上", color = if (connected) Color.Unspecified else Color.Red) },
                    modifier = Modifier.clickable { syncNow() },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("现在试一条") },
                    trailingContent = { Text(">") },
                    modifier = Modifier.clickable(enabled = !working) {
                        scope.launch {
                            working = true
                            runCatching { CompanionSync.tryNow(context) }
                                .onSuccess { toaster.show("已让阿辞试着发一条", ToastType.Success) }
                                .onFailure { toaster.show("暂时没发出去", ToastType.Error) }
                            working = false
                        }
                    },
                )
            }
        }
    }
    ModelListSheet(state = modelList, onSelect = ::choose)
}
