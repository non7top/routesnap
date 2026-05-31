package com.routesnap.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import com.routesnap.app.data.exif.MediaMetadata
import com.routesnap.app.data.exif.MetadataExtractor
import com.routesnap.app.data.local.TripManifestDao
import com.routesnap.app.data.local.TripManifestEntity
import com.routesnap.app.domain.clustering.ClusteringAlgorithm
import com.routesnap.app.domain.model.AspectRatio
import com.routesnap.app.domain.model.RenderStatus
import com.routesnap.app.domain.model.TemplatePreset
import com.routesnap.app.domain.model.TransitionType
import com.routesnap.app.domain.model.TripManifest
import com.routesnap.app.domain.model.TripSegment
import com.routesnap.app.domain.model.ZoomRect
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for managing trips and media
 */
class TripRepository(
    private val context: Context,
    private val tripManifestDao: TripManifestDao,
    private val contentResolver: ContentResolver,
) {
    private val metadataExtractor = MetadataExtractor(contentResolver)
    private val clusteringAlgorithm = ClusteringAlgorithm()
    private val moshi =
        Moshi
            .Builder()
            .add(KotlinJsonAdapterFactory())
            .add(UriAdapter)
            .build()
    private val tripAdapter = moshi.adapter(TripManifest::class.java)

    /**
     * Get all trips as a Flow
     */
    fun getAllTrips(): Flow<List<TripManifest>> =
        tripManifestDao.getAllTrips().map { entities ->
            entities.mapNotNull { entity ->
                try {
                    tripAdapter.fromJson(entity.jsonData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deserializing trip ${entity.id}", e)
                    null
                }
            }
        }

    /**
     * Get a specific trip by ID
     */
    suspend fun getTripById(id: String): TripManifest? =
        tripManifestDao.getTripById(id)?.let { entity ->
            tripAdapter.fromJson(entity.jsonData)
        }

    /**
     * Save a new trip or update existing
     */
    suspend fun saveTrip(trip: TripManifest) {
        withContext(Dispatchers.IO) {
            val jsonData = tripAdapter.toJson(trip)
            val entity =
                TripManifestEntity(
                    id = trip.id,
                    name = trip.name,
                    createdAt = trip.createdAt,
                    jsonData = jsonData,
                    status = trip.status,
                    outputPath = trip.outputPath,
                )
            tripManifestDao.insertTrip(entity)
        }
    }

    suspend fun updateTripStyle(
        tripId: String,
        template: TemplatePreset,
        aspectRatio: AspectRatio,
        transitionOverride: TransitionType?,
    ) {
        val trip = getTripById(tripId) ?: return
        saveTrip(trip.copy(template = template, aspectRatio = aspectRatio, transitionOverride = transitionOverride))
    }

    suspend fun updateSegmentZoomRects(
        tripId: String,
        segmentId: String,
        startRect: ZoomRect,
        endRect: ZoomRect,
    ) {
        val trip = getTripById(tripId) ?: return
        val updated =
            trip.copy(
                segments =
                    trip.segments.map { seg ->
                        if (seg.id == segmentId) {
                            seg.copy(startZoomRect = startRect, endZoomRect = endRect, isReviewed = true)
                        } else {
                            seg
                        }
                    },
            )
        saveTrip(updated)
    }

    /**
     * Update trip status
     */
    suspend fun updateTripStatus(
        tripId: String,
        status: RenderStatus,
    ) {
        val trip = getTripById(tripId) ?: return
        val updated = trip.copy(status = status)
        saveTrip(updated)
    }

    /**
     * Delete a trip and its private photo copies
     */
    suspend fun deleteTrip(tripId: String) {
        withContext(Dispatchers.IO) {
            projectDir(tripId).deleteRecursively()
        }
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
    suspend fun extractMetadataBatch(uris: List<Uri>): List<MediaMetadata> = metadataExtractor.extractMetadataBatch(uris)

    /**
     * Create a new trip from selected media.
     * Photos are copied into app-private storage so URIs survive app restarts.
     */
    suspend fun createTripFromMedia(
        name: String,
        uris: List<Uri>,
    ): TripManifest {
        val rawSegments = processSelectedMedia(uris)

        // Reserve a trip ID up-front so we know the destination directory.
        val tripId = java.util.UUID.randomUUID().toString()
        val persistedSegments = withContext(Dispatchers.IO) {
            copyPhotosToPrivateStorage(tripId, rawSegments)
        }

        val clusters = clusteringAlgorithm.createClustersFromSegments(persistedSegments)
        val trip =
            TripManifest(
                id = tripId,
                name = name,
                segments = persistedSegments,
                clusters = clusters,
                totalDurationMs = persistedSegments.sumOf { it.durationMs },
            )
        saveTrip(trip)
        return trip
    }

    /**
     * Copies PHOTO segments from their content URIs into app-private storage under
     * filesDir/projects/<tripId>/photos/. Returns the segment list with updated URIs.
     * VIDEO and MAP_TRAVEL segments are left unchanged.
     */
    private fun copyPhotosToPrivateStorage(
        tripId: String,
        segments: List<TripSegment>,
    ): List<TripSegment> {
        val photosDir = File(projectDir(tripId), "photos").also { it.mkdirs() }
        return segments.map { segment ->
            val srcUri = segment.uri
            if (segment.type != com.routesnap.app.domain.model.SegmentType.PHOTO || srcUri == null) {
                return@map segment
            }
            try {
                val ext = srcUri.lastPathSegment?.substringAfterLast('.', "jpg") ?: "jpg"
                val destFile = File(photosDir, "${segment.id}.$ext")
                contentResolver.openInputStream(srcUri)?.use { input ->
                    destFile.outputStream().use { input.copyTo(it) }
                }
                segment.copy(uri = Uri.fromFile(destFile))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy photo ${segment.id}: ${e.message}", e)
                segment // keep original URI if copy fails
            }
        }
    }

    private fun projectDir(tripId: String): File =
        File(context.filesDir, "projects/$tripId")

    companion object {
        private const val TAG = "TripRepository"
    }
}

/**
 * Moshi adapter for Uri serialization
 */
object UriAdapter {
    @ToJson
    fun toJson(uri: Uri?): String = uri?.toString() ?: ""

    @FromJson
    fun fromJson(uriString: String): Uri? = if (uriString.isEmpty()) null else Uri.parse(uriString)
}
