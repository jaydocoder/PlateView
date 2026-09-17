package com.jaydocoder.plateview

import android.app.Application
import android.util.Log
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
        runOptionalStartupTask("Sentry 初始化") { configureSentry(this) }
        runOptionalStartupTask("车辆缓存定时同步") {
            EntryPointAccessors.fromApplication(
                this,
                VehicleCacheSchedulerEntryPoint::class.java,
            ).scheduler().schedulePeriodic()
        }
        runOptionalStartupTask("查询记录定时同步") {
            EntryPointAccessors.fromApplication(
                this,
                QueryEventSyncSchedulerEntryPoint::class.java,
            ).queryEventSyncScheduler().schedulePeriodic()
        }
    }

    private fun runOptionalStartupTask(name: String, task: () -> Unit) {
        runCatching(task).onFailure { throwable ->
            Log.e(LOG_TAG, "${name}失败，已跳过以保证应用可以启动", throwable)
        }
    }

    private companion object {
        const val LOG_TAG = "PlateViewStartup"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface QueryEventSyncSchedulerEntryPoint {
    fun queryEventSyncScheduler(): QueryEventSyncScheduler
}
