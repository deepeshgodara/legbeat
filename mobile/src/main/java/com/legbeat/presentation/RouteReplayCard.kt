package com.legbeat.presentation

import android.content.Intent
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.io.File
import java.util.Locale

@Composable
fun RouteReplayCard(
    ride: Ride,
    samples: List<CadenceSample>,
    modifier: Modifier = Modifier,
    flyoverSettings: FlyoverSettingsRepository? = null,
    onLaunch3DFlyover: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val settings = flyoverSettings ?: remember { FlyoverSettingsRepository(context) }
    val currentSavedLayer by settings.mapLayer.collectAsState()
    var activeLayer by remember { mutableStateOf(currentSavedLayer) }
    var showLayerMenu by remember { mutableStateOf(false) }

    // Check if a pre-rendered flyover video exists for this workout
    val existingVideoFile = remember(ride.id) {
        FlyoverVideoRecorder.findLatestVideoForRide(context, ride.id)
    }

    // Filter samples with valid GPS coordinates
    val gpsSamples = remember(samples) {
        samples.filter { it.latitude != null && it.longitude != null }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Navigation,
                        contentDescription = null,
                        tint = ElectricMint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Interactive Route Replay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Share Video button if a 3D flyover video exists
                    if (existingVideoFile != null && existingVideoFile.exists()) {
                        IconButton(
                            onClick = {
                                val shareIntent = FlyoverVideoRecorder.createShareIntent(context, existingVideoFile)
                                context.startActivity(Intent.createChooser(shareIntent, "Share 3D Flyover Video"))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share Video",
                                tint = ElectricYellow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (gpsSamples.isNotEmpty() && onLaunch3DFlyover != null) {
                        TextButton(
                            onClick = onLaunch3DFlyover,
                            colors = ButtonDefaults.textButtonColors(contentColor = ElectricYellow)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("3D Flyover", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (gpsSamples.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No GPS route recorded for this workout",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Location tracking activates automatically during outdoor rides when location permission is granted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                // Interactive Route Replay State
                val totalDurationMs = ride.durationMs.coerceAtLeast(1000L).toFloat()
                var scrubTimeMs by remember { mutableFloatStateOf(0f) }
                var isPlaying by remember { mutableStateOf(false) }
                var playbackSpeed by remember { mutableIntStateOf(settings.replaySpeed.value) }
                var followMarker by remember { mutableStateOf(false) }

                // Map & Marker references
                var maplibreMapRef by remember { mutableStateOf<MapLibreMap?>(null) }
                var currentMarkerRef by remember { mutableStateOf<Marker?>(null) }
                var routePolylineRef by remember { mutableStateOf<Polyline?>(null) }

                fun applyReplayLayer(map: MapLibreMap, layer: MapLayerType) {
                    val styleBuilder = if (layer.isRaster) {
                        Style.Builder().fromJson(layer.buildStyleJson())
                    } else {
                        Style.Builder().fromUri(layer.tileOrStyleUrl)
                    }
                    map.setStyle(styleBuilder) { _ ->
                        val points = gpsSamples.map { LatLng(it.latitude!!, it.longitude!!) }
                        if (points.isNotEmpty()) {
                            routePolylineRef = map.addPolyline(
                                PolylineOptions()
                                    .addAll(points)
                                    .color(AndroidColor.parseColor("#CCFF00"))
                                    .width(4.5f)
                            )

                            val boundsBuilder = LatLngBounds.Builder()
                            points.forEach { boundsBuilder.include(it) }
                            try {
                                val bounds = boundsBuilder.build()
                                map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 60), 500)
                            } catch (_: Exception) {
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(points.first())
                                    .zoom(14.0)
                                    .build()
                            }

                            val firstPt = points.first()
                            currentMarkerRef = map.addMarker(
                                MarkerOptions().position(firstPt).title("Start")
                            )
                        }
                    }
                }

                // Continuous playback loop
                LaunchedEffect(isPlaying, playbackSpeed, totalDurationMs) {
                    if (isPlaying) {
                        val stepMs = 50L
                        while (isActive && isPlaying) {
                            delay(stepMs)
                            val nextTime = scrubTimeMs + (stepMs * playbackSpeed)
                            if (nextTime >= totalDurationMs) {
                                scrubTimeMs = totalDurationMs
                                isPlaying = false
                            } else {
                                scrubTimeMs = nextTime
                            }
                        }
                    }
                }

                // Interpolate current metrics & position for the current scrubTimeMs
                val currentTimestamp = ride.startTimeMs + scrubTimeMs.toLong()
                val interpolatedSample = remember(currentTimestamp, gpsSamples) {
                    interpolateSampleAt(currentTimestamp, gpsSamples)
                }

                // Update marker position on Map
                LaunchedEffect(interpolatedSample, maplibreMapRef) {
                    val map = maplibreMapRef ?: return@LaunchedEffect
                    val sample = interpolatedSample ?: return@LaunchedEffect
                    val lat = sample.latitude ?: return@LaunchedEffect
                    val lon = sample.longitude ?: return@LaunchedEffect
                    val latLng = LatLng(lat, lon)

                    if (currentMarkerRef == null) {
                        currentMarkerRef = map.addMarker(
                            MarkerOptions().position(latLng).title("Current Position")
                        )
                    } else {
                        currentMarkerRef?.position = latLng
                    }

                    if (followMarker) {
                        map.easeCamera(CameraUpdateFactory.newLatLng(latLng), 100)
                    }
                }

                // Map Container with Floating HUD Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
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
                            mapView.onDestroy()
                        }
                    }

                    AndroidView(
                        factory = {
                            mapView.apply {
                                getMapAsync { map ->
                                    maplibreMapRef = map
                                    applyReplayLayer(map, activeLayer)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Floating HUD Overlay (Top-Start)
                    interpolatedSample?.let { sample ->
                        val zone = CadenceZone.fromRpm(sample.rpm)
                        val speedKmh = (sample.speed ?: 0f) * 3.6f
                        val altitudeM = sample.altitude

                        Surface(
                            modifier = Modifier
                                .padding(10.dp)
                                .align(Alignment.TopStart),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xDD121212)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${sample.rpm} RPM",
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 15.sp,
                                        color = Color(zone.colorHex)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(zone.colorHex), shape = CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = zone.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.LightGray
                                    )
                                }

                                Row(
                                    modifier = Modifier.padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f km/h", speedKmh),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = ElectricMint
                                    )
                                    if (altitudeM != null) {
                                        Text(
                                            text = String.format(Locale.US, "%.0f m", altitudeM),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Map Control Buttons (Top-End)
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Layer Selector Button
                        Box {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xCC222222),
                                modifier = Modifier.size(36.dp)
                            ) {
                                IconButton(onClick = { showLayerMenu = true }) {
                                    Icon(
                                        Icons.Default.Layers,
                                        contentDescription = "Change Layer",
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
                                            Text(
                                                text = layer.displayName,
                                                fontWeight = if (layer == activeLayer) FontWeight.Bold else FontWeight.Normal,
                                                color = if (layer == activeLayer) ElectricYellow else Color.White,
                                                fontSize = 12.sp
                                            )
                                        },
                                        onClick = {
                                            activeLayer = layer
                                            settings.setMapLayer(layer)
                                            showLayerMenu = false
                                            maplibreMapRef?.let { map ->
                                                applyReplayLayer(map, layer)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Recenter Route Bounds
                        Surface(
                            shape = CircleShape,
                            color = Color(0xCC222222),
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(onClick = {
                                val points = gpsSamples.map { LatLng(it.latitude!!, it.longitude!!) }
                                if (points.isNotEmpty()) {
                                    val boundsBuilder = LatLngBounds.Builder()
                                    points.forEach { boundsBuilder.include(it) }
                                    try {
                                        maplibreMapRef?.easeCamera(
                                            CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 60),
                                            500
                                        )
                                    } catch (_: Exception) {}
                                }
                            }) {
                                Icon(
                                    Icons.Default.CenterFocusStrong,
                                    contentDescription = "Fit Route",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Toggle Camera Follow
                        Surface(
                            shape = CircleShape,
                            color = if (followMarker) ElectricYellow else Color(0xCC222222),
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(onClick = { followMarker = !followMarker }) {
                                Icon(
                                    Icons.Default.Navigation,
                                    contentDescription = "Follow Marker",
                                    tint = if (followMarker) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Replay Scrubbing Slider & Controls
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = scrubTimeMs.coerceIn(0f, totalDurationMs),
                        onValueChange = {
                            scrubTimeMs = it
                            isPlaying = false // pause during user manual scrub
                        },
                        valueRange = 0f..totalDurationMs,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricYellow,
                            activeTrackColor = ElectricYellow,
                            inactiveTrackColor = Color(0xFF444444)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Play / Pause, Replay Speed & Timestamp Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (scrubTimeMs >= totalDurationMs) {
                                        scrubTimeMs = 0f
                                    }
                                    isPlaying = !isPlaying
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(ElectricYellow, CircleShape)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Speed Selector Button (1x, 2x, 4x, 8x)
                            TextButton(
                                onClick = {
                                    playbackSpeed = when (playbackSpeed) {
                                        1 -> 2
                                        2 -> 4
                                        4 -> 8
                                        else -> 1
                                    }
                                    settings.setReplaySpeed(playbackSpeed)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = Color(0xFF2A2A2A),
                                    contentColor = ElectricMint
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("${playbackSpeed}x", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        // Formatted Elapsed Time / Total Duration
                        Text(
                            text = "${formatTime(scrubTimeMs.toLong())} / ${formatTime(totalDurationMs.toLong())}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

/**
 * Binary search + linear interpolation to find the sample and location at target timestamp.
 */
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

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
