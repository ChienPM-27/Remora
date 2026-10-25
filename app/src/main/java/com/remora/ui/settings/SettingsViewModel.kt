package com.remora.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remora.capture.CapturePolicy
import com.remora.data.local.dao.AppSettingDao
import com.remora.data.local.entity.AppSettingEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val DEFAULT_AUTO_DETECT_SUBTITLE = "bật cửa sổ lưu bài học khi bạn chụp màn hình"
const val STATUS_PAUSED_SUBTITLE = "Đang tạm dừng"

/**
 * Pure function determining whether automatic screenshot detection is enabled.
 * Returns true if capture is NOT suppressed according to [CapturePolicy.isCaptureSuppressed].
 */
fun isAutoDetectEnabled(
    setting: AppSettingEntity?,
    nowMs: Long = System.currentTimeMillis()
): Boolean = !CapturePolicy.isCaptureSuppressed(setting, nowMs)

/**
 * Pure function formatting the status subtitle for auto-detect switch in Settings.
 * - If setting is null: returns default description.
 * - If paused indefinitely: returns "Đang tạm dừng".
 * - If paused temporarily until a future timestamp: returns "Đang tắt đến HH:mm".
 * - Otherwise: returns default description.
 */
fun formatAutoDetectStatusSubtitle(
    setting: AppSettingEntity?,
    nowMs: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String {
    if (setting == null) {
        return DEFAULT_AUTO_DETECT_SUBTITLE
    }
    if (setting.paused) {
        return STATUS_PAUSED_SUBTITLE
    }
    if (setting.pauseUntil > nowMs) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm", locale).withZone(zoneId)
        val formattedTime = formatter.format(Instant.ofEpochMilli(setting.pauseUntil))
        return "Đang tắt đến $formattedTime"
    }
    return DEFAULT_AUTO_DETECT_SUBTITLE
}

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Loaded(val setting: AppSettingEntity?) : SettingsUiState
}

class SettingsViewModel(
    private val appSettingDao: AppSettingDao
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = appSettingDao.getSettingFlow()
        .map<AppSettingEntity?, SettingsUiState> { SettingsUiState.Loaded(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState.Loading
        )

    val setting: StateFlow<AppSettingEntity?> = appSettingDao.getSettingFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun setAutoDetectEnabled(enabled: Boolean): Job = viewModelScope.launch {
        appSettingDao.ensureRow()
        if (enabled) {
            appSettingDao.setPaused(false)
            appSettingDao.setPauseUntil(0L)
        } else {
            appSettingDao.setPaused(true)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean): Job = viewModelScope.launch {
        appSettingDao.ensureRow()
        appSettingDao.setNotificationsEnabled(enabled)
    }
}
