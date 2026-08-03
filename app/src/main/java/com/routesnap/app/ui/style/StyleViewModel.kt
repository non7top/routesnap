package com.routesnap.app.ui.style

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routesnap.app.data.audio.MusicWaveformExtractor
import com.routesnap.app.data.repository.TripRepository
import com.routesnap.app.domain.model.AspectRatio
import com.routesnap.app.domain.model.MusicTrack
import com.routesnap.app.domain.model.TemplatePreset
import com.routesnap.app.domain.model.TransitionType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MusicTrackUiState(
    val track: MusicTrack,
    val waveform: List<Float> = emptyList(),
    val isLoading: Boolean = false,
    val trackDurationMs: Long = 0,
) {
    val effectiveEndMs: Long get() = if (track.endMs > 0) track.endMs else trackDurationMs
}

@Suppress("LongParameterList")
data class StyleUiState(
    val selectedAspectRatio: AspectRatio = AspectRatio.PORTRAIT,
    val selectedTemplate: TemplatePreset = TemplatePreset.BALANCED,
    val selectedTransition: TransitionType? = null,
    val musicTracks: List<MusicTrackUiState> = emptyList(),
    val videoDurationMs: Long = 0,
    val isProcessing: Boolean = false,
) {
    val hasTracks: Boolean get() = musicTracks.isNotEmpty()
}

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
                val trackStates = trip.musicTracks.map { t -> MusicTrackUiState(track = t, isLoading = true) }
                _uiState.value =
                    StyleUiState(
                        selectedAspectRatio = trip.aspectRatio,
                        selectedTemplate = trip.template,
                        selectedTransition = trip.transitionOverride,
                        musicTracks = trackStates,
                        videoDurationMs = trip.totalDurationMs,
                    )
                trip.musicTracks.indices.forEach { i -> loadWaveform(i, trip.musicTracks[i].uri) }
            }
        }

        fun updateAspectRatio(aspectRatio: AspectRatio) {
            _uiState.update { it.copy(selectedAspectRatio = aspectRatio) }
        }

        fun updateTemplate(template: TemplatePreset) {
            _uiState.update { it.copy(selectedTemplate = template) }
        }

        fun updateTransition(transition: TransitionType?) {
            _uiState.update { it.copy(selectedTransition = transition) }
        }

        fun addMusicTrack(
            uri: Uri,
            displayName: String,
        ) {
            val newState = MusicTrackUiState(track = MusicTrack(uri = uri, displayName = displayName), isLoading = true)
            val index = _uiState.value.musicTracks.size
            _uiState.update { it.copy(musicTracks = it.musicTracks + newState) }
            loadWaveform(index, uri)
        }

        fun removeTrack(index: Int) {
            _uiState.update { state ->
                state.copy(musicTracks = state.musicTracks.toMutableList().also { it.removeAt(index) })
            }
        }

        fun setTrackTrim(
            index: Int,
            startMs: Long,
            endMs: Long,
        ) {
            _uiState.update { state ->
                val updated =
                    state.musicTracks.mapIndexed { i, ts ->
                        if (i == index) ts.copy(track = ts.track.copy(startMs = startMs, endMs = endMs)) else ts
                    }
                state.copy(musicTracks = updated)
            }
        }

        private fun loadWaveform(
            index: Int,
            uri: Uri,
        ) {
            viewModelScope.launch {
                val (amplitudes, durationMs) = MusicWaveformExtractor.extract(context, uri)
                _uiState.update { state ->
                    val updated =
                        state.musicTracks.mapIndexed { i, ts ->
                            if (i == index) {
                                val effectiveEnd = if (ts.track.endMs > 0) ts.track.endMs else durationMs
                                ts.copy(
                                    waveform = amplitudes,
                                    trackDurationMs = durationMs,
                                    isLoading = false,
                                    track = ts.track.copy(endMs = effectiveEnd),
                                )
                            } else {
                                ts
                            }
                        }
                    state.copy(musicTracks = updated)
                }
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
                val tracks = state.musicTracks.map { ts -> ts.track.copy(endMs = ts.effectiveEndMs) }
                tripRepository.updateTripStyle(
                    id,
                    TripRepository.TripStyle(
                        template = state.selectedTemplate,
                        aspectRatio = state.selectedAspectRatio,
                        transitionOverride = state.selectedTransition,
                        musicTracks = tracks,
                        musicVolumeDb = 0f,
                    ),
                )
                onReady()
            }
        }
    }
