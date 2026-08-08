package com.jaydocoder.plateview.domain.vehicle

interface VehicleRepository {
    suspend fun search(accessToken: String, keyword: String): List<VehicleCandidate>

    suspend fun getVehicle(accessToken: String, vehicleId: Long): VehicleDetail

    suspend fun getCatalogVersion(accessToken: String): Long

    suspend fun getCatalog(accessToken: String, limit: Int, offset: Int): VehicleCatalogPage

    suspend fun getFullCatalog(
        accessToken: String,
        version: Long,
        limit: Int,
        offset: Int,
    ): VehicleFullCatalogPage
}

data class VehicleCatalogPage(
    val catalogVersion: Long,
    val total: Int,
    val candidates: List<VehicleCandidate>,
)

data class VehicleFullCatalogPage(
    val catalogVersion: Long,
    val total: Int,
    val vehicles: List<VehicleDetail>,
)
