package com.jaydocoder.plateview.data.workorder

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.Response

interface WorkOrderApi {
    @GET("work-orders/home-search")
    suspend fun searchHome(
        @Header("Authorization") authorization: String,
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int,
    ): WorkOrderHomeSearchResponseDto

    @GET("work-orders/search")
    suspend fun search(@Header("Authorization") authorization: String, @Query("keyword") keyword: String): WorkOrderSearchResponseDto

    @GET("work-orders/messages/search")
    suspend fun searchMessages(
        @Header("Authorization") authorization: String,
        @Query("keyword") keyword: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
    ): WechatMessagePageDto

    @GET("work-orders/messages/{messageId}")
    suspend fun messageDetail(@Header("Authorization") authorization: String, @Path("messageId") messageId: Long): WechatMessageDto

    @GET("work-orders/messages/catalog/changes")
    suspend fun messageChanges(
        @Header("Authorization") authorization: String,
        @Query("afterVersion") afterVersion: Long,
        @Query("afterId") afterId: Long,
        @Query("targetRevision") targetRevision: Long,
        @Query("limit") limit: Int,
    ): WechatMessageChangesDto

    @GET("work-orders/messages/catalog/full")
    suspend fun fullMessageCatalog(
        @Header("Authorization") authorization: String,
        @Query("afterId") afterId: Long,
        @Query("targetRevision") targetRevision: Long,
        @Query("limit") limit: Int,
    ): WechatMessageFullCatalogDto

    @GET("work-orders/catalog/version")
    suspend fun catalogVersion(@Header("Authorization") authorization: String): WorkOrderCatalogVersionDto

    @GET("work-orders/catalog/changes")
    suspend fun changes(@Header("Authorization") authorization: String, @Query("afterVersion") afterVersion: Long, @Query("afterId") afterId: Long, @Query("targetRevision") targetRevision: Long, @Query("limit") limit: Int): WorkOrderChangesDto

    @GET("work-orders/catalog/full")
    suspend fun fullWorkOrderCatalog(
        @Header("Authorization") authorization: String,
        @Query("afterId") afterId: Long,
        @Query("targetRevision") targetRevision: Long,
        @Query("limit") limit: Int,
    ): WorkOrderFullCatalogDto

    @GET("work-orders/attachments")
    suspend fun attachmentCatalog(
        @Header("Authorization") authorization: String,
        @Query("afterId") afterId: Long,
        @Query("limit") limit: Int,
    ): WorkOrderAttachmentCatalogPageDto

    @GET("work-orders/attachments/manifest")
    suspend fun attachmentManifest(
        @Header("Authorization") authorization: String,
        @Query("afterId") afterId: Long,
        @Query("limit") limit: Int,
        @Query("catalogRevision") catalogRevision: Long? = null,
        @Query("includeOriginal") includeOriginal: Boolean = true,
    ): WorkOrderAttachmentManifestPageDto

    @retrofit2.http.POST("work-orders/attachments/cache-status")
    suspend fun saveAttachmentCacheStatus(
        @Header("Authorization") authorization: String,
        @retrofit2.http.Body request: AttachmentCacheStatusRequestDto,
    ): retrofit2.Response<Unit>

    @GET("work-orders/attachments/{attachmentId}")
    suspend fun attachmentFile(
        @Header("Authorization") authorization: String,
        @Header("Range") range: String?,
        @Path("attachmentId") attachmentId: Long,
        @Query("variant") variant: String,
    ): Response<ResponseBody>

    @GET("work-orders/{recordId}")
    suspend fun detail(@Header("Authorization") authorization: String, @Path("recordId") recordId: Long): WorkOrderDto

    @GET("work-orders/{recordId}/history")
    suspend fun history(@Header("Authorization") authorization: String, @Path("recordId") recordId: Long): WorkOrderHistoryDto

    @GET("work-orders/{recordId}/images/{imageId}")
    suspend fun image(@Header("Authorization") authorization: String, @Header("Range") range: String?, @Path("recordId") recordId: Long, @Path("imageId") imageId: Long, @Query("variant") variant: String): Response<ResponseBody>

    @GET("work-orders/messages/{messageId}/attachments/{attachmentId}")
    suspend fun attachment(
        @Header("Authorization") authorization: String,
        @Header("Range") range: String?,
        @Path("messageId") messageId: Long,
        @Path("attachmentId") attachmentId: Long,
        @Query("variant") variant: String,
    ): Response<ResponseBody>
}

data class WorkOrderSearchResponseDto(val catalogVersion: Long, val candidates: List<WorkOrderDto>)
data class WorkOrderHomeSearchResponseDto(
    val workOrderCandidates: List<WorkOrderDto>,
    val wechatMessages: List<WechatMessageDto>,
    val workOrderHasMore: Boolean,
    val wechatMessageHasMore: Boolean,
    val catalogVersion: Long,
    val workOrderFailed: Boolean = false,
    val wechatMessageFailed: Boolean = false,
)
data class WorkOrderCatalogVersionDto(val catalogVersion: Long)
data class CatalogTombstoneDto(val entityId: Long, val operation: String)
data class WorkOrderChangesDto(val catalogVersion: Long, val nextVersion: Long, val nextId: Long, val hasMore: Boolean, val records: List<WorkOrderDto>, val tombstones: List<CatalogTombstoneDto> = emptyList(), val fullSyncRequired: Boolean = false)
data class WechatMessageChangesDto(val catalogVersion: Long, val nextVersion: Long, val nextId: Long, val hasMore: Boolean, val records: List<WechatMessageDto>, val tombstones: List<CatalogTombstoneDto> = emptyList(), val fullSyncRequired: Boolean = false)
data class WorkOrderFullCatalogDto(val catalogVersion: Long, val records: List<WorkOrderDto>, val nextAfterId: Long?, val hasMore: Boolean)
data class WechatMessageFullCatalogDto(val catalogVersion: Long, val records: List<WechatMessageDto>, val nextAfterId: Long?, val hasMore: Boolean)
data class WorkOrderHistoryDto(val records: List<WorkOrderDto>)
data class WorkOrderDto(
    val id: Long, val orderNumber: String?, val rawPlate: String?, val normalizedPlate: String?, val vehicleType: String?,
    val declaredPeople: Int?, val rawValidTime: String?, val location: String?, val verificationMethod: String?, val reason: String?,
    val remarks: String?, val status: String, val parseQuality: String, val catalogRevision: Long, val rawContent: String,
    val sentAt: String, val sourceKey: String, val sourceName: String, val senderUsername: String?, val senderDisplay: String?,
    val senderGroupNickname: String?, val people: List<WorkOrderPersonDto> = emptyList(), val images: List<WorkOrderImageDto> = emptyList(),
    val vehicles: List<WorkOrderVehicleDto> = emptyList(), val displayName: String? = null,
)
data class WorkOrderPersonDto(val rawLine: String, val name: String?, val identityNumber: String?)
data class WorkOrderVehicleDto(val rawDescription: String, val rawPlate: String, val normalizedPlate: String, val vehicleType: String?)
data class WorkOrderImageDto(
    val id: Long, val sha256: String?, val contentType: String?, val originalSize: Long?, val previewAvailable: Boolean,
    val thumbnailAvailable: Boolean, val availability: String, val kind: String = "IMAGE", val fileName: String? = null,
    val pageCount: Int? = null, val sourceQuality: String = "UNKNOWN",
)
data class WechatMessagePageDto(val records: List<WechatMessageDto>, val nextOffset: Int?)
data class WechatMessageDto(
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
    val plateNumbers: List<String> = emptyList(),
    val attachments: List<WorkOrderAttachmentDto> = emptyList(),
    val catalogRevision: Long = 0,
)
data class WorkOrderAttachmentDto(
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
data class WorkOrderAttachmentCatalogPageDto(
    val items: List<WorkOrderAttachmentCatalogItemDto>,
    val nextAfterId: Long?,
)
data class WorkOrderAttachmentManifestPageDto(
    val manifestRevision: Long,
    val items: List<WorkOrderAttachmentManifestItemDto>,
    val nextAfterId: Long?,
)
data class WorkOrderAttachmentManifestItemDto(
    val attachmentId: Long,
    val kind: String,
    val fileName: String? = null,
    val originalSize: Long? = null,
    val sha256: String? = null,
    val sourceQuality: String = "UNKNOWN",
    val originalAvailable: Boolean = false,
    val previewAvailable: Boolean = false,
    val thumbnailAvailable: Boolean = false,
    val downloadUrl: String = "",
)
data class AttachmentCacheStatusRequestDto(
    val clientInstanceId: String,
    val manifestRevision: Long,
    val items: List<AttachmentCacheStatusItemDto>,
)
data class AttachmentCacheStatusItemDto(
    val attachmentId: Long,
    val variant: String,
    val status: String,
    val downloadedBytes: Long = 0,
    val expectedSize: Long? = null,
    val sha256: String? = null,
    val attemptCount: Int = 0,
    val lastErrorCode: String? = null,
)
data class WorkOrderAttachmentCatalogItemDto(
    val id: Long,
    val kind: String,
    val fileName: String?,
    val sha256: String?,
    val contentType: String?,
    val originalSize: Long?,
    val originalAvailable: Boolean,
    val previewAvailable: Boolean,
    val thumbnailAvailable: Boolean,
    val availability: String,
    val sourceQuality: String,
    val pageCount: Int?,
)
