package me.rerere.rikkahub.ui.pages.assistant.detail

import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.MemoryConsolidationScheduler
import me.rerere.rikkahub.data.ai.MemoryConsolidationStatus
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.skills.SkillManager
import me.rerere.rikkahub.data.skills.SkillMetadata
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import kotlin.uuid.Uuid

private const val TAG = "AssistantDetailVM"

class AssistantDetailVM(
    private val id: String,
    private val settingsStore: SettingsStore,
    private val memoryRepository: MemoryRepository,
    private val memoryNodeRepository: me.rerere.rikkahub.data.repository.MemoryNodeRepository,
    private val memoryIndex: me.rerere.rikkahub.data.repository.MemoryIndex,
    private val memoryConsolidationScheduler: MemoryConsolidationScheduler,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {
    private val assistantId = Uuid.parse(id)

    private val _skills = MutableStateFlow<List<SkillMetadata>>(emptyList())
    val skills = _skills.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _skills.value = skillManager.listSkills()
        }
    }

    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    val mcpServerConfigs = settingsStore
        .settingsFlow.map { settings ->
            settings.mcpServers
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val assistant: StateFlow<Assistant> = settingsStore
        .settingsFlow
        .map { settings ->
            settings.assistants.find { it.id == assistantId } ?: Assistant()
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = Assistant()
        )

    val memories = assistant
        .flatMapLatest { currentAssistant ->
            memoryRepository.getFilesFlow(memoryIdOf(currentAssistant))
        }
        .stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val memoryNodes = assistant
        .flatMapLatest { currentAssistant -> memoryNodeRepository.summariesFlow(memoryIdOf(currentAssistant)) }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList())

    val memoryPlates = assistant
        .flatMapLatest { currentAssistant -> memoryNodeRepository.platesFlow(memoryIdOf(currentAssistant)) }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList())

    val embeddingKey: StateFlow<String?> = settingsStore.settingsFlow
        .map { memoryIndex.currentKey(it) }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = null)

    val memoryConsolidation: StateFlow<MemoryConsolidationStatus> = assistant
        .flatMapLatest { currentAssistant ->
            memoryConsolidationScheduler.statusOf(memoryIdOf(currentAssistant))
        }
        .stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = MemoryConsolidationStatus()
        )

    val providers = settingsStore
        .settingsFlow
        .map { settings ->
            settings.providers
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val tags = settingsStore
        .settingsFlow
        .map { settings ->
            settings.assistantTags
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val workspaces: StateFlow<List<WorkspaceEntity>> = workspaceRepository
        .listFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    fun updateTags(tagIds: List<Uuid>, tags: List<Tag>) {
        viewModelScope.launch {
            settingsStore.update { it.copy(assistantTags = tags) }
            update(
                assistant.value.copy(
                    tags = tagIds.toList()
                )
            )
            Log.d(TAG, "updateTags: ${tagIds.joinToString(",")}")
            cleanupUnusedTags()
        }
    }

    fun cleanupUnusedTags() {
        viewModelScope.launch {
            settingsStore.update { settings ->
                val validTagIds = settings.assistantTags.map { it.id }.toSet()

                // 清理 assistant 中的无效 tag id
                val cleanedAssistants = settings.assistants.map { assistant ->
                    val validTags = assistant.tags.filter { tagId ->
                        validTagIds.contains(tagId)
                    }
                    if (validTags.size != assistant.tags.size) {
                        assistant.copy(tags = validTags)
                    } else {
                        assistant
                    }
                }

                // 获取清理后的 assistant 中使用的 tag id
                val usedTagIds = cleanedAssistants.flatMap { it.tags }.toSet()

                // 清理未使用的 tags
                val cleanedTags = settings.assistantTags.filter { tag ->
                    usedTagIds.contains(tag.id)
                }

                // 检查是否需要更新
                val needUpdateAssistants = cleanedAssistants != settings.assistants
                val needUpdateTags = cleanedTags.size != settings.assistantTags.size

                if (needUpdateAssistants || needUpdateTags) {
                    settings.copy(
                        assistants = cleanedAssistants,
                        assistantTags = cleanedTags
                    )
                } else {
                    settings
                }
            }
        }
    }

    fun update(assistant: Assistant) {
        viewModelScope.launch {
            var replaced: Assistant? = null
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map {
                        if (it.id == assistant.id) {
                            replaced = it
                            assistant
                        } else {
                            it
                        }
                    })
            }
            replaced?.let { old ->
                checkAvatarDelete(old = old, new = assistant) // 删除旧头像
                checkBackgroundDelete(old = old, new = assistant) // 删除旧背景
            }
        }
    }

    // 助手还没加载出来时 assistant 是个占位对象，记忆库要按页面拿到的 assistantId 来定位
    private fun memoryIdOf(assistant: Assistant) = if (assistant.useGlobalMemory) {
        MemoryRepository.GLOBAL_MEMORY_ID
    } else {
        assistantId.toString()
    }

    /** 当前助手读写的记忆库 */
    val memoryId: String get() = memoryIdOf(assistant.value)

    /** 整理所有待整理的对话，并把整个记忆库清理一遍 */
    fun organizeMemory() {
        memoryConsolidationScheduler.organize(memoryId)
    }

    fun deleteMemory(memory: MemoryFile) {
        viewModelScope.launch {
            memoryRepository.deleteFile(memoryIdOf(assistant.value), memory.path)
        }
    }

    private val _memoryError = MutableStateFlow<String?>(null)

    /** 记忆房间里最近一次没做成的操作，界面弹出来给用户看 */
    val memoryError: StateFlow<String?> = _memoryError.asStateFlow()

    fun dismissMemoryError() {
        _memoryError.value = null
    }

    /** [onDone] 在主线程回调，true 表示做成了；界面靠它决定关不关对话框 */
    private fun launchMemory(onDone: (Boolean) -> Unit = {}, action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                action()
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "memory node action failed", e)
                _memoryError.value = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                false
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    /** [node] 为 null 时新增 */
    fun saveMemoryNode(
        node: me.rerere.rikkahub.data.db.dao.MemoryNodeSummary?,
        room: String,
        content: String,
        onDone: (Boolean) -> Unit = {},
    ) =
        launchMemory(onDone) {
            if (node == null) {
                memoryNodeRepository.add(memoryId, room, content)
            } else {
                val found = memoryNodeRepository.edit(
                    memoryId = memoryId,
                    id = node.id,
                    room = room,
                    content = content,
                    importance = node.importance,
                    mood = node.mood,
                    keywords = node.keywords,
                    happenedAt = node.happenedAt,
                )
                check(found) { "这条记忆已经不在了，可能刚被删掉或被同步替换" }
            }
        }

    fun deleteMemoryNode(node: me.rerere.rikkahub.data.db.dao.MemoryNodeSummary) =
        launchMemory { memoryNodeRepository.delete(memoryId, node.id) }

    fun saveMemoryPlate(kind: String, content: String, onDone: (Boolean) -> Unit = {}) =
        launchMemory(onDone) { memoryNodeRepository.editPlate(memoryId, kind, content) }

    /** 粘贴的旧记忆按列表项、空行分段拆开，每段一条 */
    fun importMemoryText(room: String, text: String, onDone: (Boolean) -> Unit = {}) = launchMemory(onDone) {
        memoryNodeRepository.addAll(memoryId, room, me.rerere.rikkahub.data.repository.LegacyMemoryParser.splitBlocks(text))
    }

    fun reindexMemory() = launchMemory { memoryIndex.index(memoryId) }

    fun checkAvatarDelete(old: Assistant, new: Assistant) {
        if (old.avatar is Avatar.Image && old.avatar != new.avatar) {
            filesManager.deleteChatFiles(listOf(old.avatar.url.toUri()))
        }
    }

    fun checkBackgroundDelete(old: Assistant, new: Assistant) {
        val oldBackground = old.background
        val newBackground = new.background

        if (oldBackground != null && oldBackground != newBackground) {
            try {
                val oldUri = oldBackground.toUri()
                if (oldUri.scheme == "content" || oldUri.scheme == "file") {
                    filesManager.deleteChatFiles(listOf(oldUri))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete background file: $oldBackground", e)
            }
        }
    }
}
