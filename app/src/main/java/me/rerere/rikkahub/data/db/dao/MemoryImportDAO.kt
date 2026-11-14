package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.MemoryImportEntity

@Dao
interface MemoryImportDAO {
    @Query("SELECT origin FROM memory_import WHERE assistant_id = :assistantId")
    suspend fun originsOf(assistantId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(rows: List<MemoryImportEntity>): List<Long>

    @Query("DELETE FROM memory_import WHERE assistant_id = :assistantId AND origin IN (:origins)")
    suspend fun deleteOrigins(assistantId: String, origins: List<String>)

    @Query("DELETE FROM memory_import WHERE assistant_id = :assistantId")
    suspend fun deleteOfAssistant(assistantId: String)
}
