package com.jaydocoder.plateview.data.history

import android.content.Context
import androidx.room.Room
import com.jaydocoder.plateview.domain.history.SearchHistoryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SearchHistoryDatabaseModule {
    @Provides
    @Singleton
    fun provideSearchHistoryDatabase(
        @ApplicationContext context: Context,
    ): SearchHistoryDatabase = Room.databaseBuilder(
        context,
        SearchHistoryDatabase::class.java,
        "search-history.db",
    ).build()

    @Provides
    fun provideSearchHistoryDao(database: SearchHistoryDatabase): SearchHistoryDao =
        database.searchHistoryDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchHistoryModule {
    @Binds
    @Singleton
    abstract fun bindSearchHistoryRepository(
        repository: RoomSearchHistoryRepository,
    ): SearchHistoryRepository
}
