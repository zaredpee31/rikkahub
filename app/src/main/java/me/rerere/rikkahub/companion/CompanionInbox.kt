package me.rerere.rikkahub.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.isAwaitingToolApproval
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun CompanionInbox(conversationId: Uuid, onSettings: () -> Unit) {
    val context = LocalContext.current
    val chat: ChatService = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val scope = rememberCoroutineScope()
    val storedConversation by chat.getCompanionConversationFlow().collectAsStateWithLifecycle()
    val conversation = storedConversation ?: return
    val job by remember(conversationId) { chat.getGenerationJobStateFlow(conversationId) }.collectAsStateWithLifecycle(initialValue = null)
    val errors by chat.errors.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var ready by remember(conversationId) { mutableStateOf(false) }
    var modelName by remember(conversationId) { mutableStateOf("") }
    var draft by rememberSaveable(conversationId.toString()) { mutableStateOf("") }
    DisposableEffect(conversationId) {
        chat.addConversationReference(conversationId)
        onDispose { chat.removeConversationReference(conversationId) }
    }
    LaunchedEffect(conversationId) {
        try {
            chat.openCompanionConversation(conversationId)
            val activeSettings = settingsStore.awaitLoaded()
            val stored = chat.getCompanionConversation()
            modelName = stored?.config?.chatModelId?.let(activeSettings::findModelById)?.displayName.orEmpty()
            if (CompanionSync.enabled(context)) CompanionSync.sync(context)
            ready = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ready = false
        }
    }
    val selectedMessages = conversation.currentMessages.filter {
        it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT
    }
    LaunchedEffect(selectedMessages.size) {
        if (selectedMessages.isNotEmpty()) list.animateScrollToItem(selectedMessages.lastIndex)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("阿辞的消息")
                        Text(modelName.ifBlank { "未选择模型" }, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { BackButton() },
                actions = { TextButton(onClick = onSettings) { Text("设置") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(12.dp)) {
                    if (conversation.isAwaitingToolApproval) Text("有工具操作等待你确认。")
                    if (job?.isActive == true) Text("正在回复…", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f),
                            maxLines = 5,
                            placeholder = { Text(if (modelName.isBlank()) "先去设置里选个模型" else "发消息…") },
                            enabled = ready && modelName.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Button(enabled = ready && modelName.isNotBlank() && draft.isNotBlank(), onClick = {
                            val text = draft.trim()
                            if (text.isNotEmpty()) {
                                chat.sendCompanionMessage(listOf(UIMessagePart.Text(text)))
                                draft = ""
                                scope.launch { runCatching { CompanionSync.sync(context) } }
                            }
                        }) { Text("发送") }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(selectedMessages, key = { it.id.toString() }) { message ->
                val mine = message.role == MessageRole.USER
                val body = message.toText().ifBlank { if (mine) "非文字消息" else "正在处理…" }
                val bubbles = if (mine) listOf(body) else body.lines().map(String::trim).filter(String::isNotEmpty).ifEmpty { listOf(body) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(if (mine) "我" else "阿辞", style = MaterialTheme.typography.labelSmall)
                    bubbles.forEach { line ->
                        Surface(
                            modifier = Modifier.widthIn(max = 320.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            SelectionContainer {
                                Text(line, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                        }
                    }
                    Text(message.createdAt.toString().substringAfter('T').take(5), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
