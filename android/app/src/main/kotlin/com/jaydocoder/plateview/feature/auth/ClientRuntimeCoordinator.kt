package com.jaydocoder.plateview.feature.auth

import android.content.Context
import com.jaydocoder.plateview.data.network.ClientPolicyAckRequest
import com.jaydocoder.plateview.data.network.ClientPolicyApi
import com.jaydocoder.plateview.data.network.ClientRuntimePolicy
import com.jaydocoder.plateview.data.network.ClientRuntimePolicyRepository
import com.jaydocoder.plateview.domain.history.SearchHistoryRepository
import com.jaydocoder.plateview.domain.vehicle.VehicleCacheRepository
import com.jaydocoder.plateview.domain.workorder.WorkOrderRepository
import com.jaydocoder.plateview.data.cache.VehicleCacheSyncScheduler
import com.jaydocoder.plateview.data.workorder.WechatAttachmentCacheSyncScheduler
import com.jaydocoder.plateview.feature.update.UpdatePromptStateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientRuntimeCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimePolicyRepository: ClientRuntimePolicyRepository,
    private val policyApi: ClientPolicyApi,
    private val vehicleCacheRepository: VehicleCacheRepository,
    private val workOrderRepository: WorkOrderRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val updatePromptStateRepository: UpdatePromptStateRepository,
    private val vehicleCacheSyncScheduler: VehicleCacheSyncScheduler,
    private val attachmentCacheSyncScheduler: WechatAttachmentCacheSyncScheduler,
) {
    suspend fun apply(session: AuthSession, remote: ClientRuntimePolicy) {
        val previous = runtimePolicyRepository.policy.value
        runtimePolicyRepository.applyRemotePolicy(remote, session.accessToken)
        val active = runtimePolicyRepository.policy.value.copy(cacheResetRevision = remote.cacheResetRevision)
        if (active.updateBaseUrl != previous.updateBaseUrl) {
            updatePromptStateRepository.clearCachedForcedUpdate(session.userId)
        }
        if (active.vehicleResultLimit == 0) {
            vehicleCacheRepository.clearSnapshot(session.userId)
            searchHistoryRepository.clear(session.username)
        }
        if (active.workOrderResultLimit == 0) workOrderRepository.clearWorkOrders(session.userId)
        if (active.wechatMessageResultLimit == 0) workOrderRepository.clearMessages(session.userId)
        if (active.workOrderResultLimit == 0 && active.wechatMessageResultLimit == 0) {
            workOrderRepository.clear(session.userId)
        }
        if (active.cacheResetRevision > runtimePolicyRepository.appliedCacheResetRevision(session.userId)) {
            runtimePolicyRepository.beginCacheMaintenance()
            try {
                vehicleCacheSyncScheduler.cancelAndAwait()
                attachmentCacheSyncScheduler.cancelAndAwait()
                clearCaches(session)
                runtimePolicyRepository.markCacheResetApplied(session.userId, active.cacheResetRevision)
                rebuildCaches(session, active)
            } finally {
                runtimePolicyRepository.endCacheMaintenance()
                vehicleCacheSyncScheduler.schedulePeriodic()
                attachmentCacheSyncScheduler.schedule()
            }
        } else if (
            active.policyRevision != previous.policyRevision ||
            active.apiBaseUrl != previous.apiBaseUrl ||
            active.updateBaseUrl != previous.updateBaseUrl
        ) {
            rebuildCaches(session, active)
        }
        runCatching { acknowledge(session, active) }
    }

    private suspend fun clearCaches(session: AuthSession) {
        vehicleCacheRepository.clearSnapshot(session.userId)
        workOrderRepository.clear(session.userId)
        workOrderRepository.clearDeviceAttachmentFiles()
        searchHistoryRepository.clear(session.username)
        File(context.filesDir, "avatars").listFiles()
            ?.filter { it.name.startsWith("avatar-${session.userId}.") }
            ?.forEach(File::delete)
        context.cacheDir.listFiles()?.forEach(File::deleteRecursively)
    }

    private suspend fun rebuildCaches(session: AuthSession, policy: ClientRuntimePolicy) {
        if (policy.vehicleResultLimit > 0) {
            runCatching { vehicleCacheRepository.synchronizeCatalog(session.accessToken, session.userId, forceVersionCheck = true) }
        }
        if (session.wechatWorkOrderAccessEnabled && (policy.workOrderResultLimit > 0 || policy.wechatMessageResultLimit > 0)) {
            runCatching { workOrderRepository.synchronize(session.accessToken, session.userId, forceVersionCheck = true) }
            runCatching { workOrderRepository.synchronizeAttachmentManifest(session.accessToken, session.userId) }
            attachmentCacheSyncScheduler.scheduleImmediate()
        }
    }

    private suspend fun acknowledge(session: AuthSession, policy: ClientRuntimePolicy) {
        policyApi.acknowledge(
            "Bearer ${session.accessToken}",
            ClientPolicyAckRequest(
                clientInstanceId = runtimePolicyRepository.clientInstanceId(),
                appliedCacheResetRevision = runtimePolicyRepository.appliedCacheResetRevision(session.userId),
                appliedPolicyRevision = policy.policyRevision,
                apiBaseUrl = policy.apiBaseUrl,
                updateBaseUrl = policy.updateBaseUrl,
            ),
        )
    }
}
