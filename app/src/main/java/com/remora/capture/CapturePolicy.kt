package com.remora.capture

import com.remora.data.local.entity.AppSettingEntity

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
}
