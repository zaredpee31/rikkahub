package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.MemoryConsolidationScheduler
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.utils.UiState
import androidx.core.net.toUri
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.memoryId
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

class ChatDrawerVM(
    private val context: Application,
    private val settingsStore: SettingsStore,
    conversationRepo: ConversationRepository,
    private val folderRepo: FolderRepository,
    private val chatService: ChatService,
    private val memoryConsolidationScheduler: MemoryConsolidationScheduler,
    private val savedStateHandle: SavedStateHandle,
    val updateChecker: UpdateChecker,
    private val filesManager: FilesManager,
) : ViewModel() {

    private val assistantIdFlow = settingsStore.settingsFlow
        .map { it.assistantId }
        .distinctUntilChanged()

    private val sortOrderFlow = settingsStore.settingsFlow
        .map { it.displaySetting.conversationSortOrder }
        .distinctUntilChanged()

    // 当前选中的文件夹筛选，null 表示「未归类」视图
    private val _selectedFolderId = MutableStateFlow<Uuid?>(null)
    val selectedFolderId: StateFlow<Uuid?> = _selectedFolderId.asStateFlow()

    // 当前助手的文件夹列表（Room Flow，增删改自动刷新）
    val folders: StateFlow<List<Folder>> = assistantIdFlow
        .flatMapLatest { folderRepo.getFoldersOfAssistant(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前助手的记忆库是否正在后台整理；没开记忆的助手即使共用的全局记忆库在整理也不显示
    val memoryDreaming: StateFlow<Boolean> = settingsStore.settingsFlow
        .map { settings -> settings.getCurrentAssistant().takeIf { it.enableMemory }?.memoryId }
        .distinctUntilChanged()
        .flatMapLatest { memoryId ->
            if (memoryId == null) flowOf(false) else memoryConsolidationScheduler.statusOf(memoryId).map { it.running }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val conversations: Flow<PagingData<ConversationListItem>> =
        combine(assistantIdFlow, _selectedFolderId, sortOrderFlow) { assistantId, folderId, sortOrder ->
            Triple(assistantId, folderId, sortOrder)
        }
            .flatMapLatest { (assistantId, folderId, sortOrder) ->
                val paging = if (folderId == null) {
                    conversationRepo.getUnfiledConversationsOfAssistantPaging(assistantId, sortOrder)
                } else {
                    conversationRepo.getConversationsOfFolderPaging(folderId, sortOrder)
                }
                paging.map { pagingData ->
                    pagingData
                        .map { ConversationListItem.Item(it) }
                        .insertSeparators<ConversationListItem.Item, ConversationListItem> { before, after ->
                            when {
                                after == null -> null
                                after.conversation.isPinned -> {
                                    if (before == null) ConversationListItem.PinnedHeader else null
                                }

                                else -> {
                                    // 日期分组跟随排序依据：按创建时间排序时以创建日期分组
                                    val afterDate = after.conversation.sortDate(sortOrder)
                                    val sameGroup = before != null &&
                                        !before.conversation.isPinned &&
                                        before.conversation.sortDate(sortOrder) == afterDate
                                    if (sameGroup) {
                                        null
                                    } else {
                                        ConversationListItem.DateHeader(
                                            date = afterDate,
                                            label = getDateLabel(afterDate),
                                            sortOrder = sortOrder,
                                        )
                                    }
                                }
                            }
                        }
                }
            }
            .cachedIn(viewModelScope)

    // 正在生成回复的会话，用来在列表里标出加载中的那条
    val conversationJobs: StateFlow<Map<Uuid, Job?>> = chatService
        .getConversationJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 更新检查。以前在 ChatVM 里，抽屉挂到消息页上以后没有 ChatVM，挪到这里
    val updateState: StateFlow<UiState<me.rerere.rikkahub.utils.UpdateInfo>> = settingsStore.settingsFlow
        .map { settings ->
            !me.rerere.rikkahub.BuildConfig.IS_PLAY_STORE &&
                !settings.init &&
                settings.displaySetting.updateCheckDisabledUntilEpochMillis <= System.currentTimeMillis()
        }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (enabled) updateChecker.updateState else flowOf(UiState.Loading)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), UiState.Loading)

    val scrollIndex: Int get() = savedStateHandle["scrollIndex"] ?: 0
    val scrollOffset: Int get() = savedStateHandle["scrollOffset"] ?: 0

    init {
        // 助手切换时重置文件夹筛选，回到「聊天」视图，
        // 避免继续显示上一个助手文件夹内的会话（文件夹是助手内分组）
        viewModelScope.launch {
            assistantIdFlow.collect {
                _selectedFolderId.value = null
            }
        }
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        savedStateHandle["scrollIndex"] = index
        savedStateHandle["scrollOffset"] = offset
    }

    fun selectFolder(folderId: Uuid?) {
        _selectedFolderId.value = folderId
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val assistantId = assistantIdFlow.first()
            folderRepo.createFolder(assistantId, trimmed)
        }
    }

    fun renameFolder(folderId: Uuid, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            folderRepo.renameFolder(folderId, trimmed)
        }
    }

    /**
     * 删除文件夹。若文件夹内有正在生成回复的会话，拒绝删除并返回 false（UI 层据此提示用户）。
     */
    fun deleteFolder(folderId: Uuid): Boolean {
        if (chatService.hasGeneratingConversationInFolder(folderId)) {
            return false
        }
        viewModelScope.launch {
            // 经 ChatService 删除：会同步清空活跃 session 内存态的 folderId，避免整对象保存写回已删文件夹
            chatService.deleteFolder(folderId)
            if (_selectedFolderId.value == folderId) {
                _selectedFolderId.value = null
            }
        }
        return true
    }

    // 抽屉里改设置（昵称、头像、排序）。头像换了要删掉旧文件，和 ChatVM.updateSettings 一样
    fun updateSettings(fn: (me.rerere.rikkahub.data.datastore.Settings) -> me.rerere.rikkahub.data.datastore.Settings): Job =
        viewModelScope.launch {
            var oldAvatar: Avatar? = null
            var newAvatar: Avatar? = null
            settingsStore.update { old ->
                fn(old).also { new ->
                    oldAvatar = old.displaySetting.userAvatar
                    newAvatar = new.displaySetting.userAvatar
                }
            }
            val previous = oldAvatar
            if (previous is Avatar.Image && previous != newAvatar) {
                filesManager.deleteChatFiles(listOf(previous.url.toUri()))
            }
        }

    fun generateTitle(conversationId: Uuid, force: Boolean = false) {
        viewModelScope.launch {
            chatService.generateTitle(conversationId, force)
        }
    }

    fun deleteConversation(conversationId: Uuid) {
        viewModelScope.launch {
            chatService.deleteConversation(conversationId)
        }
    }

    fun togglePinned(conversationId: Uuid) {
        viewModelScope.launch {
            chatService.toggleConversationPinned(conversationId)
        }
    }

    fun moveConversationToAssistant(conversationId: Uuid, targetAssistantId: Uuid) {
        viewModelScope.launch {
            chatService.moveConversationToAssistant(conversationId, targetAssistantId)
            settingsStore.selectAssistant(targetAssistantId)
        }
    }

    fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        viewModelScope.launch {
            // 经 ChatService 移动：活跃会话会先同步内存态，避免后续整对象保存覆盖 folder_id
            chatService.moveConversationToFolder(conversationId, folderId)
        }
    }

    private fun Conversation.sortDate(sortOrder: ConversationSortOrder): LocalDate {
        val instant = when (sortOrder) {
            ConversationSortOrder.UPDATE_TIME -> updateAt
            ConversationSortOrder.CREATE_TIME -> createAt
        }
        return instant.atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun getDateLabel(date: LocalDate): String {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        return when (date) {
            today -> context.getString(R.string.chat_page_today)
            yesterday -> context.getString(R.string.chat_page_yesterday)
            else -> date.toLocalString(date.year != today.year)
        }
    }
}
