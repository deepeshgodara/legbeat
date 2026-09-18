package com.legbeat.analytics.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.legbeat.analytics.db.dao.CadenceSampleDao
import com.legbeat.analytics.db.dao.RideDao
import com.legbeat.analytics.db.entity.CadenceSampleEntity
import com.legbeat.analytics.db.entity.HeartRateSampleEntity
import com.legbeat.analytics.db.entity.PowerSampleEntity
import com.legbeat.analytics.db.entity.RideEntity

@Database(
    entities = [
        RideEntity::class,
        CadenceSampleEntity::class,
        HeartRateSampleEntity::class,
        PowerSampleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LegBeatDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun cadenceSampleDao(): CadenceSampleDao

    companion object {
        const val DATABASE_NAME = "legbeat_local.db"

        fun create(context: Context): LegBeatDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                LegBeatDatabase::class.java,
                DATABASE_NAME
            ).fallbackToDestructiveMigration().build()
        }
    }
}
