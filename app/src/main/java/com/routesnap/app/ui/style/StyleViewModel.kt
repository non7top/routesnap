package com.routesnap.app.ui.style

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routesnap.app.data.repository.TripRepository
import com.routesnap.app.domain.model.AspectRatio
import com.routesnap.app.domain.model.TemplatePreset
import com.routesnap.app.domain.model.TransitionType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class StyleViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val tripRepository: TripRepository,
    ) : ViewModel() {
        private val tripId: String? = savedStateHandle["tripId"]

        private val _uiState = MutableStateFlow(StyleUiState())
        val uiState: StateFlow<StyleUiState> = _uiState.asStateFlow()

        init {
            if (tripId != null) loadExistingStyle(tripId)
        }

        private fun loadExistingStyle(id: String) {
            viewModelScope.launch {
                val trip = tripRepository.getTripById(id) ?: return@launch
                _uiState.value =
                    StyleUiState(
                        selectedAspectRatio = trip.aspectRatio,
                        selectedTemplate = trip.template,
                        selectedTransition = trip.transitionOverride,
                        musicUri = trip.musicUri,
                        musicTitle = trip.musicUri?.lastPathSegment?.substringAfterLast('/'),
                    )
            }
        }

        fun updateAspectRatio(aspectRatio: AspectRatio) {
            _uiState.value = _uiState.value.copy(selectedAspectRatio = aspectRatio)
        }

        fun updateTemplate(template: TemplatePreset) {
            _uiState.value = _uiState.value.copy(selectedTemplate = template)
        }

        fun updateTransition(transition: TransitionType?) {
            _uiState.value = _uiState.value.copy(selectedTransition = transition)
        }

        fun setMusicUri(
            uri: Uri,
            displayName: String,
        ) {
            _uiState.value = _uiState.value.copy(musicUri = uri, musicTitle = displayName)
        }

        fun removeMusic() {
            _uiState.value = _uiState.value.copy(musicUri = null, musicTitle = null)
        }

        fun saveAndRender(onReady: () -> Unit) {
            val id =
                tripId ?: run {
                    onReady()
                    return
                }
            viewModelScope.launch {
                tripRepository.updateTripStyle(
                    id,
                    TripRepository.TripStyle(
                        template = _uiState.value.selectedTemplate,
                        aspectRatio = _uiState.value.selectedAspectRatio,
                        transitionOverride = _uiState.value.selectedTransition,
                        musicUri = _uiState.value.musicUri,
                        musicVolumeDb = 0f,
                    ),
                )
                onReady()
            }
        }
    }
