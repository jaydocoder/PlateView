package com.jaydocoder.plateview.feature.consistency

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jaydocoder.plateview.data.network.ClientRuntimePolicyProvider
import com.jaydocoder.plateview.domain.vehicle.VehicleCacheRepository
import com.jaydocoder.plateview.domain.workorder.WorkOrderRepository
import com.jaydocoder.plateview.feature.auth.AuthSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val Context.catalogConsistencyDataStore by preferencesDataStore("catalog_consistency")

data class ClientCatalogState(
    val vehicleRevision: Long,
    val workOrderRevision: Long,
    val wechatMessageRevision: Long,
    val attachmentManifestRevision: Long,
    val policyRevision: Long,
    val serverTime: String,
)

enum class CatalogKind { VEHICLE, WORK_ORDER, WECHAT_MESSAGE, ATTACHMENT }

enum class CatalogSyncStatus { CONFIRMED, CHECKING, OUTDATED, SYNCING, OFFLINE_STALE, FAILED, PERMISSION_REVOKED }

internal enum class CatalogReconcileAction { CONFIRM, SYNCHRONIZE, REVOKE, REJECT_ROLLBACK }

internal fun catalogReconcileAction(appliedRevision: Long, remoteRevision: Long): CatalogReconcileAction = when {
    remoteRevision < 0 -> CatalogReconcileAction.REVOKE
    appliedRevision > remoteRevision -> CatalogReconcileAction.REJECT_ROLLBACK
    appliedRevision == remoteRevision -> CatalogReconcileAction.CONFIRM
    else -> CatalogReconcileAction.SYNCHRONIZE
}

internal fun catalogTargetsForAccount(
    hasWechatAccess: Boolean,
    vehicleRevision: Long,
    workOrderRevision: Long,
    messageRevision: Long,
    attachmentRevision: Long,
): Map<CatalogKind, Long> = mapOf(
    CatalogKind.VEHICLE to vehicleRevision,
    CatalogKind.WORK_ORDER to if (hasWechatAccess) workOrderRevision else -1L,
    CatalogKind.WECHAT_MESSAGE to if (hasWechatAccess) messageRevision else -1L,
    CatalogKind.ATTACHMENT to if (hasWechatAccess) attachmentRevision else -1L,
)

internal fun catalogSyncFailureCode(error: Throwable): String =
    error.message?.takeIf { it == "SYNC_TARGET_VERSION_MISMATCH" }
        ?: error::class.simpleName
        ?: "SYNC_FAILED"

internal fun CatalogFreshness.unavailableAfterNetworkFailure(
    now: Long,
    errorCode: String,
): CatalogFreshness = if (status == CatalogSyncStatus.PERMISSION_REVOKED) {
    this
} else {
    copy(
        status = if (now - lastConfirmedAtEpochMillis > FRESHNESS_WINDOW_MILLIS) CatalogSyncStatus.OFFLINE_STALE else status,
        lastErrorCode = errorCode,
    )
}

internal fun CatalogFreshness.revoked(remoteRevision: Long): CatalogFreshness = copy(
    appliedRevision = 0,
    observedServerRevision = remoteRevision,
    lastConfirmedAtEpochMillis = 0,
    lastSuccessfulSyncAtEpochMillis = 0,
    status = CatalogSyncStatus.PERMISSION_REVOKED,
    lastErrorCode = null,
)

internal fun CatalogFreshness.resetForPolicyChange(): CatalogFreshness = CatalogFreshness(kind)

data class CatalogFreshness(
    val kind: CatalogKind,
    val appliedRevision: Long = 0,
    val observedServerRevision: Long = 0,
    val lastConfirmedAtEpochMillis: Long = 0,
    val lastSuccessfulSyncAtEpochMillis: Long = 0,
    val status: CatalogSyncStatus = CatalogSyncStatus.CHECKING,
    val lastErrorCode: String? = null,
) {
    fun isConfirmed(now: Long = System.currentTimeMillis()): Boolean =
        status == CatalogSyncStatus.CONFIRMED && now - lastConfirmedAtEpochMillis <= FRESHNESS_WINDOW_MILLIS

}

private const val FRESHNESS_WINDOW_MILLIS = 30_000L

interface CatalogConsistencyStateProvider {
    val freshness: StateFlow<Map<CatalogKind, CatalogFreshness>>
}

object DefaultCatalogConsistencyStateProvider : CatalogConsistencyStateProvider {
    override val freshness: StateFlow<Map<CatalogKind, CatalogFreshness>> = MutableStateFlow(
        CatalogKind.entries.associateWith(::CatalogFreshness),
    )
}

@Singleton
class CatalogConsistencyCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicleCacheRepository: VehicleCacheRepository,
    private val workOrderRepository: WorkOrderRepository,
    private val runtimePolicyProvider: ClientRuntimePolicyProvider,
) : CatalogConsistencyStateProvider {
    private val kindLocks = CatalogKind.entries.associateWith { Mutex() }
    private val latestStates = ConcurrentHashMap<Long, Pair<AuthSession, ClientCatalogState>>()
    private val accountJobs = ConcurrentHashMap<Long, Job>()
    private val networkSlots = Semaphore(2)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeUserId = AtomicLong(NO_ACTIVE_USER)
    private val _freshness = MutableStateFlow(CatalogKind.entries.associateWith(::CatalogFreshness))
    override val freshness: StateFlow<Map<CatalogKind, CatalogFreshness>> = _freshness.asStateFlow()

    suspend fun accept(session: AuthSession, remote: ClientCatalogState) {
        activateAccount(session.userId)
        latestStates[session.userId] = session to remote
        synchronized(accountJobs) {
            if (accountJobs[session.userId]?.isActive == true) return
            accountJobs[session.userId] = applicationScope.launch {
                try {
                    while (true) {
                        val latest = latestStates.remove(session.userId) ?: break
                        process(latest.first, latest.second)
                    }
                } finally {
                    accountJobs.remove(session.userId)
                    if (latestStates.containsKey(session.userId)) accept(session, latestStates.getValue(session.userId).second)
                }
            }
        }
    }

    private suspend fun process(session: AuthSession, remote: ClientCatalogState) = coroutineScope {
        if (!isActiveAccount(session.userId)) return@coroutineScope
        var local = load(session.userId)
        val previousPolicyRevision = loadPolicyRevision(session.userId)
        if (previousPolicyRevision != remote.policyRevision) {
            resetForPolicyChange(session.userId, local)
            persistPolicyRevision(session.userId, remote.policyRevision)
            local = load(session.userId)
        }
        val targets = catalogTargetsForAccount(
            hasWechatAccess = session.wechatWorkOrderAccessEnabled,
            vehicleRevision = remote.vehicleRevision,
            workOrderRevision = remote.workOrderRevision,
            messageRevision = remote.wechatMessageRevision,
            attachmentRevision = remote.attachmentManifestRevision,
        )
        if (isActiveAccount(session.userId)) _freshness.value = targets.mapValues { (kind, revision) ->
            local[kind].orEmpty(kind).copy(observedServerRevision = revision, status = CatalogSyncStatus.CHECKING)
        }
        listOf(
            async { reconcile(session, CatalogKind.VEHICLE, targets.getValue(CatalogKind.VEHICLE), local[CatalogKind.VEHICLE].orEmpty(CatalogKind.VEHICLE)) },
            async {
                reconcileWorkOrderCatalogs(
                    session = session,
                    workOrderRevision = targets.getValue(CatalogKind.WORK_ORDER),
                    messageRevision = targets.getValue(CatalogKind.WECHAT_MESSAGE),
                    workOrderInitial = local[CatalogKind.WORK_ORDER].orEmpty(CatalogKind.WORK_ORDER),
                    messageInitial = local[CatalogKind.WECHAT_MESSAGE].orEmpty(CatalogKind.WECHAT_MESSAGE),
                )
            },
            async { reconcile(session, CatalogKind.ATTACHMENT, targets.getValue(CatalogKind.ATTACHMENT), local[CatalogKind.ATTACHMENT].orEmpty(CatalogKind.ATTACHMENT)) },
        ).awaitAll()
    }

    suspend fun markUnavailable(errorCode: String) {
        val now = System.currentTimeMillis()
        _freshness.value = _freshness.value.mapValues { (_, current) ->
            current.unavailableAfterNetworkFailure(now, errorCode)
        }
    }

    suspend fun deactivate(userId: Long) {
        if (activeUserId.compareAndSet(userId, NO_ACTIVE_USER)) {
            latestStates.remove(userId)
            accountJobs.remove(userId)?.cancel()
            _freshness.value = CatalogKind.entries.associateWith(::CatalogFreshness)
        }
    }

    private suspend fun reconcile(
        session: AuthSession,
        kind: CatalogKind,
        remoteRevision: Long,
        initial: CatalogFreshness,
    ) = kindLocks.getValue(kind).withLock {
        when (catalogReconcileAction(initial.appliedRevision, remoteRevision)) {
            CatalogReconcileAction.REVOKE -> {
                revoke(session.userId, kind)
                persist(session.userId, initial.revoked(remoteRevision))
                return@withLock
            }
            CatalogReconcileAction.REJECT_ROLLBACK -> {
                persist(session.userId, initial.copy(
                    observedServerRevision = remoteRevision,
                    status = CatalogSyncStatus.FAILED,
                    lastErrorCode = "SERVER_REVISION_ROLLBACK",
                ))
                return@withLock
            }
            CatalogReconcileAction.CONFIRM, CatalogReconcileAction.SYNCHRONIZE -> Unit
        }
        val now = System.currentTimeMillis()
        if (catalogReconcileAction(initial.appliedRevision, remoteRevision) == CatalogReconcileAction.CONFIRM) {
            persist(session.userId, initial.copy(
                observedServerRevision = remoteRevision,
                lastConfirmedAtEpochMillis = now,
                status = CatalogSyncStatus.CONFIRMED,
                lastErrorCode = null,
            ))
            return@withLock
        }
        persist(session.userId, initial.copy(
            observedServerRevision = remoteRevision,
            status = CatalogSyncStatus.SYNCING,
            lastErrorCode = null,
        ))
        try {
            val appliedRevision = networkSlots.withPermit { synchronize(session, kind, remoteRevision) }
            if (appliedRevision != remoteRevision) {
                throw IllegalStateException("SYNC_TARGET_VERSION_MISMATCH")
            }
            val completedAt = System.currentTimeMillis()
            persist(session.userId, initial.copy(
                appliedRevision = appliedRevision,
                observedServerRevision = remoteRevision,
                lastConfirmedAtEpochMillis = completedAt,
                lastSuccessfulSyncAtEpochMillis = completedAt,
                status = CatalogSyncStatus.CONFIRMED,
                lastErrorCode = null,
            ))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            persist(session.userId, initial.copy(
                observedServerRevision = remoteRevision,
                status = CatalogSyncStatus.FAILED,
                lastErrorCode = catalogSyncFailureCode(error),
            ))
        }
    }

    private suspend fun reconcileWorkOrderCatalogs(
        session: AuthSession,
        workOrderRevision: Long,
        messageRevision: Long,
        workOrderInitial: CatalogFreshness,
        messageInitial: CatalogFreshness,
    ) {
        val workOrderRevoked = workOrderRevision < 0
        val messageRevoked = messageRevision < 0
        if (workOrderRevoked) revoke(session.userId, CatalogKind.WORK_ORDER)
        if (messageRevoked) revoke(session.userId, CatalogKind.WECHAT_MESSAGE)
        if (workOrderRevoked && messageRevoked) {
            persist(session.userId, workOrderInitial.revoked(workOrderRevision))
            persist(session.userId, messageInitial.revoked(messageRevision))
            return
        }
        val needsWorkOrderSync = !workOrderRevoked && workOrderInitial.appliedRevision != workOrderRevision
        val needsMessageSync = !messageRevoked && messageInitial.appliedRevision != messageRevision
        if (!needsWorkOrderSync && !needsMessageSync) {
            val now = System.currentTimeMillis()
            if (!workOrderRevoked) persist(session.userId, workOrderInitial.copy(observedServerRevision = workOrderRevision, lastConfirmedAtEpochMillis = now, status = CatalogSyncStatus.CONFIRMED, lastErrorCode = null))
            if (!messageRevoked) persist(session.userId, messageInitial.copy(observedServerRevision = messageRevision, lastConfirmedAtEpochMillis = now, status = CatalogSyncStatus.CONFIRMED, lastErrorCode = null))
            return
        }
        if ((!workOrderRevoked && workOrderInitial.appliedRevision > workOrderRevision) ||
            (!messageRevoked && messageInitial.appliedRevision > messageRevision)
        ) {
            if (!workOrderRevoked) persist(session.userId, workOrderInitial.copy(observedServerRevision = workOrderRevision, status = CatalogSyncStatus.FAILED, lastErrorCode = "SERVER_REVISION_ROLLBACK"))
            if (!messageRevoked) persist(session.userId, messageInitial.copy(observedServerRevision = messageRevision, status = CatalogSyncStatus.FAILED, lastErrorCode = "SERVER_REVISION_ROLLBACK"))
            return
        }
        val pairLock = kindLocks.getValue(CatalogKind.WORK_ORDER)
        pairLock.withLock {
            if (!workOrderRevoked) persist(session.userId, workOrderInitial.copy(observedServerRevision = workOrderRevision, status = CatalogSyncStatus.SYNCING))
            if (!messageRevoked) persist(session.userId, messageInitial.copy(observedServerRevision = messageRevision, status = CatalogSyncStatus.SYNCING))
            try {
                val result = networkSlots.withPermit {
                    workOrderRepository.synchronize(
                        accessToken = session.accessToken,
                        userId = session.userId,
                        forceVersionCheck = true,
                        targetWorkOrderRevision = workOrderRevision.takeUnless { workOrderRevoked },
                        targetMessageRevision = messageRevision.takeUnless { messageRevoked },
                    )
                }
                if ((!workOrderRevoked && result.workOrderRevision != workOrderRevision) ||
                    (!messageRevoked && result.messageRevision != messageRevision)
                ) {
                    throw IllegalStateException("SYNC_TARGET_VERSION_MISMATCH")
                }
                val completedAt = System.currentTimeMillis()
                if (!workOrderRevoked) persist(session.userId, workOrderInitial.copy(appliedRevision = result.workOrderRevision, observedServerRevision = workOrderRevision, lastConfirmedAtEpochMillis = completedAt, lastSuccessfulSyncAtEpochMillis = completedAt, status = CatalogSyncStatus.CONFIRMED, lastErrorCode = null))
                if (!messageRevoked) persist(session.userId, messageInitial.copy(appliedRevision = result.messageRevision, observedServerRevision = messageRevision, lastConfirmedAtEpochMillis = completedAt, lastSuccessfulSyncAtEpochMillis = completedAt, status = CatalogSyncStatus.CONFIRMED, lastErrorCode = null))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val code = catalogSyncFailureCode(error)
                if (!workOrderRevoked) persist(session.userId, workOrderInitial.copy(observedServerRevision = workOrderRevision, status = CatalogSyncStatus.FAILED, lastErrorCode = code))
                if (!messageRevoked) persist(session.userId, messageInitial.copy(observedServerRevision = messageRevision, status = CatalogSyncStatus.FAILED, lastErrorCode = code))
            }
        }
    }

    private suspend fun synchronize(session: AuthSession, kind: CatalogKind, targetRevision: Long): Long {
        check(isActiveAccount(session.userId)) { "账号已切换，取消旧账号目录同步" }
        return when (kind) {
            CatalogKind.VEHICLE -> vehicleCacheRepository.synchronizeCatalog(
                session.accessToken,
                session.userId,
                forceVersionCheck = true,
                targetRevision = targetRevision,
            ).appliedRevision
            CatalogKind.WORK_ORDER, CatalogKind.WECHAT_MESSAGE -> error("微信目录必须通过合并同步入口处理")
            CatalogKind.ATTACHMENT -> workOrderRepository.synchronizeAttachmentManifest(
                session.accessToken,
                session.userId,
            ).appliedRevision
        }
    }

    private suspend fun revoke(userId: Long, kind: CatalogKind) {
        when (kind) {
            CatalogKind.VEHICLE -> vehicleCacheRepository.clearSnapshot(userId)
            CatalogKind.WORK_ORDER -> workOrderRepository.clearWorkOrders(userId)
            CatalogKind.WECHAT_MESSAGE -> workOrderRepository.clearMessages(userId)
            CatalogKind.ATTACHMENT -> workOrderRepository.clearAttachmentCache(userId)
        }
    }

    private suspend fun resetForPolicyChange(userId: Long, current: Map<CatalogKind, CatalogFreshness>) {
        CatalogKind.entries.forEach { kind ->
            revoke(userId, kind)
            persist(userId, current[kind].orEmpty(kind).resetForPolicyChange())
        }
    }

    private suspend fun load(userId: Long): Map<CatalogKind, CatalogFreshness> {
        val preferences = context.catalogConsistencyDataStore.data.first()
        return CatalogKind.entries.associateWith { kind ->
            val prefix = "$userId.${kind.name}"
            CatalogFreshness(
                kind = kind,
                appliedRevision = preferences[longPreferencesKey("$prefix.applied")] ?: 0,
                observedServerRevision = preferences[longPreferencesKey("$prefix.observed")] ?: 0,
                lastConfirmedAtEpochMillis = preferences[longPreferencesKey("$prefix.confirmed")] ?: 0,
                lastSuccessfulSyncAtEpochMillis = preferences[longPreferencesKey("$prefix.synced")] ?: 0,
                status = preferences[stringPreferencesKey("$prefix.status")]?.let(CatalogSyncStatus::valueOf)
                    ?: CatalogSyncStatus.CHECKING,
                lastErrorCode = preferences[stringPreferencesKey("$prefix.error")],
            )
        }
    }

    private suspend fun persist(userId: Long, value: CatalogFreshness) {
        val prefix = "$userId.${value.kind.name}"
        context.catalogConsistencyDataStore.edit { preferences ->
            preferences[longPreferencesKey("$prefix.applied")] = value.appliedRevision
            preferences[longPreferencesKey("$prefix.observed")] = value.observedServerRevision
            preferences[longPreferencesKey("$prefix.confirmed")] = value.lastConfirmedAtEpochMillis
            preferences[longPreferencesKey("$prefix.synced")] = value.lastSuccessfulSyncAtEpochMillis
            preferences[stringPreferencesKey("$prefix.status")] = value.status.name
            value.lastErrorCode?.let { preferences[stringPreferencesKey("$prefix.error")] = it }
                ?: preferences.remove(stringPreferencesKey("$prefix.error"))
        }
        if (isActiveAccount(userId)) _freshness.value = _freshness.value + (value.kind to value)
    }

    private suspend fun activateAccount(userId: Long) {
        val previous = activeUserId.getAndSet(userId)
        if (previous != NO_ACTIVE_USER && previous != userId) {
            latestStates.remove(previous)
            accountJobs.remove(previous)?.cancel()
        }
        _freshness.value = load(userId)
    }

    private fun isActiveAccount(userId: Long): Boolean = activeUserId.get() == userId

    private suspend fun loadPolicyRevision(userId: Long): Long = context.catalogConsistencyDataStore.data.first()[
        longPreferencesKey("$userId.policyRevision")
    ] ?: 0L

    private suspend fun persistPolicyRevision(userId: Long, revision: Long) {
        context.catalogConsistencyDataStore.edit { preferences ->
            preferences[longPreferencesKey("$userId.policyRevision")] = revision
        }
    }

    private fun CatalogFreshness?.orEmpty(kind: CatalogKind): CatalogFreshness = this ?: CatalogFreshness(kind)

    private companion object {
        const val FRESHNESS_WINDOW_MILLIS = 30_000L
        const val NO_ACTIVE_USER = -1L
    }
}
