package com.jaydocoder.plateview.data.vehicle

import com.jaydocoder.plateview.domain.vehicle.VehicleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VehicleApiModule {
    @Provides
    @Singleton
    fun provideVehicleApi(retrofit: Retrofit): VehicleApi = retrofit.create(VehicleApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleDataModule {
    @Binds
    @Singleton
    abstract fun bindVehicleRepository(repository: NetworkVehicleRepository): VehicleRepository
}
