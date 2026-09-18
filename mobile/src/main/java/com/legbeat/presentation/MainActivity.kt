package com.legbeat.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.legbeat.analytics.engine.OfflineCoachingEngine
import com.legbeat.analytics.engine.ZoneCalculator
import com.legbeat.analytics.repository.RideRepository
import com.legbeat.fit.FitActivityEncoder
import com.legbeat.healthconnect.HealthConnectManager
import com.legbeat.presentation.theme.LegBeatTheme
import com.legbeat.service.CadenceTrackingService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import com.legbeat.service.CadenceAudioAnnouncer
import com.legbeat.service.VoiceSettingsRepository

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
                    audioAnnouncer = audioAnnouncer
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
    audioAnnouncer: CadenceAudioAnnouncer
) {
    val isTracking by CadenceTrackingService.isTracking.collectAsState()
    var currentScreen by remember {
        mutableStateOf<Screen>(if (isTracking) Screen.ActiveRide else Screen.Dashboard)
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
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
        is Screen.Settings -> {
            SettingsScreen(
                healthConnectManager = healthConnectManager,
                voiceSettings = voiceSettings,
                audioAnnouncer = audioAnnouncer,
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
    }
}
