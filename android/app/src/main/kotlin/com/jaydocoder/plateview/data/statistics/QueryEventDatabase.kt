package com.jaydocoder.plateview.data.statistics

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LocalQueryEventEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class QueryEventDatabase : RoomDatabase() {
    abstract fun queryEventDao(): QueryEventDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE local_query_events ADD COLUMN plateNumber TEXT")
            }
        }
    }
}
