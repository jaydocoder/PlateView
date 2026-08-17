package com.jaydocoder.plateview.data.cache

import com.google.gson.Gson
import com.jaydocoder.plateview.domain.vehicle.CachedVehicleDetail
import com.jaydocoder.plateview.domain.vehicle.CatalogSyncResult
import com.jaydocoder.plateview.domain.vehicle.PlateQueryNormalizer
import com.jaydocoder.plateview.domain.vehicle.VehicleCacheRepository
import com.jaydocoder.plateview.domain.vehicle.VehicleCandidate
import com.jaydocoder.plateview.domain.vehicle.VehicleDetail
import com.jaydocoder.plateview.domain.vehicle.VehicleRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class RoomVehicleCacheRepository @Inject constructor(
    private val dao: VehicleCacheDao,
    private val vehicleRepository: VehicleRepository,
) : VehicleCacheRepository {
    private val synchronizationMutex = Mutex()
    private val gson = Gson()

    override suspend fun search(normalizedKeyword: String): List<VehicleCandidate> = dao
        .searchCandidates(normalizedKeyword, MAXIMUM_CANDIDATES)
        .map(VehicleSnapshotCacheEntity::toCandidate)

    override suspend fun synchronizeCatalog(
        accessToken: String,
        forceVersionCheck: Boolean,
    ): CatalogSyncResult = synchronizationMutex.withLock {
        val now = System.currentTimeMillis()
        val current = dao.getCatalogState()
        if (!forceVersionCheck && current != null && now - current.checkedAtEpochMillis < VERSION_CHECK_INTERVAL_MILLIS) {
            return@withLock CatalogSyncResult(refreshed = false)
        }

        val remoteVersion = vehicleRepository.getCatalogVersion(accessToken)
        if (current?.catalogVersion == remoteVersion) {
            dao.upsertCatalogState(current.copy(checkedAtEpochMillis = now))
            return@withLock CatalogSyncResult(refreshed = false)
        }

        val generation = now
        dao.deleteGeneration(generation)
        var offset = 0
        var total = 0
        do {
            val page = vehicleRepository.getFullCatalog(accessToken, remoteVersion, PAGE_SIZE, offset)
            check(page.catalogVersion == remoteVersion) { "车辆目录版本在同步期间发生变化" }
            total = page.total
            dao.insertSnapshots(page.vehicles.map { it.toEntity(generation, gson) })
            offset += page.vehicles.size
        } while (offset < total && offset > 0)
        check(offset == total) { "完整车辆目录分页结果不完整" }
        dao.promoteGeneration(
            generation = generation,
            catalogVersion = remoteVersion,
            checkedAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        CatalogSyncResult(refreshed = true)
    }

    override suspend fun getDetail(vehicleId: Long): CachedVehicleDetail? = dao.getDetail(vehicleId)?.let { entity ->
        CachedVehicleDetail(
            vehicle = gson.fromJson(entity.detailJson, VehicleDetail::class.java),
            cachedAtEpochMillis = dao.getCatalogState()?.updatedAtEpochMillis ?: 0L,
        )
    }

    override suspend fun clearSnapshot() {
        dao.clearSnapshot()
    }

    private companion object {
        const val MAXIMUM_CANDIDATES = 20
        const val PAGE_SIZE = 200
        const val VERSION_CHECK_INTERVAL_MILLIS = 15 * 60 * 1_000L
    }
}

private fun VehicleSnapshotCacheEntity.toCandidate(): VehicleCandidate = VehicleCandidate(
    id = vehicleId,
    plateNumber = plateNumber,
    category = category,
    categoryLabel = categoryLabel,
    organizationName = organizationName,
)

private fun VehicleDetail.toEntity(generation: Long, gson: Gson): VehicleSnapshotCacheEntity = VehicleSnapshotCacheEntity(
    generation = generation,
    vehicleId = id,
    plateNumber = plateNumber,
    normalizedPlate = PlateQueryNormalizer.normalize(plateNumber),
    category = category,
    categoryLabel = categoryLabel,
    organizationName = longTermProfile?.organizationName,
    detailJson = gson.toJson(this),
)
