package com.legbeat.presentation

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
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
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.legbeat.presentation.theme.DangerRed
import com.legbeat.presentation.theme.ElectricMint
import com.legbeat.presentation.theme.ElectricYellow
import com.legbeat.service.CadenceTrackingService
import com.legbeat.service.FlyoverSettingsRepository
import com.legbeat.service.MapLayerType
import com.legbeat.service.VoiceSettingsRepository
import kotlinx.coroutines.delay
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
import java.util.Locale

@Composable
fun ActiveRideScreen(
    voiceSettings: VoiceSettingsRepository,
    flyoverSettings: FlyoverSettingsRepository? = null,
    onRideFinished: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cadence by CadenceTrackingService.currentCadence.collectAsState()
    val zone by CadenceTrackingService.currentZone.collectAsState()
    val isTracking by CadenceTrackingService.isTracking.collectAsState()
    val lastFinishedRideId by CadenceTrackingService.lastFinishedRideId.collectAsState()
    val currentLocation by CadenceTrackingService.currentLocation.collectAsState()
    val liveRoutePoints by CadenceTrackingService.liveGpsRoute.collectAsState()
    val totalPedals by CadenceTrackingService.totalPedalStrokes.collectAsState()

    val isVoiceEnabled by voiceSettings.isVoiceEnabled.collectAsState()
    val voiceIntervalSec by voiceSettings.announcementIntervalSec.collectAsState()
    val isPocketModeActive by voiceSettings.isPocketModeActiveDuringRide.collectAsState()
    val isMeasureOnlyInPocket by voiceSettings.isMeasureOnlyInPocketEnabled.collectAsState()
    val isPhoneInPocketByService by CadenceTrackingService.isPhoneInPocketState.collectAsState()
    val isMeasurementPaused = isMeasureOnlyInPocket && !isPhoneInPocketByService

    val settings = flyoverSettings ?: remember { FlyoverSettingsRepository(context) }
    val currentSavedLayer by settings.mapLayer.collectAsState()
    var activeLayer by remember { mutableStateOf(currentSavedLayer) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var isFollowRider by remember { mutableStateOf(true) }

    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var isPhoneInPocket by remember { mutableStateOf(false) }

    // Map & Polyline references
    var maplibreMapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var riderMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var livePolylineRef by remember { mutableStateOf<Polyline?>(null) }

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
            CadenceTrackingService.clearLastFinishedRideId()
            onRideFinished(rideId)
        }
    }

    // Map helper to apply style
    fun applyTrackingMapStyle(map: MapLibreMap, layer: MapLayerType) {
        val styleBuilder = if (layer.isRaster) {
            Style.Builder().fromJson(layer.buildStyleJson())
        } else {
            Style.Builder().fromUri(layer.tileOrStyleUrl)
        }
        map.setStyle(styleBuilder) { _ ->
            // Re-apply live polyline
            val validPoints = liveRoutePoints.mapNotNull { sample ->
                val lat = sample.latitude
                val lon = sample.longitude
                if (lat != null && lon != null) LatLng(lat, lon) else null
            }
            if (validPoints.size >= 2) {
                livePolylineRef = map.addPolyline(
                    PolylineOptions()
                        .addAll(validPoints)
                        .color(AndroidColor.parseColor("#00E5FF"))
                        .width(6f)
                )
            }
            // Re-apply marker
            currentLocation?.let { loc ->
                val latLng = LatLng(loc.latitude, loc.longitude)
                riderMarkerRef = map.addMarker(
                    MarkerOptions().position(latLng).title("Current Position")
                )
                if (isFollowRider) {
                    map.cameraPosition = CameraPosition.Builder()
                        .target(latLng)
                        .zoom(16.5)
                        .build()
                }
            }
        }
    }

    // Update map style when layer changes
    LaunchedEffect(activeLayer) {
        maplibreMapRef?.let { map ->
            applyTrackingMapStyle(map, activeLayer)
        }
    }

    // Update rider marker and camera when location changes
    LaunchedEffect(currentLocation, maplibreMapRef) {
        val map = maplibreMapRef ?: return@LaunchedEffect
        val loc = currentLocation ?: return@LaunchedEffect
        val currentLatLng = LatLng(loc.latitude, loc.longitude)

        if (riderMarkerRef == null) {
            riderMarkerRef = map.addMarker(
                MarkerOptions().position(currentLatLng).title("Current Position")
            )
        } else {
            riderMarkerRef?.position = currentLatLng
        }

        if (isFollowRider) {
            val cameraUpdate = if (loc.hasBearing() && loc.speed > 1.5f) {
                CameraPosition.Builder()
                    .target(currentLatLng)
                    .zoom(16.5)
                    .bearing(loc.bearing.toDouble())
                    .tilt(25.0)
                    .build()
            } else {
                CameraPosition.Builder()
                    .target(currentLatLng)
                    .zoom(16.5)
                    .build()
            }
            map.easeCamera(CameraUpdateFactory.newCameraPosition(cameraUpdate), 400)
        }
    }

    // Update trail polyline when route points grow
    LaunchedEffect(liveRoutePoints.size, maplibreMapRef) {
        val map = maplibreMapRef ?: return@LaunchedEffect
        val validPoints = liveRoutePoints.mapNotNull { sample ->
            val lat = sample.latitude
            val lon = sample.longitude
            if (lat != null && lon != null) LatLng(lat, lon) else null
        }
        if (validPoints.size >= 2) {
            livePolylineRef?.let {
                try { map.removePolyline(it) } catch (_: Exception) {}
            }
            livePolylineRef = map.addPolyline(
                PolylineOptions()
                    .addAll(validPoints)
                    .color(AndroidColor.parseColor("#00E5FF"))
                    .width(6f)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ==========================================
            // TOP HALF: LIVE MAP VIEW WITH ROUTE TRAIL
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF151515))
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
                                map.addOnCameraMoveStartedListener { reason ->
                                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                        isFollowRider = false
                                    }
                                }
                                applyTrackingMapStyle(map, activeLayer)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Map Floating HUD (Top-Start): GPS Status, Speed, & Points count
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasGps = currentLocation != null
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = if (hasGps) Color(0xFF00E676) else ElectricYellow,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasGps) "GPS LIVE" else "ACQUIRING GPS...",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (hasGps) Color(0xFF00E676) else ElectricYellow,
                            fontSize = 11.sp
                        )

                        if (hasGps && currentLocation?.hasSpeed() == true) {
                            val speedKmh = currentLocation!!.speed * 3.6f
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = String.format(Locale.US, "%.1f km/h", speedKmh),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }

                        if (liveRoutePoints.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${liveRoutePoints.size} pts",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ElectricMint,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Map Layer Selector Button & Menu (Top-End)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.78f),
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { showLayerMenu = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Map Layers",
                                tint = ElectricMint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showLayerMenu,
                        onDismissRequest = { showLayerMenu = false }
                    ) {
                        MapLayerType.entries.forEach { layer ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = layer.displayName,
                                            fontWeight = if (layer == activeLayer) FontWeight.Bold else FontWeight.Normal,
                                            color = if (layer == activeLayer) ElectricYellow else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = layer.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray,
                                            fontSize = 10.sp
                                        )
                                    }
                                },
                                onClick = {
                                    activeLayer = layer
                                    settings.setMapLayer(layer)
                                    showLayerMenu = false
                                }
                            )
                        }
                    }
                }

                // Recenter on Rider Button (Bottom-End)
                Surface(
                    shape = CircleShape,
                    color = if (isFollowRider) ElectricMint.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.8f),
                    contentColor = if (isFollowRider) Color.Black else ElectricMint,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(42.dp)
                        .clickable {
                            isFollowRider = true
                            currentLocation?.let { loc ->
                                maplibreMapRef?.easeCamera(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16.5),
                                    500
                                )
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Recenter on Rider",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Divider between Map and Dashboard
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(0xFF242424))
            )

            // ==========================================
            // BOTTOM HALF: CADENCE DASHBOARD & METRICS
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Status Indicator & Cadence Zone Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF00E676), shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RECORDING",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray,
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Zone / Paused Status Badge
                    Box(
                        modifier = Modifier
                            .background(
                                if (isMeasurementPaused) ElectricMint.copy(alpha = 0.2f) else Color(zone.colorHex).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isMeasurementPaused) "PAUSED • OUT OF POCKET" else if (cadence == 0) "COASTING" else zone.label.uppercase(),
                            color = if (isMeasurementPaused) ElectricMint else if (cadence == 0) Color.Gray else Color(zone.colorHex),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp,
                            maxLines = 1
                        )
                    }
                }

                // Central Large RPM Gauge
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isMeasurementPaused || cadence == 0) "--" else cadence.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 58.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isMeasurementPaused || cadence == 0) Color.DarkGray else ElectricYellow
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isMeasurementPaused) "MEASUREMENT PAUSED (POCKET REQUIRED)" else "PEDALING CADENCE (RPM)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isMeasurementPaused) ElectricMint else Color.LightGray,
                        letterSpacing = 0.5.sp
                    )
                }

                // 3-Stat Metric Cards Row: Elapsed Time, Total Pedals, Current Speed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Elapsed Time Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "TIME",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatSeconds(elapsedSeconds),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Total Pedals Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "PEDALS",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$totalPedals",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = ElectricMint
                            )
                        }
                    }

                    // Current Speed Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "SPEED",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val speedKmh = currentLocation?.let { if (it.hasSpeed()) (it.speed * 3.6f) else 0f } ?: 0f
                            Text(
                                text = if (speedKmh > 0.5f) String.format(Locale.US, "%.1f", speedKmh) else "--",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Interactive Quick-Toggles Row: Voice Coach & In-Ride Pocket Mode
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
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isVoiceEnabled) "Voice: ${voiceIntervalSec}s" else "Muted",
                            style = MaterialTheme.typography.labelSmall,
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
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPocketModeActive) "Pocket: ON" else "Pocket: OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isPocketModeActive) ElectricMint else Color.Gray
                        )
                    }
                }

                // Stop & Save Ride Button
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
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STOP & SAVE RIDE",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Pocket Mode Touch Protection Overlay (when enabled and phone is in pocket)
        if (isPocketModeActive && isPhoneInPocket) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .clickable(enabled = false) {},
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
