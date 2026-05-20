package com.routesnap.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream

/**
 * Helper class for managing file storage
 */
class StorageHelper(private val context: Context) {

    /**
     * Get the app's output directory for rendered videos
     * Uses external files dir which is app-specific and doesn't require storage permissions
     */
    fun getOutputDirectory(): File {
        // Use app-specific external directory (no permissions needed on Android 10+)
        val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir

        val appDir = File(externalDir, APP_DIR)
        val outputDir = File(appDir, OUTPUT_DIR)

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        return outputDir
    }

    /**
     * Create a unique output file for rendered video
     */
    fun createOutputFile(fileName: String): File {
        val outputDir = getOutputDirectory()
        val safeFileName = fileName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(100) // Limit filename length

        return File(outputDir, "${safeFileName}_${System.currentTimeMillis()}.mp4")
    }

    /**
     * Save a video file to the public Gallery/Movies collection using MediaStore
     */
    fun saveVideoToGallery(videoFile: File, displayName: String): Boolean {
        return try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RouteSnap")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: run {
                    Log.e(TAG, "Failed to create MediaStore entry")
                    return false
                }
            copyFileToUri(videoFile, uri)
            contentValues.clear()
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            Log.d(TAG, "Successfully saved video to gallery: $uri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video to gallery", e)
            false
        }
    }

    private fun copyFileToUri(src: File, uri: android.net.Uri) {
        context.contentResolver.openOutputStream(uri).use { out ->
            FileInputStream(src).use { it.copyTo(out!!) }
        }
    }

    /**
     * Check if there's enough storage space
     * @param requiredBytes Minimum required bytes
     * @return true if enough space is available
     */
    fun hasEnoughStorage(requiredBytes: Long): Boolean {
        val outputDir = getOutputDirectory()
        val usableSpace = outputDir.usableSpace

        if (usableSpace < requiredBytes) {
            Log.w(
                TAG,
                "Insufficient storage: need ${requiredBytes / 1024 / 1024}MB, " +
                        "have ${usableSpace / 1024 / 1024}MB"
            )
            return false
        }

        return true
    }

    /**
     * Get available storage space in MB
     */
    fun getAvailableStorageMB(): Long {
        val outputDir = getOutputDirectory()
        return outputDir.usableSpace / 1024 / 1024
    }

    /**
     * Clean up old output files older than specified days
     */
    fun cleanupOldFiles(daysToKeep: Int = 7) {
        val outputDir = getOutputDirectory()
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)

        outputDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    Log.d(TAG, "Deleted old file: ${file.name}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "StorageHelper"
        private const val APP_DIR = "RouteSnap"
        private const val OUTPUT_DIR = "output"
    }
}
