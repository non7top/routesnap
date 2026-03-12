package com.routesnap.app.data.local

import androidx.room.*
import com.routesnap.app.domain.model.RenderStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for trip manifests
 */
@Dao
interface TripManifestDao {

    @Query("SELECT * FROM trip_manifests ORDER BY createdAt DESC")
    fun getAllTrips(): Flow<List<TripManifestEntity>>

    @Query("SELECT * FROM trip_manifests WHERE id = :id")
    suspend fun getTripById(id: String): TripManifestEntity?

    @Query("SELECT * FROM trip_manifests WHERE id = :id")
    fun getTripByIdFlow(id: String): Flow<TripManifestEntity?>

    @Query("SELECT * FROM trip_manifests WHERE status = :status ORDER BY createdAt DESC")
    fun getTripsByStatus(status: RenderStatus): Flow<List<TripManifestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripManifestEntity): Long

    @Update
    suspend fun updateTrip(trip: TripManifestEntity)

    @Delete
    suspend fun deleteTrip(trip: TripManifestEntity)

    @Query("DELETE FROM trip_manifests WHERE id = :id")
    suspend fun deleteTripById(id: String)

    @Query("SELECT COUNT(*) FROM trip_manifests")
    suspend fun getTripCount(): Int
}
