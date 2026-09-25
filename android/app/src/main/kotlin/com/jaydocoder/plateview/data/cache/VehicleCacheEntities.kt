package com.jaydocoder.plateview.data.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "vehicle_snapshot_cache",
    primaryKeys = ["userId", "generation", "vehicleId"],
    indices = [Index(value = ["userId", "generation", "normalizedPlate"])],
)
data class VehicleSnapshotCacheEntity(
    val userId: Long,
    val generation: Long,
    val vehicleId: Long,
    val plateNumber: String,
    val normalizedPlate: String,
    val category: String,
    val categoryLabel: String,
    val organizationName: String?,
    val plateColor: String?,
    val status: String,
    val searchableText: String,
    val detailJson: String,
    val detailAccessible: Boolean,
)

@Entity(tableName = "vehicle_catalog_state", primaryKeys = ["userId"])
data class VehicleCatalogStateEntity(
    val userId: Long,
    val activeGeneration: Long,
    val catalogVersion: Long,
    val checkedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
