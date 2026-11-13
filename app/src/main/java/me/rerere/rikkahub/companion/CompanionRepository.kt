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
    val conversation: StateFlow<Conversation?> = _conversation.asStateFlow()

    suspend fun load(): Conversation? = mutex.withLock {
        _conversation.value ?: readFile().also { _conversation.value = it }
    }

    suspend fun isCompanionId(id: Uuid): Boolean = load()?.id == id

    suspend fun save(conversation: Conversation) = mutex.withLock {
        file.parentFile?.mkdirs()
        temp.writeText(JsonInstant.encodeToString(CompanionData(conversation)))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        file.setReadable(false, false)
        file.setWritable(true, true)
        _conversation.value = conversation
    }

    suspend fun clear() = mutex.withLock {
        file.delete()
        temp.delete()
        _conversation.value = null
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

    private fun readFile(): Conversation? {
        if (!file.isFile) return null
        return try {
            JsonInstant.decodeFromString<CompanionData>(file.readText()).conversation
        } catch (error: Exception) {
            val broken = File(file.parentFile, "companion_messages.broken-${System.currentTimeMillis()}.json")
            check(file.renameTo(broken)) { "消息页文件损坏，且无法备份原文件：${error.message}" }
            error("消息页文件损坏，原文件已备份为 ${broken.name}，不会覆盖消息", error)
        }
    }

    @Serializable
    private data class CompanionData(val conversation: Conversation)
}
