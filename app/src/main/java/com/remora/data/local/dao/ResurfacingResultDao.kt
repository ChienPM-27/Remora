package com.remora.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.remora.data.local.entity.ResurfacingResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResurfacingResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<ResurfacingResultEntity>)

    @Query("SELECT * FROM resurfacing_result WHERE new_card_id = :newCardId ORDER BY confidence DESC")
    fun getResultsForCardFlow(newCardId: Long): Flow<List<ResurfacingResultEntity>>

    @Query("SELECT * FROM resurfacing_result ORDER BY created_at DESC, id ASC")
    fun getRecentResultsFlow(): Flow<List<ResurfacingResultEntity>>

    @Query("SELECT * FROM resurfacing_result WHERE new_card_id = :newCardId ORDER BY confidence DESC")
    suspend fun getResultsForCard(newCardId: Long): List<ResurfacingResultEntity>

    @Query("DELETE FROM resurfacing_result WHERE new_card_id = :newCardId")
    suspend fun deleteResultsForCard(newCardId: Long)

    @Query("DELETE FROM resurfacing_result")
    suspend fun deleteAllResults()

    @Query("SELECT id FROM memory_card WHERE id IN (:ids)")
    suspend fun getExistingCardIds(ids: List<Long>): List<Long>

    @Query("UPDATE memory_card SET resurfacing_attempt_count = resurfacing_attempt_count + 1 WHERE id = :cardId AND status = 'DONE' AND resurfacing_status = 'PENDING' AND resurfacing_attempt_count < :maxAttempts")
    suspend fun beginAttempt(cardId: Long, maxAttempts: Int): Int

    @Query("UPDATE memory_card SET resurfacing_status = 'DONE' WHERE id = :cardId AND status = 'DONE' AND resurfacing_status = 'PENDING'")
    suspend fun markCompleted(cardId: Long): Int

    @Query("UPDATE memory_card SET resurfacing_status = 'FAILED' WHERE id = :cardId AND status = 'DONE' AND resurfacing_status = 'PENDING'")
    suspend fun markFailed(cardId: Long): Int

    @Query("UPDATE memory_card SET resurfacing_status = 'PENDING', resurfacing_attempt_count = 0 WHERE id = :cardId AND status = 'DONE' AND resurfacing_status = 'FAILED'")
    suspend fun resetFailed(cardId: Long): Int

    /** Commit the whole source batch and its checkpoint together, including an empty batch. */
    @Transaction
    suspend fun completeBatch(cardId: Long, results: List<ResurfacingResultEntity>): Boolean {
        require(results.all { it.newCardId == cardId }) { "INVALID_BATCH_SOURCE" }
        if (markCompleted(cardId) == 0) return false
        val candidateIds = results.map { it.candidateCardId }.filterNot { it == cardId }.distinct()
        val liveIds = if (candidateIds.isEmpty()) emptySet() else getExistingCardIds(candidateIds).toSet()
        deleteResultsForCard(cardId)
        insertResults(results.filter { it.candidateCardId in liveIds })
        return true
    }
}
