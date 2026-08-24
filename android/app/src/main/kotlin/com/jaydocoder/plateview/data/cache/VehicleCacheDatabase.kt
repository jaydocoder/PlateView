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
    version = 5,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE vehicle_snapshot_cache ADD COLUMN organizationName TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE vehicle_snapshot_cache ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
                database.execSQL("ALTER TABLE vehicle_snapshot_cache ADD COLUMN searchableText TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE vehicle_snapshot_cache SET searchableText = normalizedPlate")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_vehicle_snapshot_cache_generation_searchableText ON vehicle_snapshot_cache(generation, searchableText)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM vehicle_snapshot_cache")
                database.execSQL("DELETE FROM vehicle_catalog_state")
            }
        }
    }
}
