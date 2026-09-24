package com.jaydocoder.plateview.feature.consistency

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogConsistencyModule {
    @Binds
    @Singleton
    abstract fun bindCatalogConsistencyStateProvider(
        coordinator: CatalogConsistencyCoordinator,
    ): CatalogConsistencyStateProvider
}
