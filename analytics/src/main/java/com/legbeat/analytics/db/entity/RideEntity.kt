package com.legbeat.analytics.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.legbeat.core.model.Ride

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey
    val id: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val avgCadence: Int,
    val maxCadence: Int,
    val durationMs: Long,
    val fitFilePath: String? = null,
    val healthConnectSynced: Boolean = false,
    val notes: String? = null
) {
    fun toDomain(): Ride = Ride(
        id = id,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        avgCadence = avgCadence,
        maxCadence = maxCadence,
        durationMs = durationMs,
        fitFilePath = fitFilePath,
        healthConnectSynced = healthConnectSynced,
        notes = notes
    )

    companion object {
        fun fromDomain(ride: Ride): RideEntity = RideEntity(
            id = ride.id,
            startTimeMs = ride.startTimeMs,
            endTimeMs = ride.endTimeMs,
            avgCadence = ride.avgCadence,
            maxCadence = ride.maxCadence,
            durationMs = ride.durationMs,
            fitFilePath = ride.fitFilePath,
            healthConnectSynced = ride.healthConnectSynced,
            notes = ride.notes
        )
    }
}
