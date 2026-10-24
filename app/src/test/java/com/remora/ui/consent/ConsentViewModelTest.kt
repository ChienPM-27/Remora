package com.remora.ui.consent

import com.remora.data.local.dao.AppSettingDao
import com.remora.data.local.entity.AppSettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_whenConsentGrantedIsTrueAndNotificationsEnabledIsFalse_remainsConsented() = runTest {
        val fakeDao = FakeAppSettingDao(
            setting = AppSettingEntity(
                consentGranted = true,
                notificationsEnabled = false,
                paused = false
            )
        )

        val viewModel = ConsentViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.hasConsented.value)
    }

    @Test
    fun init_whenConsentGrantedIsFalseAndNotificationsEnabledIsTrue_notConsented() = runTest {
        val fakeDao = FakeAppSettingDao(
            setting = AppSettingEntity(
                consentGranted = false,
                notificationsEnabled = true,
                paused = false
            )
        )

        val viewModel = ConsentViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.hasConsented.value)
    }

    @Test
    fun init_whenSettingIsNull_notConsented() = runTest {
        val fakeDao = FakeAppSettingDao(setting = null)

        val viewModel = ConsentViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.hasConsented.value)
    }

    @Test
    fun grantConsent_setsConsentGrantedAndDoesNotResetPausedOrNotifications() = runTest {
        val fakeDao = FakeAppSettingDao(
            setting = AppSettingEntity(
                consentGranted = false,
                notificationsEnabled = false,
                paused = true,
                pauseUntil = 12345L
            )
        )

        val viewModel = ConsentViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.hasConsented.value)

        var callbackCalled = false
        viewModel.grantConsent {
            callbackCalled = true
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(callbackCalled)
        assertTrue(viewModel.hasConsented.value)
        assertEquals(true, fakeDao.setting?.consentGranted)
        assertEquals(true, fakeDao.setting?.paused)
        assertEquals(false, fakeDao.setting?.notificationsEnabled)
        assertEquals(12345L, fakeDao.setting?.pauseUntil)
    }

    @Test
    fun grantConsent_whenSettingInitiallyNull_createsRowWithConsentGrantedTrue() = runTest {
        val fakeDao = FakeAppSettingDao(setting = null)

        val viewModel = ConsentViewModel(fakeDao)
        testDispatcher.scheduler.advanceUntilIdle()

        var callbackCalled = false
        viewModel.grantConsent {
            callbackCalled = true
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(callbackCalled)
        assertTrue(viewModel.hasConsented.value)
        assertEquals(true, fakeDao.setting?.consentGranted)
    }
}
