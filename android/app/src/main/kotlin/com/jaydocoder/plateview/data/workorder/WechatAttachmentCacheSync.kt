package com.jaydocoder.plateview.data.workorder

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
import androidx.work.BackoffPolicy
import com.jaydocoder.plateview.domain.workorder.WorkOrderRepository
import com.jaydocoder.plateview.feature.auth.AuthSessionProvider
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

class WechatAttachmentCacheSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WechatAttachmentCacheWorkerEntryPoint::class.java,
        )
        val sessionProvider = entryPoint.sessionProvider()
        val session = sessionProvider.session.first() ?: return Result.success()
        val policy = entryPoint.runtimePolicyProvider().policy.value
        if (!session.wechatWorkOrderAccessEnabled || (policy.workOrderResultLimit == 0 && policy.wechatMessageResultLimit == 0)) {
            entryPoint.repository().clear(session.userId)
            entryPoint.repository().reportAttachmentCacheStatus(
                session.accessToken,
                session.userId,
                entryPoint.runtimePolicyProvider().clientInstanceId(),
            )
            return Result.success()
        }
        return runCatching {
            val clientInstanceId = entryPoint.runtimePolicyProvider().clientInstanceId()
            val result = entryPoint.repository().synchronizeAttachments(session.accessToken, session.userId, clientInstanceId)
            entryPoint.repository().reportAttachmentCacheStatus(
                session.accessToken,
                session.userId,
                clientInstanceId,
            )
            if (result.retryable > 0) Result.retry() else Result.success()
        }.getOrElse { error ->
            if (error is HttpException && error.code() in listOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN)) {
                Result.retry()
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
interface WechatAttachmentCacheWorkerEntryPoint {
    fun repository(): WorkOrderRepository
    fun sessionProvider(): AuthSessionProvider
    fun runtimePolicyProvider(): ClientRuntimePolicyProvider
}

@Singleton
class WechatAttachmentCacheSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic = PeriodicWorkRequestBuilder<WechatAttachmentCacheSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
        val immediate = OneTimeWorkRequestBuilder<WechatAttachmentCacheSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            immediate,
        )
    }

    fun scheduleImmediate() {
        val request = OneTimeWorkRequestBuilder<WechatAttachmentCacheSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    suspend fun cancelAndAwait() {
        withContext(Dispatchers.IO) {
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(PERIODIC_WORK_NAME).result.get()
            manager.cancelUniqueWork(IMMEDIATE_WORK_NAME).result.get()
        }
    }

    private companion object {
        const val PERIODIC_WORK_NAME = "wechat-attachment-cache-periodic"
        const val IMMEDIATE_WORK_NAME = "wechat-attachment-cache-immediate"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WechatAttachmentCacheSchedulerEntryPoint {
    fun attachmentCacheScheduler(): WechatAttachmentCacheSyncScheduler
}
