package com.remora.ui.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remora.data.local.dao.AppSettingDao
import com.remora.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConsentViewModel(private val appSettingDao: AppSettingDao) : ViewModel() {
    val hasConsented: StateFlow<Boolean?> = appSettingDao.getSettingFlow()
        .map { it?.consentGranted == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun grantConsent(onGranted: () -> Unit) {
        viewModelScope.launch {
            val existing = appSettingDao.getSetting()
            appSettingDao.insertOrUpdate(
                (existing ?: AppSettingEntity(paused = true)).copy(consentGranted = true)
            )
            onGranted()
        }
    }
}
