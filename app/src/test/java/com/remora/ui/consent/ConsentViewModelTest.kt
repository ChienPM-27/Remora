package com.remora.ui.consent

import com.remora.data.local.dao.AppSettingDao
import com.remora.data.local.entity.AppSettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import kotlinx.coroutines.cancel

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentViewModelTest {
    @After
    fun resetDispatcher() { Dispatchers.resetMain() }
    private class Settings : AppSettingDao {
        val state = MutableStateFlow<AppSettingEntity?>(null)
        override suspend fun getSetting() = state.value
        override fun getSettingFlow() = state
        override suspend fun insertOrUpdate(setting: AppSettingEntity) { state.value = setting }
        override suspend fun setPaused(paused: Boolean) { state.value = state.value?.copy(paused = paused) }
        override suspend fun setNotificationsEnabled(enabled: Boolean) { state.value = state.value?.copy(notificationsEnabled = enabled) }
    }

    @Test
    fun notificationAndPauseChangesNeverRevokeConsent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val settings = Settings()
            val vm = ConsentViewModel(settings)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.hasConsented.collect {} }
            runCurrent()
            assertEquals(false, vm.hasConsented.value)
            vm.grantConsent {}
            runCurrent()
            assertEquals(true, vm.hasConsented.value)
            assertTrue(requireNotNull(settings.state.value).paused)
            settings.setNotificationsEnabled(false)
            settings.setPaused(false)
            runCurrent()
            assertEquals(true, vm.hasConsented.value)
            vm.grantConsent {}
            runCurrent()
            assertFalse(requireNotNull(settings.state.value).notificationsEnabled)
        } finally { backgroundScope.cancel(); runCurrent() }
    }
}
