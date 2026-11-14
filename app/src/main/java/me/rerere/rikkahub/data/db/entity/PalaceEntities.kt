package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 记忆宫殿里的一条记忆，是记忆的原始记录，不从别的地方派生。
 *
 * [createdAt] 是这件事发生的时间，衰减、时间链、日期显示都按它算，用户可以手动改；
 * [recordedAt] 是写进来的时间，不跟着改。
 * [assistantId] 是记忆作用域（助手的 memoryId），同一个作用域下所有窗口共用。
 */
@Entity(
    tableName = "palace_node",
    indices = [
        Index(value = ["assistant_id", "room"]),
        Index(value = ["assistant_id", "created_at"]),
        Index(value = ["assistant_id", "origin"], unique = true),
    ],
)
data class PalaceNodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    val room: String,
    val content: String,
    /** 1 到 10 */
    val importance: Int = 5,
    val mood: String = "neutral",
    /** 效价，没给就按 mood 查表 */
    val valence: Float? = null,
    /** 唤醒度，没给就按 mood 查表 */
    val arousal: Float? = null,
    /** 关键词，一行一个 */
    val tags: String = "",
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("recorded_at")
    val recordedAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
    @ColumnInfo("last_accessed_at")
    val lastAccessedAt: Long,
    @ColumnInfo("access_count")
    val accessCount: Int = 0,
    /** 幂等键，作用域内唯一：`chat:<对话>:<批次>:<序号>`、`legacy:<路径>:<哈希>`、`manual:<随机>` */
    val origin: String,
    /** chat / manual / legacy / digestion */
    val source: String,
    /** 认知消化用过的时间，0 是没用过 */
    @ColumnInfo("digested_at")
    val digestedAt: Long = 0,
    /** 所属事件盒，0 是不在盒子里 */
    @ColumnInfo("box_id")
    val boxId: Long = 0,
    /** 被压进事件盒总结后为 true，不再参与召回，原文保留 */
    val archived: Boolean = false,
    /** 内容每改一次加一；向量写回时版本对不上就作废 */
    @ColumnInfo("content_version")
    val contentVersion: Long = 1,
    @ColumnInfo("embedding_key")
    val embeddingKey: String = "",
    @ColumnInfo("embedding_dims")
    val embeddingDims: Int = 0,
    val embedding: ByteArray = byteArrayOf(),
    @ColumnInfo("embedding_error")
    val embeddingError: String = "",
) {
    val tagList: List<String> get() = tags.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PalaceNodeEntity) return false
        return id == other.id && assistantId == other.assistantId && room == other.room &&
            content == other.content && importance == other.importance && mood == other.mood &&
            valence == other.valence && arousal == other.arousal && tags == other.tags &&
            createdAt == other.createdAt && recordedAt == other.recordedAt && updatedAt == other.updatedAt &&
            lastAccessedAt == other.lastAccessedAt && accessCount == other.accessCount &&
            origin == other.origin && source == other.source && digestedAt == other.digestedAt &&
            boxId == other.boxId && archived == other.archived && contentVersion == other.contentVersion &&
            embeddingKey == other.embeddingKey && embeddingDims == other.embeddingDims &&
            embeddingError == other.embeddingError && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        fun joinTags(tags: List<String>): String =
            tags.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
    }
}

/** 两条记忆之间的链接。无向：存的时候小的 id 放 [fromId] */
@Entity(
    tableName = "palace_link",
    indices = [
        Index(value = ["assistant_id", "from_id", "to_id", "type"], unique = true),
        Index(value = ["assistant_id", "to_id"]),
    ],
)
data class PalaceLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("from_id")
    val fromId: Long,
    @ColumnInfo("to_id")
    val toId: Long,
    val type: String,
    val strength: Double,
)

/** 卧室、书房、「这阵子」门牌里的一条。用户房、自我房的核心背景在 memory_plate */
@Entity(
    tableName = "palace_plate_entry",
    indices = [Index(value = ["assistant_id", "plate"])],
)
data class PalacePlateEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    val plate: String,
    val content: String,
    @ColumnInfo("first_learned_at")
    val firstLearnedAt: Long,
    @ColumnInfo("source_count")
    val sourceCount: Int = 1,
    @ColumnInfo("updated_at")
    val updatedAt: Long,
)

/** 宫殿的零散状态：整理水位、迁移完成标记、消化计数等 */
@Entity(tableName = "palace_meta", primaryKeys = ["assistant_id", "key"])
data class PalaceMetaEntity(
    @ColumnInfo("assistant_id")
    val assistantId: String,
    val key: String,
    val value: String,
)
