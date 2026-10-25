package com.remora.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.remora.data.local.entity.MemoryCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryCardDao {

    @Query("SELECT * FROM memory_card ORDER BY created_at DESC")
    fun getAllCardsFlow(): Flow<List<MemoryCardEntity>>

    @Query("SELECT * FROM memory_card WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: Long): MemoryCardEntity?

    @Query("SELECT * FROM memory_card WHERE image_uri = :imageUri LIMIT 1")
    suspend fun getCardByImageUri(imageUri: String): MemoryCardEntity?

    @Query("SELECT COUNT(*) FROM memory_card WHERE image_uri = :imageUri")
    suspend fun countCardsByImageUri(imageUri: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: MemoryCardEntity): Long

    @Update
    suspend fun updateCard(card: MemoryCardEntity)

    @Query("DELETE FROM memory_card WHERE id = :id")
    suspend fun deleteCard(id: Long)

    @Query("SELECT * FROM memory_card WHERE status = 'DONE' AND embedding IS NOT NULL ORDER BY created_at DESC")
    suspend fun getAllDoneCardsWithEmbedding(): List<MemoryCardEntity>

    @Query("SELECT * FROM memory_card WHERE status = 'DONE' AND embedding IS NOT NULL AND id != :excludeId ORDER BY created_at DESC")
    suspend fun getCandidatesExcluding(excludeId: Long): List<MemoryCardEntity>

    @Query("SELECT * FROM memory_card WHERE status = 'DONE' AND embedding IS NOT NULL AND created_at >= :startTime AND created_at <= :endTime ORDER BY created_at DESC")
    suspend fun getDoneCardsInTimeRange(startTime: Long, endTime: Long): List<MemoryCardEntity>

    @Query("SELECT COUNT(*) FROM memory_card")
    suspend fun getCardCount(): Int

    @Query("SELECT COUNT(*) FROM memory_card WHERE status = 'DONE' AND embedding IS NOT NULL")
    suspend fun getDoneCardCount(): Int

    @Query("DELETE FROM memory_card")
    suspend fun deleteAllCards()
}
