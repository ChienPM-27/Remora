package com.remora.capture

import com.remora.data.local.entity.AppSettingEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePolicyTest {

    @Test
    fun isCaptureSuppressed_whenSettingIsNull_returnsFalse() {
        assertFalse(CapturePolicy.isCaptureSuppressed(setting = null, nowMs = 1_000L))
    }

    @Test
    fun isCaptureSuppressed_whenPausedIsTrue_returnsTrueRegardlessOfPauseUntil() {
        val setting1 = AppSettingEntity(paused = true, pauseUntil = 0L)
        assertTrue(CapturePolicy.isCaptureSuppressed(setting1, nowMs = 1_000L))

        val setting2 = AppSettingEntity(paused = true, pauseUntil = 500L)
        assertTrue(CapturePolicy.isCaptureSuppressed(setting2, nowMs = 1_000L))
    }

    @Test
    fun isCaptureSuppressed_whenPausedIsFalseAndPauseUntilInFuture_returnsTrue() {
        val setting = AppSettingEntity(paused = false, pauseUntil = 2_000L)
        assertTrue(CapturePolicy.isCaptureSuppressed(setting, nowMs = 1_000L))
    }

    @Test
    fun isCaptureSuppressed_whenPausedIsFalseAndPauseUntilInPast_returnsFalse() {
        val setting = AppSettingEntity(paused = false, pauseUntil = 500L)
        assertFalse(CapturePolicy.isCaptureSuppressed(setting, nowMs = 1_000L))
    }

    @Test
    fun isCaptureSuppressed_whenPausedIsFalseAndPauseUntilEqualsNow_returnsFalse() {
        val setting = AppSettingEntity(paused = false, pauseUntil = 1_000L)
        assertFalse(CapturePolicy.isCaptureSuppressed(setting, nowMs = 1_000L))
    }

    @Test
    fun isCaptureSuppressed_whenPausedIsFalseAndPauseUntilZero_returnsFalse() {
        val setting = AppSettingEntity(paused = false, pauseUntil = 0L)
        assertFalse(CapturePolicy.isCaptureSuppressed(setting, nowMs = 1_000L))
    }

    @Test
    fun shouldShowResurfacingAlert_whenSettingIsNull_returnsTrue() {
        assertTrue(CapturePolicy.shouldShowResurfacingAlert(setting = null))
    }

    @Test
    fun shouldShowResurfacingAlert_whenNotificationsEnabledIsTrue_returnsTrue() {
        val setting = AppSettingEntity(notificationsEnabled = true)
        assertTrue(CapturePolicy.shouldShowResurfacingAlert(setting))
    }

    @Test
    fun shouldShowResurfacingAlert_whenNotificationsEnabledIsFalse_returnsFalse() {
        val setting = AppSettingEntity(notificationsEnabled = false)
        assertFalse(CapturePolicy.shouldShowResurfacingAlert(setting))
    }
}
