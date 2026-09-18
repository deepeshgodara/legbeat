package com.legbeat.presentation

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legbeat.core.model.CadenceZone
import com.legbeat.presentation.theme.DangerRed
import com.legbeat.presentation.theme.ElectricMint
import com.legbeat.presentation.theme.ElectricYellow
import com.legbeat.service.CadenceTrackingService
import com.legbeat.service.VoiceSettingsRepository
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ActiveRideScreen(
    voiceSettings: VoiceSettingsRepository,
    onRideFinished: (String) -> Unit
) {
    val context = LocalContext.current
    val cadence by CadenceTrackingService.currentCadence.collectAsState()
    val zone by CadenceTrackingService.currentZone.collectAsState()
    val isTracking by CadenceTrackingService.isTracking.collectAsState()
    val lastFinishedRideId by CadenceTrackingService.lastFinishedRideId.collectAsState()
    val isVoiceEnabled by voiceSettings.isVoiceEnabled.collectAsState()
    val voiceIntervalSec by voiceSettings.announcementIntervalSec.collectAsState()
    val isPocketModeActive by voiceSettings.isPocketModeActiveDuringRide.collectAsState()

    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var isPhoneInPocket by remember { mutableStateOf(false) }

    // In-Ride Proximity Sensor listener for Pocket Mode Protection
    DisposableEffect(isPocketModeActive) {
        if (!isPocketModeActive) {
            isPhoneInPocket = false
            return@DisposableEffect onDispose {}
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            ?: return@DisposableEffect onDispose {}

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.sensor.type != Sensor.TYPE_PROXIMITY) return
                val distance = event.values[0]
                val maxRange = proximitySensor.maximumRange
                isPhoneInPocket = distance < maxRange && distance <= 5.0f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)

        onDispose {
            sensorManager.unregisterListener(listener)
            isPhoneInPocket = false
        }
    }

    // Timer loop
    LaunchedEffect(isTracking) {
        if (isTracking) {
            elapsedSeconds = 0L
            while (isTracking) {
                delay(1000L)
                elapsedSeconds++
            }
        }
    }

    // React to ride completion
    LaunchedEffect(lastFinishedRideId) {
        lastFinishedRideId?.let { rideId ->
            onRideFinished(rideId)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            // Header / Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFF00E676), shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RECORDING (POCKET SENSOR)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.LightGray,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Central Cadence Gauge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Zone Tag
                Box(
                    modifier = Modifier
                        .background(Color(zone.colorHex).copy(alpha = 0.2f), shape = RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (cadence == 0) "COASTING" else zone.label.uppercase(),
                        color = if (cadence == 0) Color.Gray else Color(zone.colorHex),
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Huge High-Contrast RPM value
                Text(
                    text = if (cadence == 0) "--" else cadence.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (cadence == 0) Color.DarkGray else ElectricYellow
                    ),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "PEDALING CADENCE (RPM)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Elapsed Duration Counter
                Text(
                    text = formatSeconds(elapsedSeconds),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Quick-Toggle Chips Row: Voice Coach & In-Ride Pocket Mode
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice Coach Toggle Chip
                    Row(
                        modifier = Modifier
                            .background(
                                if (isVoiceEnabled) ElectricYellow.copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                voiceSettings.setVoiceEnabled(!isVoiceEnabled)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isVoiceEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = null,
                            tint = if (isVoiceEnabled) ElectricYellow else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isVoiceEnabled) "Voice: ${voiceIntervalSec}s" else "Muted",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isVoiceEnabled) ElectricYellow else Color.Gray
                        )
                    }

                    // Pocket Mode In-Ride Toggle Chip
                    Row(
                        modifier = Modifier
                            .background(
                                if (isPocketModeActive) ElectricMint.copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                voiceSettings.setPocketModeActiveDuringRide(!isPocketModeActive)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPocketModeActive) Icons.Default.Sensors else Icons.Default.SensorsOff,
                            contentDescription = null,
                            tint = if (isPocketModeActive) ElectricMint else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPocketModeActive) "Pocket Mode: ON" else "Pocket Mode: OFF",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isPocketModeActive) ElectricMint else Color.Gray
                        )
                    }
                }
            }

            // Bottom Section: Info & Stop Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Phone securely kept in pocket. FFT algorithm processes 3-second rolling linear acceleration window to calculate cadence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val stopIntent = Intent(context, CadenceTrackingService::class.java).apply {
                            action = CadenceTrackingService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STOP & SAVE RIDE",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Pocket Mode Touch Protection & Battery Conservation Overlay
        if (isPocketModeActive && isPhoneInPocket) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .clickable(enabled = false) {}, // absorb all unintended pocket touches
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = ElectricMint,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "POCKET LOCK ACTIVE",
                        fontWeight = FontWeight.Black,
                        color = ElectricMint,
                        letterSpacing = 2.sp,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (cadence == 0) "--" else cadence.toString(),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = ElectricYellow
                    )
                    Text(
                        text = "CADENCE (RPM)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Phone is securely inside cycling pocket.\nTouch controls locked to prevent unintended taps.\nTake phone out of pocket to unlock.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

private fun formatSeconds(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
