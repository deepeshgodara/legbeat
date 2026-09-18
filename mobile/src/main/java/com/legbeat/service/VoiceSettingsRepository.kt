package com.legbeat.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("legbeat_voice_settings", Context.MODE_PRIVATE)

    private val _isVoiceEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val isVoiceEnabled: StateFlow<Boolean> = _isVoiceEnabled.asStateFlow()

    private val _announcementIntervalSec = MutableStateFlow(prefs.getInt(KEY_INTERVAL, 15))
    val announcementIntervalSec: StateFlow<Int> = _announcementIntervalSec.asStateFlow()

    private val _isPocketAutoStartEnabled = MutableStateFlow(prefs.getBoolean(KEY_POCKET_AUTO_START, false))
    val isPocketAutoStartEnabled: StateFlow<Boolean> = _isPocketAutoStartEnabled.asStateFlow()

    private val _isPocketModeActiveDuringRide = MutableStateFlow(prefs.getBoolean(KEY_POCKET_MODE_RIDE, true))
    val isPocketModeActiveDuringRide: StateFlow<Boolean> = _isPocketModeActiveDuringRide.asStateFlow()

    private val _sensorSensitivityPercent = MutableStateFlow(prefs.getInt(KEY_SENSITIVITY, 50))
    val sensorSensitivityPercent: StateFlow<Int> = _sensorSensitivityPercent.asStateFlow()

    private val _isMeasureOnlyInPocketEnabled = MutableStateFlow(prefs.getBoolean(KEY_MEASURE_ONLY_IN_POCKET, false))
    val isMeasureOnlyInPocketEnabled: StateFlow<Boolean> = _isMeasureOnlyInPocketEnabled.asStateFlow()

    fun setVoiceEnabled(enabled: Boolean) {
        _isVoiceEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setAnnouncementInterval(seconds: Int) {
        val clamped = seconds.coerceIn(5, 600)
        _announcementIntervalSec.value = clamped
        prefs.edit().putInt(KEY_INTERVAL, clamped).apply()
    }

    fun setPocketAutoStartEnabled(enabled: Boolean) {
        _isPocketAutoStartEnabled.value = enabled
        prefs.edit().putBoolean(KEY_POCKET_AUTO_START, enabled).apply()
    }

    fun setPocketModeActiveDuringRide(enabled: Boolean) {
        _isPocketModeActiveDuringRide.value = enabled
        prefs.edit().putBoolean(KEY_POCKET_MODE_RIDE, enabled).apply()
    }

    fun setSensorSensitivityPercent(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _sensorSensitivityPercent.value = clamped
        prefs.edit().putInt(KEY_SENSITIVITY, clamped).apply()
    }

    fun setMeasureOnlyInPocketEnabled(enabled: Boolean) {
        _isMeasureOnlyInPocketEnabled.value = enabled
        prefs.edit().putBoolean(KEY_MEASURE_ONLY_IN_POCKET, enabled).apply()
    }

    companion object {
        private const val KEY_ENABLED = "voice_dictation_enabled"
        private const val KEY_INTERVAL = "voice_dictation_interval_sec"
        private const val KEY_POCKET_AUTO_START = "pocket_auto_start_enabled"
        private const val KEY_POCKET_MODE_RIDE = "pocket_mode_active_during_ride"
        private const val KEY_SENSITIVITY = "sensor_sensitivity_percent"
        private const val KEY_MEASURE_ONLY_IN_POCKET = "measure_only_in_pocket"
    }
}
