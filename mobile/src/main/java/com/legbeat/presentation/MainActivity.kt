package com.legbeat.presentation

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.legbeat.analytics.engine.OfflineCoachingEngine
import com.legbeat.analytics.engine.ZoneCalculator
import com.legbeat.analytics.repository.RideRepository
import com.legbeat.fit.FitActivityEncoder
import com.legbeat.healthconnect.HealthConnectManager
import com.legbeat.presentation.theme.LegBeatTheme
import com.legbeat.service.CadenceTrackingService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.legbeat.service.CadenceAudioAnnouncer
import com.legbeat.service.FlyoverSettingsRepository
import com.legbeat.service.VoiceSettingsRepository
import com.legbeat.wear.WearableMessageSender

sealed class Screen {
    data object Dashboard : Screen()
    data object ActiveRide : Screen()
    data class Summary(val rideId: String) : Screen()
    data object Settings : Screen()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var rideRepository: RideRepository

    @Inject
    lateinit var healthConnectManager: HealthConnectManager

    @Inject
    lateinit var fitEncoder: FitActivityEncoder

    @Inject
    lateinit var zoneCalculator: ZoneCalculator

    @Inject
    lateinit var coachingEngine: OfflineCoachingEngine

    @Inject
    lateinit var voiceSettings: VoiceSettingsRepository

    @Inject
    lateinit var audioAnnouncer: CadenceAudioAnnouncer

    @Inject
    lateinit var wearMessageSender: WearableMessageSender

    @Inject
    lateinit var flyoverSettings: FlyoverSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LegBeatTheme {
                MainNavigation(
                    rideRepository = rideRepository,
                    healthConnectManager = healthConnectManager,
                    fitEncoder = fitEncoder,
                    zoneCalculator = zoneCalculator,
                    coachingEngine = coachingEngine,
                    voiceSettings = voiceSettings,
                    flyoverSettings = flyoverSettings,
                    audioAnnouncer = audioAnnouncer,
                    wearMessageSender = wearMessageSender
                )
            }
        }
    }
}

@Composable
fun MainNavigation(
    rideRepository: RideRepository,
    healthConnectManager: HealthConnectManager,
    fitEncoder: FitActivityEncoder,
    zoneCalculator: ZoneCalculator,
    coachingEngine: OfflineCoachingEngine,
    voiceSettings: VoiceSettingsRepository,
    flyoverSettings: FlyoverSettingsRepository,
    audioAnnouncer: CadenceAudioAnnouncer,
    wearMessageSender: WearableMessageSender
) {
    val context = LocalContext.current
    val isTracking by CadenceTrackingService.isTracking.collectAsState()
    val isPocketAutoStart by voiceSettings.isPocketAutoStartEnabled.collectAsState()

    var currentScreen by remember {
        mutableStateOf<Screen>(if (isTracking) Screen.ActiveRide else Screen.Dashboard)
    }

    // Intercept back button/gesture on any sub-screen and return to Dashboard
    BackHandler(enabled = currentScreen !is Screen.Dashboard) {
        currentScreen = Screen.Dashboard
    }

    // Auto navigate to ActiveRide if tracking is started elsewhere (e.g. notification / auto-start)
    LaunchedEffect(isTracking) {
        if (isTracking && currentScreen !is Screen.ActiveRide) {
            currentScreen = Screen.ActiveRide
        }
    }

    // Pocket Auto-Start: Listen to proximity sensor when enabled and not actively tracking
    DisposableEffect(isPocketAutoStart, isTracking) {
        if (!isPocketAutoStart || isTracking) {
            return@DisposableEffect onDispose {}
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            ?: return@DisposableEffect onDispose {}

        var debounceJob: Job? = null
        val coroutineScope = CoroutineScope(Dispatchers.Main)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.sensor.type != Sensor.TYPE_PROXIMITY) return
                val distance = event.values[0]
                val maxRange = proximitySensor.maximumRange
                val isNear = distance < maxRange && distance <= 5.0f

                if (isNear) {
                    if (debounceJob == null || debounceJob?.isActive == false) {
                        debounceJob = coroutineScope.launch {
                            delay(1200L)
                            if (!isTracking) {
                                audioAnnouncer.speakCustom("Pocket detected. Starting ride tracking.")
                                val startIntent = Intent(context, CadenceTrackingService::class.java).apply {
                                    action = CadenceTrackingService.ACTION_START
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(startIntent)
                                } else {
                                    context.startService(startIntent)
                                }
                                currentScreen = Screen.ActiveRide
                            }
                        }
                    }
                } else {
                    debounceJob?.cancel()
                    debounceJob = null
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)

        onDispose {
            debounceJob?.cancel()
            sensorManager.unregisterListener(listener)
        }
    }

    when (val screen = currentScreen) {
        is Screen.Dashboard -> {
            DashboardScreen(
                rideRepository = rideRepository,
                onStartRide = { currentScreen = Screen.ActiveRide },
                onRideSelected = { rideId -> currentScreen = Screen.Summary(rideId) },
                onOpenSettings = { currentScreen = Screen.Settings }
            )
        }
        is Screen.ActiveRide -> {
            ActiveRideScreen(
                voiceSettings = voiceSettings,
                flyoverSettings = flyoverSettings,
                onRideFinished = { rideId ->
                    currentScreen = Screen.Summary(rideId)
                }
            )
        }
        is Screen.Summary -> {
            PostRideSummaryScreen(
                rideId = screen.rideId,
                rideRepository = rideRepository,
                healthConnectManager = healthConnectManager,
                fitEncoder = fitEncoder,
                zoneCalculator = zoneCalculator,
                coachingEngine = coachingEngine,
                flyoverSettings = flyoverSettings,
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
        is Screen.Settings -> {
            SettingsScreen(
                healthConnectManager = healthConnectManager,
                voiceSettings = voiceSettings,
                flyoverSettings = flyoverSettings,
                audioAnnouncer = audioAnnouncer,
                wearMessageSender = wearMessageSender,
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
    }
}
