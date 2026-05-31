package com.routesnap.app.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routesnap.app.data.repository.TripRepository
import com.routesnap.app.domain.model.TripSegment
import com.routesnap.app.domain.model.ZoomRect
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhotoReviewUiState(
    val segments: List<TripSegment> = emptyList(),
    val currentIndex: Int = 0,
    val startRect: ZoomRect = ZoomRect(0f, 0f, 1f, 1f),
    val endRect: ZoomRect = ZoomRect(0.15f, 0.1f, 0.85f, 0.9f),
    val isSaving: Boolean = false,
) {
    val current: TripSegment? get() = segments.getOrNull(currentIndex)
    val hasPrev: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() = currentIndex < segments.size - 1
}

@HiltViewModel
class PhotoReviewViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val tripRepository: TripRepository,
    ) : ViewModel() {
        private val tripId: String? = savedStateHandle["tripId"]
        private val initialSegmentId: String? = savedStateHandle["segmentId"]

        private val _uiState = MutableStateFlow(PhotoReviewUiState())
        val uiState: StateFlow<PhotoReviewUiState> = _uiState.asStateFlow()

        init {
            loadSegments()
        }

        private fun loadSegments() {
            viewModelScope.launch {
                val id = tripId ?: return@launch
                val trip = tripRepository.getTripById(id) ?: return@launch
                val photoSegments = trip.segments.filter {
                    it.uri != null && it.type == com.routesnap.app.domain.model.SegmentType.PHOTO
                }
                val initialIndex = photoSegments.indexOfFirst { it.id == initialSegmentId }
                    .coerceAtLeast(0)
                val initial = photoSegments.getOrNull(initialIndex)
                _uiState.value = PhotoReviewUiState(
                    segments = photoSegments,
                    currentIndex = initialIndex,
                    startRect = initial?.startZoomRect ?: ZoomRect(0f, 0f, 1f, 1f),
                    endRect = initial?.endZoomRect ?: defaultEndRect(initialIndex),
                )
            }
        }

        fun navigateTo(index: Int) {
            saveCurrentRects()
            val state = _uiState.value
            val next = state.segments.getOrNull(index) ?: return
            _uiState.value = state.copy(
                currentIndex = index,
                startRect = next.startZoomRect ?: ZoomRect(0f, 0f, 1f, 1f),
                endRect = next.endZoomRect ?: defaultEndRect(index),
            )
        }

        fun updateStartRect(rect: ZoomRect) {
            _uiState.value = _uiState.value.copy(startRect = rect)
        }

        fun updateEndRect(rect: ZoomRect) {
            _uiState.value = _uiState.value.copy(endRect = rect)
        }

        fun saveCurrentRects() {
            val state = _uiState.value
            val segment = state.current ?: return
            val id = tripId ?: return
            viewModelScope.launch {
                tripRepository.updateSegmentZoomRects(
                    tripId = id,
                    segmentId = segment.id,
                    startRect = state.startRect,
                    endRect = state.endRect,
                )
            }
        }

        private fun defaultEndRect(index: Int): ZoomRect {
            val presets = listOf(
                ZoomRect(0.15f, 0.1f, 0.85f, 0.9f),
                ZoomRect(0.05f, 0.05f, 0.65f, 0.95f),
                ZoomRect(0.35f, 0.05f, 0.95f, 0.95f),
                ZoomRect(0.1f, 0.15f, 0.9f, 0.85f),
            )
            return presets[index % presets.size]
        }
    }
