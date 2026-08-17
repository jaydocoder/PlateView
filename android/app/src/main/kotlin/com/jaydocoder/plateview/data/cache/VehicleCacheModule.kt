package com.jaydocoder.plateview.data.cache

import android.content.Context
import androidx.room.Room
import com.jaydocoder.plateview.domain.vehicle.VehicleCacheRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Module
@InstallIn(SingletonComponent::class)
object VehicleCacheDatabaseModule {
    @Provides
    @Singleton
    fun provideVehicleCacheDatabase(
        @ApplicationContext context: Context,
        passphrase: VehicleCachePassphrase,
    ): VehicleCacheDatabase {
        SQLiteDatabase.loadLibs(context)
        return Room.databaseBuilder(
            context,
            VehicleCacheDatabase::class.java,
            "vehicle-cache.db",
        ).openHelperFactory(SupportFactory(passphrase.getOrCreate()))
            .addMigrations(VehicleCacheDatabase.MIGRATION_1_2, VehicleCacheDatabase.MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideVehicleCacheDao(database: VehicleCacheDatabase): VehicleCacheDao = database.vehicleCacheDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleCacheBindingModule {
    @Binds
    @Singleton
    abstract fun bindVehicleCacheRepository(repository: RoomVehicleCacheRepository): VehicleCacheRepository
}
