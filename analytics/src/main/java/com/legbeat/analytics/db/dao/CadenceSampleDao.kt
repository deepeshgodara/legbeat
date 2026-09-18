package com.legbeat.analytics.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.legbeat.analytics.db.entity.CadenceSampleEntity
import com.legbeat.analytics.db.entity.HeartRateSampleEntity
import com.legbeat.analytics.db.entity.PowerSampleEntity
import kotlinx.coroutines.flow.Flow

data class CadenceWithHeartRate(
    val timestampMs: Long,
    val rpm: Int,
    val bpm: Int?
)

@Dao
interface CadenceSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSamples(samples: List<CadenceSampleEntity>)

    @Query("SELECT * FROM cadence_samples WHERE rideId = :rideId ORDER BY timestampMs ASC")
    suspend fun getSamplesForRide(rideId: String): List<CadenceSampleEntity>

    @Query("SELECT * FROM cadence_samples WHERE rideId = :rideId ORDER BY timestampMs ASC")
    fun observeSamplesForRide(rideId: String): Flow<List<CadenceSampleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartRateSamples(samples: List<HeartRateSampleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPowerSamples(samples: List<PowerSampleEntity>)

    /**
     * Cross-metric query joining Cadence and Heart Rate for offline coaching insights.
     */
    @Query("""
        SELECT c.timestampMs, c.rpm, h.bpm 
        FROM cadence_samples c 
        LEFT JOIN heart_rate_samples h 
          ON c.rideId = h.rideId AND ABS(c.timestampMs - h.timestampMs) <= 2000
        WHERE c.rideId = :rideId 
        ORDER BY c.timestampMs ASC
    """)
    suspend fun getCadenceJoinedWithHeartRate(rideId: String): List<CadenceWithHeartRate>
}
