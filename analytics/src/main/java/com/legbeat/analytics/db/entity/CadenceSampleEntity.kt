package com.legbeat.analytics.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.legbeat.core.model.CadenceSample

@Entity(
    tableName = "cadence_samples",
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
data class CadenceSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rideId: String,
    val timestampMs: Long,
    val rpm: Int
) {
    fun toDomain(): CadenceSample = CadenceSample(
        timestampMs = timestampMs,
        rpm = rpm
    )
}
