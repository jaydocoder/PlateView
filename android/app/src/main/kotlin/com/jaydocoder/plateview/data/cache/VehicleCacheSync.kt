package com.jaydocoder.plateview.data.cache

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jaydocoder.plateview.domain.vehicle.VehicleCacheRepository
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

class VehicleCatalogSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            VehicleCacheWorkerEntryPoint::class.java,
        )
        val sessionProvider = entryPoint.sessionProvider()
        val session = sessionProvider.session.first() ?: return Result.success()
        return runCatching {
            entryPoint.cacheRepository().synchronizeCatalog(
                accessToken = session.accessToken,
                forceVersionCheck = true,
            )
            Result.success()
        }.getOrElse { throwable ->
            if (throwable is HttpException && throwable.code() == HTTP_UNAUTHORIZED) {
                sessionProvider.logout()
                Result.success()
            } else {
                Result.retry()
            }
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VehicleCacheWorkerEntryPoint {
    fun cacheRepository(): VehicleCacheRepository

    fun sessionProvider(): AuthSessionProvider
}

@Singleton
class VehicleCacheSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<VehicleCatalogSyncWorker>(
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private companion object {
        const val WORK_NAME = "vehicle-catalog-sync"
        const val PERIODIC_INTERVAL_MINUTES = 15L
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VehicleCacheSchedulerEntryPoint {
    fun scheduler(): VehicleCacheSyncScheduler
}
