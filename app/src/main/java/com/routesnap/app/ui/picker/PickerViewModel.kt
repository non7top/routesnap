package com.routesnap.app.ui.picker

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routesnap.app.data.repository.TripRepository
import com.routesnap.app.domain.model.TripManifest
import com.routesnap.app.domain.model.TripSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for the photo picker screen
 */
data class PickerUiState(
    val selectedUris: List<Uri> = emptyList(),
    val metadata: List<SelectedMediaMetadata> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val tripName: String = "",
    val segments: List<TripSegment> = emptyList(),
    val clusterCount: Int = 0,
    val estimatedDurationSeconds: Int = 0,
    val photosWithGps: Int = 0,
    val totalPhotos: Int = 0
) {
    val gpsPercentage: Float get() = if (totalPhotos > 0) photosWithGps.toFloat() / totalPhotos else 0f
    
    // Map URIs to cluster IDs for coloring
    val uriToClusterMap: Map<Uri, String?> by lazy {
        segments.associate { it.uri to it.clusterId }
    }
}

data class SelectedMediaMetadata(
    val uri: Uri,
    val hasLocation: Boolean,
    val timestamp: Long?,
    val clusterId: String? = null
)

/**
 * ViewModel for the photo picker screen
 */
@HiltViewModel
class PickerViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PickerUiState())
    val uiState: StateFlow<PickerUiState> = _uiState.asStateFlow()

    /**
     * Add selected URIs from Photo Picker
     */
    fun addSelectedUris(uris: List<Uri>) {
        val currentUris = _uiState.value.selectedUris
        val updatedUris = currentUris + uris
        _uiState.value = _uiState.value.copy(
            selectedUris = updatedUris.distinct()
        )
        extractMetadataForSelected()
    }

    /**
     * Remove a URI from selection
     */
    fun removeUri(uri: Uri) {
        val updatedUris = _uiState.value.selectedUris - uri
        _uiState.value = _uiState.value.copy(
            selectedUris = updatedUris
        )
        extractMetadataForSelected()
    }

    /**
     * Clear all selections
     */
    fun clearSelection() {
        _uiState.value = PickerUiState()
    }

    /**
     * Update trip name
     */
    fun updateTripName(name: String) {
        _uiState.value = _uiState.value.copy(tripName = name)
    }

    /**
     * Extract metadata and create clusters
     */
    private fun extractMetadataForSelected() {
        val uris = _uiState.value.selectedUris
        if (uris.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                metadata = emptyList(),
                segments = emptyList(),
                clusterCount = 0,
                estimatedDurationSeconds = 0,
                photosWithGps = 0,
                totalPhotos = 0
            )
            return
        }

        _uiState.value = _uiState.value.copy(isProcessing = true)

        viewModelScope.launch {
            try {
                // Extract metadata from URIs
                val metadataList = tripRepository.extractMetadataBatch(uris)
                
                // Create segment to cluster mapping
                val segments = tripRepository.processSelectedMedia(uris)
                val uriToClusterMap = segments.associate { it.uri to it.clusterId }
                
                // Convert to UI metadata with cluster info
                val metadata = metadataList.map { m ->
                    SelectedMediaMetadata(
                        uri = m.uri,
                        hasLocation = m.hasLocation,
                        timestamp = m.timestamp,
                        clusterId = uriToClusterMap[m.uri]
                    )
                }

                val clusterCount = segments.mapNotNull { it.clusterId }.distinct().size
                val totalDurationMs = segments.sumOf { it.durationMs }
                val photosWithGps = metadataList.count { it.hasLocation }

                _uiState.value = _uiState.value.copy(
                    metadata = metadata,
                    segments = segments,
                    clusterCount = clusterCount,
                    estimatedDurationSeconds = (totalDurationMs / 1000).toInt(),
                    photosWithGps = photosWithGps,
                    totalPhotos = uris.size,
                    isProcessing = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unknown error",
                    isProcessing = false
                )
            }
        }
    }

    /**
     * Create the trip and save to database
     */
    suspend fun createTrip(): TripManifest? {
        val uris = _uiState.value.selectedUris
        val name = _uiState.value.tripName.ifEmpty { "New Trip" }

        if (uris.isEmpty()) {
            return null
        }

        return try {
            tripRepository.createTripFromMedia(name, uris)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to create trip")
            null
        }
    }
}
