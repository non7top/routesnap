package com.routesnap.app.ui.render

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.routesnap.app.rendering.service.RenderForegroundService
import com.routesnap.app.rendering.service.RenderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel for the render screen
 */
@UnstableApi
@HiltViewModel
class RenderViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val renderManager: RenderManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RenderUiState())
        val uiState: StateFlow<RenderUiState> = _uiState.asStateFlow()

        private var observationJob: Job? = null

        init {
            observeRenderState()
        }

        private fun observeRenderState() {
            observationJob?.cancel()
            observationJob =
                renderManager.renderState
                    .onEach { state ->
                        when (state) {
                            is RenderManager.RenderState.Idle -> {
                                _uiState.value = RenderUiState()
                            }

                            is RenderManager.RenderState.Rendering -> {
                                _uiState.value =
                                    RenderUiState(
                                        isRendering = true,
                                        progress = state.progress,
                                        status = state.status,
                                    )
                            }

                            is RenderManager.RenderState.Completed -> {
                                _uiState.value =
                                    RenderUiState(
                                        isRendering = false,
                                        isComplete = true,
                                        progress = 100,
                                        status = "Rendering Complete!",
                                        outputPath = state.outputPath,
                                    )
                            }

                            is RenderManager.RenderState.Failed -> {
                                _uiState.value =
                                    RenderUiState(
                                        isRendering = false,
                                        error = state.error,
                                        status = "Rendering Failed",
                                    )
                            }

                            is RenderManager.RenderState.Cancelled -> {
                                _uiState.value =
                                    RenderUiState(
                                        isRendering = false,
                                        status = "Rendering Cancelled",
                                    )
                            }
                        }
                    }.launchIn(viewModelScope)
        }

        /**
         * Start rendering the video
         */
        fun startRendering(tripId: String) {
            val currentState = renderManager.renderState.value
            if (currentState is RenderManager.RenderState.Rendering) {
                return
            }

            // If we are in a terminal state, reset first
            if (currentState is RenderManager.RenderState.Failed ||
                currentState is RenderManager.RenderState.Completed ||
                currentState is RenderManager.RenderState.Cancelled
            ) {
                renderManager.reset()
            }

            RenderForegroundService.start(context, tripId)
        }

        /**
         * Explicit retry function
         */
        fun retryRendering(tripId: String) {
            renderManager.reset()
            RenderForegroundService.start(context, tripId)
        }

        /**
         * Cancel the rendering process
         */
        fun cancelRendering() {
            renderManager.cancelRendering()
        }

        override fun onCleared() {
            super.onCleared()
            observationJob?.cancel()
        }
    }
