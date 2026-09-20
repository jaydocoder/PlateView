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
            .build()

    @Provides
    fun provideDao(database: WorkOrderCacheDatabase): WorkOrderCacheDao = database.dao()
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

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkOrderBindingModule {
    @Binds
    @Singleton
    abstract fun bindRepository(repository: RoomWorkOrderRepository): WorkOrderRepository
}
