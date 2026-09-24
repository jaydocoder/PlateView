package com.jaydocoder.plateview.domain.workorder

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class WorkOrder(
    val id: Long,
    val orderNumber: String?,
    val rawPlate: String?,
    val normalizedPlate: String?,
    val vehicleType: String?,
    val declaredPeople: Int?,
    val rawValidTime: String?,
    val location: String?,
    val verificationMethod: String?,
    val reason: String?,
    val remarks: String?,
    val status: String,
    val parseQuality: String,
    val catalogRevision: Long,
    val rawContent: String,
    val sentAt: String,
    val sourceKey: String,
    val sourceName: String,
    val senderUsername: String?,
    val senderDisplay: String?,
    val senderGroupNickname: String?,
    val people: List<WorkOrderPerson>,
    val images: List<WorkOrderImage>,
    val vehicles: List<WorkOrderVehicle> = emptyList(),
    val displayName: String? = null,
)

data class WorkOrderPerson(val rawLine: String, val name: String?, val identityNumber: String?)
data class WorkOrderVehicle(val rawDescription: String, val rawPlate: String, val normalizedPlate: String, val vehicleType: String?)
data class WorkOrderImage(
    val id: Long,
    val sha256: String?,
    val contentType: String?,
    val originalSize: Long?,
    val previewAvailable: Boolean,
    val thumbnailAvailable: Boolean,
    val availability: String,
    val kind: String = "IMAGE",
    val fileName: String? = null,
    val pageCount: Int? = null,
    val sourceQuality: String = "UNKNOWN",
)

data class WorkOrderSyncResult(
    val refreshed: Boolean,
    val workOrderRevision: Long,
    val messageRevision: Long,
)
data class AttachmentManifestSyncResult(
    val refreshed: Boolean,
    val appliedRevision: Long,
)
data class CachedWorkOrderImage(val file: File, val variant: String)

data class WechatMessage(
    val id: Long,
    val businessType: String,
    val rawContent: String,
    val matchedSnippet: String,
    val sentAt: String,
    val sourceKey: String,
    val sourceName: String,
    val senderUsername: String?,
    val senderDisplay: String?,
    val senderGroupNickname: String?,
    val displayName: String,
    val plateNumbers: List<String>,
    val attachments: List<WorkOrderAttachment>,
)

data class WorkOrderAttachment(
    val id: Long,
    val kind: String,
    val fileName: String?,
    val sha256: String?,
    val contentType: String?,
    val originalSize: Long?,
    val previewAvailable: Boolean,
    val thumbnailAvailable: Boolean,
    val availability: String,
    val pageCount: Int?,
    val sourceQuality: String = "UNKNOWN",
)

data class WorkOrderAttachmentSyncResult(
    val downloaded: Int,
    val skipped: Int,
    val failed: Int,
    val retryable: Int = 0,
)

data class AttachmentDownloadState(
    val attachmentId: Long,
    val kind: String,
    val fileName: String?,
    val variant: String,
    val status: String,
    val downloadedBytes: Long,
    val expectedSize: Long?,
    val localPath: String?,
    val attemptCount: Int,
    val lastErrorCode: String?,
) {
    val progress: Float? get() = expectedSize?.takeIf { it > 0 }?.let { (downloadedBytes.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }

    fun completedFile(): CachedWorkOrderImage? = localPath
        ?.takeIf { status == "COMPLETED" }
        ?.let(::File)
        ?.takeIf { it.isFile && it.length() > 0L }
        ?.let { CachedWorkOrderImage(it, variant) }
}

data class WechatMessagePage(val records: List<WechatMessage>, val nextOffset: Int?)
data class WorkOrderHomeSearchResult(
    val workOrders: List<WorkOrder>,
    val wechatMessages: List<WechatMessage>,
    val workOrderHasMore: Boolean,
    val wechatMessageHasMore: Boolean,
    val catalogVersion: Long,
    val workOrderFailed: Boolean = false,
    val wechatMessageFailed: Boolean = false,
)

interface WorkOrderRepository {
    suspend fun searchCached(userId: Long, keyword: String): List<WorkOrder>
    suspend fun searchCached(userId: Long, keyword: String, limit: Int): List<WorkOrder> = searchCached(userId, keyword).take(limit)
    suspend fun searchRemote(accessToken: String, keyword: String): List<WorkOrder>
    suspend fun searchMessagesCached(userId: Long, keyword: String): List<WechatMessage>
    suspend fun searchMessagesCached(userId: Long, keyword: String, limit: Int): List<WechatMessage> = searchMessagesCached(userId, keyword).take(limit)
    suspend fun searchMessagesRemote(accessToken: String, keyword: String, offset: Int = 0): WechatMessagePage
    suspend fun searchHomeRemote(accessToken: String, userId: Long, keyword: String): WorkOrderHomeSearchResult
    suspend fun searchHomeRemote(accessToken: String, userId: Long, keyword: String, limit: Int): WorkOrderHomeSearchResult = searchHomeRemote(accessToken, userId, keyword)
    suspend fun getCachedWechatMessage(userId: Long, messageId: Long): WechatMessage?
    suspend fun refreshWechatMessage(accessToken: String, userId: Long, messageId: Long): WechatMessage
    suspend fun synchronize(
        accessToken: String,
        userId: Long,
        forceVersionCheck: Boolean = false,
        targetWorkOrderRevision: Long? = null,
        targetMessageRevision: Long? = null,
    ): WorkOrderSyncResult
    suspend fun synchronizeAttachmentManifest(accessToken: String, userId: Long): AttachmentManifestSyncResult =
        AttachmentManifestSyncResult(refreshed = false, appliedRevision = 0)
    suspend fun downloadPendingAttachments(accessToken: String, userId: Long, clientInstanceId: String? = null): WorkOrderAttachmentSyncResult =
        WorkOrderAttachmentSyncResult(0, 0, 0)
    suspend fun synchronizeAttachments(accessToken: String, userId: Long, clientInstanceId: String? = null): WorkOrderAttachmentSyncResult =
        downloadPendingAttachments(accessToken, userId, clientInstanceId)
    suspend fun reportAttachmentCacheStatus(accessToken: String, userId: Long, clientInstanceId: String) = Unit
    fun observeAttachmentDownload(userId: Long, attachmentId: Long): Flow<AttachmentDownloadState?> = flowOf(null)
    suspend fun retryAttachmentDownload(userId: Long, attachmentId: Long) = Unit
    suspend fun getCachedWorkOrder(userId: Long, recordId: Long): WorkOrder?
    suspend fun refreshWorkOrder(accessToken: String, userId: Long, recordId: Long): WorkOrder
    suspend fun getHistory(accessToken: String, recordId: Long): List<WorkOrder>
    suspend fun image(accessToken: String, userId: Long, recordId: Long, image: WorkOrderImage, variant: String): CachedWorkOrderImage
    suspend fun attachment(accessToken: String, userId: Long, messageId: Long, attachment: WorkOrderAttachment, variant: String): CachedWorkOrderImage
    suspend fun clear(userId: Long? = null)
    suspend fun clearWorkOrders(userId: Long) = Unit
    suspend fun clearMessages(userId: Long) = Unit
    suspend fun clearAttachmentCache(userId: Long) = Unit
}
