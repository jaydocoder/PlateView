package com.jaydocoder.plateview.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface VehicleCacheDao {
    @Query(
        """
        SELECT * FROM vehicle_snapshot_cache
        WHERE userId = :userId
            AND generation = (SELECT activeGeneration FROM vehicle_catalog_state WHERE userId = :userId)
            AND searchableText LIKE '%' || :normalizedKeyword || '%'
            AND status <> 'DELETED'
        ORDER BY
            CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END,
            CASE WHEN category = 'RESIDENT' THEN 0 ELSE 1 END,
            CASE WHEN normalizedPlate = :normalizedKeyword THEN 0
                 WHEN normalizedPlate LIKE :normalizedKeyword || '%' THEN 1
                 ELSE 2 END,
            LENGTH(normalizedPlate), normalizedPlate, vehicleId
        LIMIT :limit
        """,
    )
    suspend fun searchCandidates(userId: Long, normalizedKeyword: String, limit: Int): List<VehicleSnapshotCacheEntity>

    @Query(
        """
        SELECT * FROM vehicle_snapshot_cache
        WHERE userId = :userId
            AND generation = (SELECT activeGeneration FROM vehicle_catalog_state WHERE userId = :userId)
            AND vehicleId = :vehicleId
            AND status <> 'DELETED'
        """,
    )
    suspend fun getDetail(userId: Long, vehicleId: Long): VehicleSnapshotCacheEntity?

    @Query("SELECT * FROM vehicle_catalog_state WHERE userId = :userId")
    suspend fun getCatalogState(userId: Long): VehicleCatalogStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(items: List<VehicleSnapshotCacheEntity>)

    @Query("DELETE FROM vehicle_snapshot_cache WHERE userId = :userId AND generation = :generation AND vehicleId IN (:vehicleIds)")
    suspend fun deleteVehicles(userId: Long, generation: Long, vehicleIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogState(state: VehicleCatalogStateEntity)

    @Query("DELETE FROM vehicle_snapshot_cache WHERE userId = :userId AND generation = :generation")
    suspend fun deleteGeneration(userId: Long, generation: Long)

    @Query("DELETE FROM vehicle_snapshot_cache WHERE userId = :userId")
    suspend fun deleteAllSnapshots(userId: Long)

    @Query("DELETE FROM vehicle_catalog_state WHERE userId = :userId")
    suspend fun deleteCatalogState(userId: Long)

    @Transaction
    suspend fun promoteGeneration(
        userId: Long,
        generation: Long,
        catalogVersion: Long,
        checkedAtEpochMillis: Long,
        updatedAtEpochMillis: Long,
    ) {
        upsertCatalogState(
            VehicleCatalogStateEntity(
                userId = userId,
                activeGeneration = generation,
                catalogVersion = catalogVersion,
                checkedAtEpochMillis = checkedAtEpochMillis,
                updatedAtEpochMillis = updatedAtEpochMillis,
            ),
        )
        deleteOtherGenerations(userId, generation)
    }

    @Transaction
    suspend fun applyChanges(
        userId: Long,
        generation: Long,
        upserts: List<VehicleSnapshotCacheEntity>,
        removals: List<Long>,
        catalogVersion: Long,
        checkedAtEpochMillis: Long,
        updatedAtEpochMillis: Long,
    ) {
        if (upserts.isNotEmpty()) insertSnapshots(upserts)
        if (removals.isNotEmpty()) deleteVehicles(userId, generation, removals)
        upsertCatalogState(
            VehicleCatalogStateEntity(
                userId = userId,
                activeGeneration = generation,
                catalogVersion = catalogVersion,
                checkedAtEpochMillis = checkedAtEpochMillis,
                updatedAtEpochMillis = updatedAtEpochMillis,
            ),
        )
    }

    @Query("DELETE FROM vehicle_snapshot_cache WHERE userId = :userId AND generation != :activeGeneration")
    suspend fun deleteOtherGenerations(userId: Long, activeGeneration: Long)

    @Transaction
    suspend fun clearSnapshot(userId: Long) {
        deleteAllSnapshots(userId)
        deleteCatalogState(userId)
    }
}
