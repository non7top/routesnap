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
 * Transition effect applied at the boundary between segments.
 * NONE = hard cut, FADE_BLACK / FADE_WHITE = dip through colour,
 * FLASH = white spike that decays rapidly at the segment head.
 */
enum class TransitionType(
    val label: String,
) {
    NONE("Cut"),
    FADE_BLACK("Fade"),
    FADE_WHITE("Dip White"),
    FLASH("Flash"),
}

/**
 * Normalized rectangle (0–1 coordinates) used as Ken Burns start/end frames.
 */
data class ZoomRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    companion object {
        val FULL = ZoomRect(0f, 0f, 1f, 1f)

        /**
         * Default start/end rect pairs reproducing the old 4-direction Ken Burns
         * (scale 1.15→1.5, ±0.06 NDC pan). y-values differ from x-values because
         * Media3's GL pipeline flips the y-axis (ty = +cy*scale, not -cy*scale).
         */
        private val DEFAULTS =
            listOf(
                Pair(ZoomRect(0.09f, 0.04f, 0.96f, 0.91f), ZoomRect(0.15f, 0.19f, 0.81f, 0.85f)), // TL→BR
                Pair(ZoomRect(0.04f, 0.04f, 0.91f, 0.91f), ZoomRect(0.19f, 0.19f, 0.85f, 0.85f)), // TR→BL
                Pair(ZoomRect(0.09f, 0.09f, 0.96f, 0.96f), ZoomRect(0.15f, 0.15f, 0.81f, 0.81f)), // BL→TR
                Pair(ZoomRect(0.04f, 0.09f, 0.91f, 0.96f), ZoomRect(0.19f, 0.15f, 0.85f, 0.81f)), // BR→TL
            )

        fun defaultPair(index: Int): Pair<ZoomRect, ZoomRect> = DEFAULTS[index % DEFAULTS.size]
    }
}

/**
 * Represents a single segment in the trip video timeline
 */
data class TripSegment(
    val id: String =
        java.util.UUID
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
    val transitionType: TransitionType? = null,
    val transitionDurationMs: Long? = null,
    val photoAspectRatio: Float? = null,
    val startZoomRect: ZoomRect? = null,
    val endZoomRect: ZoomRect? = null,
    val isReviewed: Boolean = false,
)

/**
 * Represents a cluster of photos taken at similar location/time
 */
data class Cluster(
    val id: String =
        java.util.UUID
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
    val defaultTransitionType: TransitionType,
    val defaultTransitionDurationMs: Long,
) {
    FAST_PACED(2000, 3000, "Fast-Paced", "Quick cuts, energetic", TransitionType.NONE, 100L),
    BALANCED(4000, 5000, "Balanced", "Standard pacing", TransitionType.NONE, 250L),
    CINEMATIC(5000, 8000, "Cinematic", "Slow, dramatic with Ken Burns", TransitionType.NONE, 400L),
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
 * A single music track in the playlist, with optional trim region and per-track fades.
 */
data class MusicTrack(
    val uri: Uri,
    val displayName: String = "",
    val startMs: Long = 0,
    val endMs: Long = 0,
    val fadeInMs: Long = 1000,
    val fadeOutMs: Long = 1000,
)

/**
 * Complete trip manifest containing all segments and settings
 */
@Suppress("LongParameterList")
data class TripManifest(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val segments: List<TripSegment> = emptyList(),
    val clusters: List<Cluster> = emptyList(),
    val totalDurationMs: Long = 0,
    val aspectRatio: AspectRatio = AspectRatio.PORTRAIT,
    val template: TemplatePreset = TemplatePreset.BALANCED,
    val transitionOverride: TransitionType? = null,
    val musicTracks: List<MusicTrack> = emptyList(),
    val musicVolumeDb: Float = 0f,
    val status: RenderStatus = RenderStatus.DRAFT,
    val outputPath: String? = null,
) {
    val photoCount: Int get() = segments.count { it.type == SegmentType.PHOTO }
    val videoCount: Int get() = segments.count { it.type == SegmentType.VIDEO }
    val totalItems: Int get() = segments.size
}
