package com.legbeat.core.model

/**
 * Domain representation of a recorded cycling session.
 */
data class Ride(
    val id: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val avgCadence: Int,
    val maxCadence: Int,
    val durationMs: Long,
    val fitFilePath: String? = null,
    val healthConnectSynced: Boolean = false,
    val notes: String? = null
)
