package com.routesnap.app.domain.model

import android.net.Uri

/**
 * Types of segments in a trip video
 */
enum class SegmentType {
    PHOTO,
    VIDEO,
    MAP_TRAVEL,
}

/**
 * Represents a geographic coordinate
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double,
) {
    /**
     * Calculate distance to another LatLng in kilometers using Haversine formula
     */
    fun distanceTo(other: LatLng): Double {
        val earthRadius = 6371.0 // km

        val lat1Rad = Math.toRadians(latitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val deltaLat = Math.toRadians(other.latitude - latitude)
        val deltaLon = Math.toRadians(other.longitude - longitude)

        val a =
            Math.sin(deltaLat / 2).pow(2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLon / 2).pow(2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    private fun Double.pow(power: Int): Double = Math.pow(this, power.toDouble())
}

/**
 * Represents a single segment in the trip video timeline
 */
data class TripSegment(
    val id: String = java.util.UUID
        .randomUUID()
        .toString(),
    val type: SegmentType,
    val uri: Uri?,
    val durationMs: Long,
    val startCoord: LatLng?,
    val endCoord: LatLng?,
    val clusterId: String?,
    val timestamp: Long? = null,
    val order: Int = 0,
)

/**
 * Represents a cluster of photos taken at similar location/time
 */
data class Cluster(
    val id: String = java.util.UUID
        .randomUUID()
        .toString(),
    val name: String,
    val centerCoord: LatLng?,
    val segments: List<TripSegment>,
    val startTime: Long?,
    val endTime: Long?,
)

/**
 * Video aspect ratio options
 */
enum class AspectRatio(
    val width: Int,
    val height: Int,
    val displayName: String,
) {
    SQUARE(1, 1, "Square (1:1)"),
    PORTRAIT(9, 16, "Portrait (9:16)"),
    LANDSCAPE(16, 9, "Landscape (16:9)"),
}

/**
 * Template presets for video pacing
 */
enum class TemplatePreset(
    val photoDurationMs: Long,
    val videoHighlightDurationMs: Long,
    val displayName: String,
    val description: String,
) {
    FAST_PACED(2000, 3000, "Fast-Paced", "Quick cuts, energetic"),
    BALANCED(4000, 5000, "Balanced", "Standard pacing"),
    CINEMATIC(5000, 8000, "Cinematic", "Slow, dramatic with Ken Burns"),
}

/**
 * Render status for a trip
 */
enum class RenderStatus {
    DRAFT,
    RENDERING,
    COMPLETED,
    FAILED,
}

/**
 * Complete trip manifest containing all segments and settings
 */
@Suppress("LongParameterList")
data class TripManifest(
    val id: String = java.util.UUID
        .randomUUID()
        .toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val segments: List<TripSegment> = emptyList(),
    val clusters: List<Cluster> = emptyList(),
    val totalDurationMs: Long = 0,
    val aspectRatio: AspectRatio = AspectRatio.SQUARE,
    val template: TemplatePreset = TemplatePreset.BALANCED,
    val musicUri: Uri? = null,
    val status: RenderStatus = RenderStatus.DRAFT,
    val outputPath: String? = null,
) {
    val photoCount: Int get() = segments.count { it.type == SegmentType.PHOTO }
    val videoCount: Int get() = segments.count { it.type == SegmentType.VIDEO }
    val totalItems: Int get() = segments.size
}
