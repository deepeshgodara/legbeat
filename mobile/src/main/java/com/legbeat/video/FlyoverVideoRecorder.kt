package com.legbeat.video

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * On-device cinematic video recorder leveraging Android MediaProjection and MediaRecorder.
 * Encodes 1080p/720p H.264 MP4 videos of 3D terrain flyovers with zero cloud dependencies.
 */
class FlyoverVideoRecorder(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    var isRecording: Boolean = false
        private set

    fun startRecording(
        resultCode: Int,
        data: Intent,
        width: Int,
        height: Int,
        densityDpi: Int,
        rideId: String
    ): Boolean {
        try {
            // 1. Ensure even dimensions for H.264 encoder
            val encWidth = (width / 2) * 2
            val encHeight = (height / 2) * 2

            // 2. Prepare local output file in Movies dir
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            val outputFile = File(dir, "legbeat_flyover_${rideId}_${System.currentTimeMillis()}.mp4")
            currentOutputFile = outputFile

            // 3. Start ScreenCaptureService foreground service first (Mandatory on Android 14+)
            val serviceIntent = Intent(context, ScreenCaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // 4. Initialize MediaRecorder with H.264 encoder
            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(encWidth, encHeight)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(6_000_000) // 6 Mbps for crisp 3D terrain
                setOutputFile(outputFile.absolutePath)
                prepare()
            }
            mediaRecorder = recorder

            // 5. Acquire MediaProjection
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, data)
            mediaProjection = projection

            // 6. Create VirtualDisplay attached to MediaRecorder surface
            virtualDisplay = projection.createVirtualDisplay(
                "LegBeatFlyoverCapture",
                encWidth,
                encHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            // 7. Start recording
            recorder.start()
            isRecording = true
            Log.i(TAG, "Flyover recording started: ${outputFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start flyover video recording", e)
            cleanup()
            return false
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        isRecording = false
        val file = currentOutputFile

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder stop failed (possibly too short): ${e.message}")
        }

        cleanup()

        // Stop foreground capture service
        try {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop ScreenCaptureService: ${e.message}")
        }

        // Export to Android MediaStore Movies so it's readily accessible in Gallery
        if (file != null && file.exists() && file.length() > 0) {
            exportToMediaStore(file)
        }

        return file
    }

    private fun cleanup() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
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

    fun createShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "LegBeat 3D Flyover Replay")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        private const val TAG = "FlyoverVideoRecorder"

        fun findLatestVideoForRide(context: Context, rideId: String): File? {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            val files = dir.listFiles { _, name ->
                name.startsWith("legbeat_flyover_${rideId}_") && name.endsWith(".mp4")
            } ?: return null
            return files.maxByOrNull { it.lastModified() }
        }

        fun createShareIntent(context: Context, file: File): Intent {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            return Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "LegBeat 3D Flyover Replay")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
