package com.jaydocoder.plateview

import android.app.Application
import com.jaydocoder.plateview.data.cache.VehicleCacheSchedulerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PlateViewApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EntryPointAccessors.fromApplication(
            this,
            VehicleCacheSchedulerEntryPoint::class.java,
        ).scheduler().schedulePeriodic()
    }
}
