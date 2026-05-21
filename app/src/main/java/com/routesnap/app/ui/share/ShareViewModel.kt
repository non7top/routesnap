package com.routesnap.app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routesnap.app.util.StorageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ShareUiState(
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val videoFile: File? = null,
)

@HiltViewModel
class ShareViewModel
    @Inject
    constructor(
        private val storageHelper: StorageHelper,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ShareUiState())
        val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

        fun setVideoPath(path: String?) {
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    _uiState.value = _uiState.value.copy(videoFile = file)
                }
            }
        }

        fun saveToGallery() {
            val videoFile = _uiState.value.videoFile
            if (videoFile == null || !videoFile.exists()) {
                _uiState.value = _uiState.value.copy(error = "Video file not found")
                return
            }

            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            viewModelScope.launch {
                val success =
                    withContext(Dispatchers.IO) {
                        storageHelper.saveVideoToGallery(
                            videoFile,
                            "RouteSnap_${videoFile.nameWithoutExtension}",
                        )
                    }

                if (success) {
                    _uiState.value =
                        _uiState.value.copy(
                            isSaving = false,
                            saveSuccess = true,
                        )
                } else {
                    _uiState.value =
                        _uiState.value.copy(
                            isSaving = false,
                            error = "Failed to save to gallery",
                        )
                }
            }
        }

        fun onToastShown() {
            _uiState.value = _uiState.value.copy(saveSuccess = false, error = null)
        }
    }
