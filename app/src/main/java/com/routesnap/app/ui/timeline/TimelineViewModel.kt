package com.routesnap.app.ui.timeline

import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routesnap.app.data.repository.TripRepository
import com.routesnap.app.domain.model.SegmentType
import com.routesnap.app.domain.model.TripSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the timeline screen
 */
@HiltViewModel
class TimelineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripRepository: TripRepository,
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
                    val dateFmt = SimpleDateFormat("MMM d", Locale.getDefault())
                    val clusters = trip.clusters.map { cluster ->
                        val segmentIndices = trip.segments.mapIndexedNotNull { index, segment ->
                            if (segment.clusterId == cluster.id) index else null
                        }
                        val firstTimestamp = segmentIndices.firstNotNullOfOrNull {
                            trip.segments.getOrNull(it)?.timestamp
                        }
                        TimelineCluster(
                            id = cluster.id,
                            name = cluster.name,
                            segmentIndices = segmentIndices,
                            dateLabel = firstTimestamp?.let { dateFmt.format(Date(it)) },
                        )
                    }

                    _uiState.value = TimelineUiState(
                        segments = trip.segments,
                        clusters = clusters,
                        tripId = tripId,
                        isLoading = false
                    )

                    geocodeSegments(trip.segments)
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

    private fun geocodeSegments(segments: List<TripSegment>) {
        if (!Geocoder.isPresent()) return
        viewModelScope.launch {
            val geocoder = Geocoder(context, Locale.getDefault())
            val coordCache = mutableMapOf<String, String>()
            val locations = mutableMapOf<String, String>()

            segments
                .filter { it.type != SegmentType.MAP_TRAVEL && it.startCoord != null }
                .forEach { segment ->
                    val coord = segment.startCoord!!
                    val cacheKey = "%.3f,%.3f".format(coord.latitude, coord.longitude)
                    val name = coordCache.getOrPut(cacheKey) {
                        withContext(Dispatchers.IO) {
                            try {
                                @Suppress("DEPRECATION")
                                val address = geocoder.getFromLocation(coord.latitude, coord.longitude, 1)?.firstOrNull()
                                val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea
                                val country = address?.countryName
                                listOfNotNull(city, country).joinToString(", ")
                            } catch (e: Exception) {
                                android.util.Log.w("TimelineViewModel", "Geocoding failed for $cacheKey", e)
                                ""
                            }
                        }
                    }
                    if (name.isNotEmpty()) locations[segment.id] = name
                }

            val updatedClusters = _uiState.value.clusters.map { cluster ->
                val firstLocation = cluster.segmentIndices.firstNotNullOfOrNull { idx ->
                    _uiState.value.segments.getOrNull(idx)?.id?.let { locations[it] }
                }
                cluster.copy(locationName = firstLocation)
            }
            _uiState.value = _uiState.value.copy(
                segmentLocations = locations,
                clusters = updatedClusters,
            )
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
