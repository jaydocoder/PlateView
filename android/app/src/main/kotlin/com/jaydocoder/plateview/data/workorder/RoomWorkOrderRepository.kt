package com.jaydocoder.plateview.data.workorder

import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import okhttp3.ResponseBody

@Singleton
class RoomWorkOrderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: WorkOrderApi,
    private val dao: WorkOrderCacheDao,
) : WorkOrderRepository {
    private val gson = Gson()
    private val synchronizationMutex = Mutex()

    override suspend fun searchCached(keyword: String): List<WorkOrder> = dao.search(normalize(keyword), MAXIMUM_RESULTS).map(::fromEntity)

    override suspend fun searchRemote(accessToken: String, keyword: String): List<WorkOrder> =
        api.search(bearer(accessToken), keyword).candidates.map(WorkOrderDto::toDomain)

    override suspend fun searchMessagesCached(keyword: String): List<WechatMessage> =
        dao.searchMessages(normalize(keyword), MAXIMUM_RESULTS).map { gson.fromJson(it.detailJson, WechatMessage::class.java) }

    override suspend fun searchMessagesRemote(accessToken: String, keyword: String, offset: Int): WechatMessagePage {
        val page = api.searchMessages(bearer(accessToken), keyword, offset, MAXIMUM_RESULTS)
        val records = page.records.map(WechatMessageDto::toDomain)
        if (records.isNotEmpty()) dao.upsertMessages(records.map { it.toEntity(gson) })
        return WechatMessagePage(records, page.nextOffset)
    }

    override suspend fun searchHomeRemote(accessToken: String, keyword: String): WorkOrderHomeSearchResult {
        val response = api.searchHome(bearer(accessToken), keyword, MAXIMUM_RESULTS)
        val workOrders = response.workOrderCandidates.map(WorkOrderDto::toDomain)
        val messages = response.wechatMessages.map(WechatMessageDto::toDomain)
        if (workOrders.isNotEmpty()) dao.upsert(workOrders.map { it.toEntity(gson) })
        if (messages.isNotEmpty()) dao.upsertMessages(messages.map { it.toEntity(gson) })
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

    override suspend fun getMessageDetail(accessToken: String, messageId: Long): WechatMessage {
        val remote = runCatching { api.messageDetail(bearer(accessToken), messageId).toDomain() }
        remote.getOrNull()?.let { record ->
            dao.upsertMessages(listOf(record.toEntity(gson)))
            return record
        }
        return dao.getMessage(messageId)?.let { gson.fromJson(it.detailJson, WechatMessage::class.java) }
            ?: throw remote.exceptionOrNull()!!
    }

    override suspend fun synchronize(accessToken: String, forceVersionCheck: Boolean): WorkOrderSyncResult = synchronizationMutex.withLock {
        val now = System.currentTimeMillis()
        var state = dao.state()
        if (!forceVersionCheck && state != null && now - state.checkedAtEpochMillis < VERSION_CHECK_INTERVAL_MILLIS) {
            return@withLock WorkOrderSyncResult(false)
        }
        val remoteVersion = api.catalogVersion(bearer(accessToken)).catalogVersion
        if (state?.catalogVersion == remoteVersion) {
            dao.updateState(state.copy(checkedAtEpochMillis = now))
            return@withLock WorkOrderSyncResult(false)
        }
        var afterVersion = state?.catalogVersion ?: 0L
        do {
            val page = api.changes(bearer(accessToken), afterVersion, PAGE_SIZE)
            if (page.records.isNotEmpty()) dao.upsert(page.records.map { it.toDomain().toEntity(gson) })
            afterVersion = page.nextVersion
            state = WorkOrderCatalogStateEntity(catalogVersion = afterVersion, checkedAtEpochMillis = now)
            dao.updateState(state)
        } while (page.hasMore)
        dao.updateState(WorkOrderCatalogStateEntity(catalogVersion = remoteVersion, checkedAtEpochMillis = now))
        WorkOrderSyncResult(true)
    }

    override suspend fun getDetail(accessToken: String, recordId: Long): WorkOrder {
        val remote = runCatching { api.detail(bearer(accessToken), recordId).toDomain() }
        remote.getOrNull()?.let { record ->
            dao.upsert(listOf(record.toEntity(gson)))
            return record
        }
        return dao.get(recordId)?.let(::fromEntity) ?: throw remote.exceptionOrNull()!!
    }

    override suspend fun getHistory(accessToken: String, recordId: Long): List<WorkOrder> =
        api.history(bearer(accessToken), recordId).records.map(WorkOrderDto::toDomain)

    override suspend fun image(accessToken: String, userId: Long, recordId: Long, image: WorkOrderImage, variant: String): CachedWorkOrderImage {
        return downloadVariant(
            request = { range -> api.image(bearer(accessToken), range, recordId, image.id, variant) },
            userId = userId,
            id = image.id,
            sha256 = image.sha256,
            variant = variant,
        )
    }

    override suspend fun attachment(accessToken: String, userId: Long, messageId: Long, attachment: WorkOrderAttachment, variant: String): CachedWorkOrderImage =
        downloadVariant(
            request = { range -> api.attachment(bearer(accessToken), range, messageId, attachment.id, variant) },
            userId = userId,
            id = attachment.id,
            sha256 = attachment.sha256,
            variant = variant,
        )

    private suspend fun downloadVariant(
        request: suspend (String?) -> Response<ResponseBody>,
        userId: Long,
        id: Long,
        sha256: String?,
        variant: String,
    ): CachedWorkOrderImage {
        val directory = File(context.filesDir, "work-order-images/$userId").also { check(it.exists() || it.mkdirs()) }
        val cacheKey = "$id-$variant-${sha256.orEmpty()}"
        directory.listFiles()?.firstOrNull { it.nameWithoutExtension == cacheKey }?.let { return CachedWorkOrderImage(it, variant) }
        val temporary = File(directory, "$cacheKey.download")
        var downloadedBytes = temporary.length().coerceAtLeast(0L)
        var response = request(downloadedBytes.takeIf { it > 0L }?.let { "bytes=$it-" })
        if (response.code() == HTTP_RANGE_NOT_SATISFIABLE || !response.isSuccessful) {
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
        body.byteStream().use { input ->
            FileOutputStream(temporary, partial).buffered().use { output -> input.copyTo(output) }
        }
        response.raw().close()
        check(temporary.length() > 0L) { "服务器返回了空图片" }
        check(temporary.renameTo(target) || run { temporary.copyTo(target, overwrite = true); temporary.delete(); true }) { "无法写入微信车单图片缓存" }
        return CachedWorkOrderImage(target, variant)
    }

    override suspend fun clear(userId: Long?) {
        dao.clear()
        val root = File(context.filesDir, "work-order-images")
        if (userId == null) root.deleteRecursively() else File(root, userId.toString()).deleteRecursively()
    }

    private fun fromEntity(entity: WorkOrderCacheEntity): WorkOrder = gson.fromJson(entity.detailJson, WorkOrder::class.java)

    private companion object {
        const val MAXIMUM_RESULTS = 8
        const val PAGE_SIZE = 200
        const val VERSION_CHECK_INTERVAL_MILLIS = 15 * 60 * 1_000L
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val HTTP_CONTENT_RANGE_HEADER = "Content-Range"
        fun bearer(token: String) = "Bearer $token"
        fun normalize(value: String) = value.uppercase().replace(Regex("[\\s，。；：、,.;:()（）【】\\[\\]_-]+"), "")
    }
}

private fun WorkOrderDto.toDomain() = WorkOrder(
    id, orderNumber, rawPlate, normalizedPlate, vehicleType, declaredPeople, rawValidTime, location, verificationMethod,
    reason, remarks, status, parseQuality, catalogRevision, rawContent, sentAt, sourceKey, sourceName, senderUsername,
    senderDisplay, senderGroupNickname, people.map { WorkOrderPerson(it.rawLine, it.name, it.identityNumber) },
    images.map { WorkOrderImage(it.id, it.sha256, it.contentType, it.originalSize, it.previewAvailable, it.thumbnailAvailable, it.availability, it.kind, it.fileName, it.pageCount) },
    vehicles.map { WorkOrderVehicle(it.rawDescription, it.rawPlate, it.normalizedPlate, it.vehicleType) },
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
        WorkOrderAttachment(it.id, it.kind, it.fileName, it.sha256, it.contentType, it.originalSize, it.previewAvailable, it.thumbnailAvailable, it.availability, it.pageCount)
    },
)

private fun WorkOrder.toEntity(gson: Gson) = WorkOrderCacheEntity(
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
    detailJson = gson.toJson(this),
)

private fun WechatMessage.toEntity(gson: Gson) = WechatMessageCacheEntity(
    messageId = id,
    businessType = businessType,
    displayName = displayName,
    sourceName = sourceName,
    sentAt = sentAt,
    searchableText = listOf(displayName, senderDisplay, senderGroupNickname, sourceName, rawContent, plateNumbers.joinToString())
        .joinToString("").uppercase().replace(Regex("[\\s，。；：、,.;:()（）【】\\[\\]_-]+"), ""),
    detailJson = gson.toJson(this),
)
