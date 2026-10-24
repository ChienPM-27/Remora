package com.remora.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.remora.data.local.entity.ResurfacingResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResurfacingResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<ResurfacingResultEntity>)

    @Query("SELECT * FROM resurfacing_result WHERE new_card_id = :newCardId ORDER BY confidence DESC")
    fun getResultsForCardFlow(newCardId: Long): Flow<List<ResurfacingResultEntity>>

    @Query("SELECT new_card_id FROM resurfacing_result ORDER BY created_at DESC, id DESC LIMIT 1")
    fun getLatestSourceCardIdFlow(): Flow<Long?>

    @Query("SELECT new_card_id FROM resurfacing_result ORDER BY created_at DESC, id DESC LIMIT 1")
    suspend fun getLatestSourceCardId(): Long?

    @Query("SELECT * FROM resurfacing_result ORDER BY created_at DESC LIMIT 20")
    fun getRecentResultsFlow(): Flow<List<ResurfacingResultEntity>>

    @Query("SELECT * FROM resurfacing_result WHERE new_card_id = :newCardId ORDER BY confidence DESC")
    suspend fun getResultsForCard(newCardId: Long): List<ResurfacingResultEntity>

    @Query("DELETE FROM resurfacing_result WHERE new_card_id = :newCardId")
    suspend fun deleteResultsForCard(newCardId: Long)

    @Query("DELETE FROM resurfacing_result")
    suspend fun deleteAllResults()
}
