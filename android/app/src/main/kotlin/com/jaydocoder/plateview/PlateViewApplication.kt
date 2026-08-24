package com.jaydocoder.plateview

import android.app.Application
import com.jaydocoder.plateview.data.cache.VehicleCacheSchedulerEntryPoint
import com.jaydocoder.plateview.data.statistics.QueryEventSyncScheduler
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class PlateViewApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EntryPointAccessors.fromApplication(
            this,
            VehicleCacheSchedulerEntryPoint::class.java,
        ).scheduler().schedulePeriodic()
        EntryPointAccessors.fromApplication(
            this,
            QueryEventSyncSchedulerEntryPoint::class.java,
        ).queryEventSyncScheduler().schedulePeriodic()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface QueryEventSyncSchedulerEntryPoint {
    fun queryEventSyncScheduler(): QueryEventSyncScheduler
}
