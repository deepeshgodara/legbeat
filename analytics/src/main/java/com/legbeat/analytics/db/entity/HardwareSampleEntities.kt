package com.legbeat.analytics.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "heart_rate_samples",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["rideId", "timestampMs"])
    ]
)
data class HeartRateSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rideId: String,
    val timestampMs: Long,
    val bpm: Int
)

@Entity(
    tableName = "power_samples",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["rideId", "timestampMs"])
    ]
)
data class PowerSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rideId: String,
    val timestampMs: Long,
    val watts: Int
)
