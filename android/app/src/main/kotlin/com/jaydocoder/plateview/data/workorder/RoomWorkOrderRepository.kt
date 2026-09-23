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
import java.util.concurrent.ConcurrentHashMap
import retrofit2.Response
import okhttp3.ResponseBody

@Singleton
class RoomWorkOrderRepository @Inject constructor(
    private val api: WorkOrderApi,
    private val dao: WorkOrderCacheDao,
    private val runtimePolicyProvider: ClientRuntimePolicyProvider,
    private val attachmentCacheRepository: WechatAttachmentCacheRepository,
) : WorkOrderRepository {
    private val gson = Gson()
    private val synchronizationMutex = Mutex()
    private val attachmentSynchronizationMutex = Mutex()

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

    override suspend fun synchronize(accessToken: String, userId: Long, forceVersionCheck: Boolean): WorkOrderSyncResult = synchronizationMutex.withLock {
        val now = System.currentTimeMillis()
        var state = dao.state(userId) ?: WorkOrderCatalogStateEntity(userId, 0, 0, 0)
        if (!forceVersionCheck && state.checkedAtEpochMillis > 0 && now - state.checkedAtEpochMillis < VERSION_CHECK_INTERVAL_MILLIS) {
            return@withLock WorkOrderSyncResult(false)
        }
        var refreshed = false
        val limits = runtimePolicyProvider.policy.value
        if (limits.workOrderResultLimit > 0) {
            val remoteVersion = api.catalogVersion(bearer(accessToken)).catalogVersion
            var afterVersion = state.catalogVersion
            while (afterVersion < remoteVersion) {
                val page = api.changes(bearer(accessToken), afterVersion, PAGE_SIZE)
                if (page.records.isNotEmpty()) {
                    dao.upsert(page.records.map { it.toDomain().toEntity(userId, gson, now, now) })
                    refreshed = true
                }
                afterVersion = page.nextVersion
                state = state.copy(catalogVersion = afterVersion, checkedAtEpochMillis = now)
                dao.updateState(state)
                if (!page.hasMore) break
            }
            state = state.copy(catalogVersion = remoteVersion)
        }
        if (limits.wechatMessageResultLimit > 0) {
            var afterVersion = state.messageCatalogVersion
            var remoteMessageVersion = afterVersion
            do {
                val page = api.messageChanges(bearer(accessToken), afterVersion, PAGE_SIZE)
                remoteMessageVersion = page.catalogVersion
                if (page.records.isNotEmpty()) {
                    dao.upsertMessages(page.records.map { dto ->
                        dto.toDomain().toEntity(userId, gson, dto.catalogRevision, now, now)
                    })
                    refreshed = true
                }
                afterVersion = page.nextVersion
                state = state.copy(messageCatalogVersion = afterVersion, checkedAtEpochMillis = now)
                dao.updateState(state)
            } while (page.hasMore)
            state = state.copy(messageCatalogVersion = remoteMessageVersion)
        }
        dao.updateState(state.copy(checkedAtEpochMillis = now))
        WorkOrderSyncResult(refreshed)
    }

    override suspend fun synchronizeAttachments(accessToken: String, userId: Long): WorkOrderAttachmentSyncResult =
        attachmentSynchronizationMutex.withLock {
            var afterId = 0L
            var downloaded = 0
            var skipped = 0
            var failed = 0
            val activeCacheKeys = ConcurrentHashMap.newKeySet<String>()
            do {
                val page = api.attachmentManifest(bearer(accessToken), afterId, ATTACHMENT_PAGE_SIZE)
                val now = System.currentTimeMillis()
                dao.upsertAttachmentTasks(page.items.map { item ->
                    WechatAttachmentDownloadTaskEntity(
                        userId = userId,
                        attachmentId = item.attachmentId,
                        variant = attachmentCacheVariant(item.originalAvailable),
                        sha256 = item.sha256 ?: "unknown",
                        expectedSize = item.originalSize,
                        downloadedBytes = 0,
                        localPath = null,
                        status = "DISCOVERED",
                        attemptCount = 0,
                        nextRetryAt = null,
                        lastErrorCode = null,
                        manifestRevision = page.manifestRevision,
                        updatedAt = now,
                    )
                })
                page.items.chunked(ATTACHMENT_DOWNLOAD_CONCURRENCY).forEach { batch ->
                    val outcomes = coroutineScope {
                        batch.map { item ->
                            async {
                                val variant = attachmentCacheVariant(item.originalAvailable)
                                val key = attachmentCacheKey(item.attachmentId, variant, item.sha256, item.sourceQuality)
                                activeCacheKeys += key
                                val cachedFile = attachmentCacheRepository.findCached(userId, item.attachmentId, variant, item.sha256, item.sourceQuality)
                                if (cachedFile != null) {
                                    dao.upsertAttachmentTasks(listOf(taskFor(userId, item, variant, page.manifestRevision, "COMPLETED", cachedFile.absolutePath)))
                                    AttachmentDownloadOutcome.SKIPPED
                                } else {
                                    runCatching {
                                        downloadVariant(
                                            request = { range -> api.attachmentFile(bearer(accessToken), range, item.attachmentId, variant) },
                                            userId = userId,
                                            id = item.attachmentId,
                                            sha256 = item.sha256,
                                            variant = variant,
                                            sourceQuality = item.sourceQuality,
                                        )
                                    }.fold(
                                        onSuccess = { cached ->
                                            dao.upsertAttachmentTasks(listOf(taskFor(userId, item, variant, page.manifestRevision, "COMPLETED", cached.file.absolutePath)))
                                            AttachmentDownloadOutcome.DOWNLOADED
                                        },
                                        onFailure = { error ->
                                            dao.upsertAttachmentTasks(listOf(taskFor(userId, item, variant, page.manifestRevision, "RETRY_WAIT", key, error::class.simpleName)))
                                            AttachmentDownloadOutcome.FAILED
                                        },
                                    )
                                }
                            }
                        }.awaitAll()
                    }
                    downloaded += outcomes.count { it == AttachmentDownloadOutcome.DOWNLOADED }
                    skipped += outcomes.count { it == AttachmentDownloadOutcome.SKIPPED }
                    failed += outcomes.count { it == AttachmentDownloadOutcome.FAILED }
                }
                afterId = page.nextAfterId ?: afterId
            } while (page.nextAfterId != null)
            if (failed == 0) pruneAttachmentCache(attachmentCacheRepository.directory(userId), activeCacheKeys)
            WorkOrderAttachmentSyncResult(downloaded, skipped, failed)
        }

    override suspend fun reportAttachmentCacheStatus(accessToken: String, userId: Long, clientInstanceId: String) {
        val tasks = dao.attachmentTasks(userId)
        if (tasks.isEmpty()) return
        api.saveAttachmentCacheStatus(
            bearer(accessToken),
            AttachmentCacheStatusRequestDto(
                clientInstanceId = clientInstanceId,
                manifestRevision = tasks.maxOf { it.manifestRevision },
                items = tasks.map {
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

    private fun taskFor(
        userId: Long,
        item: WorkOrderAttachmentManifestItemDto,
        variant: String,
        manifestRevision: Long,
        status: String,
        cacheKey: String,
        errorCode: String? = null,
    ) = WechatAttachmentDownloadTaskEntity(
        userId, item.attachmentId, variant, item.sha256 ?: "unknown", item.originalSize,
        if (status == "COMPLETED") item.originalSize ?: 0 else 0,
        cacheKey, status, if (status == "RETRY_WAIT") 1 else 0,
        if (status == "RETRY_WAIT") System.currentTimeMillis() + 60_000 else null,
        errorCode, manifestRevision, System.currentTimeMillis(),
    )

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
        downloadVariant(
            request = { range -> api.attachmentFile(bearer(accessToken), range, attachment.id, variant) },
            userId = userId,
            id = attachment.id,
            sha256 = attachment.sha256,
            variant = variant,
            sourceQuality = attachment.sourceQuality,
        ).also { check(!runtimePolicyProvider.cacheMaintenanceActive.value) { "客户端缓存正在清理" } }

    private suspend fun downloadVariant(
        request: suspend (String?) -> Response<ResponseBody>,
        userId: Long,
        id: Long,
        sha256: String?,
        variant: String,
        sourceQuality: String = "UNKNOWN",
    ): CachedWorkOrderImage {
        return attachmentCacheRepository.getOrDownload(
            userId = userId,
            attachmentId = id,
            variant = variant,
            sha256 = sha256,
            sourceQuality = sourceQuality,
            request = request,
        )
    }

    override suspend fun clear(userId: Long?) = synchronizationMutex.withLock {
        attachmentSynchronizationMutex.withLock {
            if (userId == null) dao.clearAll() else dao.clear(userId)
            attachmentCacheRepository.clear(userId)
            Unit
        }
    }

    override suspend fun clearWorkOrders(userId: Long) = dao.clearRecords(userId)

    override suspend fun clearMessages(userId: Long) = dao.clearMessages(userId)

    private fun fromEntity(entity: WorkOrderCacheEntity): WorkOrder = gson.fromJson(entity.detailJson, WorkOrder::class.java)

    private companion object {
        const val MAXIMUM_RESULTS = 8
        const val PAGE_SIZE = 200
        const val ATTACHMENT_PAGE_SIZE = 200
        const val ATTACHMENT_DOWNLOAD_CONCURRENCY = 3
        const val VERSION_CHECK_INTERVAL_MILLIS = 15 * 60 * 1_000L
        fun bearer(token: String) = "Bearer $token"
        fun normalize(value: String) = value.uppercase().replace(Regex("[\\s，。；：、,.;:()（）【】\\[\\]_-]+"), "")
    }
}

internal suspend fun downloadAttachmentVariant(
    request: suspend (String?) -> Response<ResponseBody>,
    directory: File,
    cacheKey: String,
    variant: String,
    expectedSha256: String?,
): CachedWorkOrderImage {
    val temporary = File(directory, "$cacheKey.download")
    var downloadedBytes = temporary.length().coerceAtLeast(0L)
    var response = request(downloadedBytes.takeIf { it > 0L }?.let { "bytes=$it-" })
    if (response.code() == HTTP_RANGE_NOT_SATISFIABLE || !response.isSuccessful) {
        response.body()?.close()
        temporary.delete()
        downloadedBytes = 0L
        response = request(null)
    }
    val body = checkNotNull(response.body()) { "服务器未返回微信附件" }
    val extension = when (body.contentType()?.subtype) {
        "jpeg" -> "jpg"
        "png" -> "png"
        "gif" -> "gif"
        "pdf" -> "pdf"
        else -> "webp"
    }
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
            FileOutputStream(temporary, partial).buffered().use { output -> input.copyTo(output) }
        }
    }
    check(temporary.length() > 0L) { "服务器返回了空图片" }
    if (!expectedSha256.isNullOrBlank()) {
        val actualSha256 = temporary.sha256()
        check(actualSha256.equals(expectedSha256, ignoreCase = true)) {
            temporary.delete()
            "微信附件下载校验失败"
        }
    }
    check(temporary.renameTo(target) || run { temporary.copyTo(target, overwrite = true); temporary.delete(); true }) {
        "无法写入微信车单图片缓存"
    }
    return CachedWorkOrderImage(target, variant)
}

internal fun findCachedAttachmentFile(
    directory: File,
    cacheKey: String,
    id: Long,
    variant: String,
    expectedSha256: String?,
): File? {
    val files = directory.listFiles().orEmpty().filter { it.isFile && it.extension != "download" }
    files.firstOrNull { it.nameWithoutExtension == cacheKey }?.let { return it }
    if (expectedSha256.isNullOrBlank()) return null
    return files.firstOrNull { candidate ->
        candidate.name.startsWith("$id-$variant-") && candidate.sha256().equals(expectedSha256, ignoreCase = true)
    }
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

private enum class AttachmentDownloadOutcome { DOWNLOADED, SKIPPED, FAILED }

internal fun attachmentCacheVariant(originalAvailable: Boolean): String =
    if (originalAvailable) "original" else "thumbnail"

internal fun attachmentCacheKey(id: Long, variant: String, sha256: String?, sourceQuality: String): String =
    "$id-$variant-${sourceQuality.lowercase()}-${sha256.orEmpty()}"

internal fun pruneAttachmentCache(directory: File, activeCacheKeys: Set<String>) {
    directory.listFiles().orEmpty().filter(File::isFile).forEach { file ->
        val cacheKey = if (file.extension == "download") file.name.removeSuffix(".download") else file.nameWithoutExtension
        if (cacheKey !in activeCacheKeys) file.delete()
    }
}

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
