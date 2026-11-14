package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity

/** 列表和界面用，不带向量字节 */
data class MemoryNodeSummary(
    val id: Long,
    val assistantId: String,
    val room: String,
    val content: String,
    val importance: Float,
    val mood: String,
    val keywords: String,
    val happenedAt: Long,
    val createdAt: Long,
    val contentVersion: Long,
    val updatedAt: Long,
    val origin: String,
    val source: String,
    val embeddingKey: String,
    val embeddingDims: Int,
    val embeddingError: String,
)

@Dao
interface MemoryNodeDAO {
    @Query(
        "SELECT id, assistant_id AS assistantId, room, content, importance, mood, keywords, " +
            "happened_at AS happenedAt, created_at AS createdAt, content_version AS contentVersion, " +
            "updated_at AS updatedAt, origin, source, embedding_key AS embeddingKey, " +
            "embedding_dims AS embeddingDims, embedding_error AS embeddingError " +
            "FROM memory_node WHERE assistant_id = :assistantId ORDER BY created_at DESC, id DESC"
    )
    fun getSummariesFlow(assistantId: String): Flow<List<MemoryNodeSummary>>

    @Query(
        "SELECT id, assistant_id AS assistantId, room, content, importance, mood, keywords, " +
            "happened_at AS happenedAt, created_at AS createdAt, content_version AS contentVersion, " +
            "updated_at AS updatedAt, origin, source, embedding_key AS embeddingKey, " +
            "embedding_dims AS embeddingDims, embedding_error AS embeddingError " +
            "FROM memory_node WHERE assistant_id = :assistantId ORDER BY created_at DESC, id DESC"
    )
    suspend fun getSummaries(assistantId: String): List<MemoryNodeSummary>

    @Query("SELECT * FROM memory_node WHERE assistant_id = :assistantId AND id = :id")
    suspend fun getNode(assistantId: String, id: Long): MemoryNodeEntity?

    @Query("SELECT * FROM memory_node WHERE assistant_id = :assistantId AND origin = :origin")
    suspend fun getByOrigin(assistantId: String, origin: String): MemoryNodeEntity?

    @Query("SELECT origin FROM memory_node WHERE assistant_id = :assistantId AND origin IN (:origins)")
    suspend fun existingOrigins(assistantId: String, origins: List<String>): List<String>

    /** 还没建向量，或向量是别的模型/端点/维度算的 */
    @Query(
        "SELECT * FROM memory_node WHERE assistant_id = :assistantId " +
            "AND (embedding_key != :key OR embedding_dims != :dims OR length(embedding) = 0) " +
            "ORDER BY id LIMIT :limit"
    )
    suspend fun getNodesNeedingEmbedding(assistantId: String, key: String, dims: Int, limit: Int): List<MemoryNodeEntity>

    /** 召回用：只取已经用当前模型算好向量的 */
    @Query(
        "SELECT * FROM memory_node WHERE assistant_id = :assistantId " +
            "AND embedding_key = :key AND embedding_dims = :dims AND length(embedding) > 0"
    )
    suspend fun getIndexedNodes(assistantId: String, key: String, dims: Int): List<MemoryNodeEntity>

    @Query("SELECT COUNT(*) FROM memory_node WHERE assistant_id = :assistantId")
    suspend fun count(assistantId: String): Int

    @Insert
    suspend fun insert(node: MemoryNodeEntity): Long

    /** 迁移用：同一个来源已经导过就跳过 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(nodes: List<MemoryNodeEntity>): List<Long>

    /**
     * 只有内容版本没变才写向量。返回 0 说明内容在计算期间被改过，这次结果作废。
     */
    @Query(
        "UPDATE memory_node SET embedding = :embedding, embedding_key = :key, embedding_dims = :dims, " +
            "embedding_error = '' WHERE assistant_id = :assistantId AND id = :id AND content_version = :contentVersion"
    )
    suspend fun saveEmbedding(
        assistantId: String,
        id: Long,
        contentVersion: Long,
        embedding: ByteArray,
        key: String,
        dims: Int,
    ): Int

    @Query(
        "UPDATE memory_node SET embedding_error = :error " +
            "WHERE assistant_id = :assistantId AND id = :id AND content_version = :contentVersion"
    )
    suspend fun saveEmbeddingError(assistantId: String, id: Long, contentVersion: Long, error: String)

    /** 改内容；内容变了时由调用方加版本号并清向量（[clearEmbedding]），旧向量不能继续按旧内容召回 */
    @Query(
        "UPDATE memory_node SET room = :room, content = :content, importance = :importance, mood = :mood, " +
            "keywords = :keywords, happened_at = :happenedAt, content_version = :contentVersion, " +
            "updated_at = :updatedAt, " +
            "embedding = CASE WHEN :clearEmbedding THEN X'' ELSE embedding END, " +
            "embedding_key = CASE WHEN :clearEmbedding THEN '' ELSE embedding_key END, " +
            "embedding_dims = CASE WHEN :clearEmbedding THEN 0 ELSE embedding_dims END, " +
            "embedding_error = CASE WHEN :clearEmbedding THEN '' ELSE embedding_error END " +
            "WHERE assistant_id = :assistantId AND id = :id"
    )
    suspend fun updateContent(
        assistantId: String,
        id: Long,
        room: String,
        content: String,
        importance: Float,
        mood: String,
        keywords: String,
        happenedAt: Long,
        contentVersion: Long,
        updatedAt: Long,
        clearEmbedding: Boolean,
    ): Int

    @Query("DELETE FROM memory_node WHERE assistant_id = :assistantId AND id = :id")
    suspend fun delete(assistantId: String, id: Long): Int

    @Query("DELETE FROM memory_node WHERE assistant_id = :assistantId")
    suspend fun deleteOfAssistant(assistantId: String)

    @Query("SELECT * FROM memory_node WHERE assistant_id = :assistantId")
    suspend fun getAll(assistantId: String): List<MemoryNodeEntity>

    @Query("SELECT * FROM memory_node WHERE assistant_id = :assistantId AND source = 'legacy'")
    suspend fun getLegacy(assistantId: String): List<MemoryNodeEntity>

    @Query("DELETE FROM memory_node WHERE assistant_id = :assistantId AND id IN (:ids)")
    suspend fun deleteIds(assistantId: String, ids: List<Long>): Int

    /** 派生自旧记忆的节点被用户改过后，换成新的来源键，不再跟着原文件走 */
    @Query("UPDATE memory_node SET source = :source, origin = :origin WHERE assistant_id = :assistantId AND id = :id")
    suspend fun detach(assistantId: String, id: Long, source: String, origin: String): Int

    @Query(
        "SELECT * FROM memory_node WHERE assistant_id = :assistantId AND room IN (:rooms) " +
            "ORDER BY created_at DESC, id DESC LIMIT :limit"
    )
    suspend fun getRecent(assistantId: String, rooms: List<String>, limit: Int): List<MemoryNodeEntity>
}
