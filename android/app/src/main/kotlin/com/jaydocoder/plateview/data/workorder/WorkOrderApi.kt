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

    @GET("work-orders/catalog/version")
    suspend fun catalogVersion(@Header("Authorization") authorization: String): WorkOrderCatalogVersionDto

    @GET("work-orders/catalog/changes")
    suspend fun changes(@Header("Authorization") authorization: String, @Query("afterVersion") afterVersion: Long, @Query("limit") limit: Int): WorkOrderChangesDto

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
data class WorkOrderChangesDto(val catalogVersion: Long, val nextVersion: Long, val hasMore: Boolean, val records: List<WorkOrderDto>)
data class WorkOrderHistoryDto(val records: List<WorkOrderDto>)
data class WorkOrderDto(
    val id: Long, val orderNumber: String?, val rawPlate: String?, val normalizedPlate: String?, val vehicleType: String?,
    val declaredPeople: Int?, val rawValidTime: String?, val location: String?, val verificationMethod: String?, val reason: String?,
    val remarks: String?, val status: String, val parseQuality: String, val catalogRevision: Long, val rawContent: String,
    val sentAt: String, val sourceKey: String, val sourceName: String, val senderUsername: String?, val senderDisplay: String?,
    val senderGroupNickname: String?, val people: List<WorkOrderPersonDto> = emptyList(), val images: List<WorkOrderImageDto> = emptyList(),
    val vehicles: List<WorkOrderVehicleDto> = emptyList(),
)
data class WorkOrderPersonDto(val rawLine: String, val name: String?, val identityNumber: String?)
data class WorkOrderVehicleDto(val rawDescription: String, val rawPlate: String, val normalizedPlate: String, val vehicleType: String?)
data class WorkOrderImageDto(
    val id: Long, val sha256: String?, val contentType: String?, val originalSize: Long?, val previewAvailable: Boolean,
    val thumbnailAvailable: Boolean, val availability: String, val kind: String = "IMAGE", val fileName: String? = null,
    val pageCount: Int? = null,
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
)
