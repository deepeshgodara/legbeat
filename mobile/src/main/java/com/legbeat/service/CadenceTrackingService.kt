package com.legbeat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
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
    lateinit var audioAnnouncer: CadenceAudioAnnouncer

    @Inject
    lateinit var voiceSettings: VoiceSettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var processingJob: Job? = null

    private lateinit var sensorManager: SensorManager
    private var linearAccelSensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val pipeline = SignalProcessingPipeline()
    private val recordedSamples = mutableListOf<CadenceSample>()

    private var rideStartTimeMs: Long = 0L
    private var rideId: String = ""

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

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
        return START_STICKY
    }

    private fun startTracking() {
        if (_isTracking.value) return

        wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours maximum safety timeout
        rideStartTimeMs = System.currentTimeMillis()
        rideId = UUID.randomUUID().toString()
        recordedSamples.clear()
        pipeline.reset()
        pipeline.setSensitivity(voiceSettings.sensorSensitivityPercent.value)

        // Dynamically update pipeline sensitivity if modified in Settings
        serviceScope.launch {
            voiceSettings.sensorSensitivityPercent.collect { percent ->
                pipeline.setSensitivity(percent)
            }
        }

        _isTracking.value = true
        _currentRideId.value = rideId

        startForegroundServiceNotification("0 RPM", "Starting ride tracking...")

        // Automatically launch companion app on connected watch
        serviceScope.launch {
            wearMessageSender.openAppOnWatch()
        }

        // Register sensor listener (SENSOR_DELAY_GAME ~50 Hz)
        linearAccelSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }

        var lastAnnouncementTimeMs = System.currentTimeMillis()

        // Periodic 500ms cadence estimation loop (2 Hz)
        processingJob = serviceScope.launch {
            while (isActive) {
                delay(500L)
                val estimate: CadenceEstimate = pipeline.processWindow()
                val now = System.currentTimeMillis()
                val rpm = estimate.rpm
                val zone = CadenceZone.fromRpm(rpm)

                _currentCadence.value = rpm
                _currentZone.value = zone

                if (rpm > 0) {
                    recordedSamples.add(CadenceSample(now, rpm))
                }

                // Voice Dictation: dictate current cadence after every user-specified interval
                val intervalMs = voiceSettings.announcementIntervalSec.value * 1000L
                if (voiceSettings.isVoiceEnabled.value && (now - lastAnnouncementTimeMs >= intervalMs)) {
                    lastAnnouncementTimeMs = now
                    audioAnnouncer.speakCadence(rpm)
                }

                // Stream to Wear OS
                wearMessageSender.sendCadence(rpm, zone.code, now)

                // Update notification
                val elapsedMin = (now - rideStartTimeMs) / 60000L
                updateNotification("$rpm RPM", "$elapsedMin min elapsed • ${zone.label}")
            }
        }
    }

    private fun stopTracking() {
        if (!_isTracking.value) return

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
            rideRepository.saveRide(ride, samplesSnapshot)
            _lastFinishedRideId.value = rideId

            // Clean up wake lock and foreground state
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            _isTracking.value = false
            _currentCadence.value = 0
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        pipeline.onSensorSample(event.timestamp, x, y, z)
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
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
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
        sensorManager.unregisterListener(this)
        processingJob?.cancel()
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        _isTracking.value = false
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
    }
}
