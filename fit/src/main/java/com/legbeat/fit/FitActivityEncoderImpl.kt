package com.legbeat.fit

import com.garmin.fit.Activity
import com.garmin.fit.ActivityMesg
import com.garmin.fit.DateTime
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.Manufacturer
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.Ride
import java.io.File
import java.util.Date

/**
 * Production implementation of Garmin FIT binary encoder using official FIT SDK 21.158.0.
 */
class FitActivityEncoderImpl : FitActivityEncoder {

    override fun encode(
        outputFile: File,
        ride: Ride,
        samples: List<CadenceSample>
    ): Result<File> = runCatching {
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.parentFile?.mkdirs()

        val encoder = FileEncoder(outputFile, Fit.ProtocolVersion.V2_0)

        // 1. FileId Message (Required as first record in standard FIT files)
        val fileId = FileIdMesg().apply {
            type = com.garmin.fit.File.ACTIVITY
            manufacturer = Manufacturer.DEVELOPMENT
            product = 1
            serialNumber = 1001L
            timeCreated = DateTime(Date(ride.startTimeMs))
        }
        encoder.write(fileId)

        // 2. High-Frequency Record Messages (One per cadence sample)
        samples.forEach { sample ->
            val record = RecordMesg().apply {
                timestamp = DateTime(Date(sample.timestampMs))
                cadence = sample.rpm.toShort()
            }
            encoder.write(record)
        }

        // 3. Session Message (Aggregate workout summary)
        val durationSec = (ride.durationMs / 1000.0).toFloat()
        val session = SessionMesg().apply {
            startTime = DateTime(Date(ride.startTimeMs))
            totalElapsedTime = durationSec
            totalTimerTime = durationSec
            sport = Sport.CYCLING
            subSport = SubSport.ROAD
            avgCadence = ride.avgCadence.toShort()
            maxCadence = ride.maxCadence.toShort()
        }
        encoder.write(session)

        // 4. Activity Message (Container message)
        val activity = ActivityMesg().apply {
            timestamp = DateTime(Date(ride.endTimeMs))
            totalTimerTime = durationSec
            numSessions = 1
            type = Activity.MANUAL
        }
        encoder.write(activity)

        encoder.close()
        outputFile
    }
}
