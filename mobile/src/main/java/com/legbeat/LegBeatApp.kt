package com.legbeat

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LegBeatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            org.maplibre.android.MapLibre.getInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
