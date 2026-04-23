package com.routesnap.app.ui.style

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routesnap.app.domain.model.AspectRatio
import com.routesnap.app.domain.model.TemplatePreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the style screen
 */
@HiltViewModel
class StyleViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(StyleUiState())
    val uiState: StateFlow<StyleUiState> = _uiState.asStateFlow()

    /**
     * Update the selected aspect ratio
     */
    fun updateAspectRatio(aspectRatio: AspectRatio) {
        _uiState.value = _uiState.value.copy(
            selectedAspectRatio = aspectRatio
        )
    }

    /**
     * Update the selected template preset
     */
    fun updateTemplate(template: TemplatePreset) {
        _uiState.value = _uiState.value.copy(
            selectedTemplate = template
        )
    }

    /**
     * Select music (placeholder for now)
     */
    fun selectMusic() {
        // Placeholder: Implement music picker in Phase 2
        _uiState.value = _uiState.value.copy(
            musicSelected = !_uiState.value.musicSelected,
            musicTitle = if (!_uiState.value.musicSelected) "Default Track" else null
        )
    }
}
