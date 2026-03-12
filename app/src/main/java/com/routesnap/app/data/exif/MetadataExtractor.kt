package com.routesnap.app.data.exif

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.routesnap.app.domain.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Extracted metadata from a media file
 */
data class MediaMetadata(
    val uri: Uri,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long?,
    val width: Int? = null,
    val height: Int? = null,
    val fileSize: Long? = null,
    val error: MetadataError? = null
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
    val latLng: LatLng? get() = latitude?.let { lat -> longitude?.let { lng -> LatLng(lat, lng) } }
}

/**
 * Represents errors that can occur during metadata extraction
 */
sealed class MetadataError {
    data class IoError(val message: String) : MetadataError()
    data class ParseError(val message: String) : MetadataError()
    data class SecurityError(val message: String) : MetadataError()
    data class UnknownError(val message: String, val exception: Throwable) : MetadataError()
}

/**
 * Utility class for extracting EXIF metadata from photos and videos
 */
class MetadataExtractor(private val contentResolver: ContentResolver) {

    companion object {
        private const val TAG = "MetadataExtractor"
    }

    /**
     * Extract metadata from a media URI
     */
    suspend fun extractMetadata(uri: Uri): MediaMetadata = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)

                val latLong = exif.latLong
                val latitude = latLong?.getOrNull(0)
                val longitude = latLong?.getOrNull(1)

                val timestamp = extractTimestamp(exif)

                val (width, height) = extractDimensions(exif)

                MediaMetadata(
                    uri = uri,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = timestamp,
                    width = width,
                    height = height
                )
            } ?: MediaMetadata(
                uri = uri,
                latitude = null,
                longitude = null,
                timestamp = null,
                error = MetadataError.IoError("Could not open input stream for $uri")
            )
        } catch (e: IOException) {
            Log.e(TAG, "Error extracting EXIF metadata for $uri", e)
            MediaMetadata(
                uri = uri,
                latitude = null,
                longitude = null,
                timestamp = null,
                error = MetadataError.IoError("IO error: ${e.message ?: "Unknown IO error"}")
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied extracting metadata for $uri", e)
            MediaMetadata(
                uri = uri,
                latitude = null,
                longitude = null,
                timestamp = null,
                error = MetadataError.SecurityError("Permission denied: ${e.message}")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error extracting metadata for $uri", e)
            MediaMetadata(
                uri = uri,
                latitude = null,
                longitude = null,
                timestamp = null,
                error = MetadataError.UnknownError("Unexpected error: ${e.message ?: "Unknown error"}", e)
            )
        }
    }

    /**
     * Extract timestamp from EXIF data
     */
    private fun extractTimestamp(exif: ExifInterface): Long? {
        // Try various date/time tags
        val dateTimeTags = listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_DATETIME
        )

        for (tag in dateTimeTags) {
            exif.getAttribute(tag)?.let { dateTimeStr ->
                parseDateTime(dateTimeStr)?.let { return it }
            }
        }

        return null
    }

    /**
     * Parse EXIF datetime string to timestamp
     * EXIF format: "yyyy:MM:dd HH:mm:ss"
     */
    private fun parseDateTime(dateTimeStr: String): Long? {
        return try {
            val formatter = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            val date: Date = formatter.parse(dateTimeStr) ?: return null
            date.time
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing datetime: $dateTimeStr", e)
            null
        }
    }

    /**
     * Extract image dimensions from EXIF
     */
    private fun extractDimensions(exif: ExifInterface): Pair<Int?, Int?> {
        val width = exif.getAttributeInt(
            ExifInterface.TAG_IMAGE_WIDTH,
            -1
        ).takeIf { it != -1 }

        val height = exif.getAttributeInt(
            ExifInterface.TAG_IMAGE_LENGTH,
            -1
        ).takeIf { it != -1 }

        return Pair(width, height)
    }

    /**
     * Batch extract metadata from multiple URIs
     */
    suspend fun extractMetadataBatch(uris: List<Uri>): List<MediaMetadata> = withContext(Dispatchers.IO) {
        uris.map { uri ->
            extractMetadata(uri)
        }
    }
}
