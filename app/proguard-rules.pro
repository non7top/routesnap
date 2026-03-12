# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Hilt
-dontwarn dagger.**
-dontwarn javax.inject.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# MapLibre
-dontwarn org.maplibre.**
-keep class org.maplibre.** { *; }

# Media3
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Moshi - Keep model classes for JSON serialization
-keep class com.routesnap.app.domain.model.** { *; }
-keepclassmembers class com.routesnap.app.domain.model.** { *; }
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keepclassmembers,allowobfuscation class * {
    @com.squareup.moshi.FromJson void *(...);
    @com.squareup.moshi.ToJson void *(...);
}

# Keep LatLng class for Moshi serialization
-keep class com.routesnap.app.domain.model.LatLng { *; }

# Keep TripManifest and TripSegment for JSON serialization
-keep class com.routesnap.app.domain.model.TripManifest { *; }
-keep class com.routesnap.app.domain.model.TripSegment { *; }
-keep class com.routesnap.app.domain.model.Cluster { *; }

# Keep enums
-keepclassmembers enum com.routesnap.app.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
