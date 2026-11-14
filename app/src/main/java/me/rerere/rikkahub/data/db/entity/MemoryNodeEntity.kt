package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条记忆节点：日常小事、共同经历、最近在做的事。
 *
 * 和 [MemoryFileEntity] 并存。Markdown 文件继续承载稳定事实和说话偏好，
 * 节点承载那些不该被「一个月后还成不成立」筛掉的具体小事。
 * 向量只在本地保存；换了向量模型或端点（[embeddingKey] 或维度对不上）就当没有向量，重新计算。
 */
@Entity(
    tableName = "memory_node",
    indices = [
        Index(value = ["assistant_id", "created_at"]),
        Index(value = ["assistant_id", "room"]),
        Index(value = ["assistant_id", "origin"], unique = true),
    ],
)
data class MemoryNodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    /** living_room / bedroom / study / user_room / self_room */
    val room: String,
    val content: String,
    /** 0 到 1 */
    val importance: Float = 0.5f,
    /** 用户说这件事时的心情，没有就空 */
    val mood: String = "",
    /** 逗号分隔的关键词，给没有向量时的兜底检索用 */
    val keywords: String = "",
    /**
     * 这件事发生的时间，毫秒。0 表示不知道什么时候发生的，不能拿创建时间冒充。
     */
    @ColumnInfo("happened_at")
    val happenedAt: Long = 0,
    @ColumnInfo("created_at")
    val createdAt: Long = 0,
    /** 内容每次改动加一；后台写向量时必须带上读到的版本，旧向量不能覆盖新内容 */
    @ColumnInfo("content_version")
    val contentVersion: Long = 1,
    @ColumnInfo("updated_at")
    val updatedAt: Long = 0,
    /**
     * 幂等键，在一个记忆库里唯一，不能为空（由 MemoryNodeRepository 保证）。
     * 旧记忆派生的是 `legacy:<路径>:<内容哈希>`，手动添加或改过的旧记忆是 `manual:<随机>`。
     */
    val origin: String = "",
    /** companion 对话、legacy 迁移，或 manual 手动添加 */
    val source: String = "companion",
    /** 算出这条向量的端点和模型标识，空表示还没算 */
    @ColumnInfo("embedding_key")
    val embeddingKey: String = "",
    /** 向量维度，0 表示还没算 */
    @ColumnInfo("embedding_dims")
    val embeddingDims: Int = 0,
    /** 向量字节，小端 float32；空表示还没算 */
    val embedding: ByteArray = byteArrayOf(),
    /** 最近一次建向量失败的原因，空表示没失败过 */
    @ColumnInfo("embedding_error")
    val embeddingError: String = "",
) {
    /** 有用 [key]、[dims] 这套配置算好的向量。换了配置的旧向量不算 */
    fun hasEmbedding(key: String, dims: Int): Boolean =
        embedding.isNotEmpty() && embeddingKey == key && embeddingDims == dims

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryNodeEntity) return false
        return id == other.id && assistantId == other.assistantId && room == other.room &&
            content == other.content && importance == other.importance && mood == other.mood &&
            keywords == other.keywords && happenedAt == other.happenedAt &&
            createdAt == other.createdAt && updatedAt == other.updatedAt &&
            contentVersion == other.contentVersion && origin == other.origin && source == other.source &&
            embeddingKey == other.embeddingKey && embeddingDims == other.embeddingDims &&
            embeddingError == other.embeddingError && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int = id.hashCode()
}
