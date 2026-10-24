package com.remora.capture

import com.remora.data.local.dao.AppSettingDao
import com.remora.data.local.entity.AppSettingEntity

const val ONE_HOUR_MS = 3_600_000L

object CapturePolicy {

    /**
     * Determines whether screenshot capture handling should be suppressed.
     * Capture is suppressed if monitoring is explicitly paused or if temporary pause has not elapsed.
     * A null setting row means capture is not suppressed (default allow).
     */
    fun isCaptureSuppressed(setting: AppSettingEntity?, nowMs: Long): Boolean {
        if (setting == null) return false
        return setting.paused || setting.pauseUntil > nowMs
    }

    /**
     * Determines whether resurfacing alert notifications should be shown to the user.
     * A null setting row defaults to showing notifications (enabled).
     */
    fun shouldShowResurfacingAlert(setting: AppSettingEntity?): Boolean {
        return setting?.notificationsEnabled ?: true
    }

    /**
     * Applies capture action mutations to [AppSettingDao].
     * - MUTE_1H: sets pause_until to nowMs + 1 hour (3_600_000 ms) and clears paused.
     * - PAUSE: sets paused to true and clears pause_until.
     * Returns true if the action was handled and modified settings; false otherwise.
     */
    suspend fun applyCaptureAction(action: String, dao: AppSettingDao, nowMs: Long): Boolean {
        return when (action) {
            NotificationHelper.ACTION_MUTE_1H -> {
                dao.ensureRow()
                dao.setPaused(false)
                dao.setPauseUntil(nowMs + ONE_HOUR_MS)
                true
            }
            NotificationHelper.ACTION_PAUSE -> {
                dao.ensureRow()
                dao.setPaused(true)
                dao.setPauseUntil(0L)
                true
            }
            else -> false
        }
    }
}

suspend fun applyCaptureAction(action: String, dao: AppSettingDao, nowMs: Long): Boolean =
    CapturePolicy.applyCaptureAction(action, dao, nowMs)
