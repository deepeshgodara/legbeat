package com.legbeat.presentation

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableFloatStateOf
import androidx.core.content.FileProvider
import com.legbeat.service.FlyoverSettingsRepository
import com.legbeat.video.FlyoverVideoGenerator
import com.legbeat.video.FlyoverVideoRecorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legbeat.analytics.engine.OfflineCoachingEngine
import com.legbeat.analytics.engine.ZoneCalculator
import com.legbeat.service.CadenceTrackingService
import com.legbeat.analytics.repository.RideRepository
import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.CadenceZone
import com.legbeat.core.model.Ride
import com.legbeat.fit.FitActivityEncoder
import com.legbeat.healthconnect.HealthConnectManager
import com.legbeat.presentation.theme.ElectricMint
import com.legbeat.presentation.theme.ElectricYellow
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostRideSummaryScreen(
    rideId: String,
    rideRepository: RideRepository,
    healthConnectManager: HealthConnectManager,
    fitEncoder: FitActivityEncoder,
    zoneCalculator: ZoneCalculator,
    coachingEngine: OfflineCoachingEngine,
    flyoverSettings: FlyoverSettingsRepository? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ride by remember { mutableStateOf<Ride?>(null) }
    var samples by remember { mutableStateOf<List<CadenceSample>>(emptyList()) }
    var isSyncingToHealth by remember { mutableStateOf(false) }
    var isExportingFit by remember { mutableStateOf(false) }
    var healthSyncedState by remember { mutableStateOf(false) }
    var fitExportPath by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShortRideDialog by remember { mutableStateOf(false) }
    var showFlyoverDialog by remember { mutableStateOf(false) }
    var hasCheckedShortRide by remember(rideId) { mutableStateOf(false) }

    var isGeneratingVideo by remember { mutableStateOf(false) }
    var videoGenerationProgress by remember { mutableFloatStateOf(0f) }
    var videoGenerationStatus by remember { mutableStateOf("") }
    var latestFlyoverVideo by remember { mutableStateOf<File?>(null) }
    var showVideoSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(rideId) {
        val loadedRide = withContext(Dispatchers.IO) { rideRepository.getRideById(rideId) }
        val loadedSamples = withContext(Dispatchers.IO) { rideRepository.getSamplesForRide(rideId) }
        ride = loadedRide
        samples = loadedSamples
        healthSyncedState = loadedRide?.healthConnectSynced == true
        fitExportPath = loadedRide?.fitFilePath
        latestFlyoverVideo = FlyoverVideoRecorder.findLatestVideoForRide(context, rideId)
        if (!hasCheckedShortRide && loadedRide != null) {
            val isShort = loadedRide.durationMs < 90_000L || loadedSamples.size < 10
            if (isShort) {
                showShortRideDialog = true
            }
            hasCheckedShortRide = true
        }
    }

    if (showShortRideDialog && ride != null) {
        val shortRide = ride!!
        AlertDialog(
            onDismissRequest = { showShortRideDialog = false },
            title = { Text("Short Ride Recorded", fontWeight = FontWeight.Bold) },
            text = {
                val durStr = formatDuration(shortRide.durationMs)
                Text("This ride was very short ($durStr). Would you like to delete this session?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDeleteId = shortRide.id
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                rideRepository.deleteRide(toDeleteId)
                            }
                            if (CadenceTrackingService.currentRideId.value == toDeleteId) {
                                val stopIntent = Intent(context, CadenceTrackingService::class.java).apply {
                                    action = CadenceTrackingService.ACTION_STOP
                                }
                                context.startService(stopIntent)
                                CadenceTrackingService.resetTrackingState()
                            }
                            Toast.makeText(context, "Session deleted", Toast.LENGTH_SHORT).show()
                            showShortRideDialog = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                ) {
                    Text("Delete Session", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showShortRideDialog = false }) {
                    Text("Keep Ride")
                }
            }
        )
    }

    if (showDeleteDialog && ride != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Workout", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this workout? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDeleteId = ride?.id ?: return@TextButton
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                rideRepository.deleteRide(toDeleteId)
                            }
                            if (CadenceTrackingService.currentRideId.value == toDeleteId) {
                                val stopIntent = Intent(context, CadenceTrackingService::class.java).apply {
                                    action = CadenceTrackingService.ACTION_STOP
                                }
                                context.startService(stopIntent)
                                CadenceTrackingService.resetTrackingState()
                            }
                            Toast.makeText(context, "Workout deleted", Toast.LENGTH_SHORT).show()
                            showDeleteDialog = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val effectiveFlyoverSettings = flyoverSettings ?: remember { FlyoverSettingsRepository(context) }
    val videoGenerator = remember { FlyoverVideoGenerator(context) }

    fun startVideoGeneration() {
        val currentRide = ride ?: return
        val gpsSamples = samples.filter { it.latitude != null && it.longitude != null }
        if (gpsSamples.size < 2) {
            Toast.makeText(context, "Cannot generate video: at least 2 GPS coordinates required", Toast.LENGTH_LONG).show()
            return
        }
        isGeneratingVideo = true
        videoGenerationProgress = 0.05f
        videoGenerationStatus = "Initializing 3D video rendering engine..."
        scope.launch {
            val file = videoGenerator.generateVideo(
                ride = currentRide,
                samples = samples,
                settings = effectiveFlyoverSettings,
                onProgress = { prog, status ->
                    videoGenerationProgress = prog
                    videoGenerationStatus = status
                }
            )
            isGeneratingVideo = false
            if (file != null && file.exists()) {
                latestFlyoverVideo = file
                showVideoSuccessDialog = true
                Toast.makeText(context, "3D Flyover Video Generated!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Video generation failed. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (isGeneratingVideo) {
        AlertDialog(
            onDismissRequest = { /* Modal during hardware encoding */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = ElectricYellow,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Rendering 3D Video", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = videoGenerationStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { videoGenerationProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = ElectricYellow,
                        trackColor = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${(videoGenerationProgress * 100).toInt()}% completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricMint,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {}
        )
    }

    if (showVideoSuccessDialog && latestFlyoverVideo != null) {
        AlertDialog(
            onDismissRequest = { showVideoSuccessDialog = false },
            title = {
                Text("🎬 3D Flyover Video Ready!", fontWeight = FontWeight.Bold, color = ElectricYellow)
            },
            text = {
                Column {
                    Text("Your cinematic 3D workout flyover video has been successfully rendered and saved to your device gallery.")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "File: ${latestFlyoverVideo?.name ?: ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    val mb = (latestFlyoverVideo?.length() ?: 0L) / (1024.0 * 1024.0)
                    Text(
                        text = String.format(Locale.US, "Size: %.2f MB • 720p HD MP4", mb),
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricMint,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        latestFlyoverVideo?.let { file ->
                            val shareIntent = FlyoverVideoRecorder.createShareIntent(context, file)
                            context.startActivity(Intent.createChooser(shareIntent, "Share 3D Flyover Video to Apps"))
                        }
                        showVideoSuccessDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricMint, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Video Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVideoSuccessDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showFlyoverDialog && ride != null) {
        Flyover3DDialog(
            ride = ride!!,
            samples = samples,
            flyoverSettings = effectiveFlyoverSettings,
            onDismiss = {
                showFlyoverDialog = false
                latestFlyoverVideo = FlyoverVideoRecorder.findLatestVideoForRide(context, ride!!.id)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ride Summary", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Workout",
                            tint = Color(0xFFFF5252)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        val currentRide = ride
        if (currentRide == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ElectricYellow)
            }
            return@Scaffold
        }

        val scrollState = rememberScrollState()
        val zoneBreakdown = remember(samples) { zoneCalculator.calculate(samples) }
        val coachingInsights = remember(currentRide, samples) {
            coachingEngine.generateInsights(currentRide, samples)
        }

        val isVeryShortRide = currentRide.durationMs < 90_000L || samples.size < 10
        val totalPedals = remember(currentRide, samples) {
            calculateTotalPedals(samples, currentRide)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Stats Rows (2x2 Grid)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Avg Cadence",
                    value = "${currentRide.avgCadence}",
                    unit = "RPM",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Max Cadence",
                    value = "${currentRide.maxCadence}",
                    unit = "RPM",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Duration",
                    value = formatDuration(currentRide.durationMs),
                    unit = "Time",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Total Pedals",
                    value = "$totalPedals",
                    unit = "Revolutions",
                    modifier = Modifier.weight(1f)
                )
            }

            // Interactive Route Replay with Scrubbing HUD
            RouteReplayCard(
                ride = currentRide,
                samples = samples,
                flyoverSettings = effectiveFlyoverSettings,
                onLaunch3DFlyover = {
                    showFlyoverDialog = true
                }
            )

            // Vico Time-Series Chart
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cadence Rhythm Over Time",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (samples.isNotEmpty()) {
                        // Downsample if samples > 100 for optimal chart performance
                        val chartStep = (samples.size / 60).coerceAtLeast(1)
                        val chartEntries = samples.filterIndexed { index, _ -> index % chartStep == 0 }
                            .mapIndexed { index, sample ->
                                FloatEntry(x = index.toFloat(), y = sample.rpm.toFloat())
                            }

                        val chartModel = entryModelOf(chartEntries)

                        Chart(
                            chart = lineChart(),
                            model = chartModel,
                            startAxis = rememberStartAxis(
                                valueFormatter = { value, _ -> "${value.toInt()} RPM" }
                            ),
                            bottomAxis = rememberBottomAxis(
                                valueFormatter = { value, _ ->
                                    val sec = (value * chartStep).toInt()
                                    "${sec / 60}m"
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No cadence samples recorded", color = Color.Gray)
                        }
                    }
                }
            }

            // Time in Zones Breakdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Time in Zones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    zoneBreakdown.zones.forEach { zoneDuration ->
                        ZoneRow(
                            label = zoneDuration.zone.label,
                            rpmRange = "${zoneDuration.zone.minRpm}-${zoneDuration.zone.maxRpm} RPM",
                            seconds = zoneDuration.seconds,
                            percentage = zoneDuration.percentage,
                            color = Color(zoneDuration.zone.colorHex)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Offline Coaching Insights Card
            if (isVeryShortRide) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = ElectricYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Offline Coaching Insights",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Aim for a Longer Ride",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricMint
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This ride was too short to generate detailed cadence coaching insights. Try to aim for a longer ride to unlock cadence efficiency analysis and personalized training recommendations!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            } else if (coachingInsights.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = ElectricYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Offline Coaching Insights",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        coachingInsights.forEach { insight ->
                            Text(
                                text = insight.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricMint
                            )
                            Text(
                                text = insight.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Recommendation: ${insight.recommendation}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Action Buttons: Google Health Connect & Garmin FIT Export
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // "Save to Google Health" Button (Requirement 3)
                Button(
                    onClick = {
                        scope.launch {
                            isSyncingToHealth = true
                            val result = withContext(Dispatchers.IO) {
                                healthConnectManager.writeWorkoutSession(currentRide, samples)
                            }
                            if (result.isSuccess) {
                                withContext(Dispatchers.IO) {
                                    rideRepository.markHealthConnectSynced(currentRide.id)
                                }
                                healthSyncedState = true
                                Toast.makeText(context, "Saved to Google Health Connect!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Health Connect Sync Error: ${result.exceptionOrNull()?.localizedMessage ?: "Unknown"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            isSyncingToHealth = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !healthSyncedState && !isSyncingToHealth,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (healthSyncedState) ElectricMint else ElectricYellow,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSyncingToHealth) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Syncing with Health Connect...")
                    } else if (healthSyncedState) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Synced to Google Health", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save to Google Health", fontWeight = FontWeight.Bold)
                    }
                }

                // "Export Garmin FIT File" Button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isExportingFit = true
                            val fitFile = File(context.getExternalFilesDir(null), "legbeat_${currentRide.id}.fit")
                            val result = withContext(Dispatchers.IO) {
                                fitEncoder.encode(fitFile, currentRide, samples)
                            }
                            if (result.isSuccess) {
                                withContext(Dispatchers.IO) {
                                    rideRepository.updateFitPath(currentRide.id, fitFile.absolutePath)
                                }
                                fitExportPath = fitFile.absolutePath
                                Toast.makeText(context, "FIT File saved: ${fitFile.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "FIT Export Error", Toast.LENGTH_SHORT).show()
                            }
                            isExportingFit = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (fitExportPath != null) "FIT File Ready (Strava/TrainingPeaks)" else "Export Garmin .FIT File",
                        color = Color.White
                    )
                }

                // 3D Flyover Video Section (Always present if GPS points exist)
                val hasGpsRoute = samples.any { it.latitude != null && it.longitude != null }
                if (hasGpsRoute) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = ElectricYellow,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "3D Flyover Video",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                val video = latestFlyoverVideo
                                if (video != null && video.exists()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0x3300E676)
                                    ) {
                                        Text(
                                            text = "VIDEO READY",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp,
                                            color = ElectricMint,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val currentVideo = latestFlyoverVideo
                            if (currentVideo != null && currentVideo.exists()) {
                                // Video Output Card Details
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1E1E1E)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = currentVideo.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val mb = currentVideo.length() / (1024.0 * 1024.0)
                                        Text(
                                            text = String.format(Locale.US, "%.2f MB • 720p HD MP4 • Saved in Movies/LegBeat", mb),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Prominent SHARE BUTTON
                                Button(
                                    onClick = {
                                        val shareIntent = FlyoverVideoRecorder.createShareIntent(context, currentVideo)
                                        context.startActivity(Intent.createChooser(shareIntent, "Share 3D Flyover Video to Apps"))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricMint, contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Share Video to Other Apps",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.Black
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // PLAY BUTTON
                                    OutlinedButton(
                                        onClick = {
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", currentVideo)
                                            val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "video/mp4")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(playIntent, "Play 3D Flyover Video"))
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Play Video", color = Color.White)
                                    }

                                    // REGENERATE BUTTON
                                    OutlinedButton(
                                        onClick = { startVideoGeneration() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricYellow)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = ElectricYellow)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Re-render", color = ElectricYellow)
                                    }
                                }
                            } else {
                                // No Video Generated Yet
                                Text(
                                    text = "Generate a cinematic 3D workout route flyover video with your selected tilt angle and map layer style.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { startVideoGeneration() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricYellow, contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generate 3D Flyover Video", fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Interactive 3D Map Preview
                            OutlinedButton(
                                onClick = { showFlyoverDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.LightGray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Interactive 3D Map Preview", color = Color.LightGray)
                            }
                        }
                    }
                }

                // "Delete Workout" Button
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete Workout",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252)
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = ElectricYellow
            )
            Text(unit, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun ZoneRow(
    label: String,
    rpmRange: String,
    seconds: Long,
    percentage: Float,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, shape = RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("($rpmRange)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text(
                "${seconds / 60}m ${seconds % 60}s (${percentage.toInt()}%)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (percentage / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = Color(0xFF333333)
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun calculateTotalPedals(samples: List<CadenceSample>, ride: Ride): Int {
    if (samples.isNotEmpty()) {
        var totalRevs = 0.0
        for (i in 0 until samples.size - 1) {
            val dtMs = (samples[i + 1].timestampMs - samples[i].timestampMs).coerceIn(0L, 3000L)
            val avgRpm = (samples[i].rpm + samples[i + 1].rpm) / 2.0
            totalRevs += avgRpm * (dtMs / 60000.0)
        }
        val rounded = kotlin.math.round(totalRevs).toInt()
        if (rounded > 0) return rounded
    }
    val minutes = ride.durationMs / 60000.0
    return kotlin.math.round(ride.avgCadence * minutes).toInt().coerceAtLeast(0)
}
