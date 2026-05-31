package com.routesnap.app.ui.style

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

        fun updateAspectRatio(aspectRatio: AspectRatio) {
            _uiState.value = _uiState.value.copy(selectedAspectRatio = aspectRatio)
        }

        fun updateTemplate(template: TemplatePreset) {
            _uiState.value = _uiState.value.copy(selectedTemplate = template)
        }

        fun updateTransition(transition: TransitionType?) {
            _uiState.value = _uiState.value.copy(selectedTransition = transition)
        }

        fun selectMusic() {
            _uiState.value =
                _uiState.value.copy(
                    musicSelected = !_uiState.value.musicSelected,
                    musicTitle = if (!_uiState.value.musicSelected) "Default Track" else null,
                )
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
                    _uiState.value.selectedTemplate,
                    _uiState.value.selectedAspectRatio,
                    _uiState.value.selectedTransition,
                )
                onReady()
            }
        }
    }
