package com.legbeat.presentation

import android.hardware.Sensor
import android.hardware.SensorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.legbeat.healthconnect.HealthConnectAvailability
import com.legbeat.healthconnect.HealthConnectManager
import com.legbeat.presentation.theme.ElectricMint
import com.legbeat.presentation.theme.ElectricYellow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.input.KeyboardType
import com.legbeat.service.CadenceAudioAnnouncer
import com.legbeat.service.VoiceSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import com.legbeat.wear.WatchDeviceInfo
import com.legbeat.wear.WearableMessageSender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    healthConnectManager: HealthConnectManager,
    voiceSettings: VoiceSettingsRepository,
    audioAnnouncer: CadenceAudioAnnouncer,
    wearMessageSender: WearableMessageSender,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasHealthPermissions by remember { mutableStateOf(false) }
    var availability by remember { mutableStateOf(HealthConnectAvailability.NOT_SUPPORTED) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        scope.launch {
            hasHealthPermissions = grantedPermissions.containsAll(healthConnectManager.getRequiredPermissions())
            val message = if (hasHealthPermissions) {
                "Health Connect permissions granted!"
            } else {
                "Health Connect write permissions denied"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    var connectedWatches by remember { mutableStateOf<List<WatchDeviceInfo>>(emptyList()) }
    var isSendingTestCadence by remember { mutableStateOf(false) }

    fun refreshWatches() {
        scope.launch(Dispatchers.IO) {
            val list = wearMessageSender.getConnectedWatches()
            withContext(Dispatchers.Main) {
                connectedWatches = list
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            availability = healthConnectManager.checkAvailability()
            hasHealthPermissions = healthConnectManager.hasPermissions()
            val list = wearMessageSender.getConnectedWatches()
            withContext(Dispatchers.Main) {
                connectedWatches = list
            }
        }
    }

    val sensorManager = context.getSystemService(SensorManager::class.java)
    val linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Sensors", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Local-First Privacy Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = ElectricMint)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Strict Local-First Architecture",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "LegBeat operates 100% on-device. No cloud backend, no Firebase analytics, and no login accounts exist. Your pedaling dynamics and health records never leave your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }

            // Voice Cadence Dictation Card (Pocket Audio Coach)
            val isVoiceEnabled by voiceSettings.isVoiceEnabled.collectAsState()
            val intervalSec by voiceSettings.announcementIntervalSec.collectAsState()
            var customIntervalInput by remember(intervalSec) { mutableStateOf(intervalSec.toString()) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = ElectricYellow)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Voice Cadence Dictation",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Switch(
                            checked = isVoiceEnabled,
                            onCheckedChange = { voiceSettings.setVoiceEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ElectricYellow
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Periodically dictates your current pedaling cadence aloud via Text-to-Speech (TTS) so you know your RPM without looking at your phone while riding.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Announcement Interval: every $intervalSec seconds",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Quick-Select Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 15, 30, 60).forEach { presetSec ->
                            val isSelected = intervalSec == presetSec
                            Button(
                                onClick = {
                                    voiceSettings.setAnnouncementInterval(presetSec)
                                    customIntervalInput = presetSec.toString()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) ElectricYellow else Color(0xFF2C2C2E),
                                    contentColor = if (isSelected) Color.Black else Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${presetSec}s",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Custom Interval Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customIntervalInput,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() } && input.length <= 4) {
                                    customIntervalInput = input
                                    input.toIntOrNull()?.let { sec ->
                                        if (sec >= 5) {
                                            voiceSettings.setAnnouncementInterval(sec)
                                        }
                                    }
                                }
                            },
                            label = { Text("Custom Seconds", color = Color.Gray, fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = ElectricYellow,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                val sec = customIntervalInput.toIntOrNull() ?: 15
                                voiceSettings.setAnnouncementInterval(sec)
                                Toast.makeText(context, "Interval set to $sec seconds", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricMint,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("Set", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Voice Announcement Button
                    Button(
                        onClick = {
                            audioAnnouncer.speakCustom("Voice dictation active. Current cadence: 88 RPM.")
                            Toast.makeText(context, "Playing test cadence speech...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2C2C2E),
                            contentColor = ElectricYellow
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Cadence Voice Dictation", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Wear OS Bluetooth Companion Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = ElectricYellow)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Wear OS Bluetooth Watch",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        IconButton(onClick = { refreshWatches() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.LightGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Streams real-time cadence and zone updates directly to your paired watch via Google Play Services Wearable Data Layer over Bluetooth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (connectedWatches.isNotEmpty()) {
                        connectedWatches.forEach { watch ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF2C2C2E), shape = RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(ElectricMint, shape = CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = watch.name.ifBlank { "Galaxy Watch 4" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = if (watch.isNearby) "Nearby (BT)" else "Connected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricMint,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2C2C2E), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Searching for paired watch...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Button(
                                onClick = { refreshWatches() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF3C3C3E),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Retry", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Bluetooth Watch Link Button
                    Button(
                        onClick = {
                            scope.launch {
                                isSendingTestCadence = true
                                val result = withContext(Dispatchers.IO) {
                                    wearMessageSender.sendTestCadence(88)
                                }
                                isSendingTestCadence = false
                                val count = result.getOrDefault(0)
                                if (count > 0) {
                                    Toast.makeText(context, "Bluetooth signal sent! (88 RPM to $count watch)", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Dispatched 88 RPM to Wearable Data Layer", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricYellow,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Bluetooth Watch Link (88 RPM)", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Google Health Connect Integration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Google Health Connect",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sync workouts with Android's on-device health bus for unified aggregation with GPS, power, and elevation data from other sports apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Permission Status:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasHealthPermissions) Icons.Default.CheckCircle else Icons.Default.Sync,
                                contentDescription = null,
                                tint = if (hasHealthPermissions) ElectricMint else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (hasHealthPermissions) "Granted" else "Missing Write Access",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasHealthPermissions) ElectricMint else Color.LightGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // "Sync with Health Connect" Jetpack Compose button (Requirement 2)
                    Button(
                        onClick = {
                            scope.launch {
                                if (availability != HealthConnectAvailability.INSTALLED) {
                                    Toast.makeText(
                                        context,
                                        "Google Health Connect is not available on this device",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else if (!hasHealthPermissions) {
                                    // Launch OS permission dialog using createRequestPermissionResultContract()
                                    permissionLauncher.launch(healthConnectManager.getRequiredPermissions())
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Health Connect write permissions are already active",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricYellow,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasHealthPermissions) "Permissions Active" else "Sync with Health Connect",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Hardware & Pocket Sensor Diagnostics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sensors, contentDescription = null, tint = ElectricYellow)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hardware Sensor Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    DiagnosticRow(
                        label = "Linear Acceleration Sensor",
                        value = linearAccelSensor?.name ?: "Hardware Sensor Missing"
                    )
                    DiagnosticRow(
                        label = "Sensor Vendor",
                        value = linearAccelSensor?.vendor ?: "N/A"
                    )
                    DiagnosticRow(
                        label = "Power Draw",
                        value = "${linearAccelSensor?.power ?: 0f} mA"
                    )
                    DiagnosticRow(
                        label = "Sampling Rate",
                        value = "50 Hz (20ms interval)"
                    )
                    DiagnosticRow(
                        label = "FFT Window Size",
                        value = "3.0s (150 samples zero-padded to 256)"
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}
