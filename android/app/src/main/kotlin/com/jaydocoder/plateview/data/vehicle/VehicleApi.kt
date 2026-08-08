package com.jaydocoder.plateview.data.vehicle

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface VehicleApi {
    @GET("vehicles/search")
    suspend fun search(
        @Header("Authorization") authorization: String,
        @Query("keyword") keyword: String,
    ): VehicleSearchResponseDto

    @GET("vehicles/{vehicleId}")
    suspend fun getVehicle(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: Long,
    ): VehicleDetailDto

    @GET("vehicles/catalog/version")
    suspend fun getCatalogVersion(
        @Header("Authorization") authorization: String,
    ): VehicleCatalogVersionDto

    @GET("vehicles/catalog")
    suspend fun getCatalog(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): VehicleCatalogResponseDto

    @GET("vehicles/catalog/full")
    suspend fun getFullCatalog(
        @Header("Authorization") authorization: String,
        @Query("version") version: Long,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): VehicleFullCatalogResponseDto
}
