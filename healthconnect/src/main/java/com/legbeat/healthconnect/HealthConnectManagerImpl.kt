package com.legbeat.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.Ride
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class HealthConnectManagerImpl(
    private val context: Context
) : HealthConnectManager {

    private val healthConnectClient by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    override fun checkAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.INSTALLED
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NOT_INSTALLED
            else -> HealthConnectAvailability.NOT_SUPPORTED
        }
    }

    override fun getRequiredPermissions(): Set<String> {
        return setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(CyclingPedalingCadenceRecord::class)
        )
    }

    override suspend fun hasPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(getRequiredPermissions())
    }

    override suspend fun writeWorkoutSession(
        ride: Ride,
        samples: List<CadenceSample>
    ): Result<Unit> = runCatching {
        val client = healthConnectClient
            ?: throw IllegalStateException("Health Connect is not available on this device")

        val startInstant = Instant.ofEpochMilli(ride.startTimeMs)
        val endInstant = Instant.ofEpochMilli(ride.endTimeMs)
        val zoneOffset: ZoneOffset = ZoneId.systemDefault().rules.getOffset(startInstant)

        // 1. Overall Workout Session Record
        val sessionRecord = ExerciseSessionRecord(
            startTime = startInstant,
            startZoneOffset = zoneOffset,
            endTime = endInstant,
            endZoneOffset = zoneOffset,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            title = "LegBeat Cycling Ride",
            notes = "Recorded via pocket sensor FFT pipeline",
            metadata = Metadata(
                clientRecordId = "legbeat_ride_${ride.id}"
            )
        )

        // 2. High-Resolution Cadence Series Record
        val cadenceSamples = samples.map { sample ->
            CyclingPedalingCadenceRecord.Sample(
                time = Instant.ofEpochMilli(sample.timestampMs),
                revolutionsPerMinute = sample.rpm.toDouble()
            )
        }

        val records = mutableListOf<androidx.health.connect.client.records.Record>(sessionRecord)

        if (cadenceSamples.isNotEmpty()) {
            val cadenceRecord = CyclingPedalingCadenceRecord(
                startTime = startInstant,
                startZoneOffset = zoneOffset,
                endTime = endInstant,
                endZoneOffset = zoneOffset,
                samples = cadenceSamples,
                metadata = Metadata(
                    clientRecordId = "legbeat_cadence_${ride.id}"
                )
            )
            records.add(cadenceRecord)
        }

        // 3. Push data atomically into local Health Connect store
        client.insertRecords(records)
    }
}
