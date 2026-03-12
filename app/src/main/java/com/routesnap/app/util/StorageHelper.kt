package com.routesnap.app.util

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Helper class for managing file storage
 */
class StorageHelper(private val context: Context) {

    companion object {
        private const val TAG = "StorageHelper"
        private const val APP_DIR = "RouteSnap"
        private const val OUTPUT_DIR = "output"
    }

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

        return File(outputDir, "$safeFileName.mp4")
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
}
