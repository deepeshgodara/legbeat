package com.legbeat.healthconnect

import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.Ride

enum class HealthConnectAvailability {
    INSTALLED,
    NOT_INSTALLED,
    NOT_SUPPORTED
}

/**
 * Interface defining local-first Google Health Connect operations for LegBeat.
 * Strict local-first architecture: no cloud APIs or external sync servers.
 */
interface HealthConnectManager {
    /**
     * Checks if Google Health Connect is supported and installed on this device.
     */
    fun checkAvailability(): HealthConnectAvailability

    /**
     * Set of required permissions for recording cycling sessions and cadence series.
     */
    fun getRequiredPermissions(): Set<String>

    /**
     * Checks whether the user has granted all required permissions.
     */
    suspend fun hasPermissions(): Boolean

    /**
     * Manually syncs an individual ride and its high-resolution cadence series into Health Connect.
     * Sets clientRecordId to "legbeat_ride_${ride.id}" to guarantee idempotent inserts.
     */
    suspend fun writeWorkoutSession(
        ride: Ride,
        samples: List<CadenceSample>
    ): Result<Unit>
}
