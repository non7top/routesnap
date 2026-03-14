package com.routesnap.app.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.routesnap.app.data.exif.MediaMetadata
import com.routesnap.app.data.exif.MetadataExtractor
import com.routesnap.app.data.local.TripManifestDao
import com.routesnap.app.data.local.TripManifestEntity
import com.routesnap.app.domain.clustering.ClusteringAlgorithm
import com.routesnap.app.domain.model.RenderStatus
import com.routesnap.app.domain.model.TripManifest
import com.routesnap.app.domain.model.TripSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.FromJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Repository for managing trips and media
 */
class TripRepository(
    private val tripManifestDao: TripManifestDao,
    private val contentResolver: ContentResolver
) {
    private val metadataExtractor = MetadataExtractor(contentResolver)
    private val clusteringAlgorithm = ClusteringAlgorithm()
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .add(UriAdapter)
        .build()
    private val tripAdapter = moshi.adapter(TripManifest::class.java)

    companion object {
        private const val TAG = "TripRepository"
    }

    /**
     * Get all trips as a Flow
     */
    fun getAllTrips(): Flow<List<TripManifest>> {
        return tripManifestDao.getAllTrips().map { entities ->
            entities.mapNotNull { entity ->
                try {
                    tripAdapter.fromJson(entity.jsonData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deserializing trip ${entity.id}", e)
                    null
                }
            }
        }
    }

    /**
     * Get a specific trip by ID
     */
    suspend fun getTripById(id: String): TripManifest? {
        return tripManifestDao.getTripById(id)?.let { entity ->
            tripAdapter.fromJson(entity.jsonData)
        }
    }

    /**
     * Save a new trip or update existing
     */
    suspend fun saveTrip(trip: TripManifest) {
        withContext(Dispatchers.IO) {
            val jsonData = tripAdapter.toJson(trip)
            val entity = TripManifestEntity(
                id = trip.id,
                name = trip.name,
                createdAt = trip.createdAt,
                jsonData = jsonData,
                status = trip.status,
                outputPath = trip.outputPath
            )
            tripManifestDao.insertTrip(entity)
        }
    }

    /**
     * Update trip status
     */
    suspend fun updateTripStatus(tripId: String, status: RenderStatus) {
        val trip = getTripById(tripId) ?: return
        val updated = trip.copy(status = status)
        saveTrip(updated)
    }

    /**
     * Delete a trip
     */
    suspend fun deleteTrip(tripId: String) {
        tripManifestDao.deleteTripById(tripId)
    }

    /**
     * Extract metadata from selected URIs and create clustered segments
     */
    suspend fun processSelectedMedia(uris: List<Uri>): List<TripSegment> {
        Log.d(TAG, "Processing ${uris.size} media files")

        // Extract metadata from all URIs
        val metadataList = metadataExtractor.extractMetadataBatch(uris)
        Log.d(TAG, "Extracted metadata: ${metadataList.filter { it.hasLocation }.size} with location")

        // Cluster and create segments
        val segments = clusteringAlgorithm.cluster(metadataList)
        Log.d(TAG, "Created ${segments.size} segments in ${segments.mapNotNull { it.clusterId }.distinct().size} clusters")

        return segments
    }
    
    /**
     * Extract metadata from URIs (for UI display)
     */
    suspend fun extractMetadataBatch(uris: List<Uri>): List<MediaMetadata> {
        return metadataExtractor.extractMetadataBatch(uris)
    }

    /**
     * Create a new trip from selected media
     */
    suspend fun createTripFromMedia(
        name: String,
        uris: List<Uri>
    ): TripManifest {
        val segments = processSelectedMedia(uris)
        val clusters = clusteringAlgorithm.createClustersFromSegments(segments)
        val totalDurationMs = segments.sumOf { it.durationMs }

        val trip = TripManifest(
            name = name,
            segments = segments,
            clusters = clusters,
            totalDurationMs = totalDurationMs
        )

        saveTrip(trip)
        return trip
    }
}

/**
 * Moshi adapter for Uri serialization
 */
object UriAdapter {
    @ToJson
    fun toJson(uri: Uri?): String {
        return uri?.toString() ?: ""
    }

    @FromJson
    fun fromJson(uriString: String): Uri? {
        return if (uriString.isEmpty()) null else Uri.parse(uriString)
    }
}
