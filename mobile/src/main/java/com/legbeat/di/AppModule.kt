package com.legbeat.di

import android.content.Context
import com.legbeat.analytics.db.LegBeatDatabase
import com.legbeat.analytics.engine.OfflineCoachingEngine
import com.legbeat.analytics.engine.ZoneCalculator
import com.legbeat.analytics.repository.RideRepository
import com.legbeat.analytics.repository.RideRepositoryImpl
import com.legbeat.fit.FitActivityEncoder
import com.legbeat.fit.FitActivityEncoderImpl
import com.legbeat.healthconnect.HealthConnectManager
import com.legbeat.healthconnect.HealthConnectManagerImpl
import com.legbeat.wear.WearableMessageSender
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LegBeatDatabase {
        return LegBeatDatabase.create(context)
    }

    @Provides
    @Singleton
    fun provideRideRepository(database: LegBeatDatabase): RideRepository {
        return RideRepositoryImpl(database)
    }

    @Provides
    @Singleton
    fun provideHealthConnectManager(@ApplicationContext context: Context): HealthConnectManager {
        return HealthConnectManagerImpl(context)
    }

    @Provides
    @Singleton
    fun provideFitActivityEncoder(): FitActivityEncoder {
        return FitActivityEncoderImpl()
    }

    @Provides
    @Singleton
    fun provideWearableMessageSender(@ApplicationContext context: Context): WearableMessageSender {
        return WearableMessageSender(context)
    }

    @Provides
    @Singleton
    fun provideZoneCalculator(): ZoneCalculator {
        return ZoneCalculator()
    }

    @Provides
    @Singleton
    fun provideOfflineCoachingEngine(zoneCalculator: ZoneCalculator): OfflineCoachingEngine {
        return OfflineCoachingEngine(zoneCalculator)
    }
}
