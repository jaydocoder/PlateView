package com.jaydocoder.plateview.data.cache

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VehicleSnapshotCacheEntity::class,
        VehicleCatalogStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class VehicleCacheDatabase : RoomDatabase() {
    abstract fun vehicleCacheDao(): VehicleCacheDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS vehicle_candidate_cache")
                database.execSQL("DROP TABLE IF EXISTS vehicle_detail_cache")
                database.execSQL("DROP TABLE IF EXISTS vehicle_catalog_state")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS vehicle_snapshot_cache (generation INTEGER NOT NULL, vehicleId INTEGER NOT NULL, plateNumber TEXT NOT NULL, normalizedPlate TEXT NOT NULL, category TEXT NOT NULL, categoryLabel TEXT NOT NULL, detailJson TEXT NOT NULL, PRIMARY KEY(generation, vehicleId))",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_vehicle_snapshot_cache_generation_normalizedPlate ON vehicle_snapshot_cache(generation, normalizedPlate)")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS vehicle_catalog_state (id INTEGER NOT NULL, activeGeneration INTEGER NOT NULL, catalogVersion INTEGER NOT NULL, checkedAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(id))",
                )
            }
        }
    }
}
