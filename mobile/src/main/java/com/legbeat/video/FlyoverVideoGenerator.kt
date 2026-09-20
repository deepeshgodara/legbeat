package com.legbeat.video

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance, on-device MP4 video generator.
 * Encodes cinematic 3D workout route flyovers with telemetry HUD using hardware MediaCodec and MediaMuxer.
 * Operates strictly locally with zero cloud dependencies and zero permission prompts.
 */
class FlyoverVideoGenerator(private val context: Context) {

    companion object {
        private const val TAG = "FlyoverVideoGen"
        private const val VIDEO_WIDTH = 720
        private const val VIDEO_HEIGHT = 1280
        private const val FRAME_RATE = 30
        private const val BIT_RATE = 4_000_000 // 4 Mbps
        private const val I_FRAME_INTERVAL = 1
        private const val DEFAULT_DURATION_SECONDS = 12
    }

    /**
     * Generates an MP4 flyover video file for the given ride.
     * Reports progress from 0.0f to 1.0f.
     */
    suspend fun generateVideo(
        ride: Ride,
        samples: List<CadenceSample>,
        settings: FlyoverSettingsRepository,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): File? = withContext(Dispatchers.Default) {
        val gpsSamples = samples.filter { it.latitude != null && it.longitude != null }
        if (gpsSamples.size < 2) {
            Log.e(TAG, "Cannot generate video: insufficient GPS data (need at least 2 points)")
            return@withContext null
        }

        val layer = settings.mapLayer.value
        val tiltAngle = settings.cameraTiltAngle.value
        val durationSeconds = when (settings.replaySpeed.value) {
            1 -> 18
            2 -> 12
            4 -> 8
            8 -> 6
            else -> 12
        }

        val totalFrames = FRAME_RATE * durationSeconds
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val outputFile = File(dir, "legbeat_flyover_${ride.id}_${System.currentTimeMillis()}.mp4")

        try {
            onProgress(0.05f, "Preparing hardware video encoder...")

            // Setup MediaFormat
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false

            val frameBitmap = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frameBitmap)
            val yuvBuffer = ByteArray(VIDEO_WIDTH * VIDEO_HEIGHT * 3 / 2)
            val argbBuffer = IntArray(VIDEO_WIDTH * VIDEO_HEIGHT)
            val bufferInfo = MediaCodec.BufferInfo()

            // Pre-calculate coordinate bounding box
            val minLat = gpsSamples.minOf { it.latitude!! }
            val maxLat = gpsSamples.maxOf { it.latitude!! }
            val minLon = gpsSamples.minOf { it.longitude!! }
            val maxLon = gpsSamples.maxOf { it.longitude!! }

            val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
            val lonSpan = (maxLon - minLon).coerceAtLeast(0.0001)

            val totalDurationMs = ride.durationMs.coerceAtLeast(1000L)

            // Pre-allocate Paints
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(40, 255, 255, 255)
                strokeWidth = 1.5f
                style = Paint.Style.STROKE
            }
            val routeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(80, 204, 255, 0)
                strokeWidth = 14f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val routeMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CCFF00") // Electric Yellow-Green
                strokeWidth = 6f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val routeFuturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(60, 200, 200, 200)
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }
            val riderDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00E676") // Electric Mint
                style = Paint.Style.FILL
            }
            val riderRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 0, 230, 118)
                strokeWidth = 4f
                style = Paint.Style.STROKE
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            Log.i(TAG, "Starting frame rendering: $totalFrames frames at 30 fps")

            for (frame in 0 until totalFrames) {
                val progressFraction = frame.toFloat() / totalFrames.toFloat()
                val targetTimestamp = ride.startTimeMs + (progressFraction * totalDurationMs).toLong()

                // Find current sample
                val currentSample = interpolateSampleAt(targetTimestamp, gpsSamples) ?: gpsSamples.first()
                val currentLat = currentSample.latitude ?: gpsSamples.first().latitude!!
                val currentLon = currentSample.longitude ?: gpsSamples.first().longitude!!

                // Lookahead bearing
                val lookaheadTimestamp = targetTimestamp + 2000L
                val lookSample = interpolateSampleAt(lookaheadTimestamp, gpsSamples) ?: currentSample
                val bearing = calculateBearing(currentLat, currentLon, lookSample.latitude ?: currentLat, lookSample.longitude ?: currentLon)

                // 1. Draw 3D Perspective Map Scene
                renderMapScene(
                    canvas = canvas,
                    layer = layer,
                    tiltAngle = tiltAngle,
                    bearing = bearing,
                    gpsSamples = gpsSamples,
                    progressFraction = progressFraction,
                    currentLat = currentLat,
                    currentLon = currentLon,
                    minLat = minLat,
                    minLon = minLon,
                    latSpan = latSpan,
                    lonSpan = lonSpan,
                    bgPaint = bgPaint,
                    gridPaint = gridPaint,
                    routeGlowPaint = routeGlowPaint,
                    routeMainPaint = routeMainPaint,
                    routeFuturePaint = routeFuturePaint,
                    riderDotPaint = riderDotPaint,
                    riderRingPaint = riderRingPaint,
                    frameIndex = frame
                )

                // 2. Draw Cinematic Telemetry HUD Overlay
                renderTelemetryHud(
                    canvas = canvas,
                    ride = ride,
                    sample = currentSample,
                    layer = layer,
                    tiltAngle = tiltAngle,
                    progressFraction = progressFraction,
                    textPaint = textPaint
                )

                // 3. Convert frame to YUV420SP
                bitmapToYuv420sp(frameBitmap, VIDEO_WIDTH, VIDEO_HEIGHT, argbBuffer, yuvBuffer)

                // 4. Send to MediaCodec
                var inputBufferIndex = encoder.dequeueInputBuffer(10_000L)
                while (inputBufferIndex < 0) {
                    videoTrackIndex = drainEncoder(encoder, muxer, bufferInfo, muxerStarted, videoTrackIndex) { started, _ ->
                        muxerStarted = started
                    }
                    inputBufferIndex = encoder.dequeueInputBuffer(10_000L)
                }

                val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(yuvBuffer)
                val presentationTimeUs = (frame * 1_000_000L / FRAME_RATE)
                encoder.queueInputBuffer(inputBufferIndex, 0, yuvBuffer.size, presentationTimeUs, 0)

                // 5. Drain encoded output to muxer
                videoTrackIndex = drainEncoder(encoder, muxer, bufferInfo, muxerStarted, videoTrackIndex) { started, _ ->
                    muxerStarted = started
                }

                if (frame % 15 == 0 || frame == totalFrames - 1) {
                    val pct = (frame + 1).toFloat() / totalFrames.toFloat()
                    onProgress(0.1f + (pct * 0.85f), "Rendering frame ${frame + 1} of $totalFrames (${(pct * 100).toInt()}%)")
                }
            }

            // Signal End of Stream
            var eosQueued = false
            var eosAttempts = 0
            while (!eosQueued && eosAttempts < 20) {
                val eosBufferIndex = encoder.dequeueInputBuffer(10_000L)
                if (eosBufferIndex >= 0) {
                    encoder.queueInputBuffer(eosBufferIndex, 0, 0, (totalFrames * 1_000_000L / FRAME_RATE), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    eosQueued = true
                } else {
                    videoTrackIndex = drainEncoder(encoder, muxer, bufferInfo, muxerStarted, videoTrackIndex) { started, _ ->
                        muxerStarted = started
                    }
                    eosAttempts++
                }
            }

            // Drain remaining frames until EOS
            var isEos = false
            var retryCount = 0
            while (!isEos && retryCount < 30) {
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

            // Clean up encoder and muxer
            try {
                encoder.stop()
                encoder.release()
            } catch (_: Exception) {}

            try {
                if (muxerStarted) {
                    muxer.stop()
                }
                muxer.release()
            } catch (_: Exception) {}

            frameBitmap.recycle()

            onProgress(0.98f, "Saving to media gallery...")
            exportToMediaStore(outputFile)

            onProgress(1.0f, "3D Flyover Video Ready!")
            Log.i(TAG, "Video generation successful: ${outputFile.absolutePath}, size=${outputFile.length()} bytes")
            return@withContext outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error generating 3D flyover video", e)
            return@withContext null
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

    private fun renderMapScene(
        canvas: Canvas,
        layer: MapLayerType,
        tiltAngle: Float,
        bearing: Double,
        gpsSamples: List<CadenceSample>,
        progressFraction: Float,
        currentLat: Double,
        currentLon: Double,
        minLat: Double,
        minLon: Double,
        latSpan: Double,
        lonSpan: Double,
        bgPaint: Paint,
        gridPaint: Paint,
        routeGlowPaint: Paint,
        routeMainPaint: Paint,
        routeFuturePaint: Paint,
        riderDotPaint: Paint,
        riderRingPaint: Paint,
        frameIndex: Int
    ) {
        val w = VIDEO_WIDTH.toFloat()
        val h = VIDEO_HEIGHT.toFloat()

        // 1. Background sky/terrain based on layer
        val topColor = when (layer) {
            MapLayerType.SATELLITE -> Color.rgb(10, 15, 25)
            MapLayerType.TERRAIN -> Color.rgb(18, 28, 22)
            MapLayerType.STREET -> Color.rgb(20, 24, 30)
            MapLayerType.MINIMAL -> Color.rgb(10, 10, 10)
        }
        val bottomColor = when (layer) {
            MapLayerType.SATELLITE -> Color.rgb(22, 32, 45)
            MapLayerType.TERRAIN -> Color.rgb(30, 48, 35)
            MapLayerType.STREET -> Color.rgb(32, 38, 48)
            MapLayerType.MINIMAL -> Color.rgb(18, 18, 18)
        }
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h, topColor, bottomColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 2. 3D Perspective Plane parameters
        val horizonY = h * (0.28f + (1f - (tiltAngle / 90f)) * 0.22f)
        val groundHeight = h - horizonY

        // Draw perspective terrain grid
        val gridLines = 14
        for (i in 0..gridLines) {
            val frac = (i.toFloat() / gridLines.toFloat())
            val y = horizonY + (frac * frac * groundHeight)
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        val vCols = 10
        val vanishingX = w / 2f
        for (i in 0..vCols) {
            val bottomX = (i.toFloat() / vCols.toFloat()) * w
            canvas.drawLine(vanishingX, horizonY, bottomX, h, gridPaint)
        }

        // 3. Project GPS points onto 3D Perspective Ground
        // Center around current rider position
        val scale = (groundHeight * 0.75f).coerceAtLeast(100f)
        val centerX = w / 2f
        val centerY = horizonY + (groundHeight * 0.65f)

        fun projectPoint(lat: Double, lon: Double): Pair<Float, Float> {
            val dx = ((lon - currentLon) / lonSpan).toFloat()
            val dy = ((lat - currentLat) / latSpan).toFloat()

            // Rotate by negative bearing so forward is up
            val rad = Math.toRadians(-bearing)
            val rotX = (dx * cos(rad) - dy * sin(rad)).toFloat()
            val rotY = (dx * sin(rad) + dy * cos(rad)).toFloat()

            // Apply perspective compression based on tiltAngle
            val depth = (1f + rotY * 1.8f).coerceIn(0.2f, 3.0f)
            val px = centerX + (rotX * scale / depth)
            val py = centerY - (rotY * scale * (tiltAngle / 90f) / depth)
            return Pair(px, py)
        }

        // Build Paths
        val fullPath = Path()
        val traveledPath = Path()
        val activeIndex = ((gpsSamples.size - 1) * progressFraction).toInt()

        var firstPoint = true
        for (i in gpsSamples.indices) {
            val s = gpsSamples[i]
            val (px, py) = projectPoint(s.latitude!!, s.longitude!!)
            if (firstPoint) {
                fullPath.moveTo(px, py)
                traveledPath.moveTo(px, py)
                firstPoint = false
            } else {
                fullPath.lineTo(px, py)
                if (i <= activeIndex) {
                    traveledPath.lineTo(px, py)
                }
            }
        }

        // Draw Full (Future) Route Dimmed
        canvas.drawPath(fullPath, routeFuturePaint)

        // Draw Traveled Route with Neon Glow
        canvas.drawPath(traveledPath, routeGlowPaint)
        canvas.drawPath(traveledPath, routeMainPaint)

        // 4. Draw Animated Rider Avatar at center
        val pulse = 6f * (sin(frameIndex * 0.25f) + 1f)
        canvas.drawCircle(centerX, centerY, 16f + pulse, riderRingPaint)
        canvas.drawCircle(centerX, centerY, 10f, riderDotPaint)

        // Draw heading arrow
        val arrowLength = 26f
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 3.5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(centerX, centerY, centerX, centerY - arrowLength, arrowPaint)
        canvas.drawLine(centerX, centerY - arrowLength, centerX - 6f, centerY - arrowLength + 8f, arrowPaint)
        canvas.drawLine(centerX, centerY - arrowLength, centerX + 6f, centerY - arrowLength + 8f, arrowPaint)
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
            color = Color.argb(190, 15, 15, 15)
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
            color = Color.argb(220, 18, 18, 18)
            style = Paint.Style.FILL
        }
        val hudBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 255, 255, 255)
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

    private fun bitmapToYuv420sp(bitmap: Bitmap, width: Int, height: Int, argb: IntArray, yuv: ByteArray) {
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[j * width + i]
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
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
                Log.i(TAG, "Video registered in MediaStore: $uri")
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

        val idx = samples.indexOfFirst { it.timestampMs >= targetTimestamp }
        if (idx <= 0) return samples.first()

        val s0 = samples[idx - 1]
        val s1 = samples[idx]
        val dt = (s1.timestampMs - s0.timestampMs).toFloat()
        if (dt <= 0) return s0

        val f = ((targetTimestamp - s0.timestampMs) / dt).coerceIn(0f, 1f)

        val lat0 = s0.latitude
        val lat1 = s1.latitude
        val lat = if (lat0 != null && lat1 != null) {
            lat0 + (lat1 - lat0) * f
        } else lat0 ?: lat1

        val lon0 = s0.longitude
        val lon1 = s1.longitude
        val lon = if (lon0 != null && lon1 != null) {
            lon0 + (lon1 - lon0) * f
        } else lon0 ?: lon1

        val spd0 = s0.speed
        val spd1 = s1.speed
        val speed = if (spd0 != null && spd1 != null) {
            spd0 + (spd1 - spd0) * f
        } else spd0 ?: spd1

        val alt0 = s0.altitude
        val alt1 = s1.altitude
        val alt = if (alt0 != null && alt1 != null) {
            alt0 + (alt1 - alt0) * f
        } else alt0 ?: alt1

        val rpm = (s0.rpm + (s1.rpm - s0.rpm) * f).toInt()

        return s0.copy(
            latitude = lat,
            longitude = lon,
            speed = speed,
            altitude = alt,
            rpm = rpm,
            timestampMs = targetTimestamp
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
}
