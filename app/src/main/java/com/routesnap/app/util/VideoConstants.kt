package com.routesnap.app.util

/**
 * Constants used throughout the app
 */
object VideoConstants {

    // Durations
    const val DEFAULT_PHOTO_DURATION_MS = 4000L
    const val DEFAULT_VIDEO_HIGHLIGHT_DURATION_MS = 5000L
    const val MAP_TRANSITION_DURATION_MS = 2000L

    // Clustering thresholds
    const val CLUSTER_MAX_DISTANCE_KM = 5.0
    const val CLUSTER_MAX_TIME_GAP_HOURS = 4L
    const val BURST_MODE_THRESHOLD = 50
    const val BURST_MODE_WINDOW_MINUTES = 10L
    const val BURST_MODE_KEEP_COUNT = 3

    // Video limits
    const val MAX_VIDEO_DURATION_MS = 300000L // 5 minutes
    const val MIN_VIDEO_DURATION_MS = 60000L  // 1 minute

    // Output settings
    const val DEFAULT_VIDEO_BITRATE = 8_000_000 // 8 Mbps
    const val DEFAULT_AUDIO_BITRATE = 128_000   // 128 kbps
    const val DEFAULT_FRAME_RATE = 30

    // Resolutions
    const val RESOLUTION_720P_WIDTH = 1280
    const val RESOLUTION_720P_HEIGHT = 720
    const val RESOLUTION_1080P_WIDTH = 1920
    const val RESOLUTION_1080P_HEIGHT = 1080
    const val RESOLUTION_4K_WIDTH = 3840
    const val RESOLUTION_4K_HEIGHT = 2160

    // Aspect ratios
    const val ASPECT_RATIO_SQUARE = 1.0f
    const val ASPECT_RATIO_PORTRAIT = 9.0f / 16.0f
    const val ASPECT_RATIO_LANDSCAPE = 16.0f / 9.0f
}
