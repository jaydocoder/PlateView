package com.jaydocoder.plateview.data.workorder

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jaydocoder.plateview.data.cache.VehicleCachePassphrase
import com.jaydocoder.plateview.domain.workorder.WorkOrderRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.sqlcipher.database.SupportFactory
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object WorkOrderDataModule {
    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): WorkOrderApi = retrofit.create(WorkOrderApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, passphrase: VehicleCachePassphrase): WorkOrderCacheDatabase =
        Room.databaseBuilder(context, WorkOrderCacheDatabase::class.java, "work-order-cache.db")
            .openHelperFactory(SupportFactory(passphrase.getOrCreate()))
            .addMigrations(WORK_ORDER_CACHE_MIGRATION_1_2)
            .addMigrations(WORK_ORDER_CACHE_MIGRATION_2_3)
            .addMigrations(WORK_ORDER_CACHE_MIGRATION_3_4)
            .addMigrations(WORK_ORDER_CACHE_MIGRATION_4_5)
            .addMigrations(WORK_ORDER_CACHE_MIGRATION_5_6)
            .addMigrations(WORK_ORDER_CACHE_MIGRATION_6_7)
            .build()

    @Provides
    fun provideDao(database: WorkOrderCacheDatabase): WorkOrderCacheDao = database.dao()
}

internal val WORK_ORDER_CACHE_MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE `wechat_attachment_download_tasks_new` (`userId` INTEGER NOT NULL, `attachmentId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `fileName` TEXT, `variant` TEXT NOT NULL, `sha256` TEXT NOT NULL, `sourceQuality` TEXT NOT NULL, `expectedSize` INTEGER, `downloadedBytes` INTEGER NOT NULL, `localPath` TEXT, `status` TEXT NOT NULL, `priority` INTEGER NOT NULL, `foregroundRequested` INTEGER NOT NULL, `attemptCount` INTEGER NOT NULL, `nextRetryAt` INTEGER, `lastErrorCode` TEXT, `manifestRevision` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`userId`, `attachmentId`, `variant`, `sha256`, `sourceQuality`))")
        db.execSQL("INSERT INTO `wechat_attachment_download_tasks_new` (`userId`, `attachmentId`, `kind`, `fileName`, `variant`, `sha256`, `sourceQuality`, `expectedSize`, `downloadedBytes`, `localPath`, `status`, `priority`, `foregroundRequested`, `attemptCount`, `nextRetryAt`, `lastErrorCode`, `manifestRevision`, `createdAt`, `updatedAt`) SELECT `userId`, `attachmentId`, 'IMAGE', NULL, `variant`, `sha256`, 'UNKNOWN', `expectedSize`, `downloadedBytes`, `localPath`, `status`, 100, 0, `attemptCount`, `nextRetryAt`, `lastErrorCode`, `manifestRevision`, `updatedAt`, `updatedAt` FROM `wechat_attachment_download_tasks`")
        db.execSQL("DROP TABLE `wechat_attachment_download_tasks`")
        db.execSQL("ALTER TABLE `wechat_attachment_download_tasks_new` RENAME TO `wechat_attachment_download_tasks`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_wechat_attachment_download_tasks_userId_status_nextRetryAt_foregroundRequested_priority_createdAt` ON `wechat_attachment_download_tasks` (`userId`, `status`, `nextRetryAt`, `foregroundRequested`, `priority`, `createdAt`)")
    }
}

internal val WORK_ORDER_CACHE_MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `wechat_attachment_download_tasks` ADD COLUMN `sentAt` TEXT")
    }
}

internal val WORK_ORDER_CACHE_MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `work_order_cache` ADD COLUMN `orderYear` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `work_order_cache` ADD COLUMN `sourceKey` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `work_order_cache` SET `orderYear` = COALESCE(CAST(strftime('%Y', `sentAt`, '+8 hours') AS INTEGER), 0)")
        db.execSQL("UPDATE `work_order_cache` SET `sourceKey` = `sourceName` WHERE `sourceKey` = ''")
    }
}

private val WORK_ORDER_CACHE_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `wechat_message_cache` (
                `messageId` INTEGER NOT NULL,
                `businessType` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `sourceName` TEXT NOT NULL,
                `sentAt` TEXT NOT NULL,
                `searchableText` TEXT NOT NULL,
                `detailJson` TEXT NOT NULL,
                PRIMARY KEY(`messageId`)
            )
            """.trimIndent(),
        )
    }
}

private val WORK_ORDER_CACHE_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `wechat_attachment_download_tasks` (
                `userId` INTEGER NOT NULL,
                `attachmentId` INTEGER NOT NULL,
                `variant` TEXT NOT NULL,
                `sha256` TEXT NOT NULL,
                `expectedSize` INTEGER,
                `downloadedBytes` INTEGER NOT NULL,
                `localPath` TEXT,
                `status` TEXT NOT NULL,
                `attemptCount` INTEGER NOT NULL,
                `nextRetryAt` INTEGER,
                `lastErrorCode` TEXT,
                `manifestRevision` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`userId`, `attachmentId`, `variant`, `sha256`)
            )
            """.trimIndent(),
        )
    }
}

private val WORK_ORDER_CACHE_MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS `work_order_cache`")
        database.execSQL("DROP TABLE IF EXISTS `wechat_message_cache`")
        database.execSQL("DROP TABLE IF EXISTS `work_order_catalog_state`")
        database.execSQL(
            "CREATE TABLE `work_order_cache` (`userId` INTEGER NOT NULL, `recordId` INTEGER NOT NULL, `orderNumber` TEXT, `rawPlate` TEXT, `status` TEXT NOT NULL, `sourceName` TEXT NOT NULL, `location` TEXT, `rawValidTime` TEXT, `sentAt` TEXT NOT NULL, `searchableText` TEXT NOT NULL, `catalogRevision` INTEGER NOT NULL, `cachedAt` INTEGER NOT NULL, `lastValidatedAt` INTEGER NOT NULL, `detailJson` TEXT NOT NULL, PRIMARY KEY(`userId`, `recordId`))",
        )
        database.execSQL(
            "CREATE TABLE `wechat_message_cache` (`userId` INTEGER NOT NULL, `messageId` INTEGER NOT NULL, `businessType` TEXT NOT NULL, `displayName` TEXT NOT NULL, `sourceName` TEXT NOT NULL, `sentAt` TEXT NOT NULL, `searchableText` TEXT NOT NULL, `catalogRevision` INTEGER NOT NULL, `cachedAt` INTEGER NOT NULL, `lastValidatedAt` INTEGER NOT NULL, `detailJson` TEXT NOT NULL, PRIMARY KEY(`userId`, `messageId`))",
        )
        database.execSQL(
            "CREATE TABLE `work_order_catalog_state` (`userId` INTEGER NOT NULL, `catalogVersion` INTEGER NOT NULL, `messageCatalogVersion` INTEGER NOT NULL, `checkedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`userId`))",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkOrderBindingModule {
    @Binds
    @Singleton
    abstract fun bindRepository(repository: RoomWorkOrderRepository): WorkOrderRepository
}
