package com.jaydocoder.plateview.data.admin

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface AdminApi {
    @GET("admin/vehicles")
    suspend fun listVehicles(
        @Header("Authorization") authorization: String,
        @Query("keyword") keyword: String?,
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

    @DELETE("admin/vehicles/{vehicleId}")
    suspend fun deactivateVehicle(
        @Header("Authorization") authorization: String,
        @Header("If-Match-Version") version: Int,
        @Path("vehicleId") vehicleId: Long,
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

    @GET("admin/imports")
    suspend fun listImports(@Header("Authorization") authorization: String): AdminImportBatchListResponseDto

    @GET("admin/imports/{batchId}")
    suspend fun getImportBatch(
        @Header("Authorization") authorization: String,
        @Path("batchId") batchId: Long,
    ): AdminImportBatchDto

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
    suspend fun listAudit(@Header("Authorization") authorization: String): AdminAuditListResponseDto
}
