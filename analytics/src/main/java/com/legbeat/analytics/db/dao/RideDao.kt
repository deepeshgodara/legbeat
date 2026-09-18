package com.legbeat.analytics.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.legbeat.analytics.db.entity.RideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: RideEntity)

    @Update
    suspend fun updateRide(ride: RideEntity)

    @Query("SELECT * FROM rides WHERE id = :id")
    suspend fun getRideById(id: String): RideEntity?

    @Query("SELECT * FROM rides ORDER BY startTimeMs DESC")
    fun getAllRides(): Flow<List<RideEntity>>

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun deleteRide(id: String)

    @Query("UPDATE rides SET healthConnectSynced = 1 WHERE id = :id")
    suspend fun markHealthConnectSynced(id: String)
}
