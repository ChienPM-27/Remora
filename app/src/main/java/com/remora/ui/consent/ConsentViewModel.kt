package com.remora.ui.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remora.data.local.dao.AppSettingDao
import com.remora.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConsentViewModel(
    private val appSettingDao: AppSettingDao
) : ViewModel() {

    private val _hasConsented = MutableStateFlow(false)
    val hasConsented: StateFlow<Boolean> = _hasConsented.asStateFlow()

    init {
        viewModelScope.launch {
            val setting = appSettingDao.getSetting()
            if (setting?.consentGranted == true) {
                _hasConsented.value = true
            }
        }
    }

    fun grantConsent(onGranted: () -> Unit) {
        viewModelScope.launch {
            appSettingDao.insertOrUpdate(
                AppSettingEntity(id = 1L, notificationsEnabled = true, paused = true, consentGranted = true)
            )
            _hasConsented.value = true
            onGranted()
        }
    }
}
