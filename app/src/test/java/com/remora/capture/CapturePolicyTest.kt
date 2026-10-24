package com.remora.capture

import com.remora.data.local.dao.AppSettingDao
import com.remora.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePolicyTest {

    private class FakeAppSettingDao(
        var setting: AppSettingEntity? = null
    ) : AppSettingDao {
        override suspend fun getSetting(): AppSettingEntity? = setting

        override fun getSettingFlow(): Flow<AppSettingEntity?> = flowOf(setting)

        override suspend fun ensureRow() {
            if (setting == null) {
                setting = AppSettingEntity()
            }
        }

        override suspend fun setConsent(granted: Boolean) {
            ensureRow()
            setting = setting?.copy(consentGranted = granted)
        }

        override suspend fun setPaused(paused: Boolean) {
            ensureRow()
            setting = setting?.copy(paused = paused)
        }

        override suspend fun setPauseUntil(pauseUntil: Long) {
            ensureRow()
            setting = setting?.copy(pauseUntil = pauseUntil)
        }

        override suspend fun setNotificationsEnabled(enabled: Boolean) {
            ensureRow()
            setting = setting?.copy(notificationsEnabled = enabled)
        }
    }

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

    @Test
    fun applyCaptureAction_mute1h_setsPauseUntilToNowPlusOneHour() = runTest {
        val fakeDao = FakeAppSettingDao(
            setting = AppSettingEntity(paused = false, pauseUntil = 0L)
        )
        val fixedNow = 1_700_000_000_000L

        val result = applyCaptureAction(NotificationHelper.ACTION_MUTE_1H, fakeDao, fixedNow)

        assertTrue(result)
        assertEquals(fixedNow + 3_600_000L, fakeDao.setting?.pauseUntil)
        assertFalse(fakeDao.setting?.paused ?: true)
    }

    @Test
    fun applyCaptureAction_mute1h_whenSettingInitiallyNull_initializesRowAndSetsPauseUntil() = runTest {
        val fakeDao = FakeAppSettingDao(setting = null)
        val fixedNow = 1_700_000_000_000L

        val result = applyCaptureAction(NotificationHelper.ACTION_MUTE_1H, fakeDao, fixedNow)

        assertTrue(result)
        assertEquals(fixedNow + 3_600_000L, fakeDao.setting?.pauseUntil)
        assertFalse(fakeDao.setting?.paused ?: true)
    }

    @Test
    fun applyCaptureAction_pause_setsPausedToTrue() = runTest {
        val fakeDao = FakeAppSettingDao(
            setting = AppSettingEntity(paused = false, pauseUntil = 1_000L)
        )
        val fixedNow = 1_700_000_000_000L

        val result = applyCaptureAction(NotificationHelper.ACTION_PAUSE, fakeDao, fixedNow)

        assertTrue(result)
        assertTrue(fakeDao.setting?.paused ?: false)
        assertEquals(0L, fakeDao.setting?.pauseUntil)
    }

    @Test
    fun applyCaptureAction_pause_whenSettingInitiallyNull_initializesRowAndSetsPaused() = runTest {
        val fakeDao = FakeAppSettingDao(setting = null)
        val fixedNow = 1_700_000_000_000L

        val result = applyCaptureAction(NotificationHelper.ACTION_PAUSE, fakeDao, fixedNow)

        assertTrue(result)
        assertTrue(fakeDao.setting?.paused ?: false)
    }

    @Test
    fun applyCaptureAction_unknownOrSkipAction_returnsFalseAndDoesNotModifyDao() = runTest {
        val fakeDao = FakeAppSettingDao(
            setting = AppSettingEntity(paused = false, pauseUntil = 1234L)
        )
        val fixedNow = 1_700_000_000_000L

        val skipResult = applyCaptureAction(NotificationHelper.ACTION_SKIP, fakeDao, fixedNow)
        assertFalse(skipResult)
        assertEquals(1234L, fakeDao.setting?.pauseUntil)
        assertFalse(fakeDao.setting?.paused ?: true)

        val unknownResult = applyCaptureAction("UNKNOWN_ACTION", fakeDao, fixedNow)
        assertFalse(unknownResult)
        assertEquals(1234L, fakeDao.setting?.pauseUntil)
        assertFalse(fakeDao.setting?.paused ?: true)
    }

    @Test
    fun applyCaptureAction_mute1h_whenPreviouslyPaused_resetsPausedToFalseAndSetsPauseUntil() = runTest {
        val fakeDao = FakeAppSettingDao(
            setting = AppSettingEntity(paused = true, pauseUntil = 0L, consentGranted = true, notificationsEnabled = false)
        )
        val fixedNow = 1_700_000_000_000L

        val result = applyCaptureAction(NotificationHelper.ACTION_MUTE_1H, fakeDao, fixedNow)

        assertTrue(result)
        // Must clear paused to false so capture resumes after 1 hour!
        assertFalse(fakeDao.setting?.paused ?: true)
        assertEquals(fixedNow + ONE_HOUR_MS, fakeDao.setting?.pauseUntil)
        // Preserves consent and notificationsEnabled
        assertTrue(fakeDao.setting?.consentGranted ?: false)
        assertFalse(fakeDao.setting?.notificationsEnabled ?: true)

        // During 1 hour: suppressed
        assertTrue(CapturePolicy.isCaptureSuppressed(fakeDao.setting, fixedNow + 1_800_000L))
        // After 1 hour: NOT suppressed anymore (resumed)
        assertFalse(CapturePolicy.isCaptureSuppressed(fakeDao.setting, fixedNow + ONE_HOUR_MS + 1L))
    }

    @Test
    fun applyCaptureAction_pause_whenPreviouslyMuted_clearsPauseUntilToZero() = runTest {
        val fakeDao = FakeAppSettingDao(
            setting = AppSettingEntity(paused = false, pauseUntil = 1_700_003_600_000L, consentGranted = true, notificationsEnabled = true)
        )
        val fixedNow = 1_700_000_000_000L

        val result = applyCaptureAction(NotificationHelper.ACTION_PAUSE, fakeDao, fixedNow)

        assertTrue(result)
        assertTrue(fakeDao.setting?.paused ?: false)
        // pauseUntil must be cleared to 0L so it represents indefinite pause
        assertEquals(0L, fakeDao.setting?.pauseUntil)
        // Preserves consent and notificationsEnabled
        assertTrue(fakeDao.setting?.consentGranted ?: false)
        assertTrue(fakeDao.setting?.notificationsEnabled ?: false)

        // Indefinite pause is always suppressed
        assertTrue(CapturePolicy.isCaptureSuppressed(fakeDao.setting, fixedNow + 10_000_000L))
    }

    @Test
    fun captureActionReceiver_applyCaptureAction_delegatesSuccessfully() = runTest {
        val fakeDao = FakeAppSettingDao(
            setting = AppSettingEntity(paused = true, pauseUntil = 0L)
        )
        val fixedNow = 5_000_000L

        val result = CaptureActionReceiver.applyCaptureAction(NotificationHelper.ACTION_MUTE_1H, fakeDao, fixedNow)

        assertTrue(result)
        assertEquals(5_000_000L + ONE_HOUR_MS, fakeDao.setting?.pauseUntil)
        assertFalse(fakeDao.setting?.paused ?: true)
    }
}
