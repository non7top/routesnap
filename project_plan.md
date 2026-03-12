# RouteSnap - Android Travel Recap Video Generator

## Project Overview
Build a high-performance Android application that generates a 1–5 minute travel recap video combining user-selected photos/videos with animated Google Maps routes based on GPS metadata.

---

## Core Architecture & Tech Stack

| Component | Technology | Rationale |
|-----------|------------|-----------|
| **Language** | Kotlin | Modern, concise, coroutines support |
| **UI Framework** | Jetpack Compose | Declarative UI, easier state management |
| **Map Engine** | MapLibre Native | Free OSM tiles, no API costs, supports snapshots |
| **Video Engine** | Jetpack Media3 Transformer | Hardware acceleration, native Android support, smaller APK |
| **Metadata Extraction** | ExifInterface | GPS coordinates, timestamps from photos/videos |
| **Dependency Injection** | Hilt | Testability, cleaner architecture |
| **Local Storage** | Room Database | Persist trip manifests, resume/retry scenarios |
| **Background Processing** | Foreground Service + WorkManager | Reliable video rendering with progress notifications |

### Dependencies (`libs.versions.toml`)
```toml
[versions]
media3 = "1.2.1"
maplibre = "11.0.0"
hilt = "2.48.1"
room = "2.6.1"

[libraries]
androidx-media3-transformer = { group = "androidx.media3", name = "media3-transformer", version.ref = "media3" }
androidx-media3-effect = { group = "androidx.media3", name = "media3-effect", version.ref = "media3" }
maplibre-android-sdk = { group = "org.maplibre.gl", name = "android-sdk", version.ref = "maplibre" }
androidx-exifinterface = { group = "androidx.exifinterface", name = "exifinterface", version = "1.3.7" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
```

---

## Data Models

### Core Structures
```kotlin
enum class SegmentType { PHOTO, VIDEO, MAP_TRAVEL }

data class TripSegment(
    val type: SegmentType,
    val uri: Uri?,          // For Photos/Videos
    val durationMs: Long,
    val startCoord: LatLng?, // For Map Segments
    val endCoord: LatLng?,
    val clusterId: String?
)

data class TripManifest(
    val id: String,
    val name: String,
    val createdAt: Long,
    val segments: List<TripSegment>,
    val totalDurationMs: Long,
    val aspectRatio: AspectRatio,
    val musicUri: Uri?
)

enum class AspectRatio { SQUARE_1_1, PORTRAIT_9_16, LANDSCAPE_16_9 }
```

### Room Entity
```kotlin
@Entity(tableName = "trip_manifests")
data class TripManifestEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val jsonData: String, // Serialized segments
    val status: RenderStatus
)

enum class RenderStatus { DRAFT, RENDERING, COMPLETED, FAILED }
```

---

## Phase 1: Intelligent Media Picker & Data Extraction

### Task: Create gallery for user media selection
**Implementation:** Use Android Photo Picker (no permission required)

```kotlin
val pickMedia = registerForActivityResult(PickVisualMedia()) { uris ->
    uris.forEach { uri ->
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        extractMetadata(uri)
    }
}

pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
```

### GPS & Timestamp Extraction
```kotlin
fun extractMetadata(uri: Uri): MediaMetadata {
    val exif = ExifInterface(contentResolver.openInputStream(uri)!!)
    val latLong = exif.latLong // [latitude, longitude]
    val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
    return MediaMetadata(uri, latLong?.get(0), latLong?.get(1), dateTime)
}
```

### Smart Clustering Logic
Group photos to prevent video chaos:

| Rule | Threshold | Action |
|------|-----------|--------|
| **Spatial** | Distance < 5km | Merge into current cluster |
| **Temporal** | Time diff > 4 hours | Force NEW cluster |
| **Burst Mode** | 50+ photos in 10 min | Select top 3 (quality/face score) |

**Output:** `List<Cluster>` with `MAP_TRAVEL` segments inserted between clusters

---

## Phase 2: Map Animation Engine (The "Swoosh" Effect)

### Option A: MapSnapshotter (Recommended for MVP)
```kotlin
fun generateMapBackground(
    context: Context,
    route: List<LatLng>,
    callback: (Bitmap) -> Unit
) {
    val options = MapSnapshotter.Options(1080, 1920).apply {
        withStyleBuilder(Style.Builder().fromUri(
            "https://demotiles.maplibre.org/style.json"
        ))
        withRegion(calculateBoundingBox(route))
    }

    val snapshotter = MapSnapshotter(context, options)
    snapshotter.start { snapshot ->
        callback(snapshot.bitmap)
    }
}
```

### Option B: CanvasOverlay for Line Animation
```kotlin
val mapOverlay = object : CanvasOverlay() {
    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val progress = (presentationTimeUs / segmentDuration).coerceIn(0f, 1f)
        val currentPath = calculatePartialPath(fullRoute, progress)
        canvas.drawPath(currentPath, paintLine) // Draws the "Swoosh"
    }
}
```

### Camera Animation Logic
- **Start:** "Space-to-Earth" zoom-in on first photo location
- **Between clusters:** Zoom out to show path, tilt/rotate into next location
- **End:** Zoom into final destination

---

## Phase 3: Video Assembly (The "Director")

### Media3 Composition Pipeline
```kotlin
fun buildComposition(segments: List<TripSegment>): Composition {
    val sequences = segments.map { segment ->
        when (segment.type) {
            SegmentType.PHOTO -> {
                val imageItem = MediaItem.Builder()
                    .setUri(segment.uri)
                    .setImageDurationMs(segment.durationMs)
                    .build()

                val effect = ScaleToFitTransformation.Builder()
                    .setScale(1.0f, 1.2f) // Ken Burns zoom
                    .build()

                EditedMediaItem.Builder(imageItem)
                    .setEffects(Effects(listOf(effect), listOf()))
                    .build()
            }
            SegmentType.MAP_TRAVEL -> {
                val mapItem = MediaItem.Builder()
                    .setUri(segment.uri)
                    .build()
                EditedMediaItem.Builder(mapItem)
                    .setEffects(Effects(listOf(mapOverlay), listOf()))
                    .build()
            }
            SegmentType.VIDEO -> {
                val videoItem = MediaItem.fromUri(segment.uri!!)
                EditedMediaItem.Builder(videoItem)
                    .setDurationUs(segment.durationMs * 1000)
                    .build()
            }
        }
    }

    return Composition.Builder(
        sequences.filterNotNull().map { EditedMediaItemSequence(it) }
    ).build()
}
```

### Audio Ducking
```kotlin
val audioTrack = AudioMixEffect.Builder()
    .setDuckingLevel(-0.8f) // Reduce music when video plays
    .build()
```

### Rendering with Foreground Service
```kotlin
@HiltAndroidApp
class RouteSnapApplication : Application()

@AndroidEntryPoint
class RenderService : ForegroundService() {
    private val transformer = Transformer.Builder(this).build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(RENDER_NOTIFICATION_ID, createProgressNotification())
        transformer.start(composition, outputPath)
        return START_NOT_STICKY
    }
}
```

---

## Phase 4: UI/UX Flow

### Screen 1: Create New Trip
- Photo Picker integration
- Multi-select photos/videos
- Display count and estimated video duration

### Screen 2: Review Timeline
- Map view with pins showing clusters
- Drag to reorder segments
- Swipe to delete
- Adjust segment duration slider

### Screen 3: Style
- Aspect ratio selector (1:1, 9:16, 16:9)
- Music picker (local files or royalty-free library)
- Template presets:
  - **Fast-paced:** 2s/photo
  - **Balanced:** 4s/photo
  - **Cinematic:** 5s/photo + Ken Burns

### Screen 4: Rendering
- Progress bar (0-100%)
- ETA display
- Cancel button
- Low-power mode toggle (lower resolution)

### Screen 5: Share
- Save to Gallery
- Share to Instagram/TikTok
- Export quality options (720p, 1080p, 4K)

---

## Phase 5: Performance & Scaling

### Smart Skip for Large Selections
If user selects 100+ photos:
- Auto-limit to 30 best photos (quality score, variety)
- Keep video under 5 minutes
- Show warning with option to override

### Memory Management
- Process EXIF extraction in background coroutine
- Use pagination for gallery (max 50 items per page)
- Release bitmaps after snapshot generation
- Target heap: < 256MB

### Background Execution Strategy
| Task | Execution Method |
|------|------------------|
| EXIF extraction | Coroutine (Dispatchers.IO) |
| Map snapshot generation | WorkManager (deferrable) |
| Video rendering | Foreground Service (immediate) |
| Export/sharing | WorkManager (network) |

---

## Project Structure
```
app/
├── data/
│   ├── repository/      # MediaRepository, TripRepository
│   ├── local/           # Room DAOs, TypeConverters
│   └── exif/            # MetadataExtractor
├── domain/
│   ├── model/           # TripSegment, TripManifest, Cluster
│   └── clustering/      # ClusterAlgorithm, DistanceCalculator
├── ui/
│   ├── picker/          # PhotoPickerScreen, ViewModel
│   ├── timeline/        # TimelineScreen, DragDropList
│   ├── style/           # StyleScreen, AspectRatioSelector
│   ├── rendering/       # RenderScreen, ProgressState
│   └── share/           # ShareScreen
├── rendering/
│   ├── composition/     # Media3CompositionBuilder
│   ├── map/             # MapSnapshotterWrapper, CanvasOverlay
│   └── service/         # RenderForegroundService
├── di/
│   ├── AppModule.kt     # Hilt modules
│   └── Qualifiers.kt    # Custom qualifiers
└── util/
    ├── PermissionHelper.kt
    └── VideoConstants.kt
```

---

## Critical Implementation Notes

### Permission Strategy (Android 14+)
```kotlin
// Photo Picker - NO permission needed for selected URIs
registerForActivityResult(PickVisualMedia()) { ... }

// For full gallery access (if custom picker needed)
// Manifest: READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED
```

### API Level Requirements
- **Minimum:** API 26 (Android 8.0)
- **Target:** API 34 (Android 14)
- Photo Picker available API 30+ (fallback for older)

### Cost Considerations
| Service | Cost | Mitigation |
|---------|------|------------|
| MapLibre + OSM | Free | No API key required |
| Media3 | Free | Part of AndroidX |
| Google Maps Static | $2-7 per 1000 requests | Not used (MapLibre instead) |

---

## Implementation Priorities

### MVP (Must Have) - v1.0
1. ✅ Photo Picker + EXIF extraction
2. ✅ Basic clustering (time + distance)
3. ✅ Static map snapshots with polyline overlay
4. ✅ Media3 composition (photos + map segments)
5. ✅ Foreground service for rendering
6. ✅ Save to gallery

### V2 (Should Have) - v1.5
1. Video trimming/highlights
2. Ken Burns effects
3. Audio ducking
4. Aspect ratio selection
5. Room database for trip persistence
6. Hilt dependency injection

### V3 (Nice to Have) - v2.0
1. 3D terrain visualization
2. Beat-synced editing (audio analysis)
3. Instagram/TikTok direct export
4. Cloud backup
5. Collaborative trips (multi-user)

---

## Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| `MapSnapshotter` slow for long routes | High | Pre-generate tiles in parallel; cache results |
| Media3 `CanvasOverlay` drops frames | Medium | Test early; fallback to pre-rendered segments |
| Memory OOM with 100+ photos | High | Pagination in picker; stream processing |
| Foreground Service battery drain | Medium | Add "low power" mode (lower resolution) |
| Map tile loading offline | Low | Cache tiles; offline-first design |

---

## Success Metrics
- **Render time:** < 2 minutes for 5-minute video (mid-range device)
- **Crash-free rate:** > 99.5%
- **App size:** < 50MB (APK), < 100MB (installed)
- **Memory usage:** < 256MB peak during rendering

---

## Next Steps
1. Initialize Android project with Compose template
2. Set up Hilt, Room, Media3 dependencies
3. Implement Photo Picker + EXIF extraction
4. Build clustering algorithm
5. Create MapSnapshotter wrapper
6. Implement Media3 composition pipeline
7. Build UI screens (Compose)
8. Add Foreground Service for rendering
9. Test on various devices (low/mid/high-end)
10. Beta release + iterate
