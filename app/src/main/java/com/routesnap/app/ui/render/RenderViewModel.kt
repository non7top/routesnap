package com.routesnap.app.ui.render

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routesnap.app.data.repository.TripRepository
import com.routesnap.app.util.StorageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the render screen
 */
@HiltViewModel
class RenderViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val storageHelper: StorageHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(RenderUiState())
    val uiState: StateFlow<RenderUiState> = _uiState.asStateFlow()

    private var renderJob: Job? = null

    /**
     * Start rendering the video
     */
    fun startRendering(tripId: String) {
        // Cancel any existing render job
        renderJob?.cancel()

        renderJob = viewModelScope.launch {
            _uiState.value = RenderUiState(
                isRendering = true,
                progress = 0,
                status = "Initializing..."
            )

            try {
                // Simulate rendering progress (placeholder for actual Media3 implementation)
                val trip = tripRepository.getTripById(tripId)
                if (trip == null) {
                    _uiState.value = RenderUiState(
                        isRendering = false,
                        error = "Trip not found"
                    )
                    return@launch
                }

                // Phase 1: Preparing
                _uiState.value = _uiState.value.copy(
                    status = "Preparing assets...",
                    progress = 10
                )
                delay(500)

                // Phase 2: Processing photos
                _uiState.value = _uiState.value.copy(
                    status = "Processing photos...",
                    progress = 25
                )
                delay(800)

                // Phase 3: Generating map animations
                _uiState.value = _uiState.value.copy(
                    status = "Generating map animations...",
                    progress = 50
                )
                delay(1000)

                // Phase 4: Compositing video
                _uiState.value = _uiState.value.copy(
                    status = "Compositing video...",
                    progress = 75
                )
                delay(800)

                // Phase 5: Finalizing
                _uiState.value = _uiState.value.copy(
                    status = "Finalizing...",
                    progress = 90
                )
                delay(400)

                // Complete - use proper storage API instead of hardcoded path
                val outputFile = storageHelper.createOutputFile(trip.name)
                _uiState.value = RenderUiState(
                    isRendering = false,
                    isComplete = true,
                    progress = 100,
                    status = "Rendering Complete!",
                    outputPath = outputFile.absolutePath
                )

                // Update trip status in repository
                tripRepository.updateTripStatus(tripId, com.routesnap.app.domain.model.RenderStatus.COMPLETED)

            } catch (e: kotlinx.coroutines.CancellationException) {
                // Render was cancelled
                _uiState.value = RenderUiState(
                    isRendering = false,
                    error = null,
                    status = "Rendering cancelled",
                )
                @Suppress("RethrowCaughtException")
                throw e // Re-throw to properly cancel coroutine
            } catch (e: Exception) {
                _uiState.value = RenderUiState(
                    isRendering = false,
                    error = e.message ?: "Rendering failed"
                )
            }
        }
    }

    /**
     * Cancel the rendering process
     */
    fun cancelRendering() {
        renderJob?.cancel()
        renderJob = null
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel render job when ViewModel is cleared
        renderJob?.cancel()
    }
}
