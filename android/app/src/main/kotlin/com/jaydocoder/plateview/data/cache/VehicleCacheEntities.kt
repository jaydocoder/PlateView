package com.jaydocoder.plateview.data.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "vehicle_snapshot_cache",
    primaryKeys = ["generation", "vehicleId"],
    indices = [Index(value = ["generation", "normalizedPlate"])],
)
data class VehicleSnapshotCacheEntity(
    val generation: Long,
    val vehicleId: Long,
    val plateNumber: String,
    val normalizedPlate: String,
    val category: String,
    val categoryLabel: String,
    val organizationName: String?,
    val detailJson: String,
)

@Entity(tableName = "vehicle_catalog_state", primaryKeys = ["id"])
data class VehicleCatalogStateEntity(
    val id: Int = 1,
    val activeGeneration: Long,
    val catalogVersion: Long,
    val checkedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
