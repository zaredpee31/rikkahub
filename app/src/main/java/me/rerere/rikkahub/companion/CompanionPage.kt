package me.rerere.rikkahub.companion

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

/** Functional settings/entry only; message rendering is the existing ChatPage until visual work. */
@Composable
fun CompanionSettingsPage(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val nav = LocalNavController.current
    val repository: ConversationRepository = koinInject()
    val chat: ChatService = koinInject()
    val scope = rememberCoroutineScope()
    val assistant = settings.getCurrentAssistant()
    var recent by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var target by remember { mutableStateOf(CompanionSync.target(context)) }
    var enabled by remember { mutableStateOf(CompanionSync.enabled(context)) }
    var morning by remember { mutableStateOf(CompanionSync.morning(context)) }
    var proactive by remember { mutableStateOf(CompanionSync.proactive(context)) }
    var status by remember { mutableStateOf(CompanionSync.status(context)) }
    var working by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) status = "通知权限未开启：仍可收进会话，但通知栏不会显示"
    }
    LaunchedEffect(assistant.id) { recent = repository.getRecentConversations(assistant.id, 30) }
    fun synchronize() {
        scope.launch {
            working = true
            try { CompanionSync.sync(context); status = CompanionSync.status(context)
            } catch (_: Exception) { status = CompanionSync.status(context)
            } finally { working = false }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("阿辞的消息") }, navigationIcon = { TextButton(onClick = onDone) { Text("返回消息") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前助手：${assistant.name.ifBlank { "未命名" }}")
                    Text("为当前助手建立专属消息会话。主动消息只来这里，其他窗口不接收；回复沿用同一助手、记忆与工具。")
                    Text("开启后，将绑定助手的人设、已开启的记忆、近期聊天文字以及所选模型接口凭据，通过 HTTPS 同步到你的小家服务器。服务器保存的是只读上下文，不会回写覆盖手机记忆。")
                    Text("手机通过后台连接收消息；强制停止、省电限制、无网络或关闭通知权限会影响到达时间。服务器保留未收消息，联网后补收。")
                }
            }
            item {
                Text("绑定会话：" + (recent.firstOrNull { it.id.toString() == target }?.title ?: if (target.isBlank()) "尚未选择" else target))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("周一至周五 08:00 早安 · 西安天气")
                    Switch(checked = morning, onCheckedChange = {
                        morning = it; CompanionSync.options(context, morning, proactive)
                        if (enabled) synchronize()
                    })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("不定时主动聊天（可选择不发）")
                    Switch(checked = proactive, onCheckedChange = {
                        proactive = it; CompanionSync.options(context, morning, proactive)
                        if (enabled) synchronize()
                    })
                }
                Text("工作日 09:00～23:00，周末 11:00～23:00；间隔随机，不规定每天几条，也可能整天不发。未回复时偶尔补一句，不连续追问、不催睡觉。")
            }
            item {
                Button(enabled = !working && target.isNotBlank(), onClick = {
                    scope.launch {
                        working = true
                        try {
                            if (enabled) {
                                // Tell server first. If offline, retain receiver retry via WorkManager.
                                CompanionSync.setEnabled(context, false)
                                enabled = false
                                context.stopService(Intent(context, CompanionReceiverService::class.java))
                                CompanionSync.sync(context)
                            } else {
                                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                CompanionSync.setEnabled(context, true)
                                CompanionSync.sync(context)
                                ContextCompat.startForegroundService(context, Intent(context, CompanionReceiverService::class.java))
                                enabled = true
                            }
                            status = CompanionSync.status(context)
                        } catch (e: Exception) {
                            if (!enabled && CompanionSync.enabled(context)) CompanionSync.setEnabled(context, false)
                            status = CompanionSync.status(context)
                        } finally { working = false }
                    }
                }) { Text(if (working) "正在连接…" else if (enabled) "关闭主动消息" else "启用并同步到小家服务器") }
                OutlinedButton(enabled = !working, onClick = { synchronize() }) { Text("检查连接 / 同步最新上下文") }
                if (target.isNotBlank()) Button(onClick = { onDone() }) { Text("打开消息会话") }
                Text(status)
                Text("当前服务器主动生成仅支持文字，尚未接入 MCP 工具调用；在会话中回复仍使用原有工具。")
            }
            item {
                HorizontalDivider()
                Text("专属消息会话（当前助手）")
                OutlinedButton(enabled = !enabled && !working && !CompanionSync.needsDisable(context), onClick = {
                    scope.launch {
                        working = true
                        try {
                            val id = Uuid.random()
                            chat.createCompanionConversation(id, assistant.id)
                            CompanionSync.bind(context, id)
                            target = id.toString()
                            recent = repository.getRecentConversations(assistant.id, 30)
                            status = "消息会话已建立，可以启用接收"
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e
                        } catch (_: Exception) { status = "创建失败，请检查助手设置，稍后重试"
                        } finally { working = false }
                    }
                }) { Text("新建消息会话，同一个助手") }
                if (enabled) Text("先关闭主动消息，再更换接收会话。")
            }
        }
    }
}
