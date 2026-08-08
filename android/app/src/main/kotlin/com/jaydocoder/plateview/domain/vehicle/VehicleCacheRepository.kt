package com.jaydocoder.plateview.domain.vehicle

data class CachedVehicleDetail(
    val vehicle: VehicleDetail,
    val cachedAtEpochMillis: Long,
)

data class CatalogSyncResult(
    val refreshed: Boolean,
)

interface VehicleCacheRepository {
    suspend fun search(normalizedKeyword: String): List<VehicleCandidate>

    suspend fun synchronizeCatalog(
        accessToken: String,
        forceVersionCheck: Boolean = false,
    ): CatalogSyncResult

    suspend fun getDetail(vehicleId: Long): CachedVehicleDetail?

    suspend fun clearSnapshot()
}
