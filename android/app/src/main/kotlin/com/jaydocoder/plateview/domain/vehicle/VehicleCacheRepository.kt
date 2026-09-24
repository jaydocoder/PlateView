package com.jaydocoder.plateview.domain.vehicle

data class CachedVehicleDetail(
    val vehicle: VehicleDetail,
    val cachedAtEpochMillis: Long,
)

data class CatalogSyncResult(
    val refreshed: Boolean,
    val appliedRevision: Long,
)

interface VehicleCacheRepository {
    suspend fun search(userId: Long, normalizedKeyword: String): List<VehicleCandidate>

    suspend fun search(userId: Long, normalizedKeyword: String, limit: Int): List<VehicleCandidate> =
        search(userId, normalizedKeyword).take(limit)

    suspend fun synchronizeCatalog(
        accessToken: String,
        userId: Long,
        forceVersionCheck: Boolean = false,
        targetRevision: Long? = null,
    ): CatalogSyncResult

    suspend fun getDetail(userId: Long, vehicleId: Long): CachedVehicleDetail?

    suspend fun clearSnapshot(userId: Long)
}
