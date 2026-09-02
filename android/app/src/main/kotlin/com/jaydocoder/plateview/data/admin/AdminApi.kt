package com.jaydocoder.plateview.data.admin

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.ResponseBody

interface AdminApi {
    @GET("admin/vehicles/creation-capabilities")
    suspend fun getVehicleCreationCapabilities(
        @Header("Authorization") authorization: String,
    ): AdminVehicleCreationCapabilitiesDto

    @GET("admin/vehicles")
    suspend fun listVehicles(
        @Header("Authorization") authorization: String,
        @Query("keyword") keyword: String?,
        @Query("status") status: String?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): AdminVehicleListResponseDto

    @GET("admin/vehicles/{vehicleId}")
    suspend fun getVehicle(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: Long,
    ): AdminVehicleDto

    @POST("admin/vehicles")
    suspend fun createVehicle(
        @Header("Authorization") authorization: String,
        @Body request: AdminVehicleWriteRequestDto,
    ): AdminVehicleDto

    @PUT("admin/vehicles/{vehicleId}")
    suspend fun updateVehicle(
        @Header("Authorization") authorization: String,
        @Header("If-Match-Version") version: Int,
        @Path("vehicleId") vehicleId: Long,
        @Body request: AdminVehicleWriteRequestDto,
    ): AdminVehicleDto

    @POST("admin/vehicles/{vehicleId}/status")
    suspend fun updateVehicleStatus(
        @Header("Authorization") authorization: String,
        @Header("If-Match-Version") version: Int,
        @Path("vehicleId") vehicleId: Long,
        @Body request: AdminVehicleStatusUpdateRequestDto,
    ): AdminVehicleDto

    @GET("admin/users")
    suspend fun listUsers(@Header("Authorization") authorization: String): AdminUserListResponseDto

    @POST("admin/users")
    suspend fun createUser(
        @Header("Authorization") authorization: String,
        @Body request: AdminUserCreateRequestDto,
    ): AdminUserDto

    @PUT("admin/users/{userId}")
    suspend fun updateUser(
        @Header("Authorization") authorization: String,
        @Header("If-Match-Version") version: Int,
        @Path("userId") userId: Long,
        @Body request: AdminUserUpdateRequestDto,
    ): AdminUserDto

    @GET("admin/users/{userId}/avatar")
    suspend fun downloadUserAvatar(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
    ): ResponseBody

    @Multipart
    @POST("admin/users/{userId}/avatar")
    suspend fun uploadUserAvatar(
        @Header("Authorization") authorization: String,
        @Header("If-Match-Version") version: Int,
        @Path("userId") userId: Long,
        @Part avatar: MultipartBody.Part,
    ): AdminUserDto

    @POST("admin/users/{userId}/avatar/delete")
    suspend fun deleteUserAvatar(
        @Header("Authorization") authorization: String,
        @Header("If-Match-Version") version: Int,
        @Path("userId") userId: Long,
    ): AdminUserDto

    @GET("admin/imports")
    suspend fun listImports(@Header("Authorization") authorization: String): AdminImportBatchListResponseDto

    @GET("admin/imports/{batchId}")
    suspend fun getImportBatch(
        @Header("Authorization") authorization: String,
        @Path("batchId") batchId: Long,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("filter") filter: String,
    ): AdminImportBatchDto

    @GET("admin/imports/{batchId}/rows/{rowId}")
    suspend fun getImportRowDetail(
        @Header("Authorization") authorization: String,
        @Path("batchId") batchId: Long,
        @Path("rowId") rowId: Long,
    ): AdminImportRowDetailDto

    @Multipart
    @POST("admin/imports/preview")
    suspend fun previewImport(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
    ): AdminImportBatchDto

    @POST("admin/imports/{batchId}/rows/resolutions")
    suspend fun updateImportResolution(
        @Header("Authorization") authorization: String,
        @Path("batchId") batchId: Long,
        @Body request: AdminImportResolutionRequestDto,
    ): AdminImportBatchDto

    @POST("admin/imports/{batchId}/publish")
    suspend fun publishImport(
        @Header("Authorization") authorization: String,
        @Path("batchId") batchId: Long,
    ): AdminImportBatchDto

    @POST("admin/imports/{batchId}/rollback")
    suspend fun rollbackImport(
        @Header("Authorization") authorization: String,
        @Path("batchId") batchId: Long,
    ): AdminImportBatchDto

    @GET("admin/audit")
    suspend fun listAudit(
        @Header("Authorization") authorization: String,
        @Query("range") range: String,
        @Query("actorId") actorId: Long?,
        @Query("actionType") actionType: String?,
        @Query("result") result: String?,
        @Query("keyword") keyword: String?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): AdminAuditListResponseDto
}
