package com.legbeat.analytics.repository

import com.legbeat.analytics.db.LegBeatDatabase
import com.legbeat.analytics.db.dao.CadenceWithHeartRate
import com.legbeat.analytics.db.entity.CadenceSampleEntity
import com.legbeat.analytics.db.entity.RideEntity
import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.Ride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface RideRepository {
    fun getAllRides(): Flow<List<Ride>>
    suspend fun getRideById(id: String): Ride?
    suspend fun getSamplesForRide(rideId: String): List<CadenceSample>
    suspend fun saveRide(ride: Ride, samples: List<CadenceSample>)
    suspend fun updateFitPath(rideId: String, path: String)
    suspend fun markHealthConnectSynced(rideId: String)
    suspend fun deleteRide(id: String)
    suspend fun getJoinedMetrics(rideId: String): List<CadenceWithHeartRate>
}

class RideRepositoryImpl(
    private val database: LegBeatDatabase
) : RideRepository {

    private val rideDao = database.rideDao()
    private val sampleDao = database.cadenceSampleDao()

    override fun getAllRides(): Flow<List<Ride>> {
        return rideDao.getAllRides().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getRideById(id: String): Ride? {
        return rideDao.getRideById(id)?.toDomain()
    }

    override suspend fun getSamplesForRide(rideId: String): List<CadenceSample> {
        return sampleDao.getSamplesForRide(rideId).map { it.toDomain() }
    }

    override suspend fun saveRide(ride: Ride, samples: List<CadenceSample>) {
        val rideEntity = RideEntity.fromDomain(ride)
        rideDao.insertRide(rideEntity)

        val sampleEntities = samples.map { sample ->
            CadenceSampleEntity.fromDomain(ride.id, sample)
        }
        sampleDao.insertSamples(sampleEntities)
    }

    override suspend fun updateFitPath(rideId: String, path: String) {
        val existing = rideDao.getRideById(rideId) ?: return
        rideDao.updateRide(existing.copy(fitFilePath = path))
    }

    override suspend fun markHealthConnectSynced(rideId: String) {
        rideDao.markHealthConnectSynced(rideId)
    }

    override suspend fun deleteRide(id: String) {
        rideDao.deleteRide(id)
    }

    override suspend fun getJoinedMetrics(rideId: String): List<CadenceWithHeartRate> {
        return sampleDao.getCadenceJoinedWithHeartRate(rideId)
    }
}
