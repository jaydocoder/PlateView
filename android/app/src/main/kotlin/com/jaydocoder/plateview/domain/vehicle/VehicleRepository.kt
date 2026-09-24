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

    suspend fun getCatalogChanges(
        accessToken: String,
        afterRevision: Long,
        afterId: Long,
        targetRevision: Long,
        limit: Int,
    ): VehicleCatalogChangePage
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

data class VehicleCatalogChange(
    val revision: Long,
    val entityId: Long,
    val operation: String,
    val vehicle: VehicleDetail?,
)

data class VehicleCatalogChangePage(
    val catalogVersion: Long,
    val nextRevision: Long,
    val nextId: Long,
    val hasMore: Boolean,
    val fullSyncRequired: Boolean,
    val changes: List<VehicleCatalogChange>,
)
