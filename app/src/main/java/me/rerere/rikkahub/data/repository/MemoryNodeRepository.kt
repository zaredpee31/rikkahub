package me.rerere.rikkahub.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.MemoryImportDAO
import me.rerere.rikkahub.data.db.dao.MemoryNodeDAO
import me.rerere.rikkahub.data.db.dao.MemoryNodeSummary
import me.rerere.rikkahub.data.db.dao.MemoryPlateDAO
import me.rerere.rikkahub.data.db.entity.MemoryImportEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryPlateEntity
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.MemoryPlateKind
import me.rerere.rikkahub.data.model.MemoryRoom
import me.rerere.rikkahub.data.model.MemorySource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "MemoryNodeRepository"
private const val SQL_CHUNK = 500

/**
 * 记忆节点和门牌。
 *
 * 旧 Markdown 记忆不动，节点从它派生（[MemorySource.LEGACY]）：
 * - 第一次导入某个记忆库前，把它的全部 Markdown 原文另存一份 JSON 到 `files/memory_backups/`；
 * - 原文里删掉或改掉的那一块，对应的派生节点（连同向量）一起删，改后的内容作为新节点导入；
 * - 用户在节点界面删掉的派生节点，[MemoryImportEntity] 记着它导入过，原文还在也不会再导回来；
 * - 用户在节点界面改过的派生节点变成 [MemorySource.MANUAL]，换一个新来源键，此后不再跟着原文件走。
 *
 * profile 整份进用户门牌，不截断；用户手动改过门牌后，profile 的变化不再覆盖门牌。
 * preferences 不进节点也不进门牌，保持原样常驻。
 */
class MemoryNodeRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val memoryRepository: MemoryRepository,
    private val nodeDao: MemoryNodeDAO,
    private val plateDao: MemoryPlateDAO,
    private val importDao: MemoryImportDAO,
) {
    // 同步旧记忆和界面里的增删改不能交错，否则刚删的节点可能被同步时写回去
    private val lock = Mutex()
    private val started = AtomicBoolean(false)

    /** 启动时同步所有记忆库，之后 Markdown 一有改动就同步对应的库 */
    fun start(scope: CoroutineScope) {
        if (started.getAndSet(true)) return
        scope.launch(Dispatchers.IO) {
            memoryRepository.changes.collect { memoryId ->
                runCatching { reconcile(memoryId) }
                    .onFailure { Log.e(TAG, "reconcile $memoryId failed", it) }
            }
        }
        scope.launch(Dispatchers.IO) {
            val ids = runCatching { memoryRepository.memoryIds() }
                .onFailure { Log.e(TAG, "list memory ids failed", it) }
                .getOrDefault(emptyList())
            ids.forEach { memoryId ->
                runCatching { reconcile(memoryId) }
                    .onFailure { Log.e(TAG, "reconcile $memoryId failed", it) }
            }
        }
    }

    fun summariesFlow(memoryId: String): Flow<List<MemoryNodeSummary>> = nodeDao.getSummariesFlow(memoryId)

    suspend fun summaries(memoryId: String): List<MemoryNodeSummary> = nodeDao.getSummaries(memoryId)

    suspend fun getNode(memoryId: String, id: Long): MemoryNodeEntity? = nodeDao.getNode(memoryId, id)

    fun platesFlow(memoryId: String): Flow<List<MemoryPlateEntity>> = plateDao.getPlatesFlow(memoryId)

    suspend fun plates(memoryId: String): List<MemoryPlateEntity> = plateDao.getPlates(memoryId)

    /**
     * 新增节点。[origin] 为 null 时生成一个手动来源键；给了 [origin] 而它已经存在时不重复写，返回 null。
     */
    suspend fun add(
        memoryId: String,
        room: String,
        content: String,
        importance: Float = 0.5f,
        mood: String = "",
        keywords: String = "",
        happenedAt: Long = 0,
        source: String = MemorySource.MANUAL,
        origin: String? = null,
    ): Long? {
        val text = content.trim()
        require(MemoryRoom.isValid(room)) { "Unknown room \"$room\". Use one of ${MemoryRoom.all}." }
        require(text.isNotEmpty()) { "Content must not be empty." }
        require(origin == null || origin.isNotBlank()) { "Origin must not be blank." }
        val now = System.currentTimeMillis()
        val node = MemoryNodeEntity(
            assistantId = memoryId,
            room = room,
            content = text,
            importance = importance.coerceIn(0f, 1f),
            mood = mood.trim(),
            keywords = keywords.trim(),
            happenedAt = happenedAt.coerceAtLeast(0),
            createdAt = now,
            updatedAt = now,
            origin = origin ?: newManualOrigin(),
            source = source,
        )
        return lock.withLock {
            nodeDao.insertIgnoring(listOf(node)).first().takeIf { it > 0 }
        }
    }

    /** 改节点。内容变了才加版本号并清掉旧向量。返回 false 表示节点不存在 */
    suspend fun edit(
        memoryId: String,
        id: Long,
        room: String,
        content: String,
        importance: Float,
        mood: String,
        keywords: String,
        happenedAt: Long,
    ): Boolean {
        val text = content.trim()
        require(MemoryRoom.isValid(room)) { "Unknown room \"$room\". Use one of ${MemoryRoom.all}." }
        require(text.isNotEmpty()) { "Content must not be empty." }
        return lock.withLock {
            database.withTransaction {
                val node = nodeDao.getNode(memoryId, id) ?: return@withTransaction false
                val changed = node.content != text
                nodeDao.updateContent(
                    assistantId = memoryId,
                    id = id,
                    room = room,
                    content = text,
                    importance = importance.coerceIn(0f, 1f),
                    mood = mood.trim(),
                    keywords = keywords.trim(),
                    happenedAt = happenedAt.coerceAtLeast(0),
                    contentVersion = if (changed) node.contentVersion + 1 else node.contentVersion,
                    updatedAt = System.currentTimeMillis(),
                    clearEmbedding = changed,
                )
                if (node.source == MemorySource.LEGACY) {
                    nodeDao.detach(memoryId, id, MemorySource.MANUAL, newManualOrigin())
                }
                true
            }
        }
    }

    /** 删节点。导入记录保留，派生自旧记忆的也不会再导回来 */
    suspend fun delete(memoryId: String, id: Long): Boolean = lock.withLock {
        nodeDao.delete(memoryId, id) > 0
    }

    suspend fun editPlate(memoryId: String, kind: String, content: String) {
        require(kind in MemoryPlateKind.all) { "Unknown plate \"$kind\"." }
        lock.withLock {
            plateDao.upsert(
                MemoryPlateEntity(
                    assistantId = memoryId,
                    kind = kind,
                    content = content.trim(),
                    updatedAt = System.currentTimeMillis(),
                    source = MemorySource.MANUAL,
                )
            )
        }
    }

    /** 删掉一个记忆库的节点、门牌和导入记录。只在这个库确实不再有人用时调用，别对 `__global__` 调 */
    suspend fun deleteScope(memoryId: String) = lock.withLock {
        database.withTransaction {
            nodeDao.deleteOfAssistant(memoryId)
            plateDao.deleteOfAssistant(memoryId)
            importDao.deleteOfAssistant(memoryId)
        }
    }

    /**
     * 复制一个记忆库的节点、门牌和导入记录。要在复制 Markdown 之前调用：
     * 先有导入记录，复制过去的原文就不会把源库里已经删掉的节点导回来。
     */
    suspend fun copyScope(fromMemoryId: String, toMemoryId: String) = lock.withLock {
        database.withTransaction {
            val now = System.currentTimeMillis()
            nodeDao.getAll(fromMemoryId).chunked(SQL_CHUNK).forEach { chunk ->
                nodeDao.insertIgnoring(chunk.map { it.copy(id = 0, assistantId = toMemoryId, updatedAt = now) })
            }
            plateDao.getPlates(fromMemoryId).forEach { plateDao.upsert(it.copy(assistantId = toMemoryId)) }
            importDao.insertIgnoring(importDao.originsOf(fromMemoryId).map { MemoryImportEntity(toMemoryId, it) })
        }
    }

    /** 让一个记忆库的派生节点和用户门牌跟上它当前的 Markdown */
    suspend fun reconcile(memoryId: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val files = memoryRepository.getFiles(memoryId)
            val blocks = files.flatMap { LegacyMemoryParser.parse(it) }
            val wanted = blocks.mapTo(HashSet()) { it.origin }
            val imported = importDao.originsOf(memoryId).toHashSet()
            // 先备份后导入；备份写不出来就不导入，下次再试
            if (files.isNotEmpty() && !hasBackup(memoryId)) backup(memoryId, files)
            val now = System.currentTimeMillis()
            database.withTransaction {
                val gone = nodeDao.getLegacy(memoryId).filter { it.origin !in wanted }
                gone.map { it.id }.chunked(SQL_CHUNK).forEach { nodeDao.deleteIds(memoryId, it) }
                // 原文里已经没有的块，导入记录也清掉：以后同样的话再写回原文，应当重新导入
                imported.filter { it.startsWith("legacy:") && it !in wanted }
                    .chunked(SQL_CHUNK)
                    .forEach { importDao.deleteOrigins(memoryId, it) }

                val fresh = blocks.filter { it.origin !in imported }
                fresh.chunked(SQL_CHUNK).forEach { chunk ->
                    nodeDao.insertIgnoring(chunk.map { it.toNode(memoryId, now) })
                    importDao.insertIgnoring(chunk.map { MemoryImportEntity(memoryId, it.origin) })
                }
                syncUserPlate(memoryId, files.firstOrNull { it.path == MemoryFile.PROFILE_PATH }, now)
            }
        }
    }

    private suspend fun syncUserPlate(memoryId: String, profile: MemoryFile?, now: Long) {
        val plate = plateDao.getPlate(memoryId, MemoryPlateKind.USER)
        if (plate != null && plate.source != MemorySource.LEGACY) return
        val content = profile?.editableBody?.trim().orEmpty()
        if (plate == null && content.isEmpty()) return
        if (plate?.content == content) return
        plateDao.upsert(
            MemoryPlateEntity(
                assistantId = memoryId,
                kind = MemoryPlateKind.USER,
                content = content,
                updatedAt = now,
                source = MemorySource.LEGACY,
            )
        )
    }

    private fun safeIdOf(memoryId: String) = memoryId.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun hasBackup(memoryId: String): Boolean {
        val prefix = safeIdOf(memoryId) + "-"
        return File(context.filesDir, BACKUP_DIR).listFiles()
            ?.any { it.name.startsWith(prefix) && it.name.endsWith(".json") } == true
    }

    private fun backup(memoryId: String, files: List<MemoryFile>) {
        val dir = File(context.filesDir, BACKUP_DIR).apply { mkdirs() }
        val safeId = safeIdOf(memoryId)
        val array = JSONArray()
        files.forEach {
            array.put(
                JSONObject()
                    .put("path", it.path)
                    .put("content", it.content)
                    .put("updatedAt", it.updatedAt)
            )
        }
        val target = File(dir, "$safeId-${System.currentTimeMillis()}.json")
        val temp = File(dir, target.name + ".tmp")
        temp.writeText(JSONObject().put("memoryId", memoryId).put("files", array).toString())
        check(temp.renameTo(target)) { "Could not write memory backup ${target.path}" }
    }

    private fun LegacyBlock.toNode(memoryId: String, now: Long) = MemoryNodeEntity(
        assistantId = memoryId,
        room = room,
        content = content,
        keywords = context,
        createdAt = now,
        updatedAt = now,
        origin = origin,
        source = MemorySource.LEGACY,
    )

    private fun newManualOrigin() = "manual:" + UUID.randomUUID()

    companion object {
        const val BACKUP_DIR = "memory_backups"
    }
}
