package com.legbeat.core.model

/**
 * Single instantaneous cadence measurement with timestamp.
 */
data class CadenceSample(
    val timestampMs: Long,
    val rpm: Int
)
