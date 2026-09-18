package com.legbeat.fit

import com.garmin.fit.Decode
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesgListener
import com.garmin.fit.SessionMesgListener
import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.Ride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream

class FitActivityEncoderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `encodes valid FIT file readable by Garmin Decode engine`() {
        val encoder = FitActivityEncoderImpl()
        val outputFile = File(tempFolder.root, "test_ride.fit")

        val startTime = 1700000000000L
        val duration = 60_000L
        val endTime = startTime + duration

        val ride = Ride(
            id = "ride_12345",
            startTimeMs = startTime,
            endTimeMs = endTime,
            avgCadence = 88,
            maxCadence = 104,
            durationMs = duration
        )

        val samples = (0 until 60).map { i ->
            CadenceSample(
                timestampMs = startTime + i * 1000L,
                rpm = 85 + (i % 15)
            )
        }

        val result = encoder.encode(outputFile, ride, samples)
        assertTrue("Encoding must succeed", result.isSuccess)
        assertTrue("Generated file must exist", outputFile.exists())
        assertTrue("Generated file must not be empty", outputFile.length() > 0)

        // Validate using official Garmin FIT SDK Decode validator
        FileInputStream(outputFile).use { input ->
            val isFit = Decode().isFileFit(input)
            assertTrue("File must be recognized as valid FIT format", isFit)
        }

        FileInputStream(outputFile).use { input ->
            val integrity = Decode().checkFileIntegrity(input)
            assertTrue("File integrity checksum (CRC) must be valid", integrity)
        }

        // Verify decoded records
        val broadcaster = MesgBroadcaster()
        var recordCount = 0
        var decodedAvgCadence: Short? = null

        broadcaster.addListener(RecordMesgListener { mesg ->
            recordCount++
        })
        broadcaster.addListener(SessionMesgListener { mesg ->
            decodedAvgCadence = mesg.avgCadence
        })

        FileInputStream(outputFile).use { input ->
            broadcaster.run(input)
        }

        assertEquals(60, recordCount)
        assertEquals(88.toShort(), decodedAvgCadence)
    }
}
