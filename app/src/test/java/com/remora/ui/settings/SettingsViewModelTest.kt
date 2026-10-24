package com.remora.ui.settings

import com.remora.data.local.dao.AppSettingDao
import com.remora.data.local.entity.AppSettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeAppSettingDao(
        initialSetting: AppSettingEntity? = null
    ) : AppSettingDao {
        private val _flow = MutableStateFlow(initialSetting)

        var setting: AppSettingEntity?
            get() = _flow.value
            set(value) {
                _flow.value = value
            }

        var ensureRowCount = 0
            private set

        override suspend fun getSetting(): AppSettingEntity? = setting

        override fun getSettingFlow(): Flow<AppSettingEntity?> = _flow.asStateFlow()

        override suspend fun ensureRow() {
            ensureRowCount++
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setting_exposesStateFlowFromDao() = runTest {
        val initial = AppSettingEntity(
            consentGranted = true,
            notificationsEnabled = true,
            paused = false,
            pauseUntil = 0L
        )
        val fakeDao = FakeAppSettingDao(initial)
        val viewModel = SettingsViewModel(fakeDao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.setting.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(initial, viewModel.setting.value)

        val updated = initial.copy(paused = true)
        fakeDao.setting = updated
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(updated, viewModel.setting.value)
    }

    @Test
    fun setAutoDetectEnabled_whenFalse_setsPausedTrue() = runTest {
        val fakeDao = FakeAppSettingDao(
            initialSetting = AppSettingEntity(
                consentGranted = true,
                notificationsEnabled = true,
                paused = false,
                pauseUntil = 0L
            )
        )
        val viewModel = SettingsViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setAutoDetectEnabled(false)
        testDispatcher.scheduler.advanceUntilIdle()

        val current = fakeDao.setting
        assertNotNull(current)
        assertTrue(current!!.paused)
    }

    @Test
    fun setAutoDetectEnabled_whenTrue_setsPausedFalseAndClearsPauseUntil() = runTest {
        val fakeDao = FakeAppSettingDao(
            initialSetting = AppSettingEntity(
                consentGranted = true,
                notificationsEnabled = true,
                paused = true,
                pauseUntil = 5_000L
            )
        )
        val viewModel = SettingsViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setAutoDetectEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        val current = fakeDao.setting
        assertNotNull(current)
        assertFalse(current!!.paused)
        assertEquals(0L, current.pauseUntil)
    }

    @Test
    fun setAutoDetectEnabled_sequence_toggleOnOffOn() = runTest {
        val fakeDao = FakeAppSettingDao(
            initialSetting = AppSettingEntity(
                consentGranted = true,
                notificationsEnabled = true,
                paused = false,
                pauseUntil = 0L
            )
        )
        val viewModel = SettingsViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Initially ON
        assertFalse(fakeDao.setting!!.paused)

        // 2. Toggle to OFF
        viewModel.setAutoDetectEnabled(false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(fakeDao.setting!!.paused)

        // 3. Toggle back to ON
        viewModel.setAutoDetectEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(fakeDao.setting!!.paused)
        assertEquals(0L, fakeDao.setting!!.pauseUntil)

        // 4. Toggle back to OFF
        viewModel.setAutoDetectEnabled(false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(fakeDao.setting!!.paused)
    }

    @Test
    fun setAutoDetectEnabled_whenTemporarilyMuted_turnOnClearsPauseUntil() = runTest {
        val fixedNow = 1_700_000_000_000L
        val muteUntil = fixedNow + 3_600_000L
        val fakeDao = FakeAppSettingDao(
            initialSetting = AppSettingEntity(
                consentGranted = true,
                notificationsEnabled = true,
                paused = false,
                pauseUntil = muteUntil
            )
        )
        val viewModel = SettingsViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        // Before: auto-detect is suppressed by pauseUntil
        assertFalse(isAutoDetectEnabled(fakeDao.setting, fixedNow))
        assertEquals(muteUntil, fakeDao.setting!!.pauseUntil)

        // Action: User turns switch ON from Settings
        viewModel.setAutoDetectEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        // After: pauseUntil must be cleared to 0L and paused must be false
        val current = fakeDao.setting!!
        assertFalse(current.paused)
        assertEquals(0L, current.pauseUntil)
        assertTrue(isAutoDetectEnabled(current, fixedNow))
    }

    @Test
    fun setAutoDetectEnabled_whenDaoInitiallyEmpty_ensuresRowFirst() = runTest {
        val fakeDao = FakeAppSettingDao(initialSetting = null)
        val viewModel = SettingsViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setAutoDetectEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(fakeDao.setting)
        assertFalse(fakeDao.setting!!.paused)
        assertEquals(0L, fakeDao.setting!!.pauseUntil)
        assertTrue(fakeDao.ensureRowCount > 0)
    }

    @Test
    fun setNotificationsEnabled_whenFalse_updatesNotificationsEnabledToFalse() = runTest {
        val fakeDao = FakeAppSettingDao(
            initialSetting = AppSettingEntity(
                consentGranted = true,
                notificationsEnabled = true,
                paused = false,
                pauseUntil = 0L
            )
        )
        val viewModel = SettingsViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setNotificationsEnabled(false)
        testDispatcher.scheduler.advanceUntilIdle()

        val current = fakeDao.setting
        assertNotNull(current)
        assertFalse(current!!.notificationsEnabled)
    }

    @Test
    fun setNotificationsEnabled_whenTrue_updatesNotificationsEnabledToTrue() = runTest {
        val fakeDao = FakeAppSettingDao(
            initialSetting = AppSettingEntity(
                consentGranted = true,
                notificationsEnabled = false,
                paused = false,
                pauseUntil = 0L
            )
        )
        val viewModel = SettingsViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setNotificationsEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        val current = fakeDao.setting
        assertNotNull(current)
        assertTrue(current!!.notificationsEnabled)
    }

    @Test
    fun setNotificationsEnabled_whenDaoInitiallyEmpty_ensuresRowFirst() = runTest {
        val fakeDao = FakeAppSettingDao(initialSetting = null)
        val viewModel = SettingsViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setNotificationsEnabled(false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(fakeDao.setting)
        assertFalse(fakeDao.setting!!.notificationsEnabled)
        assertTrue(fakeDao.ensureRowCount > 0)
    }

    // --- Tests for formatAutoDetectStatusSubtitle pure function ---

    @Test
    fun formatAutoDetectStatusSubtitle_whenSettingIsNull_returnsDefaultDescription() {
        val result = formatAutoDetectStatusSubtitle(setting = null, nowMs = 1_000L)
        assertEquals(DEFAULT_AUTO_DETECT_SUBTITLE, result)
    }

    @Test
    fun formatAutoDetectStatusSubtitle_whenNormalActive_returnsDefaultDescription() {
        val setting = AppSettingEntity(paused = false, pauseUntil = 0L)
        val result = formatAutoDetectStatusSubtitle(setting = setting, nowMs = 1_000L)
        assertEquals(DEFAULT_AUTO_DETECT_SUBTITLE, result)
    }

    @Test
    fun formatAutoDetectStatusSubtitle_whenPausedIndefinitely_returnsDangTamDung() {
        val setting = AppSettingEntity(paused = true, pauseUntil = 0L)
        val result = formatAutoDetectStatusSubtitle(setting = setting, nowMs = 1_000L)
        assertEquals(STATUS_PAUSED_SUBTITLE, result)
        assertEquals("Đang tạm dừng", result)
    }

    @Test
    fun formatAutoDetectStatusSubtitle_whenPausedIndefinitelyWithNonZeroPauseUntil_returnsDangTamDung() {
        val setting = AppSettingEntity(paused = true, pauseUntil = 999_999L)
        val result = formatAutoDetectStatusSubtitle(setting = setting, nowMs = 1_000L)
        assertEquals("Đang tạm dừng", result)
    }

    @Test
    fun formatAutoDetectStatusSubtitle_whenTemporarilyMutedInFuture_returnsDangTatDenHHmm() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val locale = Locale("vi", "VN")
        // 1700000000000L is 2023-11-14T22:13:20Z -> in Asia/Ho_Chi_Minh (+7) it is 05:13 on Nov 15
        val pauseUntil = 1_700_000_000_000L
        val nowMs = pauseUntil - 1_800_000L // 30 minutes earlier

        val setting = AppSettingEntity(paused = false, pauseUntil = pauseUntil)
        val result = formatAutoDetectStatusSubtitle(
            setting = setting,
            nowMs = nowMs,
            zoneId = zone,
            locale = locale
        )

        val expectedTime = DateTimeFormatter.ofPattern("HH:mm", locale).withZone(zone)
            .format(Instant.ofEpochMilli(pauseUntil))
        assertEquals("Đang tắt đến $expectedTime", result)
        assertEquals("Đang tắt đến 05:13", result)
    }

    @Test
    fun formatAutoDetectStatusSubtitle_whenTemporarilyMutedRespectsInjectedZoneId() {
        val utcZone = ZoneId.of("UTC")
        val pauseUntil = 1_700_000_000_000L
        val nowMs = pauseUntil - 60_000L

        val setting = AppSettingEntity(paused = false, pauseUntil = pauseUntil)
        val result = formatAutoDetectStatusSubtitle(
            setting = setting,
            nowMs = nowMs,
            zoneId = utcZone,
            locale = Locale.US
        )

        assertEquals("Đang tắt đến 22:13", result)
    }

    @Test
    fun formatAutoDetectStatusSubtitle_whenTemporaryMuteHasElapsed_returnsDefaultDescription() {
        val pauseUntil = 1_000L
        val nowMs = 2_000L // in the past

        val setting = AppSettingEntity(paused = false, pauseUntil = pauseUntil)
        val result = formatAutoDetectStatusSubtitle(setting = setting, nowMs = nowMs)

        assertEquals(DEFAULT_AUTO_DETECT_SUBTITLE, result)
    }

    @Test
    fun formatAutoDetectStatusSubtitle_whenTemporaryMuteEqualsNow_returnsDefaultDescription() {
        val pauseUntil = 1_000L
        val nowMs = 1_000L

        val setting = AppSettingEntity(paused = false, pauseUntil = pauseUntil)
        val result = formatAutoDetectStatusSubtitle(setting = setting, nowMs = nowMs)

        assertEquals(DEFAULT_AUTO_DETECT_SUBTITLE, result)
    }

    // --- Tests for isAutoDetectEnabled pure function ---

    @Test
    fun isAutoDetectEnabled_whenSettingIsNull_returnsTrue() {
        assertTrue(isAutoDetectEnabled(setting = null, nowMs = 1_000L))
    }

    @Test
    fun isAutoDetectEnabled_whenNormalActive_returnsTrue() {
        val setting = AppSettingEntity(paused = false, pauseUntil = 0L)
        assertTrue(isAutoDetectEnabled(setting = setting, nowMs = 1_000L))
    }

    @Test
    fun isAutoDetectEnabled_whenPausedIndefinitely_returnsFalse() {
        val setting = AppSettingEntity(paused = true, pauseUntil = 0L)
        assertFalse(isAutoDetectEnabled(setting = setting, nowMs = 1_000L))
    }

    @Test
    fun isAutoDetectEnabled_whenTemporarilyMutedInFuture_returnsFalse() {
        val setting = AppSettingEntity(paused = false, pauseUntil = 2_000L)
        assertFalse(isAutoDetectEnabled(setting = setting, nowMs = 1_000L))
    }

    @Test
    fun isAutoDetectEnabled_whenTemporaryMuteHasElapsed_returnsTrue() {
        val setting = AppSettingEntity(paused = false, pauseUntil = 500L)
        assertTrue(isAutoDetectEnabled(setting = setting, nowMs = 1_000L))
    }

    @Test
    fun isAutoDetectEnabled_whenTemporaryMuteEqualsNow_returnsTrue() {
        val setting = AppSettingEntity(paused = false, pauseUntil = 1_000L)
        assertTrue(isAutoDetectEnabled(setting = setting, nowMs = 1_000L))
    }
}
