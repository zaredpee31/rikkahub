package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * 一个记忆库的门牌：常驻的稳定背景，不靠向量召回。
 *
 * 每个记忆库两块：`user` 是用户本人的稳定背景，`self` 是助手对自己的认知。
 * 门牌只放短背景，具体经历放在节点里。
 */
@Entity(tableName = "memory_plate", primaryKeys = ["assistant_id", "kind"])
data class MemoryPlateEntity(
    @ColumnInfo("assistant_id")
    val assistantId: String,
    /** user / self */
    val kind: String,
    val content: String = "",
    @ColumnInfo("updated_at")
    val updatedAt: Long = 0,
    /** user 手动改过，或 consolidator 自动写的 */
    val source: String = "consolidator",
)
