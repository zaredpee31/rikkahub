package me.rerere.rikkahub.companion

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.uuid.Uuid

/** The companion inbox is outside Room's ordinary Conversation tables. */
class CompanionRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "companion_messages.json")
    private val temp = File(context.applicationContext.filesDir, "companion_messages.json.tmp")
    private val mutex = Mutex()
    private val _conversation = MutableStateFlow<Conversation?>(null)
    private var cachedConsolidatedAt: Long? = null
    val conversation: StateFlow<Conversation?> = _conversation.asStateFlow()

    suspend fun load(): Conversation? = mutex.withLock {
        loadLocked()
        _conversation.value
    }

    suspend fun isCompanionId(id: Uuid): Boolean = load()?.id == id

    suspend fun save(conversation: Conversation) = mutex.withLock { writeLocked(conversation) }

    suspend fun clear() = mutex.withLock {
        file.delete()
        temp.delete()
        _conversation.value = null
        cachedConsolidatedAt = null
    }

    suspend fun getConsolidatedAt(id: Uuid): Long? = mutex.withLock {
        loadLocked()
        if (_conversation.value?.id == id) cachedConsolidatedAt else null
    }

    suspend fun hasConsolidationMark(id: Uuid): Boolean = mutex.withLock {
        loadLocked()
        _conversation.value?.id == id && cachedConsolidatedAt != null
    }

    suspend fun advanceConsolidatedAt(id: Uuid, consolidatedAt: Long): Boolean = mutex.withLock {
        loadLocked()
        val current = _conversation.value ?: return@withLock false
        if (current.id != id) return@withLock false
        if (cachedConsolidatedAt != null && consolidatedAt <= cachedConsolidatedAt!!) return@withLock true
        cachedConsolidatedAt = consolidatedAt
        writeLocked(current)
        true
    }

    private suspend fun loadLocked() {
        if (_conversation.value == null) {
            val data = readFile()
            _conversation.value = data?.conversation
            cachedConsolidatedAt = data?.consolidatedAt
        }
    }

    private suspend fun writeLocked(conversation: Conversation) {
        file.parentFile?.mkdirs()
        temp.writeText(JsonInstant.encodeToString(CompanionData(conversation, cachedConsolidatedAt)))
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        file.setReadable(false, false)
        file.setWritable(true, true)
        _conversation.value = conversation
    }

    suspend fun update(transform: (Conversation) -> Conversation): Conversation {
        val current = load() ?: error("消息会话尚未建立")
        val updated = transform(current)
        save(updated)
        return updated
    }

    suspend fun updateModel(modelId: Uuid) {
        update { current -> current.copy(config = current.config?.copy(chatModelId = modelId) ?: error("消息会话配置缺失")) }
    }

    suspend fun migrate(legacy: Conversation): Conversation {
        val current = load()
        if (current != null) return current
        save(legacy)
        check(load()?.id == legacy.id) { "消息页迁移校验失败，旧会话不会删除" }
        return legacy
    }

    private fun readFile(): CompanionData? {
        if (!file.isFile) return null
        return try {
            JsonInstant.decodeFromString<CompanionData>(file.readText())
        } catch (cause: Exception) {
            val broken = File(file.parentFile, "companion_messages.broken-${System.currentTimeMillis()}.json")
            check(file.renameTo(broken)) { "消息页文件损坏，且无法备份原文件：${cause.message}" }
            throw IllegalStateException("消息页文件损坏，原文件已备份为 ${broken.name}，不会覆盖消息", cause)
        }
    }

    @Serializable
    private data class CompanionData(val conversation: Conversation, val consolidatedAt: Long? = null)
}
