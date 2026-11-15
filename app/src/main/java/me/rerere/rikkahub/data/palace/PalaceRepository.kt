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
import me.rerere.rikkahub.data.db.dao.PalaceIdVersion
import me.rerere.rikkahub.data.db.dao.PalaceNodeLite
import me.rerere.rikkahub.data.db.entity.MemoryPlateEntity
import me.rerere.rikkahub.data.db.entity.PalaceBoxEntity
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
    /** 事件盒总结 */
    const val BOX = "box"
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
        me.rerere.rikkahub.companion.CompanionSync.contextChanged(memoryChanged = true)
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

    /** 旧记忆日期修复要看的节点：整理写入的（source=chat） */
    suspend fun chatNodesForDateRepair(scope: String): List<PalaceDateRepair.Node> = withContext(Dispatchers.IO) {
        dao.allNodes(scope).filter { it.source == PalaceSource.CHAT }.map { PalaceDateRepair.Node(it.id, it.origin, it.createdAt) }
    }

    suspend fun dateRepairDone(scope: String): String? = dao.meta(scope, PalaceDateRepair.META_DONE)

    /** 改日期、原日期存进 palace_meta、完成标记，一个事务 */
    suspend fun applyDateRepair(scope: String, plan: PalaceDateRepair.Plan, summary: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            plan.fixes.forEach { f ->
                dao.setMeta(PalaceMetaEntity(scope, PalaceDateRepair.META_ORIG + f.id, f.from.toString()))
                dao.setCreatedAt(scope, f.id, f.to, f.from)
            }
            dao.setMeta(PalaceMetaEntity(scope, PalaceDateRepair.META_DONE, summary))
        }
        if (plan.fixes.isNotEmpty()) changed(scope)
        Log.i(TAG, "date repair $scope: $summary")
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
    ): List<Long?> {
        val now = System.currentTimeMillis()
        val ids = ArrayList<Long?>()
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
                ids += id.takeIf { it > 0 }
            }
            meta.forEach { (k, v) -> dao.setMeta(PalaceMetaEntity(scope, k, v)) }
        }
        if (ids.any { it != null }) changed(scope)
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
        lastEventAt: Long? = null,
    ): Boolean {
        val old = dao.node(scope, id) ?: return false
        val now = System.currentTimeMillis()
        val newContent = content?.trim()?.takeIf { it.isNotEmpty() } ?: old.content
        val newRoom = room?.let { PalaceRoom.normalize(it) } ?: old.room
        val newImportance = importance?.coerceIn(1, 10) ?: old.importance
        val newMood = mood?.let { PalaceMood.normalize(it) } ?: old.mood
        val newTags = tags?.let { PalaceNodeEntity.joinTags(it) } ?: old.tags
        val newCreated = createdAt ?: old.createdAt
        // 最近进展时间：整理的 update 直接给；手动或工具只改了发生日期（createdAt 变了）就两个一起改；
        // 其余情况保持不变。任何时候都不早于 createdAt
        val newLast = when {
            lastEventAt != null -> lastEventAt
            createdAt != null && createdAt != old.createdAt -> createdAt
            else -> old.lastEventAt
        }.coerceAtLeast(newCreated)
        val n = if (newContent != old.content) {
            dao.rewriteNode(scope, id, newRoom, newContent, newImportance, newMood, newTags, newCreated, newLast, now)
        } else {
            dao.updateMeta(scope, id, newRoom, newImportance, newMood, newTags, newCreated, newLast, now)
        }
        if (n > 0) changed(scope)
        return n > 0
    }

    /** 删一条，连同它的链接；删了就没了，不会被别的流程再生成回来 */
    suspend fun delete(scope: String, id: Long): Boolean {
        var n = 0
        database.withTransaction {
            val boxId = dao.node(scope, id)?.boxId ?: 0L
            dao.deleteLinksOf(scope, id)
            // 删的是盒子总结就把盒子的总结指针清掉；盒子删空了连盒子一起删
            val box = boxId.takeIf { it > 0 }?.let { dao.box(scope, it) }
            if (box != null && box.summaryNodeId == id) {
                dao.updateBox(box.copy(summaryNodeId = 0, updatedAt = System.currentTimeMillis()))
            }
            n = dao.deleteNode(scope, id)
            if (box != null && dao.anyInBox(scope, box.id) == 0) dao.deleteBox(scope, box.id)
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

    suspend fun addPlateEntry(scope: String, plate: String, content: String, tag: String = "") {
        require(plate in PalacePlate.all)
        val text = content.trim()
        require(text.isNotEmpty())
        val now = System.currentTimeMillis()
        dao.insertPlateEntries(listOf(PalacePlateEntryEntity(assistantId = scope, plate = plate, content = text, firstLearnedAt = now, updatedAt = now, tag = PalaceDigestion.cleanTag(tag))))
        changed(scope)
    }

    suspend fun editPlateEntry(scope: String, id: Long, content: String, tag: String? = null): Boolean {
        val text = content.trim()
        val n = if (text.isEmpty()) dao.deletePlateEntry(scope, id) else dao.updatePlateEntry(scope, id, text, System.currentTimeMillis())
        if (n > 0 && text.isNotEmpty() && tag != null) dao.updatePlateEntryTag(scope, id, PalaceDigestion.cleanTag(tag))
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
            val (kept, touched) = PalaceDigestion.dropDeleted(plan, live)
            val resolve = alive(plan.resolve)
            if (resolve.isNotEmpty()) {
                dao.moveRoom(scope, resolve, PalaceRoom.BEDROOM, now)
                dao.markDigested(scope, resolve, now)
            }
            alive(plan.deepen).takeIf { it.isNotEmpty() }?.let { dao.adjustImportance(scope, it, 1, now) }
            alive(plan.fade).takeIf { it.isNotEmpty() }?.let { dao.adjustImportance(scope, it, -2, now) }
            plan.fulfill.filter { it.first in live }.forEach { (id, content) ->
                dao.resolveAnticipation(scope, listOf(id), PalaceDigestion.ANT_FULFILLED, now)
                dao.insertNode(digestNode(scope, PalaceRoom.BEDROOM, "我盼过的事成真了：$content", 7, "grateful", listOf("期盼实现"), now))
            }
            plan.disappoint.filter { it.first in live }.forEach { (id, content) ->
                dao.resolveAnticipation(scope, listOf(id), PalaceDigestion.ANT_DISAPPOINTED, now)
                dao.insertNode(digestNode(scope, PalaceRoom.ATTIC, "盼过的事没能成：$content", 5, "disappointed", listOf("期盼落空"), now))
            }
            kept.newAttic.forEach { dao.insertNode(digestNode(scope, PalaceRoom.ATTIC, it, 6, "anxious", listOf("担忧"), now)) }
            kept.newSill.forEach {
                dao.insertNode(digestNode(scope, PalaceRoom.WINDOWSILL, it, 6, "excited", listOf("期盼"), now).copy(antStatus = PalaceDigestion.ANT_ACTIVE))
            }
            // 盼满 7 天还没结果的期盼成为锚点（先处理实现/落空，再转锚点，已经收起的不会被转）
            dao.anchorAnticipations(scope, now - PalaceDigestion.ANCHOR_AFTER_MS, now)
            plan.selfConfuse.filter { it.sourceId in live }.forEach {
                dao.insertNode(
                    digestNode(scope, PalaceRoom.ATTIC, it.text, 6, "confused", listOf("自我困惑", "反刍"), now)
                        .copy(createdAt = it.createdAt, lastEventAt = it.createdAt),
                )
            }
            alive(plan.digested).chunked(500).forEach { dao.markDigested(scope, it, now) }
            plates.forEach { (plate, entries) ->
                require(plate in PalacePlate.all)
                val current = dao.plateEntriesOf(scope, plate)
                val final = if (PalaceDigestion.sameSnapshot(current, snapshots[plate].orEmpty()) && plate !in touched) {
                    entries
                } else {
                    PalaceDigestion.fallbackSubmissions(plate, current, kept.submissions[plate].orEmpty(), now)
                } ?: return@forEach
                dao.clearPlate(scope, plate)
                if (final.isNotEmpty()) dao.insertPlateEntries(final.map { it.copy(id = 0, assistantId = scope, plate = plate) })
            }
            dao.setMeta(PalaceMetaEntity(scope, META_LAST_DIGEST, now.toString()))
        }
        changed(scope)
    }

    // ---- 事件盒 ----

    suspend fun boxes(scope: String): List<PalaceBoxEntity> = dao.boxes(scope)

    /** 没收起来的成员攒够了、该写总结的盒子 */
    suspend fun eligibleBoxes(scope: String): List<Long> =
        dao.boxes(scope).filter { dao.liveMemberCount(scope, it.id, it.summaryNodeId) >= PalaceBoxes.COMPRESS_AT }.map { it.id }

    /**
     * 把模型标出来的「同一件事」装进盒子。[links] 是 (新记忆, 同一件事的另一条)，[hints] 是新记忆给的盒子名字和关键词。
     * 每条新记忆：相关的盒子一个能放的都没有就开新盒（封了或满了的记成前一个），一个就用它，几个就合成一个。
     * 已经在别的盒子里的记忆不挪。返回动过的盒子。
     */
    suspend fun bindBoxes(
        scope: String,
        links: List<Pair<Long, Long>>,
        hints: Map<Long, Pair<String, List<String>>>,
    ): Set<Long> {
        if (links.isEmpty()) return emptySet()
        val touched = LinkedHashSet<Long>()
        database.withTransaction {
            for ((newId, existing) in PalaceBoxes.groupLinks(links)) {
                val ids = (listOf(newId) + existing)
                val nodes = dao.nodesByIds(scope, ids).associateBy { it.id }
                if (newId !in nodes) continue
                val boxIds = ids.mapNotNull { nodes[it]?.boxId?.takeIf { b -> b > 0 } }.distinct()
                val boxes = boxIds.mapNotNull { id -> dao.box(scope, id).also { if (it == null) dao.clearBox(scope, id) } }
                val views = boxes.map {
                    PalaceBoxes.BoxView(
                        it.id, it.isSealed, dao.liveMemberCount(scope, it.id, it.summaryNodeId),
                        it.compressionCount, it.createdAt, it.lastCompressedAt, it.updatedAt,
                    )
                }
                val byId = boxes.associateBy { it.id }
                val now = System.currentTimeMillis()
                var created = false
                val target = when (val c = PalaceBoxes.choose(views)) {
                    is PalaceBoxes.Choice.Use -> c.id
                    is PalaceBoxes.Choice.Create -> {
                        val pred = byId[c.predecessor]
                        val hint = hints[newId]
                        created = true
                        dao.insertBox(
                            PalaceBoxEntity(
                                assistantId = scope,
                                name = hint?.first?.takeIf { it.isNotBlank() } ?: pred?.name ?: "未命名事件",
                                tags = PalaceNodeEntity.joinTags(
                                    (hint?.second?.takeIf { it.isNotEmpty() } ?: pred?.tags?.split('\n').orEmpty()).take(PalaceBoxes.MAX_TAGS),
                                ),
                                predecessorId = pred?.id ?: 0L,
                                createdAt = now,
                                updatedAt = now,
                            )
                        )
                    }
                    is PalaceBoxes.Choice.Merge -> {
                        for (other in c.others) {
                            val o = byId[other] ?: continue
                            if (o.summaryNodeId > 0 && dao.node(scope, o.summaryNodeId) != null) {
                                dao.demoteSummary(scope, o.summaryNodeId, c.primary, PalaceSource.CHAT, now)
                            }
                            dao.moveBoxMembers(scope, other, c.primary)
                            dao.deleteBox(scope, other)
                        }
                        c.primary
                    }
                }
                dao.setBoxIfFree(scope, ids, target)
                if (created && dao.anyInBox(scope, target) == 0) {
                    dao.deleteBox(scope, target)
                    continue
                }
                dao.box(scope, target)?.let { dao.updateBox(it.copy(updatedAt = now)) }
                touched += target
            }
        }
        if (touched.isNotEmpty()) changed(scope)
        return touched
    }

    data class CompressJob(
        val boxId: Long,
        val name: String,
        val oldSummary: String?,
        val members: List<PalaceNodeLite>,
        val snapshot: List<PalaceIdVersion>,
        val summaryId: Long,
    )

    /** 读出要写总结的材料；没收起来的不够 [PalaceBoxes.COMPRESS_AT] 条返回 null */
    suspend fun prepareCompression(scope: String, boxId: Long): CompressJob? {
        val box = dao.box(scope, boxId) ?: return null
        val members = dao.liveMembers(scope, boxId, box.summaryNodeId)
        if (members.size < PalaceBoxes.COMPRESS_AT) return null
        val old = box.summaryNodeId.takeIf { it > 0 }?.let { dao.node(scope, it) }
        return CompressJob(boxId, box.name, old?.content, members, dao.liveMemberVersions(scope, boxId, box.summaryNodeId), box.summaryNodeId)
    }

    /**
     * 写盒子总结、收起成员、更新盒子，同一个事务。写之前再对一遍：盒子没了、总结换了、成员被改被删被挪，都不写，原来的一条不动。
     * [vector] 是总结的向量（key 和数值），没配向量模型时为 null，交给后台补。返回是否写成。
     */
    suspend fun applyCompression(
        scope: String,
        job: CompressJob,
        result: PalaceBoxes.Compressed,
        vector: Pair<String, FloatArray>?,
    ): Boolean {
        var ok = false
        database.withTransaction {
            val box = dao.box(scope, job.boxId) ?: return@withTransaction
            if (box.summaryNodeId != job.summaryId) return@withTransaction
            if (dao.liveMemberVersions(scope, job.boxId, box.summaryNodeId) != job.snapshot) return@withTransaction
            val now = System.currentTimeMillis()
            val tags = PalaceNodeEntity.joinTags(result.tags)
            val eventTime = job.members.maxOf { it.createdAt }
            val lastEvent = job.members.maxOf { it.lastEventAt }
            val bytes = vector?.second?.toBytes() ?: byteArrayOf()
            val existing = box.summaryNodeId.takeIf { it > 0 }?.let { dao.node(scope, it) }
            val summaryId = if (existing != null) {
                dao.rewriteSummary(
                    scope, existing.id, result.room, result.content, result.importance, result.mood, tags,
                    eventTime, lastEvent, now, bytes, vector?.first.orEmpty(), vector?.second?.size ?: 0,
                )
                existing.id
            } else {
                dao.insertNode(
                    PalaceNodeEntity(
                        assistantId = scope, room = result.room, content = result.content, importance = result.importance,
                        mood = result.mood, tags = tags, createdAt = eventTime, lastEventAt = lastEvent, recordedAt = now, updatedAt = now,
                        lastAccessedAt = now, origin = "${PalaceSource.BOX}:${job.boxId}:" + UUID.randomUUID(),
                        source = PalaceSource.BOX, boxId = job.boxId,
                        embedding = bytes, embeddingKey = vector?.first.orEmpty(), embeddingDims = vector?.second?.size ?: 0,
                    )
                ).takeIf { it > 0 } ?: return@withTransaction
            }
            dao.archive(scope, job.snapshot.map { it.id }, now)
            val total = dao.memberCount(scope, job.boxId, summaryId)
            dao.updateBox(
                box.copy(
                    name = result.name.ifBlank { box.name },
                    tags = if (result.tags.isEmpty()) box.tags else tags,
                    summaryNodeId = summaryId,
                    compressionCount = box.compressionCount + 1,
                    lastCompressedAt = now,
                    updatedAt = now,
                    isSealed = box.isSealed || total >= PalaceBoxes.SEAL_AT,
                )
            )
            ok = true
        }
        if (ok) changed(scope)
        return ok
    }

    fun boxesFlow(scope: String): Flow<List<PalaceBoxEntity>> = dao.boxesFlow(scope)

    /** 改盒子的名字和关键词。只影响显示，召回只看成员的正文和关键词 */
    suspend fun renameBox(scope: String, boxId: Long, name: String, tags: String): Boolean {
        var ok = false
        database.withTransaction {
            val box = dao.box(scope, boxId) ?: return@withTransaction
            dao.updateBox(
                box.copy(
                    name = PalaceBoxes.normalizeName(name),
                    tags = PalaceNodeEntity.joinTags(PalaceBoxes.parseTags(tags)),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            ok = true
        }
        if (ok) changed(scope)
        return ok
    }

    /** 收起来的一条重新参与召回，留在盒子里当没收起的成员；不马上重写总结，攒够了自然会写 */
    suspend fun reviveArchived(scope: String, nodeId: Long): Boolean {
        var ok = false
        database.withTransaction {
            val node = dao.node(scope, nodeId) ?: return@withTransaction
            if (!node.archived || dao.unarchive(scope, nodeId, System.currentTimeMillis()) == 0) return@withTransaction
            if (node.boxId > 0) dao.box(scope, node.boxId)?.let { dao.updateBox(it.copy(updatedAt = System.currentTimeMillis())) }
            ok = true
        }
        if (ok) changed(scope)
        return ok
    }

    /** 一条记忆移出盒子，变回普通记忆。移的是总结就把盒子的总结清掉；盒子空了就删 */
    suspend fun removeFromBox(scope: String, nodeId: Long): Boolean {
        var ok = false
        database.withTransaction {
            val node = dao.node(scope, nodeId) ?: return@withTransaction
            if (node.boxId <= 0) return@withTransaction
            val now = System.currentTimeMillis()
            val box = dao.box(scope, node.boxId)
            if (box != null && box.summaryNodeId == nodeId) {
                dao.demoteSummary(scope, nodeId, 0, PalaceSource.CHAT, now)
                dao.updateBox(box.copy(summaryNodeId = 0, updatedAt = now))
            } else {
                dao.leaveBox(scope, nodeId, now)
                box?.let { dao.updateBox(it.copy(updatedAt = now)) }
            }
            if (box != null && dao.anyInBox(scope, box.id) == 0) dao.deleteBox(scope, box.id)
            ok = true
        }
        if (ok) changed(scope)
        return ok
    }

    /** 没收起来的全部移出盒子，记忆不删。总结、收起来的不动；什么都不剩就删盒。返回移出几条 */
    suspend fun unbindAllLive(scope: String, boxId: Long): Int {
        var n = 0
        database.withTransaction {
            val box = dao.box(scope, boxId) ?: return@withTransaction
            n = dao.unbindLive(scope, boxId, box.summaryNodeId, System.currentTimeMillis())
            if (n == 0) return@withTransaction
            if (dao.anyInBox(scope, boxId) == 0) dao.deleteBox(scope, boxId)
            else dao.updateBox(box.copy(updatedAt = System.currentTimeMillis()))
        }
        if (n > 0) changed(scope)
        return n
    }

    /** 她手动把两条绑成一件事：和整理时装盒同一套规则。返回所在的盒子，没绑上返回 null */
    suspend fun manualBind(scope: String, a: Long, b: Long): Long? {
        require(a != b) { "要选另一条记忆" }
        val nodes = dao.nodesByIds(scope, listOf(a, b))
        require(nodes.size == 2) { "有一条记忆已经不在了" }
        require(nodes.none { it.source == PalaceSource.BOX }) { "盒子总结不能再绑进别的盒子" }
        return bindBoxes(scope, listOf(a to b), emptyMap()).firstOrNull()
    }

    data class RegenJob(
        val boxId: Long,
        val name: String,
        val summaryId: Long,
        val lastCompressedAt: Long,
        val sources: List<PalaceNodeLite>,
    )

    /** 重新整合的材料：盒子里所有原始记忆（收起的和没收起的），不含总结 */
    suspend fun prepareRegeneration(scope: String, boxId: Long): RegenJob {
        val box = dao.box(scope, boxId) ?: error("这个事件盒已经不在了")
        val members = dao.nodesByIds(scope, dao.boxMemberIds(scope, boxId))
        val sources = PalaceBoxes.regenSources(members, boxId, box.summaryNodeId)
        if (sources.isEmpty()) error("盒子里没有能用来重新整合的原始记忆")
        return RegenJob(box.id, box.name, box.summaryNodeId, box.lastCompressedAt, sources)
    }

    /**
     * 写重新整合的结果。同一个事务里核对：盒子还在、总结没换、这期间没有别的整合写过、每条原料还在盒子里且没被改过。
     * 有一样不对就抛错，原来的一条不动。成功后原料全部收起，整合期间新进来的保持没收起；收起的累计够了封盒。
     */
    suspend fun applyRegeneration(
        scope: String,
        job: RegenJob,
        result: PalaceBoxes.Compressed,
        vector: Pair<String, FloatArray>?,
    ) {
        database.withTransaction {
            val box = dao.box(scope, job.boxId) ?: error("事件盒已经删了，整合没有保存")
            if (box.summaryNodeId != job.summaryId || box.lastCompressedAt != job.lastCompressedAt) {
                error("这个事件盒刚被别的整合更新过，请重新整合")
            }
            val current = dao.nodesByIds(scope, job.sources.map { it.id }).associateBy { it.id }
            val unchanged = job.sources.all { s ->
                val c = current[s.id]
                c != null && c.boxId == job.boxId && c.source != PalaceSource.BOX && PalaceBoxes.regenKey(c) == PalaceBoxes.regenKey(s)
            }
            if (!unchanged) error("原始记忆被改过、移出或删除了，请重新整合；原来的总结保留着")
            val now = System.currentTimeMillis()
            val tags = PalaceNodeEntity.joinTags(result.tags)
            val eventTime = job.sources.maxOf { it.createdAt }
            val lastEvent = job.sources.maxOf { it.lastEventAt }
            val bytes = vector?.second?.toBytes() ?: byteArrayOf()
            val existing = box.summaryNodeId.takeIf { it > 0 }?.let { dao.node(scope, it) }
            val summaryId = if (existing != null) {
                dao.rewriteSummary(
                    scope, existing.id, result.room, result.content, result.importance, result.mood, tags,
                    eventTime, lastEvent, now, bytes, vector?.first.orEmpty(), vector?.second?.size ?: 0,
                )
                existing.id
            } else {
                dao.insertNode(
                    PalaceNodeEntity(
                        assistantId = scope, room = result.room, content = result.content, importance = result.importance,
                        mood = result.mood, tags = tags, createdAt = eventTime, lastEventAt = lastEvent, recordedAt = now, updatedAt = now,
                        lastAccessedAt = now, origin = "${PalaceSource.BOX}:${job.boxId}:" + UUID.randomUUID(),
                        source = PalaceSource.BOX, boxId = job.boxId,
                        embedding = bytes, embeddingKey = vector?.first.orEmpty(), embeddingDims = vector?.second?.size ?: 0,
                    )
                ).takeIf { it > 0 } ?: error("整合回忆没写进去，原来的保留着")
            }
            val newlyArchived = job.sources.filter { current[it.id]?.archived == false }.map { it.id }
            if (newlyArchived.isNotEmpty()) dao.archive(scope, newlyArchived, now)
            val archivedTotal = dao.archivedCount(scope, job.boxId, summaryId)
            dao.updateBox(
                box.copy(
                    name = result.name.ifBlank { box.name },
                    tags = if (result.tags.isEmpty()) box.tags else tags,
                    summaryNodeId = summaryId,
                    compressionCount = box.compressionCount + if (newlyArchived.isNotEmpty()) 1 else 0,
                    lastCompressedAt = now,
                    updatedAt = now,
                    isSealed = box.isSealed || archivedTotal >= PalaceBoxes.SEAL_AT,
                )
            )
        }
        changed(scope)
    }

    /** 门牌整理期间她要是改过这块，就不拿旧快照的结果盖回去。返回是否写了 */
    suspend fun replacePlateIfUnchanged(
        scope: String,
        plate: String,
        snapshot: List<PalacePlateEntryEntity>,
        entries: List<PalacePlateEntryEntity>,
    ): Boolean {
        require(plate in PalacePlate.all)
        var ok = false
        database.withTransaction {
            if (!PalaceDigestion.sameSnapshot(dao.plateEntriesOf(scope, plate), snapshot)) return@withTransaction
            dao.clearPlate(scope, plate)
            if (entries.isNotEmpty()) dao.insertPlateEntries(entries.map { it.copy(id = 0, assistantId = scope, plate = plate) })
            ok = true
        }
        if (ok) changed(scope)
        return ok
    }

    /** 客厅里该搬卧室的搬过去，超出容量的挪进阁楼。返回 (升了几条, 挪了几条) */
    suspend fun consolidate(scope: String): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        var promoted = 0
        var evicted = 0
        database.withTransaction {
            val living = dao.liveNodesInRoom(scope, PalaceRoom.LIVING)
            val up = living.filter { PalaceMath.shouldPromote(it.room, it.importance, it.lastEventAt, it.accessCount, now) }.map { it.id }
            up.chunked(500).forEach { dao.moveRoom(scope, it, PalaceRoom.BEDROOM, now) }
            promoted = up.size
            val rest = living.filter { it.id !in up.toSet() }
                .map { PalaceMath.EvictCandidate(it.id, it.importance, it.lastEventAt) }
            val out = PalaceMath.pickEvictions(rest, now)
            out.chunked(500).forEach { dao.moveRoom(scope, it, PalaceRoom.ATTIC, now) }
            evicted = out.size
        }
        if (promoted + evicted > 0) changed(scope)
        return promoted to evicted
    }

    // ---- 整个作用域 ----

    suspend fun deleteScope(scope: String) = lock.withLock {
        // 只删这个助手自己的作用域，全局记忆是大家共用的，不能跟着某个助手一起删
        require(scope != MemoryRepository.GLOBAL_MEMORY_ID)
        database.withTransaction {
            dao.deleteAllLinks(scope)
            dao.deleteNodesOf(scope)
            dao.deletePlateEntriesOf(scope)
            dao.deleteBoxesOf(scope)
            dao.deleteMetaOf(scope)
            plateDao.deleteOfAssistant(scope)
            // 旧记忆（memory_node 和 Markdown 文件）同一个事务里一起删，下次启动不会再被迁回宫殿
            oldNodeDao.deleteOfAssistant(scope)
            memoryRepository.deleteFilesOfAssistant(scope)
        }
        changed(scope)
    }

    /** 复制助手时把整座宫殿复制过去，链接按新 id 对上；向量一起带过去 */
    suspend fun copyScope(from: String, to: String) = lock.withLock {
        database.withTransaction {
            val idMap = HashMap<Long, Long>()
            val boxes = dao.boxes(from)
            val boxMap = HashMap<Long, Long>()
            boxes.forEach { b -> boxMap[b.id] = dao.insertBox(b.copy(id = 0, assistantId = to, summaryNodeId = 0, predecessorId = 0)) }
            dao.allNodes(from).forEach { n ->
                val newId = dao.insertNode(n.copy(id = 0, assistantId = to, boxId = boxMap[n.boxId] ?: 0L))
                if (newId > 0) idMap[n.id] = newId
            }
            boxes.forEach { b ->
                val newId = boxMap[b.id] ?: return@forEach
                dao.updateBox(
                    b.copy(
                        id = newId, assistantId = to,
                        summaryNodeId = idMap[b.summaryNodeId] ?: 0L,
                        predecessorId = boxMap[b.predecessorId] ?: 0L,
                    )
                )
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
