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

        val semicirclesFactor = 2147483648.0 / 180.0

        // 2. High-Frequency Record Messages (One per cadence sample with GPS & metrics)
        samples.forEach { sample ->
            val record = RecordMesg().apply {
                timestamp = DateTime(Date(sample.timestampMs))
                cadence = sample.rpm.toShort()
                sample.latitude?.let { positionLat = (it * semicirclesFactor).toInt() }
                sample.longitude?.let { positionLong = (it * semicirclesFactor).toInt() }
                sample.speed?.let { speed = it }
                sample.altitude?.let { enhancedAltitude = it.toFloat() }
            }
            encoder.write(record)
        }

        // 3. Session Message (Aggregate workout summary)
        val durationSec = (ride.durationMs / 1000.0).toFloat()
        val speeds = samples.mapNotNull { sample -> sample.speed }.filter { it > 0f }
        val avgSpeedMs = if (speeds.isNotEmpty()) speeds.average().toFloat() else null
        val maxSpeedMs = speeds.maxOrNull()

        val session = SessionMesg().apply {
            startTime = DateTime(Date(ride.startTimeMs))
            totalElapsedTime = durationSec
            totalTimerTime = durationSec
            sport = Sport.CYCLING
            subSport = SubSport.ROAD
            avgCadence = ride.avgCadence.toShort()
            maxCadence = ride.maxCadence.toShort()
            avgSpeedMs?.let { avgSpeed = it }
            maxSpeedMs?.let { maxSpeed = it }
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
