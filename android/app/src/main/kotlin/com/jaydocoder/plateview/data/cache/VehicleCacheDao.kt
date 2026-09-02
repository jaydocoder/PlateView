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
        WHERE generation = (SELECT activeGeneration FROM vehicle_catalog_state WHERE id = 1)
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
    suspend fun searchCandidates(normalizedKeyword: String, limit: Int): List<VehicleSnapshotCacheEntity>

    @Query(
        """
        SELECT * FROM vehicle_snapshot_cache
        WHERE generation = (SELECT activeGeneration FROM vehicle_catalog_state WHERE id = 1)
            AND vehicleId = :vehicleId
            AND status <> 'DELETED'
        """,
    )
    suspend fun getDetail(vehicleId: Long): VehicleSnapshotCacheEntity?

    @Query("SELECT * FROM vehicle_catalog_state WHERE id = 1")
    suspend fun getCatalogState(): VehicleCatalogStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(items: List<VehicleSnapshotCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogState(state: VehicleCatalogStateEntity)

    @Query("DELETE FROM vehicle_snapshot_cache WHERE generation = :generation")
    suspend fun deleteGeneration(generation: Long)

    @Query("DELETE FROM vehicle_snapshot_cache")
    suspend fun deleteAllSnapshots()

    @Query("DELETE FROM vehicle_catalog_state")
    suspend fun deleteCatalogState()

    @Transaction
    suspend fun promoteGeneration(
        generation: Long,
        catalogVersion: Long,
        checkedAtEpochMillis: Long,
        updatedAtEpochMillis: Long,
    ) {
        upsertCatalogState(
            VehicleCatalogStateEntity(
                activeGeneration = generation,
                catalogVersion = catalogVersion,
                checkedAtEpochMillis = checkedAtEpochMillis,
                updatedAtEpochMillis = updatedAtEpochMillis,
            ),
        )
        deleteOtherGenerations(generation)
    }

    @Query("DELETE FROM vehicle_snapshot_cache WHERE generation != :activeGeneration")
    suspend fun deleteOtherGenerations(activeGeneration: Long)

    @Transaction
    suspend fun clearSnapshot() {
        deleteAllSnapshots()
        deleteCatalogState()
    }
}
