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

    fun setVoiceEnabled(enabled: Boolean) {
        _isVoiceEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setAnnouncementInterval(seconds: Int) {
        val clamped = seconds.coerceIn(5, 600)
        _announcementIntervalSec.value = clamped
        prefs.edit().putInt(KEY_INTERVAL, clamped).apply()
    }

    companion object {
        private const val KEY_ENABLED = "voice_dictation_enabled"
        private const val KEY_INTERVAL = "voice_dictation_interval_sec"
    }
}
