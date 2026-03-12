package com.routesnap.app.domain.clustering

import android.net.Uri
import com.routesnap.app.data.exif.MediaMetadata
import com.routesnap.app.domain.model.Cluster
import com.routesnap.app.domain.model.LatLng
import com.routesnap.app.domain.model.SegmentType
import com.routesnap.app.domain.model.TripSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clustering configuration parameters
 */
data class ClusteringConfig(
    val maxDistanceKm: Double = 5.0,        // Max distance within a cluster
    val maxTimeGapHours: Long = 4,          // Max time gap within a cluster
    val burstModeThreshold: Int = 50,       // Photos in time window to trigger burst mode
    val burstModeWindowMinutes: Long = 10,  // Time window for burst detection
    val burstModeKeepCount: Int = 3,        // Photos to keep in burst mode
    val photoDurationMs: Long = 4000,       // Default duration per photo
    val videoHighlightDurationMs: Long = 5000 // Default duration for video highlights
)

/**
 * Smart clustering algorithm for grouping photos by location and time
 */
class ClusteringAlgorithm(private val config: ClusteringConfig = ClusteringConfig()) {

    companion object {
        private const val TAG = "ClusteringAlgorithm"
    }

    /**
     * Cluster media metadata into logical groups
     * Returns a list of TripSegments with cluster assignments
     */
    suspend fun cluster(
        metadataList: List<MediaMetadata>
    ): List<TripSegment> = withContext(Dispatchers.Default) {
        if (metadataList.isEmpty()) return@withContext emptyList()

        // Sort by timestamp (files without timestamp go to the end)
        val sorted = metadataList.sortedWith(compareBy({ it.timestamp == null }, { it.timestamp ?: 0 }))

        // Apply burst mode filtering if needed
        val filtered = applyBurstModeFiltering(sorted)

        // Group into clusters
        val clusters = groupIntoClusters(filtered)

        // Convert to TripSegments with proper ordering and map transitions
        buildSegmentList(clusters)
    }

    /**
     * Apply burst mode filtering to reduce redundant photos
     */
    private fun applyBurstModeFiltering(metadataList: List<MediaMetadata>): List<MediaMetadata> {
        if (metadataList.size < config.burstModeThreshold) {
            return metadataList
        }

        val result = mutableListOf<MediaMetadata>()
        var i = 0

        while (i < metadataList.size) {
            val current = metadataList[i]
            val currentTimestamp = current.timestamp ?: 0L

            // Find all photos within burst window
            val burstWindow = mutableListOf<MediaMetadata>()
            var j = i

            while (j < metadataList.size) {
                val item = metadataList[j]
                val itemTimestamp = item.timestamp ?: 0L

                if (itemTimestamp - currentTimestamp <= config.burstModeWindowMinutes * 60 * 1000) {
                    burstWindow.add(item)
                    j++
                } else {
                    break
                }
            }

            if (burstWindow.size >= config.burstModeThreshold) {
                // Burst mode: keep only top N photos (by file size as quality proxy)
                val sorted = burstWindow.sortedByDescending { it.fileSize ?: 0 }
                result.addAll(sorted.take(config.burstModeKeepCount))
            } else {
                // Not a burst: add all items in the window
                result.addAll(burstWindow)
            }

            // Move to next unprocessed item
            i = j
        }

        return result
    }

    /**
     * Group sorted metadata into clusters based on distance and time
     */
    private fun groupIntoClusters(metadataList: List<MediaMetadata>): List<List<MediaMetadata>> {
        val clusters = mutableListOf<MutableList<MediaMetadata>>()
        var currentCluster = mutableListOf<MediaMetadata>()
        var lastLocation: LatLng? = null
        var lastTimestamp: Long? = null

        for (metadata in metadataList) {
            val shouldStartNewCluster = shouldStartNewCluster(
                metadata = metadata,
                lastLocation = lastLocation,
                lastTimestamp = lastTimestamp
            )

            if (shouldStartNewCluster) {
                if (currentCluster.isNotEmpty()) {
                    clusters.add(currentCluster)
                }
                currentCluster = mutableListOf()
            }

            currentCluster.add(metadata)
            lastLocation = metadata.latLng
            lastTimestamp = metadata.timestamp
        }

        if (currentCluster.isNotEmpty()) {
            clusters.add(currentCluster)
        }

        return clusters
    }

    /**
     * Determine if a new cluster should be started
     */
    private fun shouldStartNewCluster(
        metadata: MediaMetadata,
        lastLocation: LatLng?,
        lastTimestamp: Long?
    ): Boolean {
        // First item always starts a cluster
        if (lastLocation == null && lastTimestamp == null) {
            return false
        }

        // Force new cluster if no location data
        if (metadata.latLng == null) {
            return true
        }

        // Check time gap
        val currentTime = metadata.timestamp
        if (lastTimestamp != null && currentTime != null) {
            val timeGapHours = (currentTime - lastTimestamp) / (1000 * 60 * 60)
            if (timeGapHours > config.maxTimeGapHours) {
                return true
            }
        }

        // Check distance
        if (lastLocation != null) {
            val distance = metadata.latLng!!.distanceTo(lastLocation)
            if (distance > config.maxDistanceKm) {
                return true
            }
        }

        return false
    }

    /**
     * Build the final segment list with map transitions between clusters
     */
    private fun buildSegmentList(clusters: List<List<MediaMetadata>>): List<TripSegment> {
        val segments = mutableListOf<TripSegment>()
        var order = 0

        for ((clusterIndex, cluster) in clusters.withIndex()) {
            val clusterId = "cluster_$clusterIndex"
            val clusterName = "Stop ${clusterIndex + 1}"

            // Add map transition before cluster (except for first cluster)
            if (clusterIndex > 0) {
                val prevCluster = clusters[clusterIndex - 1]
                val startCoord = prevCluster.firstOrNull()?.latLng
                val endCoord = cluster.firstOrNull()?.latLng

                if (startCoord != null && endCoord != null) {
                    segments.add(
                        TripSegment(
                            type = SegmentType.MAP_TRAVEL,
                            uri = null,
                            durationMs = 2000, // 2 second transition
                            startCoord = startCoord,
                            endCoord = endCoord,
                            clusterId = clusterId,
                            order = order++
                        )
                    )
                }
            }

            // Add photo/video segments for this cluster
            for (metadata in cluster) {
                val segmentType = when {
                    metadata.uri.toString().lowercase().contains("video") -> SegmentType.VIDEO
                    else -> SegmentType.PHOTO
                }

                segments.add(
                    TripSegment(
                        type = segmentType,
                        uri = metadata.uri,
                        durationMs = if (segmentType == SegmentType.VIDEO)
                            config.videoHighlightDurationMs
                        else
                            config.photoDurationMs,
                        startCoord = metadata.latLng,
                        endCoord = metadata.latLng,
                        clusterId = clusterId,
                        timestamp = metadata.timestamp,
                        order = order++
                    )
                )
            }
        }

        return segments
    }

    /**
     * Create Cluster objects from segments for UI display
     */
    fun createClustersFromSegments(segments: List<TripSegment>): List<Cluster> {
        val clusterMap = segments
            .filter { it.clusterId != null }
            .groupBy { it.clusterId!! }

        return clusterMap.map { (clusterId, clusterSegments) ->
            val photos = clusterSegments.filter { it.type == SegmentType.PHOTO || it.type == SegmentType.VIDEO }
            val centerCoord = photos.firstOrNull()?.startCoord
            val startTime = photos.minOfOrNull { it.timestamp ?: Long.MAX_VALUE }?.takeIf { it != Long.MAX_VALUE }
            val endTime = photos.maxOfOrNull { it.timestamp ?: 0 }

            Cluster(
                id = clusterId,
                name = extractClusterName(clusterId),
                centerCoord = centerCoord,
                segments = photos,
                startTime = startTime,
                endTime = endTime
            )
        }
    }

    private fun extractClusterName(clusterId: String): String {
        val index = clusterId.substringAfter("cluster_").toIntOrNull() ?: 0
        return "Stop ${index + 1}"
    }
}
