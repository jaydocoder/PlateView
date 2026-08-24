package com.jaydocoder.plateview.data.statistics

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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

class QueryEventSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            QueryEventSyncWorkerEntryPoint::class.java,
        )
        val sessionProvider = entryPoint.sessionProvider()
        val session = sessionProvider.session.first() ?: return Result.success()
        return runCatching {
            entryPoint.statisticsRepository().synchronizePendingEvents(session)
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
interface QueryEventSyncWorkerEntryPoint {
    fun statisticsRepository(): StatisticsRepository

    fun sessionProvider(): AuthSessionProvider
}

@Singleton
class QueryEventSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<QueryEventSyncWorker>(
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).setConstraints(networkConstraints()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun requestImmediateSync() {
        val request = OneTimeWorkRequestBuilder<QueryEventSyncWorker>()
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private companion object {
        const val PERIODIC_WORK_NAME = "query-event-sync"
        const val IMMEDIATE_WORK_NAME = "query-event-sync-immediate"
        const val PERIODIC_INTERVAL_MINUTES = 15L
    }
}
