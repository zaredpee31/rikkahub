package me.rerere.rikkahub.companion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.isAwaitingToolApproval
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

/** A dedicated inbox UI; replies use the existing assistant and memory pipeline. */
@Composable
fun CompanionPage() {
    val context = LocalContext.current
    var target by remember { mutableStateOf(CompanionSync.target(context)) }
    var settings by rememberSaveable { mutableStateOf(target.isBlank()) }
    if (settings || target.isBlank()) {
        BackHandler(enabled = target.isNotBlank()) { settings = false }
        CompanionSettingsPage(onDone = {
            target = CompanionSync.target(context)
            if (target.isNotBlank()) settings = false
        })
    } else {
        val id = remember(target) { runCatching { Uuid.parse(target) }.getOrNull() }
        if (id == null) {
            TextButton(onClick = { settings = true }) { Text("请重新设置消息会话") }
        } else {
            CompanionConversation(id, onSettings = { settings = true })
        }
    }
}

@Composable
private fun CompanionConversation(id: Uuid, onSettings: () -> Unit) {
    val chat: ChatService = koinInject()
    val nav = LocalNavController.current
    val conversation by remember(id) { chat.getConversationFlow(id) }.collectAsStateWithLifecycle()
    val job by remember(id) { chat.getGenerationJobStateFlow(id) }.collectAsStateWithLifecycle(initialValue = null)
    val queue by remember(id) { chat.getMessageQueueFlow(id) }.collectAsStateWithLifecycle()
    val errors by chat.errors.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var ready by remember(id) { mutableStateOf(false) }
    var problem by remember(id) { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(id.toString()) { mutableStateOf("") }
    DisposableEffect(id) {
        chat.addConversationReference(id)
        onDispose { chat.removeConversationReference(id) }
    }
    LaunchedEffect(id) {
        try {
            chat.openCompanionConversation(id)
            ready = true
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { problem = "消息会话无法打开，请到设置检查。" }
    }
    val messages = conversation.currentMessages.filter {
        it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阿辞的消息") },
                navigationIcon = { BackButton() },
                actions = { TextButton(onClick = onSettings) { Text("设置") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(12.dp)) {
                    if (problem != null) Text(problem!!, color = MaterialTheme.colorScheme.error)
                    if (errors.any { it.conversationId == id }) {
                        Text("这次回复遇到问题，可打开详细会话查看并重试。", color = MaterialTheme.colorScheme.error)
                    }
                    if (conversation.isAwaitingToolApproval) {
                        Text("有工具操作等待你确认。")
                    }
                    if (conversation.isAwaitingToolApproval || errors.any { it.conversationId == id }) {
                        TextButton(onClick = { nav.navigate(Screen.Chat(id.toString())) }) { Text("查看详细会话 / 工具确认") }
                    }
                    if (job?.isActive == true) Text("正在回复…", style = MaterialTheme.typography.labelMedium)
                    if (queue.messages.isNotEmpty()) Text("待发送：${queue.messages.size} 条", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft, onValueChange = { draft = it },
                            modifier = Modifier.weight(1f), maxLines = 5,
                            placeholder = { Text("发消息…") },
                            enabled = ready, shape = RoundedCornerShape(20.dp),
                        )
                        Button(enabled = ready && draft.isNotBlank(), onClick = {
                            try {
                                chat.sendMessage(id, listOf(UIMessagePart.Text(draft.trim())))
                                draft = ""
                            } catch (_: Exception) { problem = "这条没发出去，文字还留着，请重试。" }
                        }) { Text("发送") }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = list, modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (messages.isEmpty()) item {
                Text("这里单独聊天，仍然是同一个助手、同一份记忆。开启主动消息后，早安和不定时消息都会来这里。",
                    style = MaterialTheme.typography.bodyMedium)
            }
            items(messages, key = { it.id.toString() }) { message ->
                val mine = message.role == MessageRole.USER
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Text(if (mine) "我" else "阿辞", style = MaterialTheme.typography.labelSmall)
                    Surface(
                        modifier = Modifier.widthIn(max = 320.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        SelectionContainer {
                            Text(message.toText().ifBlank { if (mine) "非文字消息" else "正在处理…" },
                                modifier = Modifier.padding(12.dp))
                        }
                    }
                    Text(message.createdAt.toString().replace('T', ' ').take(16), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
