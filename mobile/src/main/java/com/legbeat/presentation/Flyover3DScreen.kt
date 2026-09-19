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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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

private const val TERRAIN_STYLE_JSON = """
{
  "version": 8,
  "sources": {
    "carto-dark": {
      "type": "raster",
      "tiles": [
        "https://basemaps.cartocdn.com/rastertiles/dark_all/{z}/{x}/{y}@2x.png"
      ],
      "tileSize": 256
    },
    "terrain-dem": {
      "type": "raster-dem",
      "tiles": [
        "https://elevation-tiles-prod.s3.amazonaws.com/terrarium/{z}/{x}/{y}.png"
      ],
      "tileSize": 256,
      "encoding": "terrarium"
    }
  },
  "layers": [
    {
      "id": "carto-dark-layer",
      "type": "raster",
      "source": "carto-dark"
    }
  ],
  "terrain": {
    "source": "terrain-dem",
    "exaggeration": 1.5
  }
}
"""

@Composable
fun Flyover3DDialog(
    ride: Ride,
    samples: List<CadenceSample>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Flyover3DScreen(ride = ride, samples = samples, onDismiss = onDismiss)
    }
}

@Composable
fun Flyover3DScreen(
    ride: Ride,
    samples: List<CadenceSample>,
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

    // Video Recorder instance
    val videoRecorder = remember { FlyoverVideoRecorder(context) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var showSavedDialog by remember { mutableStateOf(false) }

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
    var playbackSpeed by remember { mutableIntStateOf(2) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var currentBearing by remember { mutableStateOf(0.0) }

    // Map references
    var maplibreMapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var currentSample by remember { mutableStateOf<CadenceSample?>(gpsSamples.first()) }

    val totalDurationMs = ride.durationMs.coerceAtLeast(1000L)

    // Animation & Flyover Camera Loop
    LaunchedEffect(isPlaying, playbackSpeed, isRecordingVideo) {
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

                // 65-Degree Tilt Camera Animate
                val camera = CameraPosition.Builder()
                    .target(currentLatLng)
                    .zoom(16.0)
                    .tilt(65.0)
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
                        map.setStyle(Style.Builder().fromJson(TERRAIN_STYLE_JSON)) { _ ->
                            val points = gpsSamples.map { LatLng(it.latitude!!, it.longitude!!) }
                            if (points.isNotEmpty()) {
                                // Draw glowing yellow route polyline
                                map.addPolyline(
                                    PolylineOptions()
                                        .addAll(points)
                                        .color(AndroidColor.parseColor("#CCFF00"))
                                        .width(5.5f)
                                )

                                // Initial Camera Setup with 65° tilt
                                val first = points.first()
                                val second = if (points.size > 1) points[1] else first
                                val initBearing = calculateBearing(first, second)
                                currentBearing = initBearing

                                map.cameraPosition = CameraPosition.Builder()
                                    .target(first)
                                    .zoom(16.0)
                                    .tilt(65.0)
                                    .bearing(initBearing)
                                    .build()

                                currentMarkerRef = map.addMarker(MarkerOptions().position(first).title("Rider"))
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Header: Branding & Close Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xAA000000),
                modifier = Modifier.size(40.dp)
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

            // Watermark / Header
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xAA111111)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LEG",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "BEAT",
                        fontWeight = FontWeight.Black,
                        color = ElectricYellow,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "3D CINEMATIC",
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = ElectricMint,
                        fontSize = 11.sp
                    )
                }
            }

            // Recording Status Indicator or Record Trigger Button
            if (isRecordingVideo) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xDDE53935)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("REC", fontWeight = FontWeight.Black, color = Color.White, fontSize = 12.sp)
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
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
                }
            }
        }

        // Bottom Controls Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
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

                        // Speed Toggle
                        TextButton(
                            onClick = {
                                playbackSpeed = when (playbackSpeed) {
                                    1 -> 2
                                    2 -> 4
                                    else -> 1
                                }
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

                    // Record MP4 Button
                    if (isRecordingVideo) {
                        Button(
                            onClick = {
                                val file = videoRecorder.stopRecording()
                                isRecordingVideo = false
                                recordedFile = file
                                if (file != null && file.exists()) {
                                    showSavedDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Recording", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                // Request MediaProjection capture
                                val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
                                progressFraction = 0f
                                isPlaying = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricMint,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export MP4 Video", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Video Saved Success Dialog
    if (showSavedDialog && recordedFile != null) {
        val file = recordedFile!!
        AlertDialog(
            onDismissRequest = { showSavedDialog = false },
            title = { Text("3D Flyover Video Saved!", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Your cinematic 3D flyover video has been successfully rendered and saved to Movies/LegBeat on your device.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        file.name,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shareIntent = videoRecorder.createShareIntent(file)
                        context.startActivity(Intent.createChooser(shareIntent, "Share 3D Flyover Video"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricYellow, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
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
