package com.legbeat.video

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.CadenceZone
import com.legbeat.core.model.Ride
import com.legbeat.service.FlyoverSettingsRepository
import com.legbeat.service.MapLayerType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import android.graphics.Path

/**
 * Hardware-accelerated 3D Map Flyover Video Recorder.
 * Captures the LIVE rendered MapLibre map tiles (vector, satellite, terrain) frame-by-frame
 * and encodes a pristine 720p H.264 MP4 with cinematic telemetry HUD overlay.
 * Operates strictly locally with zero cloud dependencies and zero permission prompts.
 */
class FlyoverMapRecorder(private val context: Context) {

    companion object {
        private const val TAG = "FlyoverMapRecorder"
        const val VIDEO_WIDTH = 720
        const val VIDEO_HEIGHT = 1280
        const val FRAME_RATE = 30
        private const val BIT_RATE = 4_500_000 // 4.5 Mbps for crisp HD map tiles
        private const val I_FRAME_INTERVAL = 1
    }

    private var isRecording = false
    private var cancelRequested = false

    fun cancel() {
        cancelRequested = true
    }

    suspend fun recordFlyover(
        map: MapLibreMap,
        ride: Ride,
        samples: List<CadenceSample>,
        settings: FlyoverSettingsRepository,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): File? = withContext(Dispatchers.Default) {
        val gpsSamples = samples.filter { it.latitude != null && it.longitude != null }
        if (gpsSamples.size < 2) {
            Log.e(TAG, "Cannot record flyover: insufficient GPS samples")
            return@withContext null
        }

        isRecording = true
        cancelRequested = false

        val layer = settings.mapLayer.value
        val tiltAngle = settings.cameraTiltAngle.value
        val durationSeconds = settings.targetVideoDurationSeconds.value.coerceIn(10, 120)

        val totalFrames = FRAME_RATE * durationSeconds
        val totalDurationMs = ride.durationMs.coerceAtLeast(1000L)

        // Precompute smooth trajectory and stabilized camera bearings
        val trajectory = precomputeFlyoverTrajectory(
            gpsSamples = gpsSamples,
            startTimeMs = ride.startTimeMs,
            totalDurationMs = totalDurationMs,
            totalFrames = totalFrames
        )

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val outputFile = File(dir, "legbeat_flyover_${ride.id}_${System.currentTimeMillis()}.mp4")

        onProgress(0.02f, "Initializing hardware video encoder...")

        // Setup MediaFormat for hardware Surface encoding
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        var encoder: MediaCodec? = null
        var codecInputSurface: CodecInputSurface? = null
        var textureRenderer: FullFrameTextureRenderer? = null
        var muxer: MediaMuxer? = null
        var frameBitmap: Bitmap? = null

        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            val inputSurface = encoder.createInputSurface()
            codecInputSurface = CodecInputSurface(inputSurface)
            codecInputSurface.makeCurrent()
            encoder.start()

            textureRenderer = FullFrameTextureRenderer().apply { init() }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            frameBitmap = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frameBitmap)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Let the map settle at the starting position
            val firstWp = trajectory.firstOrNull() ?: FlyoverWaypoint(
                lat = gpsSamples.first().latitude!!,
                lon = gpsSamples.first().longitude!!,
                bearing = 0.0,
                sample = gpsSamples.first()
            )

            withContext(Dispatchers.Main) {
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(firstWp.lat, firstWp.lon))
                    .zoom(16.0)
                    .tilt(tiltAngle.toDouble())
                    .bearing(firstWp.bearing)
                    .build()
            }
            delay(400L) // Wait for tiles to settle

            Log.i(TAG, "Starting map recording: $totalFrames frames at $FRAME_RATE fps ($durationSeconds s duration, Real MapLibre map)")

            var lastValidMapBitmap: Bitmap? = null

            for (frame in 0 until totalFrames) {
                if (cancelRequested) {
                    Log.i(TAG, "Map flyover recording cancelled by user")
                    break
                }

                val progressFraction = frame.toFloat() / totalFrames.toFloat()
                val wp = trajectory[frame]
                val curLat = wp.lat
                val curLon = wp.lon
                val currentBearing = wp.bearing
                val currentSample = wp.sample

                // 1. Update MapLibre camera on Main Thread
                withContext(Dispatchers.Main) {
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(curLat, curLon))
                        .zoom(16.0)
                        .tilt(tiltAngle.toDouble())
                        .bearing(currentBearing)
                        .build()
                }

                // 2. Capture real map frame via MapLibre snapshot
                val snapshot = withTimeoutOrNull(600L) {
                    withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine<Bitmap?> { cont ->
                            try {
                                map.snapshot { bmp ->
                                    if (cont.isActive) {
                                        cont.resume(bmp)
                                    }
                                }
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resume(null)
                            }
                        }
                    }
                }

                if (snapshot != null) {
                    lastValidMapBitmap?.recycle()
                    lastValidMapBitmap = snapshot
                }

                val mapFrame = lastValidMapBitmap

                // 3. Draw map frame scaled and center-cropped to 720x1280
                if (mapFrame != null && !mapFrame.isRecycled) {
                    val scale = max(
                        VIDEO_WIDTH.toFloat() / mapFrame.width.toFloat(),
                        VIDEO_HEIGHT.toFloat() / mapFrame.height.toFloat()
                    )
                    val scaledW = mapFrame.width * scale
                    val scaledH = mapFrame.height * scale
                    val left = (VIDEO_WIDTH - scaledW) / 2f
                    val top = (VIDEO_HEIGHT - scaledH) / 2f
                    val destRect = RectF(left, top, left + scaledW, top + scaledH)
                    val srcRect = Rect(0, 0, mapFrame.width, mapFrame.height)
                    canvas.drawBitmap(mapFrame, srcRect, destRect, null)
                } else {
                    canvas.drawColor(Color.rgb(18, 24, 32))
                }

                // 4. Draw Rider Location Pin (Center of camera target)
                val zone = CadenceZone.fromRpm(currentSample.rpm)
                renderRiderPin(
                    canvas = canvas,
                    centerX = VIDEO_WIDTH / 2f,
                    centerY = VIDEO_HEIGHT / 2f,
                    frame = frame,
                    zoneColor = zone.colorHex.toInt(),
                    textPaint = textPaint
                )

                // 5. Draw Telemetry HUD overlay
                renderTelemetryHud(
                    canvas = canvas,
                    ride = ride,
                    sample = currentSample,
                    layer = layer,
                    tiltAngle = tiltAngle,
                    progressFraction = progressFraction,
                    textPaint = textPaint
                )

                // 6. Blit onto hardware surface via OpenGL ES texture
                textureRenderer.drawFrame(frameBitmap, VIDEO_WIDTH, VIDEO_HEIGHT)
                val presentationTimeNs = frame * 1_000_000_000L / FRAME_RATE
                codecInputSurface.setPresentationTime(presentationTimeNs)
                codecInputSurface.swapBuffers()

                // 7. Drain encoded packets to MediaMuxer
                videoTrackIndex = drainEncoder(encoder, muxer, bufferInfo, muxerStarted, videoTrackIndex) { started, track ->
                    muxerStarted = started
                    videoTrackIndex = track
                }

                if (frame % 15 == 0 || frame == totalFrames - 1) {
                    val pct = (frame + 1).toFloat() / totalFrames.toFloat()
                    onProgress(0.05f + (pct * 0.90f), "Recording 3D flyover: ${(pct * 100).toInt()}% (Frame ${frame + 1}/$totalFrames @ 30 fps)")
                }
            }

            lastValidMapBitmap?.recycle()

            if (!cancelRequested) {
                // Signal End of Stream
                encoder.signalEndOfInputStream()

                // Drain remaining frames until EOS
                var isEos = false
                var retryCount = 0
                while (!isEos && retryCount < 60) {
                    val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    } else if (outIndex >= 0) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isEos = true
                        }
                        if (bufferInfo.size > 0 && muxerStarted && videoTrackIndex >= 0) {
                            val outBuf = encoder.getOutputBuffer(outIndex)
                            if (outBuf != null) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(videoTrackIndex, outBuf, bufferInfo)
                            }
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                    } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        retryCount++
                    }
                }
            }

            // Clean up hardware resources
            try { textureRenderer.release() } catch (_: Exception) {}
            try { codecInputSurface.release() } catch (_: Exception) {}
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer.stop()
                muxer.release()
            } catch (_: Exception) {}

            frameBitmap.recycle()

            if (cancelRequested) {
                outputFile.delete()
                return@withContext null
            }

            onProgress(0.97f, "Exporting to Media Gallery...")
            exportToMediaStore(outputFile)
            onProgress(1.0f, "3D Flyover Video Ready!")

            Log.i(TAG, "Map flyover recording successful: ${outputFile.absolutePath}, size=${outputFile.length()} bytes")
            return@withContext outputFile
        } catch (e: Exception) {
            if (e is CancellationException) {
                Log.i(TAG, "Flyover recording cancelled")
            } else {
                Log.e(TAG, "Flyover map recording failed", e)
            }
            outputFile.delete()
            return@withContext null
        } finally {
            isRecording = false
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        muxerStarted: Boolean,
        trackIndex: Int,
        onMuxerStart: (Boolean, Int) -> Unit
    ): Int {
        var started = muxerStarted
        var track = trackIndex
        while (true) {
            val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0L)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!started) {
                    track = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    started = true
                    onMuxerStart(true, track)
                }
            } else if (outIndex >= 0) {
                if (bufferInfo.size > 0 && started && track >= 0) {
                    val outBuf = encoder.getOutputBuffer(outIndex)
                    if (outBuf != null) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(track, outBuf, bufferInfo)
                    }
                }
                encoder.releaseOutputBuffer(outIndex, false)
            } else {
                break
            }
        }
        return track
    }

    private fun renderTelemetryHud(
        canvas: Canvas,
        ride: Ride,
        sample: CadenceSample,
        layer: MapLayerType,
        tiltAngle: Float,
        progressFraction: Float,
        textPaint: Paint
    ) {
        val w = VIDEO_WIDTH.toFloat()
        val h = VIDEO_HEIGHT.toFloat()

        // 1. Top Header Banner
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 12, 16, 22)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, w, 140f, headerPaint)

        // Logo & Title
        textPaint.color = Color.WHITE
        textPaint.textSize = 34f
        textPaint.isFakeBoldText = true
        canvas.drawText("LEG", 36f, 75f, textPaint)

        textPaint.color = Color.parseColor("#FFE500") // Electric Yellow
        canvas.drawText("BEAT", 108f, 75f, textPaint)

        textPaint.color = Color.parseColor("#00E676") // Electric Mint
        textPaint.textSize = 22f
        textPaint.isFakeBoldText = false
        canvas.drawText("3D FLYOVER REPLAY", 220f, 74f, textPaint)

        // Layer & Tilt Badge
        textPaint.color = Color.LTGRAY
        textPaint.textSize = 20f
        canvas.drawText("${layer.displayName.uppercase()} • ${tiltAngle.toInt()}° TILT", 36f, 115f, textPaint)

        // 2. Bottom Glassmorphic Telemetry HUD Box
        val hudBoxTop = h - 330f
        val hudBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 16, 20, 26)
            style = Paint.Style.FILL
        }
        val hudBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val radius = 28f
        canvas.drawRoundRect(28f, hudBoxTop, w - 28f, h - 50f, radius, radius, hudBg)
        canvas.drawRoundRect(28f, hudBoxTop, w - 28f, h - 50f, radius, radius, hudBorder)

        // Progress bar inside HUD
        val progPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFE500")
            strokeWidth = 6f
            style = Paint.Style.FILL
        }
        val progTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 60, 60, 60)
            strokeWidth = 6f
            style = Paint.Style.FILL
        }
        val barLeft = 56f
        val barRight = w - 56f
        val barY = hudBoxTop + 30f
        canvas.drawLine(barLeft, barY, barRight, barY, progTrack)
        canvas.drawLine(barLeft, barY, barLeft + (barRight - barLeft) * progressFraction, barY, progPaint)

        // Cadence RPM Metric
        val zone = CadenceZone.fromRpm(sample.rpm)
        val zoneColor = zone.colorHex.toInt()

        textPaint.color = zoneColor
        textPaint.textSize = 80f
        textPaint.isFakeBoldText = true
        canvas.drawText("${sample.rpm}", 56f, hudBoxTop + 130f, textPaint)

        textPaint.textSize = 24f
        textPaint.isFakeBoldText = false
        textPaint.color = Color.GRAY
        canvas.drawText("RPM", 195f, hudBoxTop + 95f, textPaint)

        textPaint.color = zoneColor
        textPaint.textSize = 26f
        textPaint.isFakeBoldText = true
        canvas.drawText(zone.label, 195f, hudBoxTop + 130f, textPaint)

        // Speed km/h
        val speedKmh = (sample.speed ?: 0f) * 3.6f
        textPaint.color = Color.GRAY
        textPaint.textSize = 22f
        textPaint.isFakeBoldText = false
        canvas.drawText("SPEED", 56f, hudBoxTop + 195f, textPaint)

        textPaint.color = Color.parseColor("#00E676")
        textPaint.textSize = 38f
        textPaint.isFakeBoldText = true
        canvas.drawText(String.format(Locale.US, "%.1f km/h", speedKmh), 56f, hudBoxTop + 240f, textPaint)

        // Elevation meters
        val altitude = sample.altitude ?: 0.0
        textPaint.color = Color.GRAY
        textPaint.textSize = 22f
        textPaint.isFakeBoldText = false
        canvas.drawText("ELEVATION", 280f, hudBoxTop + 195f, textPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 38f
        textPaint.isFakeBoldText = true
        canvas.drawText(String.format(Locale.US, "%.0f m", altitude), 280f, hudBoxTop + 240f, textPaint)

        // Total Pedal Count
        textPaint.color = Color.GRAY
        textPaint.textSize = 22f
        textPaint.isFakeBoldText = false
        canvas.drawText("PEDALS", 480f, hudBoxTop + 195f, textPaint)

        textPaint.color = Color.parseColor("#FFE500")
        textPaint.textSize = 38f
        textPaint.isFakeBoldText = true
        val totalPedals = (ride.avgCadence * (ride.durationMs / 60000.0)).toInt()
        val estPedals = (totalPedals * progressFraction).toInt()
        canvas.drawText("$estPedals", 480f, hudBoxTop + 240f, textPaint)
    }

    private fun exportToMediaStore(videoFile: File): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LegBeat")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    videoFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
                Log.i(TAG, "Video exported to MediaStore: $uri")
                uri
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting video to MediaStore", e)
            null
        }
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

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun interpolateAngle(current: Double, target: Double, factor: Double): Double {
        var diff = (target - current) % 360.0
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        return (current + diff * factor + 360.0) % 360.0
    }

    private fun renderRiderPin(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        frame: Int,
        zoneColor: Int,
        textPaint: Paint
    ) {
        // 1. Ground shadow (gives 3D elevation illusion under tilt)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawOval(
            RectF(centerX - 24f, centerY + 6f, centerX + 24f, centerY + 22f),
            shadowPaint
        )

        // 2. Animated outer pulsing radar rings
        val pulsePhase = (frame % 30) / 30f
        val pulseRadius = 24f + (pulsePhase * 32f) // expands from 24f to 56f
        val pulseAlpha = ((1f - pulsePhase) * 160).toInt().coerceIn(0, 255)
        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(pulseAlpha, 0, 230, 118) // Electric Mint pulse
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(centerX, centerY, pulseRadius, pulsePaint)

        // Second offset pulse for continuous radar wave
        val pulsePhase2 = ((frame + 15) % 30) / 30f
        val pulseRadius2 = 24f + (pulsePhase2 * 32f)
        val pulseAlpha2 = ((1f - pulsePhase2) * 120).toInt().coerceIn(0, 255)
        val pulsePaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(pulseAlpha2, 255, 229, 0) // Electric Yellow pulse
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawCircle(centerX, centerY, pulseRadius2, pulsePaint2)

        // 3. Crisp white outer halo ring
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, 16f, haloPaint)

        // 4. Dark rim
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 24, 32)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, 12f, rimPaint)

        // 5. Electric core (matches current Cadence zone color!)
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = zoneColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, 8f, corePaint)

        // 6. Directional Forward Heading Chevron
        // Since camera aligns with rider bearing, forward along the road is UP (-Y)
        val chevronPath = Path().apply {
            moveTo(centerX, centerY - 28f) // apex
            lineTo(centerX - 9f, centerY - 18f) // bottom-left
            lineTo(centerX, centerY - 21f) // notch
            lineTo(centerX + 9f, centerY - 18f) // bottom-right
            close()
        }
        val chevronBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val chevronFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E676")
            style = Paint.Style.FILL
        }
        canvas.drawPath(chevronPath, chevronFillPaint)
        canvas.drawPath(chevronPath, chevronBorderPaint)

        // 7. Sleek "RIDER" Pill Badge
        val badgeTop = centerY - 52f
        val badgeBottom = centerY - 32f
        val badgeWidth = 64f
        val badgeRect = RectF(centerX - (badgeWidth / 2f), badgeTop, centerX + (badgeWidth / 2f), badgeBottom)

        val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 12, 16, 22)
            style = Paint.Style.FILL
        }
        val badgeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 0, 230, 118)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(badgeRect, 10f, 10f, badgeBg)
        canvas.drawRoundRect(badgeRect, 10f, 10f, badgeBorder)

        textPaint.color = Color.WHITE
        textPaint.textSize = 12f
        textPaint.isFakeBoldText = true
        val badgeText = "RIDER"
        val textWidth = textPaint.measureText(badgeText)
        canvas.drawText(badgeText, centerX - (textWidth / 2f), badgeBottom - 5f, textPaint)
    }

    private fun precomputeFlyoverTrajectory(
        gpsSamples: List<CadenceSample>,
        startTimeMs: Long,
        totalDurationMs: Long,
        totalFrames: Int
    ): List<FlyoverWaypoint> {
        if (totalFrames <= 0) return emptyList()

        // 1. Resample raw coordinates uniformly across all frames
        val rawLats = DoubleArray(totalFrames)
        val rawLons = DoubleArray(totalFrames)
        val samples = ArrayList<CadenceSample>(totalFrames)

        val first = gpsSamples.first()
        for (i in 0 until totalFrames) {
            val fraction = i.toDouble() / (totalFrames - 1).coerceAtLeast(1)
            val targetTimestamp = startTimeMs + (fraction * totalDurationMs).toLong()
            val s = interpolateSampleAt(targetTimestamp, gpsSamples) ?: first
            samples.add(s)
            rawLats[i] = s.latitude ?: first.latitude!!
            rawLons[i] = s.longitude ?: first.longitude!!
        }

        // 2. Spatial low-pass Gaussian/boxcar smoothing over coordinates (lat, lon)
        // This removes GPS measurement noise and high-frequency coordinate jitter
        val smoothLats = DoubleArray(totalFrames)
        val smoothLons = DoubleArray(totalFrames)
        val spatialWindow = (totalFrames * 0.015).toInt().coerceIn(3, 15)

        for (i in 0 until totalFrames) {
            var sumLat = 0.0
            var sumLon = 0.0
            var weightSum = 0.0
            val start = max(0, i - spatialWindow)
            val end = min(totalFrames - 1, i + spatialWindow)
            for (j in start..end) {
                val dist = (j - i).toDouble() / spatialWindow
                val weight = exp(-dist * dist * 2.0) // Gaussian bell curve
                sumLat += rawLats[j] * weight
                sumLon += rawLons[j] * weight
                weightSum += weight
            }
            smoothLats[i] = sumLat / weightSum
            smoothLons[i] = sumLon / weightSum
        }

        // 3. Compute forward path lookahead bearings along the smoothed spatial trajectory
        val rawBearings = DoubleArray(totalFrames)
        val lookaheadFrames = (totalFrames * 0.025).toInt().coerceIn(4, 25)

        for (i in 0 until totalFrames) {
            val lookaheadIdx = min(totalFrames - 1, i + lookaheadFrames)
            if (lookaheadIdx != i) {
                rawBearings[i] = calculateBearing(
                    smoothLats[i], smoothLons[i],
                    smoothLats[lookaheadIdx], smoothLons[lookaheadIdx]
                )
            } else if (i > 0) {
                rawBearings[i] = rawBearings[i - 1]
            } else {
                rawBearings[i] = 0.0
            }
        }

        // 4. Circular moving average over bearing angles to eliminate abrupt yaw turns
        val smoothBearings = DoubleArray(totalFrames)
        val angleWindow = (totalFrames * 0.015).toInt().coerceIn(3, 12)

        for (i in 0 until totalFrames) {
            var sumSin = 0.0
            var sumCos = 0.0
            var wSum = 0.0
            val start = max(0, i - angleWindow)
            val end = min(totalFrames - 1, i + angleWindow)
            for (j in start..end) {
                val dist = (j - i).toDouble() / angleWindow
                val weight = exp(-dist * dist * 2.0)
                val rad = Math.toRadians(rawBearings[j])
                sumSin += sin(rad) * weight
                sumCos += cos(rad) * weight
                wSum += weight
            }
            val avgAngle = (Math.toDegrees(atan2(sumSin / wSum, sumCos / wSum)) + 360.0) % 360.0
            smoothBearings[i] = avgAngle
        }

        val result = ArrayList<FlyoverWaypoint>(totalFrames)
        for (i in 0 until totalFrames) {
            result.add(
                FlyoverWaypoint(
                    lat = smoothLats[i],
                    lon = smoothLons[i],
                    bearing = smoothBearings[i],
                    sample = samples[i]
                )
            )
        }
        return result
    }
}

data class FlyoverWaypoint(
    val lat: Double,
    val lon: Double,
    val bearing: Double,
    val sample: CadenceSample
)
