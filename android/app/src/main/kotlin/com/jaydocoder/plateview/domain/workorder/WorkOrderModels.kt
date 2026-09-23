package com.jaydocoder.plateview.domain.workorder

import java.io.File

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

data class WorkOrderSyncResult(val refreshed: Boolean)
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

data class WorkOrderAttachmentSyncResult(val downloaded: Int, val skipped: Int, val failed: Int)

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
    suspend fun synchronize(accessToken: String, userId: Long, forceVersionCheck: Boolean = false): WorkOrderSyncResult
    suspend fun synchronizeAttachments(accessToken: String, userId: Long): WorkOrderAttachmentSyncResult =
        WorkOrderAttachmentSyncResult(0, 0, 0)
    suspend fun reportAttachmentCacheStatus(accessToken: String, userId: Long, clientInstanceId: String) = Unit
    suspend fun getCachedWorkOrder(userId: Long, recordId: Long): WorkOrder?
    suspend fun refreshWorkOrder(accessToken: String, userId: Long, recordId: Long): WorkOrder
    suspend fun getHistory(accessToken: String, recordId: Long): List<WorkOrder>
    suspend fun image(accessToken: String, userId: Long, recordId: Long, image: WorkOrderImage, variant: String): CachedWorkOrderImage
    suspend fun attachment(accessToken: String, userId: Long, messageId: Long, attachment: WorkOrderAttachment, variant: String): CachedWorkOrderImage
    suspend fun clear(userId: Long? = null)
    suspend fun clearWorkOrders(userId: Long) = Unit
    suspend fun clearMessages(userId: Long) = Unit
}
