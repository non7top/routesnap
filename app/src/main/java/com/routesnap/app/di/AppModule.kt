package com.routesnap.app.di

import android.content.ContentResolver
import android.content.Context
import com.routesnap.app.data.local.RouteSnapDatabase
import com.routesnap.app.data.local.TripManifestDao
import com.routesnap.app.data.repository.TripRepository
import com.routesnap.app.util.StorageHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing application-level dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RouteSnapDatabase {
        return RouteSnapDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideTripManifestDao(database: RouteSnapDatabase): TripManifestDao {
        return database.tripManifestDao()
    }

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
        return context.contentResolver
    }

    @Provides
    @Singleton
    fun provideTripRepository(
        tripManifestDao: TripManifestDao,
        contentResolver: ContentResolver
    ): TripRepository {
        return TripRepository(tripManifestDao, contentResolver)
    }

    @Provides
    @Singleton
    fun provideStorageHelper(@ApplicationContext context: Context): StorageHelper {
        return StorageHelper(context)
    }
}
