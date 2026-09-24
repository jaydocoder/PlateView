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
import com.jaydocoder.plateview.feature.auth.AuthRepository
import com.jaydocoder.plateview.data.network.ClientRuntimePolicyProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            entryPoint.authRepository().checkCatalogState(session, force = true)
            Result.success()
        }.getOrElse { throwable ->
            if (throwable is HttpException && throwable.code() in listOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN)) {
                entryPoint.cacheRepository().clearSnapshot(session.userId)
                if (throwable.code() == HTTP_UNAUTHORIZED) sessionProvider.logout()
                Result.success()
            } else {
                Result.retry()
            }
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VehicleCacheWorkerEntryPoint {
    fun cacheRepository(): VehicleCacheRepository

    fun sessionProvider(): AuthSessionProvider
    fun runtimePolicyProvider(): ClientRuntimePolicyProvider
    fun authRepository(): AuthRepository
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

    suspend fun cancelAndAwait() {
        withContext(Dispatchers.IO) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME).result.get()
        }
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
