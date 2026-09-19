package com.legbeat.core.model

/**
 * Single instantaneous cadence measurement with timestamp and optional GPS/telemetry data.
 */
data class CadenceSample(
    val timestampMs: Long,
    val rpm: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val speed: Float? = null // speed in m/s
)
