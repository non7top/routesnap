package com.routesnap.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.routesnap.app.domain.model.RenderStatus

/**
 * Room entity for persisting trip manifests
 */
@Entity(tableName = "trip_manifests")
data class TripManifestEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long,
    val jsonData: String, // Serialized TripManifest
    val status: RenderStatus,
    val outputPath: String? = null
)
