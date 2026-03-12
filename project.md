Take 1

Agent Instruction: Project "RouteSnap" (Android)
Objective: Build a high-performance Android application that generates a 1–5 minute travel recap video combining user-selected photos/videos with animated Google Maps routes based on GPS metadata.
1. Core Architecture & Tech Stack

    Language: Kotlin (Jetpack Compose for UI).
    Map Engine: Google Maps SDK or Mapbox (for easier camera animation control).
    Video Engine: FFmpeg (via ffmpeg-kit-android) or Media3 Transformer API.
    Metadata: ExifInterface to extract GPS (Latitude/Longitude) and Timestamps.

2. Phase 1: Intelligent Media Picker & Data Extraction

    Task: Create a gallery where users select photos/videos.
    Logic:
        Scan selected files for GPS_LATITUDE and DATETIME.
        Sort files chronologically.
        Group media into "Clusters" (e.g., if coordinates are within 1km, they belong to "Stop A").
    Output: A JSON manifest containing: {file_path, lat, lng, timestamp, cluster_id}.

3. Phase 2: Map Animation Engine (The "Swoosh" Effect)

    Task: Generate a video sequence of the map.
    Logic:
        Initialize a MapView in a background "headless" buffer or use a static map API.
        Animate a "Polyline" (the path) from Point A to Point B.
        Camera Logic: Start with a "Space-to-Earth" zoom-in on the first photo. Between clusters, zoom out to show the path, then tilt/rotate into the next location.
        Capture: Record the MapView frames or download frames via Mapbox Static Image API to create a .mp4 segment.

4. Phase 3: Video Assembly (The "Director")

    Task: Stitch Map Segments + User Media + Music.
    FFmpeg Scripting Requirements:
        Photos: Apply a subtle "Ken Burns" (slow zoom/pan) effect (5 sec per photo).
        Videos: Trim user videos to 5-10 second "highlights" (or keep full if requested).
        Transitional Map: Insert the animated map segment between different location clusters.
        Audio: Add a background track with "Audio Ducking" (music volume drops when a user video plays).
        Overlay: Burn a small "Mini-map" or "Date/Location" text in the bottom corner.

5. Phase 4: UI/UX Flow

    Screen 1: "Create New Trip" (Select Photos/Videos).
    Screen 2: "Review Timeline" (Map view with pins; user can drag to reorder or delete).
    Screen 3: "Style" (Select 1:1, 9:16, or 16:9 aspect ratio; select Music).
    Screen 4: "Rendering" (Progress bar showing FFmpeg status).
    Screen 5: "Share" (Save to Gallery / Export to Instagram).

6. Critical Implementation Instructions for the Agent:

    Permission Handling: Ensure READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, and ACCESS_MEDIA_LOCATION are handled for Android 13+.
    Performance: All heavy video rendering must happen in a WorkManager or a Foreground Service to prevent the app from crashing.
    Scaling: If the user selects 100+ photos, implement a "Smart Skip" to keep the video under 5 minutes.

===================================================================================
Take 2

Phase 1: The Pivot (Architecture fixes)
1. Replace FFmpeg with Jetpack Media3
The Media3 Transformer API is Android's new standard for editing. It uses the device's hardware encoder/decoder, offering faster export times and a significantly smaller APK footprint than FFmpeg.

    Benefit: Native support for "Ken Burns" (Zoom/Pan) via GlEffect.
    Benefit: Native "Audio Ducking" controls via CompositionPlayer.

2. The "Virtual Camera" Map Solution
Since you cannot record a background MapView, you have two stable options for the "Swoosh" effect:

    Option A (The "TravelBoast" Method): Use a lightweight OpenGL map renderer (like MapLibre Native) that supports rendering to an off-screen FramebufferObject (FBO). This allows you to generate frames without a UI.
    Option B (The "Tile Stitch" Method - Easiest):
        Calculate the bounding box of the route.
        Download a high-res Static Map image (or stitch 4 tiles) covering the area.
        Use Media3 to apply a Pan/Zoom effect across this static image.
        Overlay an animated SVG line (Polyline) on top using a custom GlEffect.

Phase 2: Visual Inspiration & UI Targets
Your UI should aim for the "Timeline" aesthetic of Polarsteps combined with the dynamic motion of TravelBoast.
Route Animation
Timeline UI
3D Terrain
Trip Stats
Video Export
Smart Clustering Logic
Refine your clustering to be Event-Based, not just distance-based.

    Time Gap: If photos are > 4 hours apart, force a new cluster even if the location is close.
    Burst Mode: If 50 photos exist within 10 minutes, pick only the 3 with the highest file size (proxy for quality) or face detection score.

Phase 3: Revised Implementation Plan
Step 1: The Data Manifest
Generate a unified timeline object before rendering.
kotlin

data class TripSegment(
    val type: SegmentType, // PHOTO, VIDEO, MAP_TRANSITION
    val durationMs: Long,
    val assetUri: Uri?,
    val mapStartCoord: LatLng?,
    val mapEndCoord: LatLng?
)

Use code with caution.
Step 2: The "Map Swoosh" Generator (Custom GL)
Instead of recording a view, use a Custom OpenGL Renderer:

    Input: List<LatLng> (The path).
    Texture: Load a static map image of the region as a GL Texture.
    Shader: Write a Fragment Shader that draws a dashed line progressing from 0.0 to 1.0 over durationMs.
    Camera: Update the MVP (Model-View-Projection) matrix to follow the "head" of the line.
    Output: Feed these textures directly into Media3 Transformer as a video stream.

Step 3: Media3 Composition Pipeline
Construct the video non-linearly using Composition.
kotlin

// 1. Create Effects
val kenBurns = ScaleToFitTransformation.Builder()
    .setScale(1.2f, 1.0f) // Zoom out effect
    .build()

// 2. Build Clips
val photoItem = EditedMediaItem.Builder(MediaItem.fromUri(photoUri))
    .setEffects(Effects(listOf(kenBurns)))
    .setDurationUs(5_000_000) // 5 seconds
    .build()

// 3. Stitch (Sequentially)
val composition = Composition.Builder(
    listOf(
        EditedMediaItemSequence(mapIntroItem),
        EditedMediaItemSequence(photoItem),
        EditedMediaItemSequence(mapTransitionItem),
        EditedMediaItemSequence(videoHighlightItem)
    )
).build()

// 4. Export
val transformer = Transformer.Builder(context).build()
transformer.start(composition, outputPath)

Use code with caution.
Phase 4: Critical Constraints
1. Google Maps API Costs

    Risk: Using "Static Maps API" or "3D Tiles" costs money per request.
    Fix: Use Mapbox Static Images (generous free tier) or OpenStreetMap (completely free) for the map background tiles.

2. Background Execution

    Requirement: Video rendering is heavy.
    Implementation: Use a ForegroundService with type dataSync or mediaProcessing. Show a persistent notification with a progress bar. Do not rely on standard WorkManager for immediate, long-running graphical tasks as it may be deferred.

3. Permission Strategy (Android 14+)
You must request READ_MEDIA_VISUAL_USER_SELECTED alongside standard permissions to support the new partial access picker.

    Tip: Use the Android Photo Picker (ActivityResultContracts.PickMultipleVisualMedia) instead of building a custom gallery. It handles permissions automatically and requires zero maintenance.




==============================================
Take 3

Phase 1: The Project Manifest
1.1 Dependencies (libs.versions.toml)
You must use the Jetpack Media3 suite for video and MapLibre for tile generation (it allows free static snapshots without an API key if using OpenStreetMap raster tiles).
toml

[versions]
media3 = "1.2.1"
maplibre = "11.0.0"

[libraries]
androidx-media3-transformer = { group = "androidx.media3", name = "media3-transformer", version.ref = "media3" }
androidx-media3-effect = { group = "androidx.media3", name = "media3-effect", version.ref = "media3" }
maplibre-android-sdk = { group = "org.maplibre.gl", name = "android-sdk", version.ref = "maplibre" }
androidx-exifinterface = { group = "androidx.exifinterface", name = "exifinterface", version = "1.3.7" }

Use code with caution.
1.2 Permission Strategy (Android 14+)
Do not request READ_EXTERNAL_STORAGE on Android 13+. Use the Photo Picker interaction which grants temporary, permission-less access to selected URIs.
kotlin

// ActivityResultContract for the new Photo Picker
val pickMedia = registerForActivityResult(PickVisualMedia()) { uri ->
    if (uri != null) {
        // Permission is automatically granted for this specific URI
        // Persist permission across reboots if needed:
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

// Launch Mode
pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))

Use code with caution.
Phase 2: Intelligent Data Core
2.1 The Data Structure
Convert the chaotic list of user photos into a linear "Storyboard" before rendering.
kotlin

enum class SegmentType { PHOTO, VIDEO, MAP_TRAVEL }

data class TripSegment(
    val type: SegmentType,
    val uri: Uri?,          // For Photos/Videos
    val durationMs: Long,
    val startCoord: LatLng?, // For Map Segments
    val endCoord: LatLng?
)

Use code with caution.
2.2 Clustering Logic (The "Brain")
Raw GPS data is noisy. You must group photos to prevent the video from jumping every 5 seconds.

    Spatial Rule: If distance < 5km, merge into current cluster.
    Temporal Rule: If time diff > 4 hours, force NEW cluster (even if location is same).
    Output: List<Cluster>. A MAP_TRAVEL segment is inserted between every two Cluster objects.

Phase 3: The Map Engine (MapLibre)
3.1 The Snapshotter (Off-Screen Rendering)
Instead of a View, use MapSnapshotter. This generates a Bitmap of the map region without attaching to a Window.
kotlin

fun generateMapBackground(context: Context, route: List<LatLng>, callback: (Bitmap) -> Unit) {
    val options = MapSnapshotter.Options(1080, 1920).apply {
        withStyleBuilder(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json"))
        withRegion(calculateBoundingBox(route)) // Zoom to fit route
    }

    val snapshotter = MapSnapshotter(context, options)
    snapshotter.start { snapshot ->
        callback(snapshot.bitmap) // Returns a static Bitmap of the map
    }
}

Use code with caution.
3.2 The "Swoosh" Animation (Custom GL Shader)
To animate the line without a full game engine, use a Media3 GlEffect.

    Base: The Static Map Bitmap (from 3.1).
    Overlay: A custom Fragment Shader that draws a line.
    Uniform: Pass a progress float (0.0 -> 1.0) bound to the video timestamp.

For MVP: Generate 2 images (Start Map, End Map) and use a simple CrossFade or Pan effect.
For Pro: Use OverlayEffect with a CanvasOverlay to draw the line frame-by-frame.
kotlin

// Simple approach: Draw the line progressively on a CanvasOverlay
val mapOverlay = object : CanvasOverlay() {
    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val progress = (presentationTimeUs / segmentDuration).coerceIn(0f, 1f)
        val currentPath = calculatePartialPath(fullRoute, progress)
        canvas.drawPath(currentPath, paintLine) // Draws the "Swoosh"
    }
}

Use code with caution.
Phase 4: The Director (Media3 Composition)
4.1 Assembling the Sequence
Stitch the static map segments (with overlays) and user photos into one timeline.
kotlin

fun buildComposition(segments: List<TripSegment>): Composition {
    val sequences = segments.map { segment ->
        when (segment.type) {
            SegmentType.PHOTO -> {
                // Apply Ken Burns (Zoom)
                val imageItem = MediaItem.Builder().setUri(segment.uri)
                    .setImageDurationMs(segment.durationMs).build()

                val effect = ScaleToFitTransformation.Builder()
                    .setScale(1.0f, 1.2f) // Zoom from 1.0 to 1.2
                    .build()

                EditedMediaItem.Builder(imageItem)
                    .setEffects(Effects(listOf(effect), listOf()))
                    .build()
            }
            SegmentType.MAP_TRAVEL -> {
                // Static Map Image + Canvas Overlay for Line Animation
                val mapItem = MediaItem.Builder().setUri(segment.uri).build() // Bitmap URI
                EditedMediaItem.Builder(mapItem)
                    .setEffects(Effects(listOf(mapOverlay), listOf()))
                    .build()
            }
            else -> null
        }
    }

    return Composition.Builder(sequences.filterNotNull().map { EditedMediaItemSequence(it) })
        .build()
}

Use code with caution.
4.2 Execution (Foreground Service)
Rendering 4K video is memory intensive.

    Start a ForegroundService with type mediaProcessing.
    Initialize Transformer.Builder(context).build().
    Call transformer.start(composition, outputPath).
    Update the Notification progress bar via the Transformer.Listener.
