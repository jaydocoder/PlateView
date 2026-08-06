package com.jaydocoder.plateview.data.admin

import com.jaydocoder.plateview.domain.admin.AdminRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object AdminApiModule {
    @Provides
    @Singleton
    fun provideAdminApi(retrofit: Retrofit): AdminApi = retrofit.create(AdminApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdminDataModule {
    @Binds
    @Singleton
    abstract fun bindAdminRepository(repository: NetworkAdminRepository): AdminRepository

    @Binds
    @Singleton
    abstract fun bindAdminImportFileReader(reader: ContentResolverAdminImportFileReader): AdminImportFileReader
}
