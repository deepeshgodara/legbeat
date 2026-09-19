package com.legbeat.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import com.legbeat.video.FlyoverVideoGenerator
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.CadenceZone
import com.legbeat.core.model.Ride
import com.legbeat.presentation.theme.ElectricMint
import com.legbeat.presentation.theme.ElectricYellow
import com.legbeat.service.FlyoverSettingsRepository
import com.legbeat.service.MapLayerType
import com.legbeat.video.FlyoverVideoRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun Flyover3DDialog(
    ride: Ride,
    samples: List<CadenceSample>,
    flyoverSettings: FlyoverSettingsRepository,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Flyover3DScreen(
            ride = ride,
            samples = samples,
            flyoverSettings = flyoverSettings,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun Flyover3DScreen(
    ride: Ride,
    samples: List<CadenceSample>,
    flyoverSettings: FlyoverSettingsRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val densityDpi = configuration.densityDpi

    val gpsSamples = remember(samples) {
        samples.filter { it.latitude != null && it.longitude != null }
    }

    if (gpsSamples.size < 2) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Insufficient GPS Data") },
            text = { Text("At least 2 GPS coordinates are required to generate a 3D cinematic flyover.") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        )
        return
    }

    // Video Recorder & State
    val videoRecorder = remember { FlyoverVideoRecorder(context) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var existingVideoFile by remember(ride.id) {
        mutableStateOf(FlyoverVideoRecorder.findLatestVideoForRide(context, ride.id))
    }
    var showSavedDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val videoGenerator = remember { FlyoverVideoGenerator(context) }
    var isGeneratingDirectVideo by remember { mutableStateOf(false) }
    var directVideoProgress by remember { mutableFloatStateOf(0f) }
    var directVideoStatus by remember { mutableStateOf("") }

    fun generateDirectVideo(onSuccess: (File) -> Unit = {}) {
        isGeneratingDirectVideo = true
        directVideoProgress = 0.05f
        directVideoStatus = "Initializing 3D video rendering engine..."
        scope.launch {
            val file = videoGenerator.generateVideo(
                ride = ride,
                samples = samples,
                settings = flyoverSettings,
                onProgress = { prog, status ->
                    directVideoProgress = prog
                    directVideoStatus = status
                }
            )
            isGeneratingDirectVideo = false
            if (file != null && file.exists()) {
                recordedFile = file
                existingVideoFile = file
                showSavedDialog = true
                onSuccess(file)
            } else {
                Toast.makeText(context, "Video generation failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Settings State
    val savedTilt by flyoverSettings.cameraTiltAngle.collectAsState()
    val savedSpeed by flyoverSettings.replaySpeed.collectAsState()
    val savedLayer by flyoverSettings.mapLayer.collectAsState()

    var currentTiltAngle by remember { mutableFloatStateOf(savedTilt) }
    var playbackSpeed by remember { mutableIntStateOf(savedSpeed) }
    var currentLayer by remember { mutableStateOf(savedLayer) }

    var showLayerMenu by remember { mutableStateOf(false) }
    var showTiltDialog by remember { mutableStateOf(false) }

    // Screen Capture Permission Launcher
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val started = videoRecorder.startRecording(
                resultCode = result.resultCode,
                data = result.data!!,
                width = screenWidthPx,
                height = screenHeightPx,
                densityDpi = densityDpi,
                rideId = ride.id
            )
            if (started) {
                isRecordingVideo = true
                Toast.makeText(context, "Recording 3D Flyover...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not start video capture", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var currentBearing by remember { mutableStateOf(0.0) }

    // Map references
    var maplibreMapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var currentSample by remember { mutableStateOf<CadenceSample?>(gpsSamples.first()) }

    val totalDurationMs = ride.durationMs.coerceAtLeast(1000L)

    // Helper to apply layer style to MapLibre
    fun applyMapStyle(map: MapLibreMap, layer: MapLayerType) {
        val styleBuilder = if (layer.isRaster) {
            Style.Builder().fromJson(layer.buildStyleJson())
        } else {
            Style.Builder().fromUri(layer.tileOrStyleUrl)
        }
        map.setStyle(styleBuilder) { _ ->
            val points = gpsSamples.map { LatLng(it.latitude!!, it.longitude!!) }
            if (points.isNotEmpty()) {
                map.addPolyline(
                    PolylineOptions()
                        .addAll(points)
                        .color(AndroidColor.parseColor("#CCFF00"))
                        .width(5.5f)
                )

                val targetTimestamp = ride.startTimeMs + (progressFraction * totalDurationMs).toLong()
                val sample = interpolateSampleAt(targetTimestamp, gpsSamples)
                val lat = sample?.latitude ?: points.first().latitude
                val lon = sample?.longitude ?: points.first().longitude

                currentMarkerRef = map.addMarker(
                    MarkerOptions().position(LatLng(lat, lon)).title("Rider")
                )
            }
        }
    }

    // Animation & Flyover Camera Loop
    LaunchedEffect(isPlaying, playbackSpeed, isRecordingVideo, currentTiltAngle) {
        val totalSteps = (totalDurationMs / 50L).coerceAtLeast(20L)
        while (isActive && isPlaying) {
            delay(50L)
            val stepInc = (playbackSpeed.toFloat() / totalSteps.toFloat())
            val nextProg = progressFraction + stepInc

            if (nextProg >= 1f) {
                progressFraction = 1f
                isPlaying = false

                // Stop recording automatically when route finishes
                if (isRecordingVideo) {
                    val file = videoRecorder.stopRecording()
                    isRecordingVideo = false
                    recordedFile = file
                    existingVideoFile = file
                    if (file != null && file.exists()) {
                        showSavedDialog = true
                    }
                }
            } else {
                progressFraction = nextProg
            }

            // Interpolate position and metrics
            val targetTimestamp = ride.startTimeMs + (progressFraction * totalDurationMs).toLong()
            val sample = interpolateSampleAt(targetTimestamp, gpsSamples)
            currentSample = sample

            val map = maplibreMapRef
            val curLat = sample?.latitude
            val curLon = sample?.longitude
            if (map != null && curLat != null && curLon != null) {
                val currentLatLng = LatLng(curLat, curLon)

                // Update rider marker
                if (currentMarkerRef == null) {
                    currentMarkerRef = map.addMarker(MarkerOptions().position(currentLatLng).title("Rider"))
                } else {
                    currentMarkerRef?.position = currentLatLng
                }

                // Compute smooth lookahead bearing
                val lookaheadTimestamp = targetTimestamp + (3000L * playbackSpeed)
                val lookaheadSample = interpolateSampleAt(lookaheadTimestamp, gpsSamples)
                val lookLat = lookaheadSample?.latitude
                val lookLon = lookaheadSample?.longitude
                if (lookLat != null && lookLon != null) {
                    val targetBearing = calculateBearing(
                        currentLatLng,
                        LatLng(lookLat, lookLon)
                    )
                    currentBearing = interpolateAngle(currentBearing, targetBearing, 0.25)
                }

                // Tilt Camera Animation using custom tilt angle setting
                val camera = CameraPosition.Builder()
                    .target(currentLatLng)
                    .zoom(16.0)
                    .tilt(currentTiltAngle.toDouble())
                    .bearing(currentBearing)
                    .build()

                map.easeCamera(CameraUpdateFactory.newCameraPosition(camera), 100)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // MapLibre View
        val mapView = remember {
            MapView(context).apply {
                onCreate(null)
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                if (videoRecorder.isRecording) {
                    videoRecorder.stopRecording()
                }
                mapView.onDestroy()
            }
        }

        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        maplibreMapRef = map
                        applyMapStyle(map, currentLayer)

                        val points = gpsSamples.map { LatLng(it.latitude!!, it.longitude!!) }
                        if (points.isNotEmpty()) {
                            val first = points.first()
                            val second = if (points.size > 1) points[1] else first
                            val initBearing = calculateBearing(first, second)
                            currentBearing = initBearing

                            map.cameraPosition = CameraPosition.Builder()
                                .target(first)
                                .zoom(16.0)
                                .tilt(currentTiltAngle.toDouble())
                                .bearing(initBearing)
                                .build()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Header: Branding, Layer Selector, Tilt, Share, Close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 10.dp, start = 12.dp, end = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close / Back button
            Surface(
                shape = CircleShape,
                color = Color(0xAA000000),
                modifier = Modifier.size(38.dp)
            ) {
                IconButton(onClick = {
                    if (videoRecorder.isRecording) {
                        videoRecorder.stopRecording()
                    }
                    onDismiss()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            // Watermark / Header Branding
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xAA111111)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LEG",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "BEAT",
                        fontWeight = FontWeight.Black,
                        color = ElectricYellow,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "3D FLYOVER",
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = ElectricMint,
                        fontSize = 10.sp
                    )
                }
            }

            // Action Buttons: Layer Switcher, Tilt Setting, Share, REC
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Layer Selector Button & Dropdown Menu
                Box {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xCC222222),
                        modifier = Modifier.size(38.dp)
                    ) {
                        IconButton(onClick = { showLayerMenu = true }) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = "Map Layers",
                                tint = ElectricMint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showLayerMenu,
                        onDismissRequest = { showLayerMenu = false },
                        modifier = Modifier.background(Color(0xFF1E1E1E))
                    ) {
                        MapLayerType.entries.forEach { layer ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = layer.displayName,
                                            fontWeight = if (layer == currentLayer) FontWeight.Bold else FontWeight.Normal,
                                            color = if (layer == currentLayer) ElectricYellow else Color.White,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = layer.description,
                                            color = Color.Gray,
                                            fontSize = 10.sp
                                        )
                                    }
                                },
                                onClick = {
                                    currentLayer = layer
                                    flyoverSettings.setMapLayer(layer)
                                    showLayerMenu = false
                                    maplibreMapRef?.let { map ->
                                        applyMapStyle(map, layer)
                                    }
                                }
                            )
                        }
                    }
                }

                // Tilt Angle Settings Button
                Surface(
                    shape = CircleShape,
                    color = Color(0xCC222222),
                    modifier = Modifier.size(38.dp)
                ) {
                    IconButton(onClick = { showTiltDialog = true }) {
                        Icon(
                            Icons.Default.ScreenRotation,
                            contentDescription = "Camera Tilt",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Video Share Button (Always visible)
                val videoToShare = recordedFile ?: existingVideoFile
                Surface(
                    shape = CircleShape,
                    color = if (videoToShare != null && videoToShare.exists()) ElectricYellow else Color(0xCC333333),
                    modifier = Modifier.size(38.dp)
                ) {
                    IconButton(onClick = {
                        if (videoToShare != null && videoToShare.exists()) {
                            val shareIntent = FlyoverVideoRecorder.createShareIntent(context, videoToShare)
                            context.startActivity(Intent.createChooser(shareIntent, "Share 3D Flyover Video"))
                        } else {
                            generateDirectVideo { newFile ->
                                val shareIntent = FlyoverVideoRecorder.createShareIntent(context, newFile)
                                context.startActivity(Intent.createChooser(shareIntent, "Share 3D Flyover Video"))
                            }
                        }
                    }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share Video",
                            tint = if (videoToShare != null && videoToShare.exists()) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Recording Status Indicator
                if (isRecordingVideo) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xDDE53935)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("REC", fontWeight = FontWeight.Black, color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Live Telemetry HUD Overlay (Bottom-Left)
        currentSample?.let { sample ->
            val zone = CadenceZone.fromRpm(sample.rpm)
            val speedKmh = (sample.speed ?: 0f) * 3.6f
            val altitudeM = sample.altitude

            Surface(
                modifier = Modifier
                    .padding(start = 16.dp, bottom = 100.dp)
                    .align(Alignment.BottomStart),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xCC111111),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${sample.rpm}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color(zone.colorHex)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("RPM", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(zone.label, style = MaterialTheme.typography.labelMedium, color = Color(zone.colorHex))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text("SPEED", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(
                                text = String.format(Locale.US, "%.1f km/h", speedKmh),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = ElectricMint,
                                fontSize = 14.sp
                            )
                        }

                        if (altitudeM != null) {
                            Column {
                                Text("ELEVATION", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(
                                    text = String.format(Locale.US, "%.0f m", altitudeM),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // Current Layer badge
                    Text(
                        text = currentLayer.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Bottom Controls Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            color = Color(0xEE121212)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Progress Indicator
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = ElectricYellow,
                    trackColor = Color(0xFF333333)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause / Restart
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (progressFraction >= 1f) {
                                    progressFraction = 0f
                                }
                                isPlaying = !isPlaying
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(ElectricYellow, CircleShape)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Restart
                        IconButton(
                            onClick = {
                                progressFraction = 0f
                                isPlaying = true
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Speed Toggle (1x, 2x, 4x, 8x)
                        TextButton(
                            onClick = {
                                val nextSpeed = when (playbackSpeed) {
                                    1 -> 2
                                    2 -> 4
                                    4 -> 8
                                    else -> 1
                                }
                                playbackSpeed = nextSpeed
                                flyoverSettings.setReplaySpeed(nextSpeed)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = Color(0xFF262626),
                                contentColor = ElectricMint
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("${playbackSpeed}x", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Export & Share MP4 Button
                    Button(
                        onClick = {
                            generateDirectVideo { file ->
                                val shareIntent = FlyoverVideoRecorder.createShareIntent(context, file)
                                context.startActivity(Intent.createChooser(shareIntent, "Share 3D Flyover Video"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricMint,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export & Share MP4", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Camera Tilt Angle Adjustment Dialog
    if (showTiltDialog) {
        AlertDialog(
            onDismissRequest = { showTiltDialog = false },
            title = {
                Text(
                    "Camera Tilt Angle",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Set camera pitch angle for the 3D cinematic drone flyover:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "${currentTiltAngle.roundToInt()}° Pitch",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = ElectricYellow,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = currentTiltAngle,
                        onValueChange = {
                            currentTiltAngle = it
                            flyoverSettings.setCameraTiltAngle(it)
                        },
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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("30° (Top-down)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text("65° (Cinematic)", style = MaterialTheme.typography.labelSmall, color = ElectricMint)
                        Text("85° (Horizon)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(45f, 60f, 65f, 75f).forEach { preset ->
                            TextButton(
                                onClick = {
                                    currentTiltAngle = preset
                                    flyoverSettings.setCameraTiltAngle(preset)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (currentTiltAngle.roundToInt() == preset.toInt()) ElectricYellow else Color(0xFF2A2A2A),
                                    contentColor = if (currentTiltAngle.roundToInt() == preset.toInt()) Color.Black else Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("${preset.toInt()}°", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTiltDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricYellow, contentColor = Color.Black)
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (isGeneratingDirectVideo) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = ElectricYellow, strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Rendering 3D Video", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(directVideoStatus, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { directVideoProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = ElectricYellow,
                        trackColor = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("${(directVideoProgress * 100).toInt()}% completed", style = MaterialTheme.typography.labelSmall, color = ElectricMint, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {}
        )
    }

    // Video Saved Success Dialog with Prominent Share Video Button
    val fileToShare = recordedFile ?: existingVideoFile
    if (showSavedDialog && fileToShare != null) {
        AlertDialog(
            onDismissRequest = { showSavedDialog = false },
            title = { Text("🎬 3D Flyover Video Saved!", fontWeight = FontWeight.Bold, color = ElectricYellow) },
            text = {
                Column {
                    Text("Your cinematic 3D flyover video has been rendered and saved to Movies/LegBeat.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        fileToShare.name,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    val mb = fileToShare.length() / (1024.0 * 1024.0)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        String.format(Locale.US, "Size: %.2f MB • 720p HD MP4", mb),
                        color = ElectricMint,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shareIntent = FlyoverVideoRecorder.createShareIntent(context, fileToShare)
                        context.startActivity(Intent.createChooser(shareIntent, "Share 3D Flyover Video"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricMint, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Video", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavedDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

private fun calculateBearing(from: LatLng, to: LatLng): Double {
    val phi1 = Math.toRadians(from.latitude)
    val phi2 = Math.toRadians(to.latitude)
    val deltaLambda = Math.toRadians(to.longitude - from.longitude)
    val y = Math.sin(deltaLambda) * Math.cos(phi2)
    val x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda)
    val bearing = Math.toDegrees(Math.atan2(y, x))
    return (bearing + 360.0) % 360.0
}

private fun interpolateAngle(current: Double, target: Double, factor: Double): Double {
    var diff = (target - current) % 360.0
    if (diff > 180.0) diff -= 360.0
    if (diff < -180.0) diff += 360.0
    return (current + diff * factor + 360.0) % 360.0
}

private fun interpolateSampleAt(targetTimestamp: Long, samples: List<CadenceSample>): CadenceSample? {
    if (samples.isEmpty()) return null
    if (targetTimestamp <= samples.first().timestampMs) return samples.first()
    if (targetTimestamp >= samples.last().timestampMs) return samples.last()

    var low = 0
    var high = samples.size - 1

    while (low <= high) {
        val mid = (low + high) ushr 1
        val midTime = samples[mid].timestampMs

        when {
            midTime < targetTimestamp -> low = mid + 1
            midTime > targetTimestamp -> high = mid - 1
            else -> return samples[mid]
        }
    }

    val s1 = samples[(low - 1).coerceAtLeast(0)]
    val s2 = samples[low.coerceAtMost(samples.size - 1)]

    val tSpan = (s2.timestampMs - s1.timestampMs).coerceAtLeast(1L)
    val factor = ((targetTimestamp - s1.timestampMs).toFloat() / tSpan).coerceIn(0f, 1f)

    val lat1 = s1.latitude
    val lat2 = s2.latitude
    val lon1 = s1.longitude
    val lon2 = s2.longitude
    val speed1 = s1.speed
    val speed2 = s2.speed
    val alt1 = s1.altitude
    val alt2 = s2.altitude

    val interpolatedLat = if (lat1 != null && lat2 != null) lat1 + (lat2 - lat1) * factor else lat1 ?: lat2
    val interpolatedLon = if (lon1 != null && lon2 != null) lon1 + (lon2 - lon1) * factor else lon1 ?: lon2
    val interpolatedSpeed = if (speed1 != null && speed2 != null) speed1 + (speed2 - speed1) * factor else speed1 ?: speed2
    val interpolatedAlt = if (alt1 != null && alt2 != null) alt1 + (alt2 - alt1) * factor else alt1 ?: alt2
    val interpolatedRpm = (s1.rpm + (s2.rpm - s1.rpm) * factor).toInt()

    return CadenceSample(
        timestampMs = targetTimestamp,
        rpm = interpolatedRpm,
        latitude = interpolatedLat,
        longitude = interpolatedLon,
        altitude = interpolatedAlt,
        speed = interpolatedSpeed
    )
}
