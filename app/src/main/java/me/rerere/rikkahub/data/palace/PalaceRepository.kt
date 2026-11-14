package me.rerere.rikkahub.data.palace

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
import me.rerere.rikkahub.data.db.dao.MemoryNodeDAO
import me.rerere.rikkahub.data.db.dao.MemoryPlateDAO
import me.rerere.rikkahub.data.db.dao.PalaceDAO
import me.rerere.rikkahub.data.db.dao.PalaceNodeLite
import me.rerere.rikkahub.data.db.entity.MemoryPlateEntity
import me.rerere.rikkahub.data.db.entity.PalaceLinkEntity
import me.rerere.rikkahub.data.db.entity.PalaceMetaEntity
import me.rerere.rikkahub.data.db.entity.PalaceNodeEntity
import me.rerere.rikkahub.data.db.entity.PalacePlateEntryEntity
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.PendingIds
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "PalaceRepository"

/** 宫殿的核心背景：用户房「关于小葵」、自我房「关于你」，整块存在 memory_plate，不设上限，自动流程不碰 */
object PalaceCore {
    const val USER = "user"
    const val SELF = "self"
    const val SOURCE = "core"
}

object PalaceSource {
    const val CHAT = "chat"
    const val MANUAL = "manual"
    const val LEGACY = PalaceLegacy.SOURCE_LEGACY
    const val DIGESTION = "digestion"
}

/**
 * 记忆宫殿的数据入口。一个作用域（助手的 memoryId）一座宫殿，所有窗口共用。
 *
 * 宫殿是唯一的记忆来源；旧 Markdown 记忆只在第一次打开时搬一次（先备份），之后不再读。
 */
class PalaceRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val dao: PalaceDAO,
    private val plateDao: MemoryPlateDAO,
    private val oldNodeDao: MemoryNodeDAO,
    private val memoryRepository: MemoryRepository,
) {
    private val lock = Mutex()
    private val started = AtomicBoolean(false)
    private val pending = PendingIds()

    /** 哪个作用域的记忆变了：只给向量索引一个收集者用 */
    val changes: Flow<String> = pending.flow

    private fun changed(scope: String) {
        pending.mark(scope)
        me.rerere.rikkahub.companion.CompanionSync.contextChanged()
    }

    fun start(scope: CoroutineScope) {
        if (started.getAndSet(true)) return
        scope.launch(Dispatchers.IO) {
            val scopes = runCatching {
                (memoryRepository.memoryIds() + oldNodeDao.getMemoryIds() + dao.scopes()).distinct()
            }.onFailure { Log.e(TAG, "list scopes failed", it) }.getOrDefault(emptyList())
            scopes.forEach { s ->
                runCatching { ensureMigrated(s) }.onFailure { Log.e(TAG, "migrate $s failed", it) }
                pending.mark(s)
            }
        }
    }

    // ---- 迁移 ----

    /**
     * 旧记忆第一次搬进宫殿。先把旧文件、旧节点、旧门牌整份备份成 JSON，
     * 再在一个事务里写入核心背景、记忆和完成标记；任何一步失败都不留半份，下次启动重试。
     * 旧表原样保留，不删。
     */
    suspend fun ensureMigrated(scope: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (dao.meta(scope, META_MIGRATED) != null) return@withLock
            val files = memoryRepository.getFiles(scope)
            val oldNodes = oldNodeDao.getAll(scope)
            val oldPlates = plateDao.getPlates(scope)
            if (files.isNotEmpty() || oldNodes.isNotEmpty() || oldPlates.isNotEmpty()) {
                backup(scope, files, oldNodes, oldPlates)
            }
            val plan = PalaceLegacy.plan(
                files = files,
                oldNodes = oldNodes.map {
                    PalaceLegacy.OldNode(it.room, it.content, it.importance, it.mood, it.keywords, it.happenedAt, it.createdAt, it.origin, it.source)
                },
                oldPlates = oldPlates.map { PalaceLegacy.OldPlate(it.kind, it.content, it.source) },
            )
            val now = System.currentTimeMillis()
            database.withTransaction {
                plan.userCore?.let { plateDao.upsert(MemoryPlateEntity(scope, PalaceCore.USER, it, now, PalaceCore.SOURCE)) }
                plan.selfCore?.let { plateDao.upsert(MemoryPlateEntity(scope, PalaceCore.SELF, it, now, PalaceCore.SOURCE)) }
                plan.nodes.forEach { n ->
                    val created = if (n.createdAt > 0) n.createdAt else now
                    dao.insertNode(
                        PalaceNodeEntity(
                            assistantId = scope, room = n.room, content = n.content, importance = n.importance,
                            mood = n.mood, tags = PalaceNodeEntity.joinTags(n.tags), createdAt = created,
                            recordedAt = now, updatedAt = now, lastAccessedAt = created, origin = n.origin, source = n.source,
                        )
                    )
                }
                dao.setMeta(PalaceMetaEntity(scope, META_MIGRATED, now.toString()))
            }
            Log.i(TAG, "migrated $scope: ${plan.nodes.size} nodes")
        }
        changed(scope)
    }

    private fun safeIdOf(scope: String) = scope.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun backup(
        scope: String,
        files: List<me.rerere.rikkahub.data.model.MemoryFile>,
        oldNodes: List<me.rerere.rikkahub.data.db.entity.MemoryNodeEntity>,
        oldPlates: List<MemoryPlateEntity>,
    ) {
        val dir = File(context.filesDir, BACKUP_DIR).apply { mkdirs() }
        val root = JSONObject().put("memoryId", scope).put("createdAt", System.currentTimeMillis())
        root.put("files", JSONArray().apply {
            files.forEach { put(JSONObject().put("path", it.path).put("content", it.content).put("updatedAt", it.updatedAt)) }
        })
        root.put("nodes", JSONArray().apply {
            oldNodes.forEach {
                put(
                    JSONObject().put("room", it.room).put("content", it.content).put("importance", it.importance.toDouble())
                        .put("mood", it.mood).put("keywords", it.keywords).put("happenedAt", it.happenedAt)
                        .put("createdAt", it.createdAt).put("origin", it.origin).put("source", it.source)
                )
            }
        })
        root.put("plates", JSONArray().apply {
            oldPlates.forEach { put(JSONObject().put("kind", it.kind).put("content", it.content).put("source", it.source)) }
        })
        val target = File(dir, "palace-${safeIdOf(scope)}-${System.currentTimeMillis()}.json")
        val temp = File(dir, target.name + ".tmp")
        temp.writeText(root.toString())
        check(temp.renameTo(target)) { "Could not write memory backup ${target.path}" }
    }

    // ---- 读 ----

    fun nodesFlow(scope: String): Flow<List<PalaceNodeLite>> = dao.nodesFlow(scope)

    suspend fun liveNodes(scope: String): List<PalaceNodeLite> = dao.liveNodes(scope)

    suspend fun room(scope: String, room: String): List<PalaceNodeLite> = dao.liveNodesInRoom(scope, room)

    suspend fun node(scope: String, id: Long): PalaceNodeEntity? = dao.node(scope, id)

    suspend fun nodesByIds(scope: String, ids: List<Long>): List<PalaceNodeLite> =
        if (ids.isEmpty()) emptyList() else ids.chunked(500).flatMap { dao.nodesByIds(scope, it) }

    fun coreFlow(scope: String): Flow<List<MemoryPlateEntity>> = plateDao.getPlatesFlow(scope)

    suspend fun core(scope: String): Pair<String, String> {
        val plates = plateDao.getPlates(scope)
        return (plates.firstOrNull { it.kind == PalaceCore.USER }?.content.orEmpty()) to
            (plates.firstOrNull { it.kind == PalaceCore.SELF }?.content.orEmpty())
    }

    fun plateEntriesFlow(scope: String): Flow<List<PalacePlateEntryEntity>> = dao.plateEntriesFlow(scope)

    suspend fun plateEntries(scope: String): List<PalacePlateEntryEntity> = dao.plateEntries(scope)

    suspend fun meta(scope: String, key: String): String? = dao.meta(scope, key)

    suspend fun setMeta(scope: String, key: String, value: String) = dao.setMeta(PalaceMetaEntity(scope, key, value))

    // ---- 写 ----

    /** 手动或工具写一条。返回新 id */
    suspend fun add(
        scope: String,
        room: String,
        content: String,
        importance: Int = 5,
        mood: String = "neutral",
        tags: List<String> = emptyList(),
        createdAt: Long = System.currentTimeMillis(),
        source: String = PalaceSource.MANUAL,
    ): Long {
        val text = content.trim()
        require(text.isNotEmpty()) { "记忆内容不能为空" }
        val now = System.currentTimeMillis()
        val id = dao.insertNode(
            PalaceNodeEntity(
                assistantId = scope, room = PalaceRoom.normalize(room), content = text,
                importance = importance.coerceIn(1, 10), mood = PalaceMood.normalize(mood),
                tags = PalaceNodeEntity.joinTags(tags), createdAt = createdAt, recordedAt = now, updatedAt = now,
                lastAccessedAt = createdAt, origin = "$source:" + UUID.randomUUID(), source = source,
            )
        )
        check(id > 0) { "记忆没写进去" }
        changed(scope)
        return id
    }

    /**
     * 整理器写一批，和整理水位在同一个事务里：要么记忆和水位一起写进去，要么都没写，
     * 不会出现记忆写了、水位没动，下次又提取一遍的情况。返回新写入的 id。
     */
    suspend fun addExtracted(
        scope: String,
        originPrefix: String,
        memories: List<ExtractedMemory>,
        meta: List<Pair<String, String>>,
    ): List<Long> {
        val now = System.currentTimeMillis()
        val ids = ArrayList<Long>()
        database.withTransaction {
            memories.forEachIndexed { i, m ->
                val id = dao.insertNode(
                    PalaceNodeEntity(
                        assistantId = scope, room = m.room, content = m.content, importance = m.importance, mood = m.mood,
                        valence = m.valence, arousal = m.arousal, tags = PalaceNodeEntity.joinTags(m.tags),
                        createdAt = m.createdAt, recordedAt = now, updatedAt = now, lastAccessedAt = m.createdAt,
                        origin = "$originPrefix:$i", source = PalaceSource.CHAT,
                    )
                )
                if (id > 0) ids += id
            }
            meta.forEach { (k, v) -> dao.setMeta(PalaceMetaEntity(scope, k, v)) }
        }
        if (ids.isNotEmpty()) changed(scope)
        return ids
    }

    /** 改一条。正文变了会清掉旧向量重新算；返回是否找到 */
    suspend fun edit(
        scope: String,
        id: Long,
        content: String? = null,
        room: String? = null,
        importance: Int? = null,
        mood: String? = null,
        tags: List<String>? = null,
        createdAt: Long? = null,
    ): Boolean {
        val old = dao.node(scope, id) ?: return false
        val now = System.currentTimeMillis()
        val newContent = content?.trim()?.takeIf { it.isNotEmpty() } ?: old.content
        val newRoom = room?.let { PalaceRoom.normalize(it) } ?: old.room
        val newImportance = importance?.coerceIn(1, 10) ?: old.importance
        val newMood = mood?.let { PalaceMood.normalize(it) } ?: old.mood
        val newTags = tags?.let { PalaceNodeEntity.joinTags(it) } ?: old.tags
        val newCreated = createdAt ?: old.createdAt
        val n = if (newContent != old.content) {
            dao.rewriteNode(scope, id, newRoom, newContent, newImportance, newMood, newTags, newCreated, now)
        } else {
            dao.updateMeta(scope, id, newRoom, newImportance, newMood, newTags, newCreated, now)
        }
        if (n > 0) changed(scope)
        return n > 0
    }

    /** 删一条，连同它的链接；删了就没了，不会被别的流程再生成回来 */
    suspend fun delete(scope: String, id: Long): Boolean {
        var n = 0
        database.withTransaction {
            dao.deleteLinksOf(scope, id)
            n = dao.deleteNode(scope, id)
        }
        if (n > 0) changed(scope)
        return n > 0
    }

    /** 核心背景只能由用户自己、或者她明确让助手改的时候改 */
    suspend fun setCore(scope: String, kind: String, content: String) {
        require(kind == PalaceCore.USER || kind == PalaceCore.SELF)
        plateDao.upsert(MemoryPlateEntity(scope, kind, content.trim(), System.currentTimeMillis(), PalaceCore.SOURCE))
        changed(scope)
    }

    suspend fun addPlateEntry(scope: String, plate: String, content: String) {
        require(plate in PalacePlate.all)
        val text = content.trim()
        require(text.isNotEmpty())
        val now = System.currentTimeMillis()
        dao.insertPlateEntries(listOf(PalacePlateEntryEntity(assistantId = scope, plate = plate, content = text, firstLearnedAt = now, updatedAt = now)))
        changed(scope)
    }

    suspend fun editPlateEntry(scope: String, id: Long, content: String): Boolean {
        val text = content.trim()
        val n = if (text.isEmpty()) dao.deletePlateEntry(scope, id) else dao.updatePlateEntry(scope, id, text, System.currentTimeMillis())
        if (n > 0) changed(scope)
        return n > 0
    }

    suspend fun deletePlateEntry(scope: String, id: Long): Boolean {
        val n = dao.deletePlateEntry(scope, id)
        if (n > 0) changed(scope)
        return n > 0
    }

    /** 整块换掉一块门牌（消化、整理合并后用） */
    suspend fun replacePlate(scope: String, plate: String, entries: List<PalacePlateEntryEntity>) {
        require(plate in PalacePlate.all)
        database.withTransaction {
            dao.clearPlate(scope, plate)
            if (entries.isNotEmpty()) dao.insertPlateEntries(entries.map { it.copy(id = 0, assistantId = scope, plate = plate) })
        }
        changed(scope)
    }

    // ---- 链接、巩固、访问 ----

    suspend fun addLinks(scope: String, links: List<PalaceMath.Edge>) {
        if (links.isEmpty()) return
        dao.insertLinks(
            links.filter { it.a != it.b }.map {
                PalaceLinkEntity(
                    assistantId = scope, fromId = minOf(it.a, it.b), toId = maxOf(it.a, it.b), type = it.type,
                    strength = Math.round(it.strength.coerceIn(0.0, 1.0) * 100_000) / 100_000.0,
                )
            }
        )
    }

    suspend fun linksTouching(scope: String, ids: List<Long>): List<PalaceMath.Edge> =
        if (ids.isEmpty()) emptyList()
        else dao.linksTouching(scope, ids).map { PalaceMath.Edge(it.fromId, it.toId, it.type, it.strength) }

    /** 一起被想起来的记忆之间的链接加强一点；同时记下它们被想起了一次 */
    suspend fun markRecalled(scope: String, ids: List<Long>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.markAccessed(scope, ids, now)
            if (ids.size > 1) dao.strengthenAmong(scope, ids, PalaceMath.CO_ACTIVATION_STEP)
        }
    }

    suspend fun allNodes(scope: String): List<PalaceNodeEntity> = dao.allNodes(scope)

    private fun digestNode(scope: String, room: String, content: String, importance: Int, mood: String, tags: List<String>, now: Long) =
        PalaceNodeEntity(
            assistantId = scope, room = room, content = content, importance = importance, mood = PalaceMood.normalize(mood),
            tags = PalaceNodeEntity.joinTags(tags), createdAt = now, recordedAt = now, updatedAt = now, lastAccessedAt = now,
            origin = "${PalaceSource.DIGESTION}:" + UUID.randomUUID(), source = PalaceSource.DIGESTION,
        )

    /**
     * 认知消化的结果一次写进去：状态变化、新条目、门牌、消化时间在同一个事务里，
     * 中途失败就全不算，下次重来，门牌候选不会丢。
     */
    /**
     * 消化结果一次落库。[plates] 是整理后的门牌，[snapshots] 是整理前读到的样子：整理期间她手动改过的门牌，
     * 不拿旧快照的结果盖回去，只把这次的候选机械并进她改过的那份。模型请求期间被删掉的记忆，不再对它做任何事。
     */
    suspend fun applyDigest(
        scope: String,
        plan: PalaceDigestion.Plan,
        plates: Map<String, List<PalacePlateEntryEntity>>,
        snapshots: Map<String, List<PalacePlateEntryEntity>>,
        now: Long,
    ) {
        database.withTransaction {
            val live = PalaceRoom.all.flatMap { room -> dao.liveNodesInRoom(scope, room).map { it.id } }.toHashSet()
            fun alive(ids: List<Long>) = ids.filter { it in live }
            val resolve = alive(plan.resolve)
            if (resolve.isNotEmpty()) {
                dao.moveRoom(scope, resolve, PalaceRoom.BEDROOM, now)
                dao.markDigested(scope, resolve, now)
            }
            alive(plan.deepen).takeIf { it.isNotEmpty() }?.let { dao.adjustImportance(scope, it, 1, now) }
            alive(plan.fade).takeIf { it.isNotEmpty() }?.let { dao.adjustImportance(scope, it, -2, now) }
            plan.fulfill.filter { it.first in live }.forEach { (id, content) ->
                dao.archive(scope, listOf(id), now)
                dao.insertNode(digestNode(scope, PalaceRoom.BEDROOM, "我盼过的事成真了：$content", 7, "grateful", listOf("期盼实现"), now))
            }
            plan.disappoint.filter { it.first in live }.forEach { (id, content) ->
                dao.archive(scope, listOf(id), now)
                dao.insertNode(digestNode(scope, PalaceRoom.ATTIC, "盼过的事没能成：$content", 5, "disappointed", listOf("期盼落空"), now))
            }
            plan.newAttic.forEach { dao.insertNode(digestNode(scope, PalaceRoom.ATTIC, it, 6, "anxious", listOf("担忧"), now)) }
            plan.newSill.forEach { dao.insertNode(digestNode(scope, PalaceRoom.WINDOWSILL, it, 6, "excited", listOf("期盼"), now)) }
            plan.selfConfuse.filter { it.sourceId in live }.forEach {
                dao.insertNode(
                    digestNode(scope, PalaceRoom.ATTIC, it.text, 6, "confused", listOf("自我困惑", "反刍"), now)
                        .copy(createdAt = it.createdAt),
                )
            }
            alive(plan.digested).chunked(500).forEach { dao.markDigested(scope, it, now) }
            plates.forEach { (plate, entries) ->
                require(plate in PalacePlate.all)
                val current = dao.plateEntriesOf(scope, plate)
                val final = if (PalaceDigestion.sameSnapshot(current, snapshots[plate].orEmpty())) {
                    entries
                } else {
                    PalaceDigestion.fallbackSubmissions(plate, current, plan.submissions[plate].orEmpty(), now)
                } ?: return@forEach
                dao.clearPlate(scope, plate)
                if (final.isNotEmpty()) dao.insertPlateEntries(final.map { it.copy(id = 0, assistantId = scope, plate = plate) })
            }
            dao.setMeta(PalaceMetaEntity(scope, META_LAST_DIGEST, now.toString()))
        }
        changed(scope)
    }

    /** 客厅里该搬卧室的搬过去，超出容量的挪进阁楼。返回 (升了几条, 挪了几条) */
    suspend fun consolidate(scope: String): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        var promoted = 0
        var evicted = 0
        database.withTransaction {
            val living = dao.liveNodesInRoom(scope, PalaceRoom.LIVING)
            val up = living.filter { PalaceMath.shouldPromote(it.room, it.importance, it.createdAt, it.accessCount, now) }.map { it.id }
            up.chunked(500).forEach { dao.moveRoom(scope, it, PalaceRoom.BEDROOM, now) }
            promoted = up.size
            val rest = living.filter { it.id !in up.toSet() }
                .map { PalaceMath.EvictCandidate(it.id, it.importance, it.createdAt) }
            val out = PalaceMath.pickEvictions(rest, now)
            out.chunked(500).forEach { dao.moveRoom(scope, it, PalaceRoom.ATTIC, now) }
            evicted = out.size
        }
        if (promoted + evicted > 0) changed(scope)
        return promoted to evicted
    }

    // ---- 整个作用域 ----

    suspend fun deleteScope(scope: String) = lock.withLock {
        database.withTransaction {
            dao.deleteAllLinks(scope)
            dao.deleteNodesOf(scope)
            dao.deletePlateEntriesOf(scope)
            dao.deleteMetaOf(scope)
            plateDao.deleteOfAssistant(scope)
        }
        changed(scope)
    }

    /** 复制助手时把整座宫殿复制过去，链接按新 id 对上；向量一起带过去 */
    suspend fun copyScope(from: String, to: String) = lock.withLock {
        database.withTransaction {
            val idMap = HashMap<Long, Long>()
            dao.allNodes(from).forEach { n ->
                val newId = dao.insertNode(n.copy(id = 0, assistantId = to))
                if (newId > 0) idMap[n.id] = newId
            }
            dao.insertLinks(
                dao.allLinks(from).mapNotNull { l ->
                    val a = idMap[l.fromId] ?: return@mapNotNull null
                    val b = idMap[l.toId] ?: return@mapNotNull null
                    l.copy(id = 0, assistantId = to, fromId = minOf(a, b), toId = maxOf(a, b))
                }
            )
            dao.insertPlateEntries(dao.plateEntries(from).map { it.copy(id = 0, assistantId = to) })
            plateDao.getPlates(from).forEach { plateDao.upsert(it.copy(assistantId = to)) }
            // 迁移标记要带上，否则新作用域会把（空的）旧记忆再搬一遍；整理水位不带，新助手没有那段对话
            dao.allMeta(from).filter { it.key == META_MIGRATED }.forEach { dao.setMeta(it.copy(assistantId = to)) }
        }
        changed(to)
    }

    companion object {
        const val BACKUP_DIR = "memory_backups"
        const val META_MIGRATED = "migrated_v1"
        const val META_LAST_DIGEST = "digest:last"
    }
}
