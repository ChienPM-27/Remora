package com.remora.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_setting")
data class AppSettingEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long = 1L,

    @ColumnInfo(name = "consent_granted", defaultValue = "0")
    val consentGranted: Boolean = false,

    @ColumnInfo(name = "notifications_enabled")
    val notificationsEnabled: Boolean = true,

    @ColumnInfo(name = "paused")
    val paused: Boolean = false,

    @ColumnInfo(name = "pause_until")
    val pauseUntil: Long = 0L
)
