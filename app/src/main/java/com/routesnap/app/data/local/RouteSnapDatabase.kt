package com.routesnap.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for RouteSnap
 */
@Database(
    entities = [TripManifestEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RouteSnapDatabase : RoomDatabase() {

    abstract fun tripManifestDao(): TripManifestDao

    companion object {
        @Volatile
        private var INSTANCE: RouteSnapDatabase? = null

        fun getDatabase(context: Context): RouteSnapDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RouteSnapDatabase::class.java,
                    "routesnap_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
