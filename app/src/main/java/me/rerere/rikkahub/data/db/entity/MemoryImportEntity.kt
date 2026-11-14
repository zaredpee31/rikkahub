package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * 旧记忆已经导入过的来源。
 *
 * 节点被用户删掉后这里仍保留，避免下次启动把同一条又导回来。
 */
@Entity(tableName = "memory_import", primaryKeys = ["assistant_id", "origin"])
data class MemoryImportEntity(
    @ColumnInfo("assistant_id")
    val assistantId: String,
    val origin: String,
)
