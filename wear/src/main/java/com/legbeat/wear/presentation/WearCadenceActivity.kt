package com.legbeat.wear.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.wear.ambient.AmbientLifecycleObserver
import com.legbeat.wear.presentation.theme.LegBeatWearTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WearCadenceActivity : ComponentActivity() {

    private val viewModel: WearCadenceViewModel by viewModels()

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            viewModel.onAmbientChanged(
                isAmbient = true,
                burnInProtection = ambientDetails.burnInProtectionRequired
            )
        }

        override fun onExitAmbient() {
            viewModel.onAmbientChanged(
                isAmbient = false,
                burnInProtection = false
            )
        }

        override fun onUpdateAmbient() {
            // Low-power ambient tick
        }
    }

    private val ambientObserver by lazy {
        AmbientLifecycleObserver(this, ambientCallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Gracefully register ambient observer if wearable library is present on device
        if (isWearableLibraryAvailable()) {
            runCatching {
                lifecycle.addObserver(ambientObserver)
            }
        }

        setContent {
            LegBeatWearTheme {
                WearCadenceScreen(viewModel = viewModel)
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val rpm = intent?.getIntExtra("rpm", 0) ?: 0
        if (rpm > 0) {
            viewModel.simulateCadence(rpm)
        }
    }

    private fun isWearableLibraryAvailable(): Boolean {
        return try {
            Class.forName("com.google.android.wearable.compat.WearableActivityController")
            true
        } catch (e: Throwable) {
            false
        }
    }
}
