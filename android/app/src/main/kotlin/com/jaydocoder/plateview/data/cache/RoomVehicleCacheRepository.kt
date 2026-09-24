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
    private val synchronizationMutexes = mutableMapOf<Long, Mutex>()
    private val synchronizationMutexGuard = Mutex()
    private val gson = Gson()

    override suspend fun search(userId: Long, normalizedKeyword: String): List<VehicleCandidate> = search(userId, normalizedKeyword, 8)

    override suspend fun search(userId: Long, normalizedKeyword: String, limit: Int): List<VehicleCandidate> = dao
        .searchCandidates(userId, normalizedKeyword, limit.coerceIn(1, 50))
        .map(VehicleSnapshotCacheEntity::toCandidate)

    override suspend fun synchronizeCatalog(
        accessToken: String,
        userId: Long,
        forceVersionCheck: Boolean,
        targetRevision: Long?,
    ): CatalogSyncResult = synchronizationMutex(userId).withLock {
        val now = System.currentTimeMillis()
        val current = dao.getCatalogState(userId)
        if (!forceVersionCheck && current != null && now - current.checkedAtEpochMillis < VERSION_CHECK_INTERVAL_MILLIS) {
            return@withLock CatalogSyncResult(refreshed = false, appliedRevision = current.catalogVersion)
        }

        val remoteVersion = targetRevision ?: vehicleRepository.getCatalogVersion(accessToken)
        if (current?.catalogVersion == remoteVersion) {
            dao.upsertCatalogState(current.copy(checkedAtEpochMillis = now))
            return@withLock CatalogSyncResult(refreshed = false, appliedRevision = remoteVersion)
        }

        if (current != null && current.catalogVersion > 0 && current.catalogVersion % 4L == remoteVersion % 4L) {
            var afterRevision = current.catalogVersion
            var afterId = 0L
            val upserts = mutableListOf<VehicleSnapshotCacheEntity>()
            val removals = mutableListOf<Long>()
            var fullSyncRequired = false
            do {
                val page = vehicleRepository.getCatalogChanges(
                    accessToken = accessToken,
                    afterRevision = afterRevision,
                    afterId = afterId,
                    targetRevision = remoteVersion,
                    limit = PAGE_SIZE,
                )
                fullSyncRequired = page.fullSyncRequired
                if (fullSyncRequired) break
                page.changes.forEach { change ->
                    if (change.operation == "UPSERT" && change.vehicle != null) {
                        upserts += change.vehicle.toEntity(userId, current.activeGeneration, gson)
                    } else {
                        removals += change.entityId
                    }
                }
                afterRevision = page.nextRevision
                afterId = page.nextId
            } while (page.hasMore)
            if (!fullSyncRequired) {
                dao.applyChanges(
                    userId = userId,
                    generation = current.activeGeneration,
                    upserts = upserts,
                    removals = removals.distinct(),
                    catalogVersion = remoteVersion,
                    checkedAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
                return@withLock CatalogSyncResult(
                    refreshed = upserts.isNotEmpty() || removals.isNotEmpty(),
                    appliedRevision = remoteVersion,
                )
            }
        }

        val generation = now
        dao.deleteGeneration(userId, generation)
        var offset = 0
        var total = 0
        do {
            val page = vehicleRepository.getFullCatalog(accessToken, remoteVersion, PAGE_SIZE, offset)
            check(page.catalogVersion == remoteVersion) { "车辆目录版本在同步期间发生变化" }
            total = page.total
            dao.insertSnapshots(page.vehicles.map { it.toEntity(userId, generation, gson) })
            offset += page.vehicles.size
        } while (offset < total && offset > 0)
        check(offset == total) { "完整车辆目录分页结果不完整" }
        dao.promoteGeneration(
            userId = userId,
            generation = generation,
            catalogVersion = remoteVersion,
            checkedAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        CatalogSyncResult(refreshed = true, appliedRevision = remoteVersion)
    }

    override suspend fun getDetail(userId: Long, vehicleId: Long): CachedVehicleDetail? = dao.getDetail(userId, vehicleId)?.let { entity ->
        CachedVehicleDetail(
            vehicle = gson.fromJson(entity.detailJson, VehicleDetail::class.java),
            cachedAtEpochMillis = dao.getCatalogState(userId)?.updatedAtEpochMillis ?: 0L,
        )
    }

    override suspend fun clearSnapshot(userId: Long) {
        dao.clearSnapshot(userId)
    }

    private suspend fun synchronizationMutex(userId: Long): Mutex = synchronizationMutexGuard.withLock {
        synchronizationMutexes.getOrPut(userId) { Mutex() }
    }

    private companion object {
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
    plateColor = plateColor,
    status = status,
)

private fun VehicleDetail.toEntity(userId: Long, generation: Long, gson: Gson): VehicleSnapshotCacheEntity = VehicleSnapshotCacheEntity(
    userId = userId,
    generation = generation,
    vehicleId = id,
    plateNumber = plateNumber,
    normalizedPlate = PlateQueryNormalizer.normalize(plateNumber),
    category = category,
    categoryLabel = categoryLabel,
    organizationName = longTermProfile?.organizationName,
    plateColor = attributes.firstOrNull { it.label == "号牌颜色" || it.label == "车牌颜色" }?.value,
    status = status,
    searchableText = listOfNotNull(
        plateNumber,
        longTermProfile?.organizationName,
        longTermProfile?.passHolder,
        longTermProfile?.remarks,
        residentProfile?.ownerName,
        residentProfile?.remarks,
    ).joinToString(separator = " ", transform = PlateQueryNormalizer::normalize),
    detailJson = gson.toJson(this),
)
