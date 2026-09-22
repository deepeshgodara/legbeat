package com.legbeat.video

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.Ride
import com.legbeat.service.FlyoverSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlyoverVideoGeneratorTest {

    @Test
    fun testGenerateVideoProducesValidMp4() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val generator = FlyoverVideoGenerator(context)
        val settings = FlyoverSettingsRepository(context)

        val now = System.currentTimeMillis()
        val ride = Ride(
            id = "test-ride-1",
            startTimeMs = now - 60_000L,
            endTimeMs = now,
            avgCadence = 85,
            maxCadence = 105,
            durationMs = 60_000L
        )

        val samples = (0..20).map { i ->
            CadenceSample(
                timestampMs = now - 60_000L + (i * 3000L),
                rpm = 80 + (i % 15),
                latitude = 37.7749 + (i * 0.001),
                longitude = -122.4194 + (i * 0.0012),
                altitude = 50.0 + i * 2,
                speed = 6.5f + (i * 0.2f)
            )
        }

        val videoFile = generator.generateVideo(
            ride = ride,
            samples = samples,
            settings = settings
        ) { progress, status ->
            println("Progress: $progress - $status")
        }

        assertNotNull("Generated video file should not be null", videoFile)
        assertTrue("Video file should exist", videoFile!!.exists())
        assertTrue("Video file size should be greater than 50KB", videoFile.length() > 50_000L)

        // Validate frame decoding and check for absence of green artifact
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(videoFile.absolutePath)
        val frameBitmap = retriever.getFrameAtTime(3_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()

        assertNotNull("Extracted video frame should not be null", frameBitmap)
        assertTrue("Extracted frame should be 720 wide", frameBitmap!!.width == 720)
        assertTrue("Extracted frame should be 1280 tall", frameBitmap.height == 1280)

        // In corrupted videos, the bottom 20% is solid bright green (#00FF00 / R<30, G>200, B<30)
        val bottomPixel = frameBitmap.getPixel(360, 1250)
        val r = android.graphics.Color.red(bottomPixel)
        val g = android.graphics.Color.green(bottomPixel)
        val b = android.graphics.Color.blue(bottomPixel)
        val isGlitchGreen = (g > 200 && r < 40 && b < 40)
        assertTrue("Bottom of the frame should NOT have the bright green corruption artifact (found R=$r, G=$g, B=$b)", !isGlitchGreen)

        // Save extracted frame for visual confirmation to cacheDir
        val outPng = java.io.File(context.cacheDir, "test_extracted_frame.png")
        java.io.FileOutputStream(outPng).use { out ->
            frameBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        println("Saved verified frame to: ${outPng.absolutePath}")
    }
}
