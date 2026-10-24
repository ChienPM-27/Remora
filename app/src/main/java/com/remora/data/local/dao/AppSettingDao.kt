package com.remora.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.remora.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingDao {

    @Query("SELECT * FROM app_setting WHERE id = 1 LIMIT 1")
    suspend fun getSetting(): AppSettingEntity?

    @Query("SELECT * FROM app_setting WHERE id = 1 LIMIT 1")
    fun getSettingFlow(): Flow<AppSettingEntity?>

    @Query("INSERT OR IGNORE INTO app_setting (id, consent_granted, notifications_enabled, paused, pause_until) VALUES (1, 0, 1, 0, 0)")
    suspend fun ensureRow()

    @Query("UPDATE app_setting SET consent_granted = :granted WHERE id = 1")
    suspend fun setConsent(granted: Boolean)

    @Query("UPDATE app_setting SET paused = :paused WHERE id = 1")
    suspend fun setPaused(paused: Boolean)

    @Query("UPDATE app_setting SET pause_until = :pauseUntil WHERE id = 1")
    suspend fun setPauseUntil(pauseUntil: Long)

    @Query("UPDATE app_setting SET notifications_enabled = :enabled WHERE id = 1")
    suspend fun setNotificationsEnabled(enabled: Boolean)
}
