package com.jaydocoder.plateview.data.workorder

import com.google.gson.Gson
import com.jaydocoder.plateview.domain.workorder.CachedWorkOrderImage
import com.jaydocoder.plateview.domain.workorder.WorkOrder
import com.jaydocoder.plateview.domain.workorder.WorkOrderImage
import com.jaydocoder.plateview.domain.workorder.WorkOrderPerson
import com.jaydocoder.plateview.domain.workorder.WorkOrderVehicle
import com.jaydocoder.plateview.domain.workorder.WorkOrderAttachment
import com.jaydocoder.plateview.domain.workorder.WechatMessage
import com.jaydocoder.plateview.domain.workorder.WechatMessagePage
import com.jaydocoder.plateview.domain.workorder.WorkOrderRepository
import com.jaydocoder.plateview.domain.workorder.WorkOrderHomeSearchResult
import com.jaydocoder.plateview.domain.workorder.WorkOrderSyncResult
import com.jaydocoder.plateview.domain.workorder.WorkOrderAttachmentSyncResult
import com.jaydocoder.plateview.domain.workorder.AttachmentDownloadState
import com.jaydocoder.plateview.domain.workorder.AttachmentManifestSyncResult
import com.jaydocoder.plateview.data.network.ClientRuntimePolicyProvider
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import retrofit2.Response
import okhttp3.ResponseBody
import retrofit2.HttpException
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomWorkOrderRepository @Inject constructor(
    private val api: WorkOrderApi,
    private val dao: WorkOrderCacheDao,
    private val runtimePolicyProvider: ClientRuntimePolicyProvider,
    private val attachmentCacheRepository: WechatAttachmentCacheRepository,
) : WorkOrderRepository {
    private val gson = Gson()
    private val synchronizationMutex = Mutex()
    private val attachmentManifestMutex = Mutex()
    private val attachmentDownloadMutex = Mutex()

    override suspend fun searchCached(userId: Long, keyword: String): List<WorkOrder> = searchCached(userId, keyword, MAXIMUM_RESULTS)

    override suspend fun searchCached(userId: Long, keyword: String, limit: Int): List<WorkOrder> =
        dao.search(userId, normalize(keyword), limit.coerceIn(1, 50)).map(::fromEntity)

    override suspend fun searchRemote(accessToken: String, keyword: String): List<WorkOrder> =
        api.search(bearer(accessToken), keyword).candidates.map(WorkOrderDto::toDomain)

    override suspend fun searchMessagesCached(userId: Long, keyword: String): List<WechatMessage> = searchMessagesCached(userId, keyword, MAXIMUM_RESULTS)

    override suspend fun searchMessagesCached(userId: Long, keyword: String, limit: Int): List<WechatMessage> =
        dao.searchMessages(userId, normalize(keyword), limit.coerceIn(1, 50)).map { gson.fromJson(it.detailJson, WechatMessage::class.java) }

    override suspend fun searchMessagesRemote(accessToken: String, keyword: String, offset: Int): WechatMessagePage {
        val page = api.searchMessages(bearer(accessToken), keyword, offset, MAXIMUM_RESULTS)
        val records = page.records.map(WechatMessageDto::toDomain)
        return WechatMessagePage(records, page.nextOffset)
    }

    override suspend fun searchHomeRemote(accessToken: String, userId: Long, keyword: String): WorkOrderHomeSearchResult =
        searchHomeRemote(accessToken, userId, keyword, MAXIMUM_RESULTS)

    override suspend fun searchHomeRemote(accessToken: String, userId: Long, keyword: String, limit: Int): WorkOrderHomeSearchResult {
        val response = api.searchHome(bearer(accessToken), keyword, limit.coerceIn(1, 50))
        val workOrders = response.workOrderCandidates.map(WorkOrderDto::toDomain)
        val messages = response.wechatMessages.map(WechatMessageDto::toDomain)
        val now = System.currentTimeMillis()
        if (workOrders.isNotEmpty()) dao.upsert(workOrders.map { it.toEntity(userId, gson, now, now) })
        if (messages.isNotEmpty()) dao.upsertMessages(messages.map { it.toEntity(userId, gson, response.catalogVersion, now, now) })
        return WorkOrderHomeSearchResult(
            workOrders = workOrders,
            wechatMessages = messages,
            workOrderHasMore = response.workOrderHasMore,
            wechatMessageHasMore = response.wechatMessageHasMore,
            catalogVersion = response.catalogVersion,
            workOrderFailed = response.workOrderFailed,
            wechatMessageFailed = response.wechatMessageFailed,
        )
    }

    override suspend fun getCachedWechatMessage(userId: Long, messageId: Long): WechatMessage? =
        dao.getMessage(userId, messageId)?.let { gson.fromJson(it.detailJson, WechatMessage::class.java) }

    override suspend fun refreshWechatMessage(accessToken: String, userId: Long, messageId: Long): WechatMessage {
        val record = api.messageDetail(bearer(accessToken), messageId).toDomain()
        val now = System.currentTimeMillis()
        dao.upsertMessages(listOf(record.toEntity(userId, gson, catalogRevision = 0, cachedAt = now, lastValidatedAt = now)))
        return record
    }

    override suspend fun synchronize(
        accessToken: String,
        userId: Long,
        forceVersionCheck: Boolean,
        targetWorkOrderRevision: Long?,
        targetMessageRevision: Long?,
    ): WorkOrderSyncResult = synchronizationMutex.withLock {
        val now = System.currentTimeMillis()
        var state = dao.state(userId) ?: WorkOrderCatalogStateEntity(userId, 0, 0, 0)
        if (!forceVersionCheck && state.checkedAtEpochMillis > 0 && now - state.checkedAtEpochMillis < VERSION_CHECK_INTERVAL_MILLIS) {
            return@withLock WorkOrderSyncResult(false, state.catalogVersion, state.messageCatalogVersion)
        }
        var refreshed = false
        val limits = runtimePolicyProvider.policy.value
        val pendingRecords = mutableListOf<WorkOrderCacheEntity>()
        val pendingMessages = mutableListOf<WechatMessageCacheEntity>()
        val removedRecordIds = mutableListOf<Long>()
        val removedMessageIds = mutableListOf<Long>()
        var replaceWorkOrders = false
        var replaceMessages = false
        if (limits.workOrderResultLimit > 0) {
            val remoteVersion = targetWorkOrderRevision ?: api.catalogVersion(bearer(accessToken)).catalogVersion
            var afterVersion = state.catalogVersion
            var afterId = 0L
            var hasMore = afterVersion < remoteVersion
            while (hasMore) {
                val page = api.changes(bearer(accessToken), afterVersion, afterId, remoteVersion, PAGE_SIZE)
                if (page.fullSyncRequired) {
                    pendingRecords.clear()
                    removedRecordIds.clear()
                    replaceWorkOrders = true
                    var fullAfterId = 0L
                    do {
                        val fullPage = api.fullWorkOrderCatalog(bearer(accessToken), fullAfterId, remoteVersion, PAGE_SIZE)
                        check(fullPage.catalogVersion == remoteVersion) { "微信车单全量目录版本发生变化" }
                        pendingRecords += fullPage.records.map { it.toDomain().toEntity(userId, gson, now, now) }
                        fullAfterId = fullPage.nextAfterId ?: fullAfterId
                    } while (fullPage.hasMore)
                    refreshed = true
                    break
                }
                if (page.records.isNotEmpty()) {
                    pendingRecords += page.records.map { it.toDomain().toEntity(userId, gson, now, now) }
                    refreshed = true
                }
                removedRecordIds += page.tombstones.map { it.entityId }
                afterVersion = page.nextVersion
                afterId = page.nextId
                hasMore = page.hasMore
            }
            state = state.copy(catalogVersion = remoteVersion)
        }
        if (limits.wechatMessageResultLimit > 0) {
            var afterVersion = state.messageCatalogVersion
            var afterId = 0L
            var remoteMessageVersion = targetMessageRevision ?: afterVersion
            do {
                val targetRevision = if (remoteMessageVersion > afterVersion) remoteMessageVersion else api.catalogVersion(bearer(accessToken)).catalogVersion
                val page = api.messageChanges(bearer(accessToken), afterVersion, afterId, targetRevision, PAGE_SIZE)
                remoteMessageVersion = page.catalogVersion
                if (page.fullSyncRequired) {
                    pendingMessages.clear()
                    removedMessageIds.clear()
                    replaceMessages = true
                    var fullAfterId = 0L
                    do {
                        val fullPage = api.fullMessageCatalog(bearer(accessToken), fullAfterId, targetRevision, PAGE_SIZE)
                        check(fullPage.catalogVersion == targetRevision) { "聊天记录全量目录版本发生变化" }
                        pendingMessages += fullPage.records.map { dto ->
                            dto.toDomain().toEntity(userId, gson, dto.catalogRevision, now, now)
                        }
                        fullAfterId = fullPage.nextAfterId ?: fullAfterId
                    } while (fullPage.hasMore)
                    remoteMessageVersion = targetRevision
                    refreshed = true
                    break
                }
                if (page.records.isNotEmpty()) {
                    pendingMessages += page.records.map { dto ->
                        dto.toDomain().toEntity(userId, gson, dto.catalogRevision, now, now)
                    }
                    refreshed = true
                }
                removedMessageIds += page.tombstones.map { it.entityId }
                afterVersion = page.nextVersion
                afterId = page.nextId
            } while (page.hasMore)
            state = state.copy(messageCatalogVersion = remoteMessageVersion)
        }
        dao.applyCatalogSync(
            records = pendingRecords,
            messages = pendingMessages,
            removedRecordIds = removedRecordIds.distinct(),
            removedMessageIds = removedMessageIds.distinct(),
            replaceWorkOrders = replaceWorkOrders,
            replaceMessages = replaceMessages,
            state = state.copy(checkedAtEpochMillis = now),
        )
        WorkOrderSyncResult(refreshed, state.catalogVersion, state.messageCatalogVersion)
    }

    override suspend fun synchronizeAttachmentManifest(accessToken: String, userId: Long): AttachmentManifestSyncResult =
        attachmentManifestMutex.withLock {
            var afterId = 0L
            var manifestRevision: Long? = null
            var changed = false
            do {
                val page = api.attachmentManifest(bearer(accessToken), afterId, ATTACHMENT_PAGE_SIZE)
                check(manifestRevision == null || manifestRevision == page.manifestRevision) {
                    "附件清单在分页同步期间发生变化，请重新同步"
                }
                manifestRevision = page.manifestRevision
                val now = System.currentTimeMillis()
                val tasks = page.items.map { item -> manifestTask(userId, item, page.manifestRevision, now) }
                changed = changed || tasks.any { task ->
                    val existing = dao.attachmentTask(userId, task.attachmentId, task.variant, task.sha256, task.sourceQuality)
                    existing == null || existing.manifestRevision != task.manifestRevision || existing.status != task.status
                }
                dao.upsertAttachmentTasks(tasks)
                page.nextAfterId?.let { nextAfterId ->
                    check(nextAfterId > afterId) { "附件清单分页游标没有前进" }
                    afterId = nextAfterId
                }
            } while (page.nextAfterId != null)
            val appliedRevision = checkNotNull(manifestRevision) { "附件清单缺少修订号" }
            dao.revokeMissingAttachmentTasks(userId, appliedRevision, System.currentTimeMillis())
            clearRevokedAttachmentFiles(userId)
            AttachmentManifestSyncResult(changed, appliedRevision)
        }

    override suspend fun downloadPendingAttachments(accessToken: String, userId: Long, clientInstanceId: String?): WorkOrderAttachmentSyncResult =
        attachmentDownloadMutex.withLock {
            var downloaded = 0
            var skipped = 0
            var failed = 0
            var retryable = 0
            while (true) {
                val tasks = dao.readyAttachmentTasks(userId, System.currentTimeMillis(), ATTACHMENT_DOWNLOAD_CONCURRENCY)
                if (tasks.isEmpty()) break
                val outcomes = coroutineScope {
                    tasks.map { task -> async { downloadTask(accessToken, task) } }.awaitAll()
                }
                downloaded += outcomes.count { it == AttachmentDownloadOutcome.DOWNLOADED }
                skipped += outcomes.count { it == AttachmentDownloadOutcome.SKIPPED || it == AttachmentDownloadOutcome.SOURCE_UNAVAILABLE }
                failed += outcomes.count { it == AttachmentDownloadOutcome.RETRYABLE_FAILED || it == AttachmentDownloadOutcome.TERMINAL_FAILED }
                retryable += outcomes.count { it == AttachmentDownloadOutcome.RETRYABLE_FAILED }
                clientInstanceId?.let { instanceId ->
                    runCatching { reportAttachmentCacheStatus(accessToken, userId, instanceId) }
                }
            }
            WorkOrderAttachmentSyncResult(downloaded, skipped, failed, retryable)
        }

    override suspend fun synchronizeAttachments(accessToken: String, userId: Long, clientInstanceId: String?): WorkOrderAttachmentSyncResult {
        synchronizeAttachmentManifest(accessToken, userId)
        return downloadPendingAttachments(accessToken, userId, clientInstanceId)
    }

    private suspend fun clearRevokedAttachmentFiles(userId: Long) {
        dao.attachmentTasksByStatus(userId, ATTACHMENT_STATUS_REVOKED).forEach { task ->
            attachmentCacheRepository.clearAttachmentVersion(
                userId = userId,
                attachmentId = task.attachmentId,
                variant = task.variant,
                sha256 = task.sha256.takeUnless { it == "unknown" },
                sourceQuality = task.sourceQuality,
            )
            dao.updateAttachmentTask(
                task.userId, task.attachmentId, task.variant, task.sha256, task.sourceQuality, ATTACHMENT_STATUS_REVOKED,
                0, null, task.attemptCount, null, task.lastErrorCode, false, System.currentTimeMillis(),
            )
        }
    }

    private suspend fun manifestTask(
        userId: Long,
        item: WorkOrderAttachmentManifestItemDto,
        manifestRevision: Long,
        now: Long,
    ): WechatAttachmentDownloadTaskEntity {
        val sha256 = item.sha256 ?: "unknown"
        val existing = dao.attachmentTask(userId, item.attachmentId, ORIGINAL_VARIANT, sha256, item.sourceQuality)
        val cached = attachmentCacheRepository.findCached(
            userId = userId,
            attachmentId = item.attachmentId,
            variant = ORIGINAL_VARIANT,
            sha256 = item.sha256,
            sourceQuality = item.sourceQuality,
            expectedSize = item.originalSize,
        )
        val status = when {
            !item.originalAvailable -> ATTACHMENT_STATUS_SOURCE_UNAVAILABLE
            cached != null -> ATTACHMENT_STATUS_COMPLETED
            existing?.status == ATTACHMENT_STATUS_FAILED && existing.manifestRevision == manifestRevision -> ATTACHMENT_STATUS_FAILED
            existing?.status == ATTACHMENT_STATUS_RETRY_WAIT -> ATTACHMENT_STATUS_RETRY_WAIT
            else -> ATTACHMENT_STATUS_DISCOVERED
        }
        return WechatAttachmentDownloadTaskEntity(
            userId = userId,
            attachmentId = item.attachmentId,
            kind = item.kind,
            fileName = item.fileName,
            variant = ORIGINAL_VARIANT,
            sha256 = sha256,
            sourceQuality = item.sourceQuality,
            expectedSize = item.originalSize,
            downloadedBytes = cached?.length() ?: existing?.downloadedBytes ?: 0,
            localPath = cached?.absolutePath ?: existing?.localPath,
            status = status,
            priority = existing?.priority ?: PRIORITY_HISTORY,
            foregroundRequested = existing?.foregroundRequested ?: false,
            attemptCount = existing?.attemptCount ?: 0,
            nextRetryAt = existing?.nextRetryAt,
            lastErrorCode = existing?.lastErrorCode,
            manifestRevision = manifestRevision,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
    }

    private suspend fun downloadTask(accessToken: String, task: WechatAttachmentDownloadTaskEntity): AttachmentDownloadOutcome {
        if (task.status == ATTACHMENT_STATUS_COMPLETED) return AttachmentDownloadOutcome.SKIPPED
        val now = System.currentTimeMillis()
        dao.updateAttachmentTask(
            task.userId, task.attachmentId, task.variant, task.sha256, task.sourceQuality, ATTACHMENT_STATUS_DOWNLOADING,
            task.downloadedBytes, null, task.attemptCount, null, null, task.foregroundRequested, now,
        )
        var persistedBytes = task.downloadedBytes
        return runCatching {
            downloadVariant(
                request = { range -> api.attachmentFile(bearer(accessToken), range, task.attachmentId, ORIGINAL_VARIANT) },
                userId = task.userId,
                id = task.attachmentId,
                sha256 = task.sha256.takeUnless { it == "unknown" },
                variant = ORIGINAL_VARIANT,
                sourceQuality = task.sourceQuality,
                expectedSize = task.expectedSize,
                onProgress = { bytes ->
                    if (bytes - persistedBytes >= PROGRESS_PERSIST_STEP_BYTES || bytes == task.expectedSize) {
                        persistedBytes = bytes
                        dao.updateAttachmentTask(
                            task.userId, task.attachmentId, task.variant, task.sha256, task.sourceQuality, ATTACHMENT_STATUS_DOWNLOADING,
                            bytes, null, task.attemptCount, null, null, task.foregroundRequested, System.currentTimeMillis(),
                        )
                    }
                },
            )
        }.fold(
            onSuccess = { cached ->
                dao.updateAttachmentTask(
                    task.userId, task.attachmentId, task.variant, task.sha256, task.sourceQuality, ATTACHMENT_STATUS_COMPLETED,
                    cached.file.length(), cached.file.absolutePath, task.attemptCount, null, null, false, System.currentTimeMillis(),
                )
                AttachmentDownloadOutcome.DOWNLOADED
            },
            onFailure = { error ->
                val attempts = task.attemptCount + 1
                val status = when {
                    error is HttpException && error.code() in setOf(401, 403) -> ATTACHMENT_STATUS_REVOKED
                    error is HttpException && error.code() == 404 -> ATTACHMENT_STATUS_SOURCE_UNAVAILABLE
                    attempts >= MAXIMUM_ATTACHMENT_ATTEMPTS -> ATTACHMENT_STATUS_FAILED
                    error is HttpException && error.code() == 400 -> ATTACHMENT_STATUS_FAILED
                    else -> ATTACHMENT_STATUS_RETRY_WAIT
                }
                val retryAt = retryAt(attempts).takeIf { status == ATTACHMENT_STATUS_RETRY_WAIT }
                dao.updateAttachmentTask(
                    task.userId, task.attachmentId, task.variant, task.sha256, task.sourceQuality, status,
                    maxOf(task.downloadedBytes, persistedBytes), null, attempts, retryAt,
                    errorCode(error), task.foregroundRequested, System.currentTimeMillis(),
                )
                when (status) {
                    ATTACHMENT_STATUS_RETRY_WAIT -> AttachmentDownloadOutcome.RETRYABLE_FAILED
                    ATTACHMENT_STATUS_SOURCE_UNAVAILABLE -> AttachmentDownloadOutcome.SOURCE_UNAVAILABLE
                    else -> AttachmentDownloadOutcome.TERMINAL_FAILED
                }
            },
        )
    }

    override suspend fun reportAttachmentCacheStatus(accessToken: String, userId: Long, clientInstanceId: String) {
        val tasks = dao.attachmentTasks(userId)
            .groupBy { it.attachmentId to it.variant }
            .values
            .mapNotNull { versions ->
                versions.maxWithOrNull(
                    compareBy<WechatAttachmentDownloadTaskEntity> { it.status != ATTACHMENT_STATUS_REVOKED }
                        .thenBy { it.updatedAt },
                )
            }
        val manifestRevision = tasks.maxOfOrNull { it.manifestRevision } ?: 0L
        val batches = if (tasks.isEmpty()) listOf(emptyList()) else tasks.chunked(ATTACHMENT_STATUS_REPORT_BATCH_SIZE)
        batches.forEach { batch ->
            api.saveAttachmentCacheStatus(
                bearer(accessToken),
                AttachmentCacheStatusRequestDto(
                    clientInstanceId = clientInstanceId,
                    manifestRevision = manifestRevision,
                    items = batch.map {
                    AttachmentCacheStatusItemDto(
                        attachmentId = it.attachmentId,
                        variant = it.variant,
                        status = it.status,
                        downloadedBytes = it.downloadedBytes,
                        expectedSize = it.expectedSize,
                        sha256 = it.sha256.takeUnless { hash -> hash == "unknown" },
                        attemptCount = it.attemptCount,
                        lastErrorCode = it.lastErrorCode,
                    )
                    },
                ),
            )
        }
    }

    override fun observeAttachmentDownload(userId: Long, attachmentId: Long): Flow<AttachmentDownloadState?> =
        dao.observeAttachmentTask(userId, attachmentId).map { it?.toDomain() }

    override suspend fun retryAttachmentDownload(userId: Long, attachmentId: Long) {
        dao.prioritizeAttachment(userId, attachmentId, PRIORITY_FOREGROUND, System.currentTimeMillis())
    }

    override suspend fun getCachedWorkOrder(userId: Long, recordId: Long): WorkOrder? = dao.get(userId, recordId)?.let(::fromEntity)

    override suspend fun refreshWorkOrder(accessToken: String, userId: Long, recordId: Long): WorkOrder {
        val record = api.detail(bearer(accessToken), recordId).toDomain()
        val now = System.currentTimeMillis()
        dao.upsert(listOf(record.toEntity(userId, gson, now, now)))
        return record
    }

    override suspend fun getHistory(accessToken: String, recordId: Long): List<WorkOrder> =
        api.history(bearer(accessToken), recordId).records.map(WorkOrderDto::toDomain)

    override suspend fun image(accessToken: String, userId: Long, recordId: Long, image: WorkOrderImage, variant: String): CachedWorkOrderImage {
        check(!runtimePolicyProvider.cacheMaintenanceActive.value) { "客户端缓存正在清理" }
        if (variant == ORIGINAL_VARIANT) {
            return foregroundOriginal(
                accessToken = accessToken,
                userId = userId,
                attachmentId = image.id,
                kind = image.kind,
                fileName = image.fileName,
                sha256 = image.sha256,
                sourceQuality = image.sourceQuality,
                expectedSize = image.originalSize,
                originalAvailable = image.availability == "AVAILABLE",
            )
        }
        return downloadVariant(
            request = { range -> api.image(bearer(accessToken), range, recordId, image.id, variant) },
            userId = userId,
            id = image.id,
            sha256 = image.sha256,
            variant = variant,
            sourceQuality = image.sourceQuality,
        )
    }

    override suspend fun attachment(accessToken: String, userId: Long, messageId: Long, attachment: WorkOrderAttachment, variant: String): CachedWorkOrderImage =
        if (variant == ORIGINAL_VARIANT) foregroundOriginal(
            accessToken = accessToken,
            userId = userId,
            attachmentId = attachment.id,
            kind = attachment.kind,
            fileName = attachment.fileName,
            sha256 = attachment.sha256,
            sourceQuality = attachment.sourceQuality,
            expectedSize = attachment.originalSize,
            originalAvailable = attachment.availability == "AVAILABLE",
        ) else downloadVariant(
            request = { range -> api.attachmentFile(bearer(accessToken), range, attachment.id, variant) },
            userId = userId,
            id = attachment.id,
            sha256 = attachment.sha256,
            variant = variant,
            sourceQuality = attachment.sourceQuality,
        ).also { check(!runtimePolicyProvider.cacheMaintenanceActive.value) { "客户端缓存正在清理" } }

    private suspend fun foregroundOriginal(
        accessToken: String,
        userId: Long,
        attachmentId: Long,
        kind: String,
        fileName: String?,
        sha256: String?,
        sourceQuality: String,
        expectedSize: Long?,
        originalAvailable: Boolean,
    ): CachedWorkOrderImage {
        check(originalAvailable) { "服务器暂未提供原文件" }
        val now = System.currentTimeMillis()
        val hash = sha256 ?: "unknown"
        val cached = attachmentCacheRepository.findCached(userId, attachmentId, ORIGINAL_VARIANT, sha256, sourceQuality, expectedSize)
        val existing = dao.attachmentTask(userId, attachmentId, ORIGINAL_VARIANT, hash, sourceQuality)
        val task = existing?.copy(
            kind = kind,
            fileName = fileName,
            sourceQuality = sourceQuality,
            expectedSize = expectedSize,
            status = if (cached != null) ATTACHMENT_STATUS_COMPLETED else ATTACHMENT_STATUS_DISCOVERED,
            priority = PRIORITY_FOREGROUND,
            foregroundRequested = true,
            nextRetryAt = null,
            localPath = cached?.absolutePath ?: existing.localPath,
            downloadedBytes = cached?.length() ?: existing.downloadedBytes,
            updatedAt = now,
        ) ?: WechatAttachmentDownloadTaskEntity(
            userId = userId,
            attachmentId = attachmentId,
            kind = kind,
            fileName = fileName,
            variant = ORIGINAL_VARIANT,
            sha256 = hash,
            sourceQuality = sourceQuality,
            expectedSize = expectedSize,
            downloadedBytes = cached?.length() ?: 0,
            localPath = cached?.absolutePath,
            status = if (cached != null) ATTACHMENT_STATUS_COMPLETED else ATTACHMENT_STATUS_DISCOVERED,
            priority = PRIORITY_FOREGROUND,
            foregroundRequested = true,
            attemptCount = 0,
            nextRetryAt = null,
            lastErrorCode = null,
            manifestRevision = existing?.manifestRevision ?: 0,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertAttachmentTasks(listOf(task))
        cached?.let { return CachedWorkOrderImage(it, ORIGINAL_VARIANT) }
        val outcome = downloadTask(accessToken, task)
        val completed = attachmentCacheRepository.findCached(userId, attachmentId, ORIGINAL_VARIANT, sha256, sourceQuality, expectedSize)
        check(outcome == AttachmentDownloadOutcome.DOWNLOADED && completed != null) { "原文件下载失败，请重试" }
        return CachedWorkOrderImage(completed, ORIGINAL_VARIANT)
    }

    private suspend fun downloadVariant(
        request: suspend (String?) -> Response<ResponseBody>,
        userId: Long,
        id: Long,
        sha256: String?,
        variant: String,
        sourceQuality: String = "UNKNOWN",
        expectedSize: Long? = null,
        onProgress: suspend (Long) -> Unit = {},
    ): CachedWorkOrderImage {
        return attachmentCacheRepository.getOrDownload(
            userId = userId,
            attachmentId = id,
            variant = variant,
            sha256 = sha256,
            sourceQuality = sourceQuality,
            expectedSize = expectedSize,
            onProgress = onProgress,
            request = request,
        )
    }

    override suspend fun clear(userId: Long?) = synchronizationMutex.withLock {
        attachmentManifestMutex.withLock {
            attachmentDownloadMutex.withLock {
                if (userId == null) dao.clearAll() else dao.clear(userId)
                attachmentCacheRepository.clear(userId)
                Unit
            }
        }
    }

    override suspend fun clearWorkOrders(userId: Long) = dao.revokeWorkOrders(userId)

    override suspend fun clearMessages(userId: Long) = dao.revokeMessages(userId)

    override suspend fun clearAttachmentCache(userId: Long) = attachmentManifestMutex.withLock {
        attachmentDownloadMutex.withLock {
            dao.clearAttachmentTasks(userId)
            attachmentCacheRepository.clear(userId)
        }
    }

    override suspend fun clearDeviceAttachmentFiles() = attachmentManifestMutex.withLock {
        attachmentDownloadMutex.withLock {
            attachmentCacheRepository.clearSharedFiles()
        }
    }

    private fun fromEntity(entity: WorkOrderCacheEntity): WorkOrder = gson.fromJson(entity.detailJson, WorkOrder::class.java)

    private companion object {
        const val MAXIMUM_RESULTS = 8
        const val PAGE_SIZE = 200
        const val ATTACHMENT_PAGE_SIZE = 200
        const val ATTACHMENT_DOWNLOAD_CONCURRENCY = 2
        const val ORIGINAL_VARIANT = "original"
        const val PRIORITY_HISTORY = 100
        const val PRIORITY_FOREGROUND = 1_000
        const val MAXIMUM_ATTACHMENT_ATTEMPTS = 20
        const val PROGRESS_PERSIST_STEP_BYTES = 256 * 1024L
        const val ATTACHMENT_STATUS_REPORT_BATCH_SIZE = 200
        const val VERSION_CHECK_INTERVAL_MILLIS = 15 * 60 * 1_000L
        fun bearer(token: String) = "Bearer $token"
        fun normalize(value: String) = value.uppercase().replace(Regex("[\\s，。；：、,.;:()（）【】\\[\\]_-]+"), "")
    }
}

private fun retryAt(attempt: Int): Long {
    val baseMinutes = when (attempt) { 1 -> 1L; 2 -> 3L; 3 -> 8L; else -> min(60L, 8L shl (attempt - 3).coerceAtMost(3)) }
    val jitterMillis = Random.nextLong(0L, 15_001L)
    return System.currentTimeMillis() + baseMinutes * 60_000L + jitterMillis
}

private fun errorCode(error: Throwable): String = when (error) {
    is HttpException -> "HTTP_${error.code()}"
    else -> error::class.simpleName ?: "UNKNOWN"
}

private fun WechatAttachmentDownloadTaskEntity.toDomain() = AttachmentDownloadState(
    attachmentId = attachmentId,
    kind = kind,
    fileName = fileName,
    variant = variant,
    status = status,
    downloadedBytes = downloadedBytes,
    expectedSize = expectedSize,
    localPath = localPath,
    attemptCount = attemptCount,
    lastErrorCode = lastErrorCode,
)

internal const val ATTACHMENT_STATUS_DISCOVERED = "DISCOVERED"
internal const val ATTACHMENT_STATUS_DOWNLOADING = "DOWNLOADING"
internal const val ATTACHMENT_STATUS_RETRY_WAIT = "RETRY_WAIT"
internal const val ATTACHMENT_STATUS_COMPLETED = "COMPLETED"
internal const val ATTACHMENT_STATUS_FAILED = "FAILED"
internal const val ATTACHMENT_STATUS_REVOKED = "REVOKED"
internal const val ATTACHMENT_STATUS_SOURCE_UNAVAILABLE = "SOURCE_UNAVAILABLE"

internal suspend fun downloadAttachmentVariant(
    request: suspend (String?) -> Response<ResponseBody>,
    directory: File,
    cacheKey: String,
    variant: String,
    expectedSha256: String?,
    expectedSize: Long? = null,
    onProgress: suspend (Long) -> Unit = {},
): CachedWorkOrderImage {
    val temporary = File(directory, "$cacheKey.download")
    var downloadedBytes = temporary.length().coerceAtLeast(0L)
    var response = request(downloadedBytes.takeIf { it > 0L }?.let { "bytes=$it-" })
    if (response.code() == HTTP_RANGE_NOT_SATISFIABLE) {
        response.body()?.close()
        if (temporary.isCompleteAttachment(expectedSize, expectedSha256)) {
            val target = File(directory, "$cacheKey.${attachmentExtension(null, variant)}")
            check(temporary.renameTo(target) || run { temporary.copyTo(target, overwrite = true); temporary.delete(); true }) {
                "无法写入微信附件缓存"
            }
            onProgress(target.length())
            return CachedWorkOrderImage(target, variant)
        }
        temporary.delete()
        downloadedBytes = 0L
        response = request(null)
    }
    if (!response.isSuccessful) {
        response.body()?.close()
        throw retrofit2.HttpException(response)
    }
    val body = checkNotNull(response.body()) { "服务器未返回微信附件" }
    val extension = attachmentExtension(body.contentType()?.subtype, variant)
    val target = File(directory, "$cacheKey.$extension")
    val partial = downloadedBytes > 0L && response.code() == HTTP_PARTIAL_CONTENT
    if (partial) {
        check(response.headers()[HTTP_CONTENT_RANGE_HEADER]?.startsWith("bytes $downloadedBytes-") == true) {
            "服务器返回了错误的微信附件续传范围"
        }
    } else if (downloadedBytes > 0L) {
        temporary.delete()
        downloadedBytes = 0L
    }
    body.use { responseBody ->
        responseBody.byteStream().use { input ->
            FileOutputStream(temporary, partial).buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    onProgress(downloadedBytes)
                }
            }
        }
    }
    if (temporary.length() <= 0L) {
        temporary.delete()
        error("服务器返回了空附件")
    }
    if (expectedSize != null && temporary.length() != expectedSize) {
        val actualSize = temporary.length()
        temporary.delete()
        error("微信附件大小校验失败：预期${expectedSize}字节，实际${actualSize}字节")
    }
    if (!expectedSha256.isNullOrBlank()) {
        val actualSha256 = temporary.sha256()
        check(actualSha256.equals(expectedSha256, ignoreCase = true)) {
            temporary.delete()
            "微信附件下载校验失败"
        }
    }
    check(temporary.renameTo(target) || run { temporary.copyTo(target, overwrite = true); temporary.delete(); true }) {
        "无法写入微信附件缓存"
    }
    return CachedWorkOrderImage(target, variant)
}

internal fun findCachedAttachmentFile(
    directory: File,
    cacheKey: String,
    id: Long,
    variant: String,
    expectedSha256: String?,
    expectedSize: Long? = null,
): File? {
    val files = directory.listFiles().orEmpty().filter { it.isFile && it.extension != "download" }
    files.firstOrNull { it.nameWithoutExtension == cacheKey && (expectedSize == null || it.length() == expectedSize) }?.let { return it }
    if (expectedSha256.isNullOrBlank()) return null
    return files.firstOrNull { candidate ->
        candidate.name.startsWith("$id-$variant-") &&
            (expectedSize == null || candidate.length() == expectedSize) &&
            candidate.sha256().equals(expectedSha256, ignoreCase = true)
    }
}

private fun File.isCompleteAttachment(expectedSize: Long?, expectedSha256: String?): Boolean =
    length() > 0L &&
        (expectedSize == null || length() == expectedSize) &&
        (expectedSha256.isNullOrBlank() || sha256().equals(expectedSha256, ignoreCase = true))

private fun attachmentExtension(contentSubtype: String?, variant: String): String = when (contentSubtype) {
    "jpeg" -> "jpg"
    "png" -> "png"
    "gif" -> "gif"
    "pdf" -> "pdf"
    else -> if (variant == "original") "bin" else "webp"
}

private fun File.sha256(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private const val HTTP_PARTIAL_CONTENT = 206
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
private const val HTTP_CONTENT_RANGE_HEADER = "Content-Range"

private enum class AttachmentDownloadOutcome {
    DOWNLOADED,
    SKIPPED,
    RETRYABLE_FAILED,
    TERMINAL_FAILED,
    SOURCE_UNAVAILABLE,
}

internal fun attachmentCacheKey(id: Long, variant: String, sha256: String?, sourceQuality: String): String =
    "$id-$variant-${sourceQuality.lowercase()}-${sha256.orEmpty()}"

private fun WorkOrderDto.toDomain() = WorkOrder(
    id, orderNumber, rawPlate, normalizedPlate, vehicleType, declaredPeople, rawValidTime, location, verificationMethod,
    reason, remarks, status, parseQuality, catalogRevision, rawContent, sentAt, sourceKey, sourceName, senderUsername,
    senderDisplay, senderGroupNickname, people.map { WorkOrderPerson(it.rawLine, it.name, it.identityNumber) },
    images.map { WorkOrderImage(it.id, it.sha256, it.contentType, it.originalSize, it.previewAvailable, it.thumbnailAvailable, it.availability, it.kind, it.fileName, it.pageCount, it.sourceQuality) },
    vehicles.map { WorkOrderVehicle(it.rawDescription, it.rawPlate, it.normalizedPlate, it.vehicleType) },
    displayName,
)

private fun WechatMessageDto.toDomain() = WechatMessage(
    id = id,
    businessType = businessType,
    rawContent = rawContent,
    matchedSnippet = matchedSnippet,
    sentAt = sentAt,
    sourceKey = sourceKey,
    sourceName = sourceName,
    senderUsername = senderUsername,
    senderDisplay = senderDisplay,
    senderGroupNickname = senderGroupNickname,
    displayName = displayName,
    plateNumbers = plateNumbers,
    attachments = attachments.map {
        WorkOrderAttachment(it.id, it.kind, it.fileName, it.sha256, it.contentType, it.originalSize, it.previewAvailable, it.thumbnailAvailable, it.availability, it.pageCount, it.sourceQuality)
    },
)

private fun WorkOrder.toEntity(userId: Long, gson: Gson, cachedAt: Long, lastValidatedAt: Long) = WorkOrderCacheEntity(
    userId = userId,
    recordId = id,
    orderNumber = orderNumber,
    rawPlate = rawPlate,
    status = status,
    sourceName = sourceName,
    location = location,
    rawValidTime = rawValidTime,
    sentAt = sentAt,
    searchableText = listOfNotNull(orderNumber, rawPlate, normalizedPlate, rawContent).joinToString("").uppercase().replace(Regex("[\\s，。；：、,.;:()（）【】\\[\\]_-]+"), ""),
    catalogRevision = catalogRevision,
    cachedAt = cachedAt,
    lastValidatedAt = lastValidatedAt,
    detailJson = gson.toJson(this),
)

private fun WechatMessage.toEntity(userId: Long, gson: Gson, catalogRevision: Long, cachedAt: Long, lastValidatedAt: Long) = WechatMessageCacheEntity(
    userId = userId,
    messageId = id,
    businessType = businessType,
    displayName = displayName,
    sourceName = sourceName,
    sentAt = sentAt,
    searchableText = listOf(displayName, senderDisplay, senderGroupNickname, sourceName, rawContent, plateNumbers.joinToString())
        .joinToString("").uppercase().replace(Regex("[\\s，。；：、,.;:()（）【】\\[\\]_-]+"), ""),
    catalogRevision = catalogRevision,
    cachedAt = cachedAt,
    lastValidatedAt = lastValidatedAt,
    detailJson = gson.toJson(this),
)
