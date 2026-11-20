package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.NodeFavoriteTarget
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.service.ApiException
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "ChatVM"

class ChatVM(
    id: String,
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val chatService: ChatService,
    val updateChecker: UpdateChecker,
    private val analytics: FirebaseAnalytics,
    private val filesManager: FilesManager,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {
    private val _conversationId: Uuid = Uuid.parse(id)
    val conversation: StateFlow<Conversation> = chatService.getConversationFlow(_conversationId)
    var chatListInitialized by mutableStateOf(false) // 聊天列表是否已经滚动到底部

    // 聊天输入状态 - 保存在 ViewModel 中避免 TransactionTooLargeException
    val inputState = ChatInputState()

    val voiceSession = VoiceSessionController(viewModelScope, context::getString) {
        chatService.enqueueVoiceMessage(_conversationId, it)
    }

    // 异步任务 (从ChatService获取，响应式)
    val conversationJob: StateFlow<Job?> =
        chatService
            .getGenerationJobStateFlow(_conversationId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val processingStatus: StateFlow<String?> =
        chatService
            .getProcessingStatusFlow(_conversationId)

    val conversationJobs = chatService
        .getConversationJobs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        // 添加对话引用
        chatService.addConversationReference(_conversationId)

        // 初始化对话
        viewModelScope.launch {
            chatService.initializeConversation(_conversationId)
        }

        // 记住对话ID, 方便下次启动恢复
        context.writeStringPreference("lastConversationId", _conversationId.toString())
    }

    override fun onCleared() {
        voiceSession.stop()
        super.onCleared()
        // 移除对话引用
        chatService.removeConversationReference(_conversationId)
    }

    // 用户设置
    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    // 流式输出时会话每个 chunk 都在变，助手和模型只取决于下面这几个字段
    private val configuredConversation = conversation.distinctUntilChanged { old, new ->
        old.assistantId == new.assistantId && old.config == new.config &&
            old.modeInjectionIds == new.modeInjectionIds && old.lorebookIds == new.lorebookIds
    }

    // 会话视角下的助手：会话开始后，模型、思考级别、搜索等以会话上固定的配置为准
    val assistant: StateFlow<Assistant> = combine(settings, configuredConversation) { settings, conversation ->
        settings.getAssistantOf(conversation)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, settings.value.getAssistantOf(conversation.value))

    // 当前模型
    val currentChatModel: StateFlow<Model?> = combine(settings, configuredConversation) { settings, conversation ->
        settings.getChatModelOf(conversation)
    }.stateIn(viewModelScope, SharingStarted.Lazily, settings.value.getChatModelOf(conversation.value))

    // 错误状态
    val errors: StateFlow<List<ChatError>> = chatService.errors

    fun dismissError(id: Uuid) = chatService.dismissError(id)

    fun clearAllErrors() = chatService.clearAllErrors()

    val messageQueue = chatService.getMessageQueueFlow(_conversationId)

    fun removeQueuedMessage(id: Uuid) = chatService.removeQueuedMessage(_conversationId, id)

    fun beginEditQueuedMessage(id: Uuid) = chatService.beginEditQueuedMessage(_conversationId, id)

    fun finishEditQueuedMessage(id: Uuid, parts: List<UIMessagePart>?) =
        chatService.finishEditQueuedMessage(_conversationId, id, parts)

    fun resumeMessageQueue() = chatService.resumeMessageQueue(_conversationId)

    // 生成完成
    val generationDoneFlow: SharedFlow<Uuid> = chatService.generationDoneFlow

    // MCP管理器
    val mcpManager = chatService.mcpManager

    // 更新设置
    fun updateSettings(fn: (Settings) -> Settings): Job {
        return viewModelScope.launch {
            var oldSettings: Settings? = null
            var newSettings: Settings? = null
            settingsStore.update { old ->
                fn(old).also { new ->
                    oldSettings = old
                    newSettings = new
                }
            }
            // 检查用户头像是否有变化，如果有则删除旧头像
            checkUserAvatarDelete(oldSettings ?: return@launch, newSettings ?: return@launch)
        }
    }

    // 检查用户头像删除
    private fun checkUserAvatarDelete(oldSettings: Settings, newSettings: Settings) {
        val oldAvatar = oldSettings.displaySetting.userAvatar
        val newAvatar = newSettings.displaySetting.userAvatar

        if (oldAvatar is Avatar.Image && oldAvatar != newAvatar) {
            filesManager.deleteChatFiles(listOf(oldAvatar.url.toUri()))
        }
    }

    // 聊天页对助手的修改：新会话直接改助手，会话开始后只改当前会话
    fun updateAssistant(assistant: Assistant) {
        viewModelScope.launch {
            chatService.updateChatAssistant(_conversationId) { assistant }
        }
    }

    // 新会话开始前归入抽屉里选中的文件夹
    suspend fun updateDraftFolder(folderId: Uuid?) {
        chatService.updateDraftConversationFolder(_conversationId, folderId)
    }

    // 设置聊天模型
    fun setChatModel(model: Model) {
        viewModelScope.launch {
            chatService.updateChatAssistant(_conversationId) { it.copy(chatModelId = model.id) }
        }
    }

    // Update checker
    val updateState = settingsStore.settingsFlow
        .map { settings ->
            !BuildConfig.IS_PLAY_STORE &&
                !settings.init &&
                settings.displaySetting.updateCheckDisabledUntilEpochMillis <= System.currentTimeMillis()
        }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (enabled) updateChecker.updateState else flowOf(UiState.Loading)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            UiState.Loading,
        )

    /**
     * 处理消息发送
     *
     * @param content 消息内容
     * @param answer 是否触发消息生成，如果为false，则仅添加消息到消息列表中
     */
    fun handleMessageSend(content: List<UIMessagePart>,answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return
        analytics.logEvent("ai_send_message", null)

        chatService.sendMessage(_conversationId, content, answer)
    }

    fun handleMessageEdit(parts: List<UIMessagePart>, messageId: Uuid) {
        if (parts.isEmptyInputMessage()) return
        analytics.logEvent("ai_edit_message", null)

        viewModelScope.launch {
            chatService.editMessage(_conversationId, messageId, parts)
        }
    }

    fun handleCompressContext(additionalPrompt: String, keepRecentMessages: Int): Job {
        return viewModelScope.launch {
            chatService.compressConversation(_conversationId, additionalPrompt, keepRecentMessages).onFailure {
                chatService.addError(it, title = context.getString(R.string.error_title_compress_conversation))
            }
        }
    }

    suspend fun forkMessage(message: UIMessage): Conversation {
        return chatService.forkConversationAtMessage(_conversationId, message.id)
    }

    fun deleteMessage(message: UIMessage) {
        viewModelScope.launch {
            chatService.deleteMessage(_conversationId, message)
        }
    }

    fun showDeleteBlockedWhileGeneratingError() {
        chatService.addError(
            error = IllegalStateException("请先停止生成再删除消息"),
            conversationId = _conversationId,
            title = context.getString(R.string.error_title_operation)
        )
    }

    fun regenerateAtMessage(message: UIMessage) {
        analytics.logEvent("ai_regenerate_at_message", null)
        chatService.regenerateAtMessage(_conversationId, message.id)
    }

    fun handleToolApproval(
        toolCallId: String,
        approved: Boolean,
        reason: String = ""
    ) {
        analytics.logEvent("ai_tool_approval", null)
        chatService.handleToolApproval(_conversationId, toolCallId, approved, reason)
    }

    fun handleToolAnswer(
        toolCallId: String,
        answer: String,
    ) {
        analytics.logEvent("ai_tool_answer", null)
        chatService.handleToolApproval(_conversationId, toolCallId, approved = true, answer = answer)
    }

    fun stopGeneration() {
        viewModelScope.launch {
            chatService.stopGeneration(_conversationId)
        }
    }

    fun updateTitle(title: String) {
        viewModelScope.launch {
            chatService.updateTitle(_conversationId, title)
        }
    }

    fun deleteConversation(conversation: Conversation): Job =
        viewModelScope.launch {
            chatService.deleteConversation(conversation.id)
        }

    fun updatePinnedStatus(conversation: Conversation) {
        viewModelScope.launch {
            chatService.toggleConversationPinned(conversation.id)
        }
    }

    fun moveConversationToAssistant(conversation: Conversation, targetAssistantId: Uuid) {
        viewModelScope.launch {
            chatService.moveConversationToAssistant(conversation.id, targetAssistantId)
            if (conversation.id == _conversationId) {
                settingsStore.selectAssistant(targetAssistantId)
            }
        }
    }

    fun generateTitle(conversation: Conversation, force: Boolean = false) {
        viewModelScope.launch {
            chatService.generateTitle(conversation.id, force)
        }
    }

    fun selectMessageNode(nodeId: Uuid, selectIndex: Int) {
        viewModelScope.launch {
            try {
                chatService.selectMessageNode(_conversationId, nodeId, selectIndex)
            } catch (e: ApiException) {
                // 界面上的分支已经过时（比如刚被清理掉），切换不了就算了
                Log.w(TAG, "selectMessageNode: ${e.message}")
            }
        }
    }

    fun updateConversationSystemPrompt(systemPrompt: String?) {
        viewModelScope.launch {
            chatService.updateConversationSystemPrompt(_conversationId, systemPrompt)
        }
    }

    fun updateWorkspaceCwd(workspaceCwd: String?) {
        viewModelScope.launch {
            chatService.updateWorkspaceCwd(_conversationId, workspaceCwd)
        }
    }

    /** 长按「囤一条」：这条消息的文字（去掉网页内容块，独白在 Reasoning 里不带）进囤粮处待归仓。 */
    fun stashMessage(node: MessageNode, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val msg = node.currentMessage
            val text = me.rerere.rikkahub.webpage.WebPages.strip(
                msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
            )
            val fromAi = msg.role.toString().equals("assistant", ignoreCase = true)
            onDone(
                me.rerere.rikkahub.companion.StashApi.stashMessage(
                    _conversationId.toString(), node.id.toString(), fromAi, text
                )
            )
        }
    }

}
