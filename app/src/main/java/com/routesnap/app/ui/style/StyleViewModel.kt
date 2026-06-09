package com.routesnap.app.ui.style

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routesnap.app.data.audio.MusicWaveformExtractor
import com.routesnap.app.data.repository.TripRepository
import com.routesnap.app.domain.model.AspectRatio
import com.routesnap.app.domain.model.TemplatePreset
import com.routesnap.app.domain.model.TransitionType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
        @ApplicationContext private val context: Context,
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
                        musicStartMs = trip.musicStartMs,
                        musicEndMs = trip.musicEndMs,
                        musicFadeInMs = trip.musicFadeInMs,
                        musicFadeOutMs = trip.musicFadeOutMs,
                        videoDurationMs = trip.totalDurationMs,
                    )
                trip.musicUri?.let { loadWaveform(it) }
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
            _uiState.value =
                _uiState.value.copy(
                    musicUri = uri,
                    musicTitle = displayName,
                    musicStartMs = 0,
                    musicEndMs = null,
                    waveform = emptyList(),
                    isLoadingWaveform = true,
                )
            loadWaveform(uri)
        }

        fun removeMusic() {
            _uiState.value =
                _uiState.value.copy(
                    musicUri = null,
                    musicTitle = null,
                    waveform = emptyList(),
                    trackDurationMs = 0,
                    musicStartMs = 0,
                    musicEndMs = null,
                )
        }

        fun setMusicTrim(
            startMs: Long,
            endMs: Long,
        ) {
            _uiState.value = _uiState.value.copy(musicStartMs = startMs, musicEndMs = endMs)
        }

        private fun loadWaveform(uri: Uri) {
            viewModelScope.launch {
                val (amplitudes, durationMs) = MusicWaveformExtractor.extract(context, uri)
                val current = _uiState.value
                val effectiveEndMs = current.musicEndMs ?: durationMs
                _uiState.value =
                    current.copy(
                        waveform = amplitudes,
                        trackDurationMs = durationMs,
                        isLoadingWaveform = false,
                        musicEndMs = if (current.musicEndMs == null) durationMs else effectiveEndMs,
                    )
            }
        }

        fun saveAndRender(onReady: () -> Unit) {
            val id =
                tripId ?: run {
                    onReady()
                    return
                }
            viewModelScope.launch {
                val state = _uiState.value
                tripRepository.updateTripStyle(
                    id,
                    TripRepository.TripStyle(
                        template = state.selectedTemplate,
                        aspectRatio = state.selectedAspectRatio,
                        transitionOverride = state.selectedTransition,
                        musicUri = state.musicUri,
                        musicVolumeDb = 0f,
                        musicStartMs = state.musicStartMs,
                        musicEndMs = state.musicEndMs,
                        musicFadeInMs = state.musicFadeInMs,
                        musicFadeOutMs = state.musicFadeOutMs,
                    ),
                )
                onReady()
            }
        }
    }
