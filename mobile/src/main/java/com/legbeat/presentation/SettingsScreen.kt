package com.legbeat.presentation

import android.hardware.Sensor
import android.hardware.SensorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontFamily
import com.legbeat.service.FlyoverSettingsRepository
import com.legbeat.service.MapLayerType
import kotlin.math.roundToInt
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.input.KeyboardType
import com.legbeat.service.CadenceAudioAnnouncer
import kotlin.math.roundToInt
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
    flyoverSettings: FlyoverSettingsRepository,
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
                                }
                            },
                            label = { Text("Custom Seconds (min 5s)", color = Color.Gray, fontSize = 12.sp) },
                            supportingText = {
                                val entered = customIntervalInput.toIntOrNull()
                                if (entered != null && entered < 5) {
                                    Text("Minimum allowed interval is 5 seconds", color = Color(0xFFFF5252), fontSize = 11.sp)
                                } else {
                                    Text("Allowed: 5 to 600 seconds", color = Color.Gray, fontSize = 11.sp)
                                }
                            },
                            isError = customIntervalInput.isNotEmpty() && (customIntervalInput.toIntOrNull() ?: 0) < 5,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = ElectricYellow,
                                unfocusedBorderColor = Color.DarkGray,
                                errorBorderColor = Color(0xFFFF5252)
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                val entered = customIntervalInput.toIntOrNull()
                                if (entered == null || entered < 5) {
                                    Toast.makeText(context, "Minimum interval is 5 seconds", Toast.LENGTH_SHORT).show()
                                    voiceSettings.setAnnouncementInterval(5)
                                    customIntervalInput = "5"
                                } else {
                                    val clamped = entered.coerceIn(5, 600)
                                    voiceSettings.setAnnouncementInterval(clamped)
                                    customIntervalInput = clamped.toString()
                                    Toast.makeText(context, "Interval set to $clamped seconds", Toast.LENGTH_SHORT).show()
                                }
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // Checkbox: Announce average cadence for last announcement interval
                    val isAnnounceAverage by voiceSettings.isAnnounceAverageIntervalEnabled.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2C2C2E).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { voiceSettings.setAnnounceAverageIntervalEnabled(!isAnnounceAverage) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isAnnounceAverage,
                            onCheckedChange = { voiceSettings.setAnnounceAverageIntervalEnabled(it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = ElectricYellow,
                                uncheckedColor = Color.Gray,
                                checkmarkColor = Color.Black
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Announce Average for Interval",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Announces the average cadence computed over the last interval rather than instantaneous RPM.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Open App on Watch Button
                    Button(
                        onClick = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    wearMessageSender.openAppOnWatch()
                                }
                                val count = result.getOrDefault(0)
                                if (count > 0) {
                                    Toast.makeText(context, "Opening LegBeat on your watch...", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Dispatched open app command to watch", Toast.LENGTH_SHORT).show()
                                }
                            }
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
                        Text("Open App on Watch", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Pocket Auto-Start Card
            val isPocketAutoStart by voiceSettings.isPocketAutoStartEnabled.collectAsState()
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
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = ElectricYellow)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pocket Auto-Start",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Switch(
                            checked = isPocketAutoStart,
                            onCheckedChange = { voiceSettings.setPocketAutoStartEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ElectricYellow
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Automatically starts cadence tracking whenever the proximity sensor detects the phone is placed in your cycling pocket for future rides.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }

            // Measure Only In Pocket Card
            val isMeasureOnlyInPocket by voiceSettings.isMeasureOnlyInPocketEnabled.collectAsState()
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
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = ElectricMint)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Measure Only In Pocket",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Switch(
                            checked = isMeasureOnlyInPocket,
                            onCheckedChange = { voiceSettings.setMeasureOnlyInPocketEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ElectricMint
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Calculates pedaling cadence only when the proximity sensor detects the phone is inside your pocket. Automatically pauses measurement and ignores arm movement when holding the phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }

            // Pedaling Sensor Sensitivity Card
            val sensitivity by voiceSettings.sensorSensitivityPercent.collectAsState()
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
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = ElectricYellow)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pedaling Sensor Sensitivity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "$sensitivity%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = ElectricYellow
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fine-tune movement threshold from 30 to 180 RPM. Higher sensitivity detects gentle leg spin in high gears; lower sensitivity rejects rough road vibration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Slider(
                        value = sensitivity.toFloat(),
                        onValueChange = { voiceSettings.setSensorSensitivityPercent(it.roundToInt()) },
                        valueRange = 0f..100f,
                        steps = 99,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricYellow,
                            activeTrackColor = ElectricYellow,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0% (Strict)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text("50% (Default)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text("100% (High)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }

            // 3D Flyover & Map Replay Settings Card
            val flyoverTilt by flyoverSettings.cameraTiltAngle.collectAsState()
            val flyoverSpeed by flyoverSettings.replaySpeed.collectAsState()
            val flyoverLayer by flyoverSettings.mapLayer.collectAsState()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = ElectricMint)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "3D Video & Map Replay",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Map Layer Style
                    Text(
                        text = "Map Layer Style",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricYellow
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    for (layer in MapLayerType.entries) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { flyoverSettings.setMapLayer(layer) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (flyoverLayer == layer),
                                onClick = { flyoverSettings.setMapLayer(layer) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ElectricYellow,
                                    unselectedColor = Color.Gray
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = layer.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (flyoverLayer == layer) FontWeight.Bold else FontWeight.Normal,
                                    color = Color.White
                                )
                                Text(
                                    text = layer.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ℹ️ Note: Street, Satellite, and Topographic Terrain layers are 100% free with zero API keys. Live vehicle traffic view is not included as it requires proprietary commercial telematics subscriptions (Google/TomTom), violating our zero-cost local-first architecture.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Camera Tilt Angle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Camera Tilt Angle (Pitch)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricYellow
                        )
                        Text(
                            text = "${flyoverTilt.roundToInt()}°",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricMint
                        )
                    }
                    Text(
                        text = "Controls the drone pitch angle during 3D flyovers (30° top-down to 85° horizon).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = flyoverTilt,
                        onValueChange = { flyoverSettings.setCameraTiltAngle(it) },
                        valueRange = 30f..85f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricYellow,
                            activeTrackColor = ElectricYellow,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (preset in listOf(45f, 60f, 65f, 75f)) {
                            TextButton(
                                onClick = { flyoverSettings.setCameraTiltAngle(preset) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (flyoverTilt.roundToInt() == preset.toInt()) ElectricYellow else Color(0xFF2A2A2A),
                                    contentColor = if (flyoverTilt.roundToInt() == preset.toInt()) Color.Black else Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("${preset.toInt()}°", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Replay Speed
                    Text(
                        text = "Default Workout Replay Speed",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricYellow
                    )
                    Text(
                        text = "Speed multiplier for route animation and video recording.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (spd in listOf(1, 2, 4, 8)) {
                            TextButton(
                                onClick = { flyoverSettings.setReplaySpeed(spd) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (flyoverSpeed == spd) ElectricMint else Color(0xFF2A2A2A),
                                    contentColor = if (flyoverSpeed == spd) Color.Black else Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("${spd}x", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
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
