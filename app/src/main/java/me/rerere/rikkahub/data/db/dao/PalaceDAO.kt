package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.PalaceBoxEntity
import me.rerere.rikkahub.data.db.entity.PalaceLinkEntity
import me.rerere.rikkahub.data.db.entity.PalaceMetaEntity
import me.rerere.rikkahub.data.db.entity.PalaceNodeEntity
import me.rerere.rikkahub.data.db.entity.PalacePlateEntryEntity

/** 列表、检索打分用，不带向量字节 */
data class PalaceNodeLite(
    val id: Long,
    val room: String,
    val content: String,
    val importance: Int,
    val mood: String,
    val valence: Float?,
    val arousal: Float?,
    val tags: String,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val accessCount: Int,
    val source: String,
    val archived: Boolean,
    val boxId: Long,
    val embeddingKey: String,
    val embeddingError: String,
    val hasEmbedding: Boolean,
)

/** 写盒子总结前后对一下成员有没有被改过 */
data class PalaceIdVersion(
    val id: Long,
    val contentVersion: Long,
)

/** 向量检索用 */
data class PalaceVectorRow(
    val id: Long,
    val embedding: ByteArray,
)

private const val LITE =
    "SELECT id, room, content, importance, mood, valence, arousal, tags, created_at AS createdAt, " +
        "last_accessed_at AS lastAccessedAt, access_count AS accessCount, source, archived, box_id AS boxId, " +
        "embedding_key AS embeddingKey, embedding_error AS embeddingError, " +
        "(length(embedding) > 0) AS hasEmbedding FROM palace_node"

@Dao
interface PalaceDAO {
    // ---- 记忆 ----

    @Query("$LITE WHERE assistant_id = :scope ORDER BY created_at DESC, id DESC")
    fun nodesFlow(scope: String): Flow<List<PalaceNodeLite>>

    @Query("$LITE WHERE assistant_id = :scope AND archived = 0")
    suspend fun liveNodes(scope: String): List<PalaceNodeLite>

    @Query("$LITE WHERE assistant_id = :scope AND room = :room AND archived = 0 ORDER BY created_at DESC, id DESC")
    suspend fun liveNodesInRoom(scope: String, room: String): List<PalaceNodeLite>

    @Query("$LITE WHERE assistant_id = :scope AND id IN (:ids)")
    suspend fun nodesByIds(scope: String, ids: List<Long>): List<PalaceNodeLite>

    @Query("SELECT * FROM palace_node WHERE assistant_id = :scope AND id = :id")
    suspend fun node(scope: String, id: Long): PalaceNodeEntity?

    @Query("SELECT * FROM palace_node WHERE assistant_id = :scope")
    suspend fun allNodes(scope: String): List<PalaceNodeEntity>

    @Query("SELECT origin FROM palace_node WHERE assistant_id = :scope AND origin IN (:origins)")
    suspend fun existingOrigins(scope: String, origins: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM palace_node WHERE assistant_id = :scope")
    suspend fun count(scope: String): Int

    @Query("SELECT DISTINCT assistant_id FROM palace_node")
    suspend fun scopes(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNode(node: PalaceNodeEntity): Long

    @Query(
        "UPDATE palace_node SET room = :room, content = :content, importance = :importance, mood = :mood, " +
            "tags = :tags, created_at = :createdAt, updated_at = :updatedAt, content_version = content_version + 1, " +
            "embedding = X'', embedding_key = '', embedding_dims = 0, embedding_error = '' " +
            "WHERE assistant_id = :scope AND id = :id"
    )
    suspend fun rewriteNode(
        scope: String, id: Long, room: String, content: String, importance: Int, mood: String,
        tags: String, createdAt: Long, updatedAt: Long,
    ): Int

    /** 不改正文，向量保留 */
    @Query(
        "UPDATE palace_node SET room = :room, importance = :importance, mood = :mood, tags = :tags, " +
            "created_at = :createdAt, updated_at = :updatedAt WHERE assistant_id = :scope AND id = :id"
    )
    suspend fun updateMeta(
        scope: String, id: Long, room: String, importance: Int, mood: String, tags: String,
        createdAt: Long, updatedAt: Long,
    ): Int

    @Query("UPDATE palace_node SET room = :room, updated_at = :now WHERE assistant_id = :scope AND id IN (:ids)")
    suspend fun moveRoom(scope: String, ids: List<Long>, room: String, now: Long): Int

    @Query(
        "UPDATE palace_node SET access_count = access_count + 1, last_accessed_at = :now " +
            "WHERE assistant_id = :scope AND id IN (:ids)"
    )
    suspend fun markAccessed(scope: String, ids: List<Long>, now: Long)

    @Query("DELETE FROM palace_node WHERE assistant_id = :scope AND id = :id")
    suspend fun deleteNode(scope: String, id: Long): Int

    @Query("DELETE FROM palace_node WHERE assistant_id = :scope")
    suspend fun deleteNodesOf(scope: String)

    // ---- 向量 ----

    @Query(
        "SELECT * FROM palace_node WHERE assistant_id = :scope " +
            "AND (embedding_key != :key OR length(embedding) = 0) ORDER BY id LIMIT :limit"
    )
    suspend fun needingEmbedding(scope: String, key: String, limit: Int): List<PalaceNodeEntity>

    @Query(
        "SELECT id, embedding FROM palace_node WHERE assistant_id = :scope AND archived = 0 " +
            "AND embedding_key = :key AND embedding_dims = :dims AND length(embedding) > 0"
    )
    suspend fun vectors(scope: String, key: String, dims: Int): List<PalaceVectorRow>

    @Query(
        "UPDATE palace_node SET embedding = :embedding, embedding_key = :key, embedding_dims = :dims, " +
            "embedding_error = '' WHERE assistant_id = :scope AND id = :id AND content_version = :version"
    )
    suspend fun saveEmbedding(scope: String, id: Long, version: Long, embedding: ByteArray, key: String, dims: Int): Int

    @Query(
        "UPDATE palace_node SET embedding_error = :error " +
            "WHERE assistant_id = :scope AND id = :id AND content_version = :version"
    )
    suspend fun saveEmbeddingError(scope: String, id: Long, version: Long, error: String)

    @Query("UPDATE palace_node SET embedding = X'', embedding_key = '', embedding_dims = 0, embedding_error = '' WHERE assistant_id = :scope")
    suspend fun clearEmbeddings(scope: String)

    // ---- 链接 ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinks(links: List<PalaceLinkEntity>): List<Long>

    @Query("SELECT * FROM palace_link WHERE assistant_id = :scope AND (from_id IN (:ids) OR to_id IN (:ids))")
    suspend fun linksTouching(scope: String, ids: List<Long>): List<PalaceLinkEntity>

    @Query(
        "UPDATE palace_link SET strength = MIN(1.0, ROUND(strength + :step, 5)) " +
            "WHERE assistant_id = :scope AND from_id IN (:ids) AND to_id IN (:ids)"
    )
    suspend fun strengthenAmong(scope: String, ids: List<Long>, step: Double)

    @Query("DELETE FROM palace_link WHERE assistant_id = :scope AND (from_id = :id OR to_id = :id)")
    suspend fun deleteLinksOf(scope: String, id: Long)

    @Query("DELETE FROM palace_link WHERE assistant_id = :scope")
    suspend fun deleteAllLinks(scope: String)

    @Query("SELECT * FROM palace_link WHERE assistant_id = :scope")
    suspend fun allLinks(scope: String): List<PalaceLinkEntity>

    // ---- 门牌条目 ----

    @Query("SELECT * FROM palace_plate_entry WHERE assistant_id = :scope ORDER BY plate, first_learned_at, id")
    fun plateEntriesFlow(scope: String): Flow<List<PalacePlateEntryEntity>>

    @Query("SELECT * FROM palace_plate_entry WHERE assistant_id = :scope ORDER BY plate, first_learned_at, id")
    suspend fun plateEntries(scope: String): List<PalacePlateEntryEntity>

    @Query("SELECT * FROM palace_plate_entry WHERE assistant_id = :scope AND plate = :plate ORDER BY first_learned_at, id")
    suspend fun plateEntriesOf(scope: String, plate: String): List<PalacePlateEntryEntity>

    @Insert
    suspend fun insertPlateEntries(entries: List<PalacePlateEntryEntity>)

    @Query("UPDATE palace_plate_entry SET content = :content, updated_at = :now WHERE assistant_id = :scope AND id = :id")
    suspend fun updatePlateEntry(scope: String, id: Long, content: String, now: Long): Int

    @Query("DELETE FROM palace_plate_entry WHERE assistant_id = :scope AND id = :id")
    suspend fun deletePlateEntry(scope: String, id: Long): Int

    @Query("DELETE FROM palace_plate_entry WHERE assistant_id = :scope AND plate = :plate")
    suspend fun clearPlate(scope: String, plate: String)

    @Query("DELETE FROM palace_plate_entry WHERE assistant_id = :scope")
    suspend fun deletePlateEntriesOf(scope: String)

    // ---- 状态 ----

    @Query("SELECT value FROM palace_meta WHERE assistant_id = :scope AND `key` = :key")
    suspend fun meta(scope: String, key: String): String?

    @Upsert
    suspend fun setMeta(meta: PalaceMetaEntity)

    @Query("DELETE FROM palace_meta WHERE assistant_id = :scope AND `key` = :key")
    suspend fun deleteMeta(scope: String, key: String)

    @Query("SELECT * FROM palace_meta WHERE assistant_id = :scope")
    suspend fun allMeta(scope: String): List<PalaceMetaEntity>

    @Query("DELETE FROM palace_meta WHERE assistant_id = :scope")
    suspend fun deleteMetaOf(scope: String)

    @Query("UPDATE palace_node SET archived = 1, updated_at = :now WHERE assistant_id = :scope AND id IN (:ids)")
    suspend fun archive(scope: String, ids: List<Long>, now: Long): Int

    @Query("UPDATE palace_node SET importance = MAX(1, MIN(10, importance + :delta)), updated_at = :now WHERE assistant_id = :scope AND id IN (:ids)")
    suspend fun adjustImportance(scope: String, ids: List<Long>, delta: Int, now: Long): Int

    @Query("UPDATE palace_node SET digested_at = :now WHERE assistant_id = :scope AND id IN (:ids)")
    suspend fun markDigested(scope: String, ids: List<Long>, now: Long): Int

    // ---- 事件盒 ----

    @Insert
    suspend fun insertBox(box: PalaceBoxEntity): Long

    @Update
    suspend fun updateBox(box: PalaceBoxEntity)

    @Query("SELECT * FROM palace_box WHERE assistant_id = :scope AND id = :id")
    suspend fun box(scope: String, id: Long): PalaceBoxEntity?

    @Query("SELECT * FROM palace_box WHERE assistant_id = :scope ORDER BY id")
    suspend fun boxes(scope: String): List<PalaceBoxEntity>

    @Query("DELETE FROM palace_box WHERE assistant_id = :scope AND id = :id")
    suspend fun deleteBox(scope: String, id: Long)

    @Query("DELETE FROM palace_box WHERE assistant_id = :scope")
    suspend fun deleteBoxesOf(scope: String)

    /** 盒子里没收起来的成员，不含总结 */
    @Query("$LITE WHERE assistant_id = :scope AND box_id = :box AND archived = 0 AND id != :summary ORDER BY created_at, id")
    suspend fun liveMembers(scope: String, box: Long, summary: Long): List<PalaceNodeLite>

    @Query("SELECT id, content_version AS contentVersion FROM palace_node WHERE assistant_id = :scope AND box_id = :box AND archived = 0 AND id != :summary ORDER BY id")
    suspend fun liveMemberVersions(scope: String, box: Long, summary: Long): List<PalaceIdVersion>

    @Query("SELECT COUNT(*) FROM palace_node WHERE assistant_id = :scope AND box_id = :box AND archived = 0 AND id != :summary")
    suspend fun liveMemberCount(scope: String, box: Long, summary: Long): Int

    /** 累计装过多少条（收起来的也算），不含总结 */
    @Query("SELECT COUNT(*) FROM palace_node WHERE assistant_id = :scope AND box_id = :box AND id != :summary")
    suspend fun memberCount(scope: String, box: Long, summary: Long): Int

    @Query("SELECT COUNT(*) FROM palace_node WHERE assistant_id = :scope AND box_id = :box")
    suspend fun anyInBox(scope: String, box: Long): Int

    /** 只给还不在任何盒子里的记忆装盒 */
    @Query("UPDATE palace_node SET box_id = :box WHERE assistant_id = :scope AND box_id = 0 AND id IN (:ids)")
    suspend fun setBoxIfFree(scope: String, ids: List<Long>, box: Long): Int

    @Query("UPDATE palace_node SET box_id = :to WHERE assistant_id = :scope AND box_id = :from")
    suspend fun moveBoxMembers(scope: String, from: Long, to: Long)

    /** 合并盒子时，被合掉的那个盒子的总结降成普通成员，下次和别的一起重写 */
    @Query("UPDATE palace_node SET source = :source, archived = 0, box_id = :box, updated_at = :now WHERE assistant_id = :scope AND id = :id")
    suspend fun demoteSummary(scope: String, id: Long, box: Long, source: String, now: Long)

    /** 指向已经不存在的盒子的，当成不在盒子里 */
    @Query("UPDATE palace_node SET box_id = 0 WHERE assistant_id = :scope AND box_id = :box")
    suspend fun clearBox(scope: String, box: Long)

    @Query(
        "UPDATE palace_node SET room = :room, content = :content, importance = :importance, mood = :mood, tags = :tags, " +
            "created_at = :createdAt, recorded_at = :now, updated_at = :now, archived = 0, content_version = content_version + 1, " +
            "embedding = :embedding, embedding_key = :key, embedding_dims = :dims, embedding_error = '' " +
            "WHERE assistant_id = :scope AND id = :id"
    )
    suspend fun rewriteSummary(
        scope: String, id: Long, room: String, content: String, importance: Int, mood: String, tags: String,
        createdAt: Long, now: Long, embedding: ByteArray, key: String, dims: Int,
    ): Int
}
