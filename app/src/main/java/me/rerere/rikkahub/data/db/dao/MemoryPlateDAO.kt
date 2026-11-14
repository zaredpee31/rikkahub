package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MemoryPlateEntity

@Dao
interface MemoryPlateDAO {
    @Query("SELECT * FROM memory_plate WHERE assistant_id = :assistantId")
    fun getPlatesFlow(assistantId: String): Flow<List<MemoryPlateEntity>>

    @Query("SELECT * FROM memory_plate WHERE assistant_id = :assistantId")
    suspend fun getPlates(assistantId: String): List<MemoryPlateEntity>

    @Query("SELECT * FROM memory_plate WHERE assistant_id = :assistantId AND kind = :kind")
    suspend fun getPlate(assistantId: String, kind: String): MemoryPlateEntity?

    @Upsert
    suspend fun upsert(plate: MemoryPlateEntity)

    @Query("DELETE FROM memory_plate WHERE assistant_id = :assistantId")
    suspend fun deleteOfAssistant(assistantId: String)
}
