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

    /** 普通聊天路径用：消息页存档读不出来时不连累普通会话，错误留给消息页自己显示。 */
    suspend fun loadOrNull(): Conversation? = try {
        load()
    } catch (cause: Exception) {
        android.util.Log.e("CompanionRepository", "load companion failed", cause)
        null
    }

    /** 最近一次读取失败的原因，消息页据此提示，而不是当成没有会话去新建。 */
    @Volatile
    var lastLoadError: String? = null
        private set

    suspend fun isCompanionId(id: Uuid): Boolean = loadOrNull()?.id == id

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
        // 只留本应用可读写；之前 setReadable(false, false) 把自己的读取权限也撤了，重启就读不出来
        restrictToOwner(file)
        _conversation.value = conversation
        lastLoadError = null
    }

    /** 读改写在同一把锁里完成，避免和正在落库的生成结果互相覆盖。 */
    suspend fun update(transform: (Conversation) -> Conversation): Conversation = mutex.withLock {
        loadLocked()
        val current = _conversation.value ?: error("消息会话尚未建立")
        val updated = transform(current)
        writeLocked(updated)
        updated
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

    private fun restrictToOwner(target: File) {
        target.setReadable(false, false)
        target.setReadable(true, true)
        target.setWritable(false, false)
        target.setWritable(true, true)
    }

    private fun decode(source: File): CompanionData {
        restrictToOwner(source)
        val text = try {
            source.readText()
        } catch (cause: java.io.IOException) {
            // 读不出来是权限或存储问题，文件本身未必坏，不改名、不覆盖
            lastLoadError = "消息页存档暂时读不出来（${cause.message}），原文件保留未动"
            throw IllegalStateException(lastLoadError, cause)
        }
        return JsonInstant.decodeFromString<CompanionData>(text)
    }

    /** 主文件缺失时，从最近一份能正常解析的 broken 备份恢复；备份文件本身保留。 */
    private fun restoreFromBackup(): CompanionData? {
        val dir = file.parentFile ?: return null
        val backups = dir.listFiles { f -> f.name.startsWith("companion_messages.broken-") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name.removePrefix("companion_messages.broken-").removeSuffix(".json").toLongOrNull() ?: 0L }
            .orEmpty()
        for (backup in backups) {
            val data = try {
                decode(backup)
            } catch (cause: Exception) {
                android.util.Log.w("CompanionRepository", "backup ${backup.name} unusable", cause)
                continue
            }
            file.parentFile?.mkdirs()
            temp.writeText(JsonInstant.encodeToString(data))
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            restrictToOwner(file)
            android.util.Log.i("CompanionRepository", "restored companion messages from ${backup.name}")
            return data
        }
        return null
    }

    private fun readFile(): CompanionData? {
        if (!file.isFile) return restoreFromBackup().also { lastLoadError = null }
        val data = try {
            decode(file)
        } catch (cause: IllegalStateException) {
            throw cause
        } catch (cause: Exception) {
            // 真正解析失败才备份改名；先看旧备份能不能用
            val broken = File(file.parentFile, "companion_messages.broken-${System.currentTimeMillis()}.json")
            if (!file.renameTo(broken)) {
                lastLoadError = "消息页存档解析失败，且无法备份原文件：${cause.message}"
                throw IllegalStateException(lastLoadError, cause)
            }
            lastLoadError = "消息页存档解析失败，原文件已备份为 ${broken.name}"
            throw IllegalStateException(lastLoadError, cause)
        }
        lastLoadError = null
        return data
    }

    @Serializable
    private data class CompanionData(val conversation: Conversation, val consolidatedAt: Long? = null)
}
