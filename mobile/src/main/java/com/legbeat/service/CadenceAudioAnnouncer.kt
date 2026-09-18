package com.legbeat.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CadenceAudioAnnouncer @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setSpeechRate(1.05f)
            tts?.setPitch(1.0f)
            isInitialized = true
            Log.i(TAG, "TextToSpeech initialized successfully")
        } else {
            Log.e(TAG, "TextToSpeech init failed with status: $status")
        }
    }

    fun speakCadence(rpm: Int) {
        if (!isInitialized || tts == null) {
            initTts()
            return
        }
        val text = if (rpm <= 0) {
            "Cadence zero"
        } else {
            "$rpm RPM"
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CADENCE_ANNOUNCE_${System.currentTimeMillis()}")
    }

    fun speakCustom(message: String) {
        if (!isInitialized || tts == null) {
            initTts()
            return
        }
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "CUSTOM_ANNOUNCE_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TextToSpeech", e)
        } finally {
            tts = null
            isInitialized = false
        }
    }

    companion object {
        private const val TAG = "CadenceAudioAnnouncer"
    }
}
