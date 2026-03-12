package com.routesnap.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper class for handling runtime permissions
 */
class PermissionHelper(private val context: Context) {

    /**
     * Check if media reading permissions are granted
     */
    fun hasMediaPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ - Check granular permissions
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10-12 - No permission needed for MediaStore
                true
            }
            else -> {
                // Android 9 and below - Check storage permission
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /**
     * Get required permissions based on Android version
     */
    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // No permissions needed for MediaStore on Android 10+
                emptyArray()
            }
            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}
