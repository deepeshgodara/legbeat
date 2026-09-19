package com.legbeat.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.legbeat.analytics.repository.RideRepository
import com.legbeat.core.dsp.CadenceEstimate
import com.legbeat.core.dsp.SignalProcessingPipeline
import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.CadenceZone
import com.legbeat.core.model.Ride
import com.legbeat.presentation.MainActivity
import com.legbeat.wear.WearableMessageSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class CadenceTrackingService : Service(), SensorEventListener {

    @Inject
    lateinit var rideRepository: RideRepository

    @Inject
    lateinit var wearMessageSender: WearableMessageSender

    @Inject
    lateinit var voiceSettings: VoiceSettingsRepository

    @Inject
    lateinit var audioAnnouncer: CadenceAudioAnnouncer

    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var processingJob: Job? = null

    private lateinit var sensorManager: SensorManager
    private var linearAccelSensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    @Volatile private var latestLocation: Location? = null

    private val pipeline = SignalProcessingPipeline()
    private var rideStartTimeMs = 0L
    private var rideId = ""
    private val recordedSamples = mutableListOf<CadenceSample>()
    private var isPhoneInPocket = true

    override fun onCreate() {
        super.onCreate()
        if (!serviceScope.isActive) {
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LegBeat:CadenceTrackingWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_NOT_STICKY
    }

    private fun startTracking() {
        if (_isTracking.value) return

        if (!serviceScope.isActive) {
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }

        wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours maximum safety timeout
        rideStartTimeMs = System.currentTimeMillis()
        rideId = UUID.randomUUID().toString()
        recordedSamples.clear()
        pipeline.reset()
        pipeline.setSensitivity(voiceSettings.sensorSensitivityPercent.value)
        isPhoneInPocket = true

        // Dynamically update pipeline sensitivity if modified in Settings
        serviceScope.launch {
            voiceSettings.sensorSensitivityPercent.collect { percent ->
                pipeline.setSensitivity(percent)
            }
        }

        _lastFinishedRideId.value = null
        _isTracking.value = true
        _currentRideId.value = rideId

        startForegroundServiceNotification("0 RPM", "Starting ride tracking...")

        // Automatically launch companion app on connected watch
        serviceScope.launch {
            wearMessageSender.openAppOnWatch()
        }

        // Initialize 1-second high-accuracy background location tracking
        latestLocation = null
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(0f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    latestLocation = loc
                }
            }
        }
        locationCallback = callback

        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    callback,
                    android.os.Looper.getMainLooper()
                )
            }
        } catch (e: SecurityException) {
            Log.e("CadenceTrackingService", "Location permission not granted", e)
        }

        // Register sensor listeners
        linearAccelSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        proximitySensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        var lastAnnouncementTimeMs = System.currentTimeMillis()
        val intervalRpmSamples = mutableListOf<Int>()

        // Periodic 500ms cadence estimation loop (2 Hz)
        processingJob = serviceScope.launch {
            while (isActive) {
                delay(500L)
                val now = System.currentTimeMillis()
                val elapsedMin = (now - rideStartTimeMs) / 60000L

                val measureOnlyInPocket = voiceSettings.isMeasureOnlyInPocketEnabled.value
                if (measureOnlyInPocket && !isPhoneInPocket) {
                    // Pocket required for measurement, but phone is not in pocket
                    _currentCadence.value = 0
                    _currentZone.value = CadenceZone.IDLE
                    wearMessageSender.sendCadence(0, CadenceZone.IDLE.code, now)
                    updateNotification("Paused (Out of Pocket)", "$elapsedMin min elapsed • Waiting for pocket detection")
                } else {
                    val estimate: CadenceEstimate = pipeline.processWindow()
                    val rpm = estimate.rpm
                    val zone = CadenceZone.fromRpm(rpm)

                    _currentCadence.value = rpm
                    _currentZone.value = zone

                    val loc = latestLocation
                    val sample = CadenceSample(
                        timestampMs = now,
                        rpm = rpm,
                        latitude = loc?.latitude,
                        longitude = loc?.longitude,
                        altitude = if (loc?.hasAltitude() == true) loc.altitude else null,
                        speed = if (loc?.hasSpeed() == true) loc.speed else null
                    )

                    // Record sample if pedaling (>0 RPM) OR moving (>0.5 m/s) to maintain unbroken route during coasting
                    if (rpm > 0 || (loc != null && (loc.speed ?: 0f) > 0.5f)) {
                        recordedSamples.add(sample)
                    }
                    intervalRpmSamples.add(rpm)

                    // Voice Dictation: dictate cadence after every user-specified interval
                    val intervalMs = voiceSettings.announcementIntervalSec.value * 1000L
                    if (voiceSettings.isVoiceEnabled.value && (now - lastAnnouncementTimeMs >= intervalMs)) {
                        lastAnnouncementTimeMs = now
                        if (voiceSettings.isAnnounceAverageIntervalEnabled.value) {
                            val avgRpm = if (intervalRpmSamples.isNotEmpty()) {
                                intervalRpmSamples.average().toInt()
                            } else {
                                rpm
                            }
                            audioAnnouncer.speakCadence(avgRpm, isAverage = true)
                        } else {
                            audioAnnouncer.speakCadence(rpm, isAverage = false)
                        }
                        intervalRpmSamples.clear()
                    }

                    // Stream to Wear OS
                    wearMessageSender.sendCadence(rpm, zone.code, now)

                    // Update notification
                    updateNotification("$rpm RPM", "$elapsedMin min elapsed • ${zone.label}")
                }
            }
        }
    }

    private fun stopTracking() {
        if (!_isTracking.value) return

        // Immediately update state so UI and system know tracking has stopped
        _isTracking.value = false
        _currentCadence.value = 0
        _currentZone.value = CadenceZone.IDLE
        _currentRideId.value = null

        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        locationCallback = null
        latestLocation = null

        sensorManager.unregisterListener(this)
        processingJob?.cancel()
        processingJob = null

        val endTimeMs = System.currentTimeMillis()
        val durationMs = endTimeMs - rideStartTimeMs

        val avgRpm = if (recordedSamples.isNotEmpty()) {
            recordedSamples.map { it.rpm }.average().toInt()
        } else {
            0
        }
        val maxRpm = recordedSamples.maxOfOrNull { it.rpm } ?: 0

        val ride = Ride(
            id = rideId,
            startTimeMs = rideStartTimeMs,
            endTimeMs = endTimeMs,
            avgCadence = avgRpm,
            maxCadence = maxRpm,
            durationMs = durationMs
        )

        val samplesSnapshot = recordedSamples.toList()

        serviceScope.launch {
            try {
                rideRepository.saveRide(ride, samplesSnapshot)
                _lastFinishedRideId.value = ride.id
            } catch (e: Exception) {
                Log.e("CadenceTrackingService", "Error saving ride", e)
            } finally {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                pipeline.onSensorSample(event.timestamp, x, y, z)
            }
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = proximitySensor?.maximumRange ?: 5.0f
                val inPocket = distance < maxRange && distance <= 5.0f
                isPhoneInPocket = inPocket
                _isPhoneInPocketState.value = inPocket
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cadence Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time pedaling cadence and tracking status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification(rpmText: String, subtitle: String) {
        val notification = buildNotification(rpmText, subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(
                NOTIFICATION_ID,
                notification,
                serviceType
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(rpmText: String, subtitle: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(rpmText, subtitle))
    }

    private fun buildNotification(rpmText: String, subtitle: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CadenceTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LegBeat: $rpmText")
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop Ride", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        locationCallback = null
        latestLocation = null
        sensorManager.unregisterListener(this)
        processingJob?.cancel()
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        _isTracking.value = false
        _currentCadence.value = 0
        _currentZone.value = CadenceZone.IDLE
        _currentRideId.value = null
        audioAnnouncer.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.legbeat.action.START"
        const val ACTION_STOP = "com.legbeat.action.STOP"
        const val CHANNEL_ID = "legbeat_tracking_channel"
        const val NOTIFICATION_ID = 4201

        private val _isTracking = MutableStateFlow(false)
        val isTracking = _isTracking.asStateFlow()

        private val _currentCadence = MutableStateFlow(0)
        val currentCadence = _currentCadence.asStateFlow()

        private val _currentZone = MutableStateFlow(CadenceZone.IDLE)
        val currentZone = _currentZone.asStateFlow()

        private val _currentRideId = MutableStateFlow<String?>(null)
        val currentRideId = _currentRideId.asStateFlow()

        private val _lastFinishedRideId = MutableStateFlow<String?>(null)
        val lastFinishedRideId = _lastFinishedRideId.asStateFlow()

        private val _isPhoneInPocketState = MutableStateFlow(true)
        val isPhoneInPocketState = _isPhoneInPocketState.asStateFlow()

        fun clearLastFinishedRideId() {
            _lastFinishedRideId.value = null
        }

        fun resetTrackingState() {
            _isTracking.value = false
            _currentCadence.value = 0
            _currentZone.value = CadenceZone.IDLE
            _currentRideId.value = null
            _lastFinishedRideId.value = null
        }
    }
}
