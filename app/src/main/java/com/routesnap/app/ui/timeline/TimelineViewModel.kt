package com.routesnap.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routesnap.app.data.repository.TripRepository
import com.routesnap.app.domain.model.TripSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the timeline screen
 */
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    /**
     * Load a trip by ID
     */
    fun loadTrip(tripId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val trip = tripRepository.getTripById(tripId)
                if (trip != null) {
                    val clusters = trip.clusters.map { cluster ->
                        val segmentIndices = trip.segments.mapIndexedNotNull { index, segment ->
                            if (segment.clusterId == cluster.id) index else null
                        }
                        TimelineCluster(
                            id = cluster.id,
                            name = cluster.name,
                            segmentIndices = segmentIndices
                        )
                    }

                    _uiState.value = TimelineUiState(
                        segments = trip.segments,
                        clusters = clusters,
                        tripId = tripId,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Trip not found",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to load trip",
                    isLoading = false
                )
            }
        }
    }

    /**
     * Remove a segment from the timeline
     */
    fun removeSegment(segment: TripSegment) {
        val currentSegments = _uiState.value.segments
        val updatedSegments = currentSegments - segment

        // Rebuild clusters
        val clusterMap = updatedSegments
            .filter { it.clusterId != null }
            .groupBy { it.clusterId!! }

        val clusters = clusterMap.map { (clusterId, segments) ->
            val segmentIndices = updatedSegments.mapIndexedNotNull { index, seg ->
                if (seg.clusterId == clusterId) index else null
            }
            TimelineCluster(
                id = clusterId,
                name = "Stop ${clusterMap.keys.indexOf(clusterId) + 1}",
                segmentIndices = segmentIndices
            )
        }

        _uiState.value = _uiState.value.copy(
            segments = updatedSegments,
            clusters = clusters
        )
    }

    /**
     * Move a segment up in the timeline
     */
    fun moveSegmentUp(index: Int) {
        if (index <= 0) return

        val segments = _uiState.value.segments.toMutableList()
        val temp = segments[index]
        segments[index] = segments[index - 1]
        segments[index - 1] = temp

        _uiState.value = _uiState.value.copy(segments = segments)
    }

    /**
     * Move a segment down in the timeline
     */
    fun moveSegmentDown(index: Int) {
        val segments = _uiState.value.segments
        if (index >= segments.size - 1) return

        val mutableSegments = segments.toMutableList()
        val temp = mutableSegments[index]
        mutableSegments[index] = mutableSegments[index + 1]
        mutableSegments[index + 1] = temp

        _uiState.value = _uiState.value.copy(segments = mutableSegments)
    }
}
