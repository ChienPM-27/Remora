package com.remora.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.remora.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingDao {

    @Query("SELECT * FROM app_setting WHERE id = 1 LIMIT 1")
    suspend fun getSetting(): AppSettingEntity?

    @Query("SELECT * FROM app_setting WHERE id = 1 LIMIT 1")
    fun getSettingFlow(): Flow<AppSettingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(setting: AppSettingEntity)

    @Query("UPDATE app_setting SET paused = :paused WHERE id = 1")
    suspend fun setPaused(paused: Boolean)

    @Query("UPDATE app_setting SET notifications_enabled = :enabled WHERE id = 1")
    suspend fun setNotificationsEnabled(enabled: Boolean)
}
