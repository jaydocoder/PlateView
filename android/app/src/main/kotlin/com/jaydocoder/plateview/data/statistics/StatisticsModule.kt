package com.jaydocoder.plateview.data.statistics

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object StatisticsModule {
    @Provides
    @Singleton
    fun provideQueryEventDatabase(
        @ApplicationContext context: Context,
    ): QueryEventDatabase = Room.databaseBuilder(
        context,
        QueryEventDatabase::class.java,
        "query-events.db",
    ).addMigrations(QueryEventDatabase.MIGRATION_1_2).build()

    @Provides
    fun provideQueryEventDao(database: QueryEventDatabase): QueryEventDao = database.queryEventDao()

    @Provides
    @Singleton
    fun provideStatisticsApi(retrofit: Retrofit): StatisticsApi = retrofit.create(StatisticsApi::class.java)
}
