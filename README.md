# RouteSnap - Android Travel Recap Video Generator

**Phase 1 MVP** - Functional Android APK with basic features

## Overview

RouteSnap is an Android application that generates 1-5 minute travel recap videos by combining user-selected photos/videos with animated map routes based on GPS metadata.

## Features (Phase 1)

### Implemented
- ✅ **Photo Picker** - Android Photo Picker integration (no storage permissions needed)
- ✅ **EXIF Extraction** - Extract GPS coordinates and timestamps from photos
- ✅ **Smart Clustering** - Group photos by location and time
- ✅ **Timeline View** - Review and manage selected photos
- ✅ **Style Screen** - Select aspect ratio and template
- ✅ **Render Screen** - Progress visualization (simulated in Phase 1)
- ✅ **Share Screen** - Export options UI

### Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **DI**: Hilt
- **Database**: Room
- **Video**: Media3 Transformer (setup complete, implementation in Phase 2)
- **Maps**: MapLibre (setup complete, implementation in Phase 2)

## Project Structure

```
app/
├── src/main/java/com/routesnap/app/
│   ├── data/
│   │   ├── local/           # Room database, DAOs
│   │   ├── exif/            # Metadata extraction
│   │   └── repository/      # Trip repository
│   ├── domain/
│   │   ├── model/           # Data models
│   │   └── clustering/      # Clustering algorithm
│   ├── ui/
│   │   ├── picker/          # Photo picker screen
│   │   ├── timeline/        # Timeline screen
│   │   ├── style/           # Style selection screen
│   │   ├── render/          # Render progress screen
│   │   ├── share/           # Share screen
│   │   └── theme/           # Compose theme
│   ├── rendering/
│   │   └── service/         # Foreground service for rendering
│   ├── util/                # Utilities
│   ├── di/                  # Hilt dependency injection
│   ├── MainActivity.kt
│   └── RouteSnapApplication.kt
└── build.gradle.kts
```

## Build Instructions

### Prerequisites

1. **Android Studio** (Arctic Fox or newer)
2. **JDK 17**
3. **Android SDK** with:
   - API 34 (Android 14)
   - Build Tools 33.0.2
   - Android SDK Platform-Tools

### Setup

1. **Open in Android Studio**
   ```
   File -> Open -> Select the routesnap directory
   ```

2. **Sync Gradle**
   - Android Studio will automatically sync
   - Or run: `./gradlew build`

3. **Build Debug APK**
   ```bash
   ./gradlew assembleDebug
   ```

   The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

4. **Build Release APK**
   ```bash
   ./gradlew assembleRelease
   ```

### Command Line Build (with Android SDK)

```bash
export ANDROID_HOME=/path/to/android/sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

./gradlew clean assembleDebug
```

## Running the App

1. **Install on Device/Emulator**
   ```bash
   ./gradlew installDebug
   ```

2. **Or directly via ADB**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Minimum Requirements
- **Android**: 8.0 (API 26) or higher
- **RAM**: 2GB minimum
- **Storage**: 100MB free space

## Usage Flow

1. **Launch App** -> "Create New Trip"
2. **Select Photos** -> Tap "Select Photos & Videos"
3. **Review** -> See photo count, clusters, and estimated duration
4. **Timeline** -> Review and reorder photos (optional)
5. **Style** -> Choose aspect ratio and template
6. **Render** -> Watch progress (simulated in Phase 1)
7. **Share** -> Save or share your video

## Known Limitations (Phase 1)

- Video rendering is simulated (actual Media3 integration in Phase 2)
- Map animations not yet implemented
- Music selection is placeholder
- No actual video export yet

## Next Steps (Phase 2)

- [ ] Media3 Transformer integration for actual video rendering
- [ ] MapLibre map snapshot generation
- [ ] Canvas overlay for route animations
- [ ] Ken Burns effects for photos
- [ ] Audio mixing and ducking
- [ ] Real foreground service implementation

## Troubleshooting

### Build Errors

**"SDK not found"**
```bash
# Set ANDROID_HOME
export ANDROID_HOME=/path/to/android/sdk
```

**"Java version mismatch"**
- Ensure JDK 17 is installed
- Check in Android Studio: File -> Project Structure -> SDK Location

### Runtime Issues

**Photos not loading**
- Ensure you're selecting photos with GPS metadata
- Check app permissions in Settings

**App crashes on startup**
- Check logcat: `adb logcat | grep RouteSnap`
- Ensure device meets minimum requirements

## License

MIT License - See LICENSE file for details

## Contributing

This is a work in progress. Please check the project roadmap before contributing.
