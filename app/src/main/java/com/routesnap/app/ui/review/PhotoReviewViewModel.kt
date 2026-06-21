package com.routesnap.app.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routesnap.app.data.repository.TripRepository
import com.routesnap.app.domain.model.TripSegment
import com.routesnap.app.domain.model.ZoomRect
import com.routesnap.app.domain.model.ZoomRect.Companion.snapToPortraitCrop
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

        private fun rectsFor(segment: TripSegment, index: Int): Pair<ZoomRect, ZoomRect> {
            val aspect = segment.photoAspectRatio ?: 1f
            return if (aspect > 1f) {
                val (defS, defE) = ZoomRect.defaultLandscapePair(index, aspect)
                val s = segment.startZoomRect?.snapToPortraitCrop(aspect) ?: defS
                val e = segment.endZoomRect?.snapToPortraitCrop(aspect) ?: defE
                Pair(s, e)
            } else {
                val (defS, defE) = ZoomRect.defaultPair(index)
                Pair(segment.startZoomRect ?: defS, segment.endZoomRect ?: defE)
            }
        }

        private fun loadSegments() {
            viewModelScope.launch {
                val id = tripId ?: return@launch
                val trip = tripRepository.getTripById(id) ?: return@launch
                val photoSegments =
                    trip.segments.filter {
                        it.uri != null && it.type == com.routesnap.app.domain.model.SegmentType.PHOTO
                    }
                val initialIndex =
                    photoSegments
                        .indexOfFirst { it.id == initialSegmentId }
                        .coerceAtLeast(0)
                val initial = photoSegments.getOrNull(initialIndex)
                val (startRect, endRect) =
                    if (initial != null) rectsFor(initial, initialIndex) else ZoomRect.defaultPair(0)
                _uiState.value =
                    PhotoReviewUiState(
                        segments = photoSegments,
                        currentIndex = initialIndex,
                        startRect = startRect,
                        endRect = endRect,
                    )
            }
        }

        fun navigateTo(index: Int) {
            saveCurrentRects()
            val state = _uiState.value
            val next = state.segments.getOrNull(index) ?: return
            val (startRect, endRect) = rectsFor(next, index)
            _uiState.value = state.copy(currentIndex = index, startRect = startRect, endRect = endRect)
        }

        fun updateStartRect(rect: ZoomRect) {
            val aspect = _uiState.value.current?.photoAspectRatio ?: 1f
            val snapped = if (aspect > 1f) rect.snapToPortraitCrop(aspect) else rect
            _uiState.value = _uiState.value.copy(startRect = snapped)
        }

        fun updateEndRect(rect: ZoomRect) {
            val aspect = _uiState.value.current?.photoAspectRatio ?: 1f
            val snapped = if (aspect > 1f) rect.snapToPortraitCrop(aspect) else rect
            _uiState.value = _uiState.value.copy(endRect = snapped)
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
    }
