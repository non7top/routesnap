package com.routesnap.app.rendering.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.routesnap.app.data.repository.TripRepository
import com.routesnap.app.domain.model.SegmentType
import com.routesnap.app.domain.model.TripManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages video rendering using Media3 Transformer
 */
@UnstableApi
@Singleton
class RenderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripRepository: TripRepository,
) {
    private val _renderState = MutableStateFlow<RenderState>(RenderState.Idle)
    val renderState: StateFlow<RenderState> = _renderState.asStateFlow()

    private var transformer: Transformer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    /**
     * Start rendering a trip video
     */
    fun startRendering(trip: TripManifest, outputFile: File) {
        if (_renderState.value is RenderState.Rendering) {
            return
        }

        _renderState.value = RenderState.Rendering(0, "Initializing...")

        try {
            val composition = buildComposition(trip)
            val transformerInstance = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        progressJob?.cancel()
                        _renderState.value = RenderState.Completed(outputFile.absolutePath)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        progressJob?.cancel()
                        _renderState.value = RenderState.Failed(
                            exportException.message ?: "Unknown rendering error"
                        )
                    }
                })
                .build()

            this.transformer = transformerInstance
            transformerInstance.start(composition, outputFile.absolutePath)

            // Start progress tracking
            startProgressTracking(transformerInstance)

        } catch (e: Exception) {
            _renderState.value = RenderState.Failed(e.message ?: "Failed to start rendering")
        }
    }

    private fun buildComposition(trip: TripManifest): Composition {
        val editedMediaItems = trip.segments.mapNotNull { segment ->
            val uri = segment.uri ?: return@mapNotNull null

            val mediaItem = when (segment.type) {
                SegmentType.PHOTO -> {
                    MediaItem.Builder()
                        .setUri(uri)
                        .setImageDurationMs(segment.durationMs)
                        .build()
                }
                SegmentType.VIDEO -> {
                    // For now, take the full video or a highlight
                    // Trimming will be implemented in later subtasks
                    MediaItem.fromUri(uri)
                }
                SegmentType.MAP_TRAVEL -> {
                    // Map travel will be implemented in #66
                    // For now, skip or use a placeholder
                    return@mapNotNull null
                }
            }

            EditedMediaItem.Builder(mediaItem).build()
        }

        val sequence = EditedMediaItemSequence(editedMediaItems)
        return Composition.Builder(listOf(sequence)).build()
    }

    private fun startProgressTracking(transformer: Transformer) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val progressHolder = androidx.media3.transformer.ProgressHolder()
                val progressState = transformer.getProgress(progressHolder)

                if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val currentRendering = _renderState.value as? RenderState.Rendering
                    if (currentRendering != null) {
                        _renderState.value = RenderState.Rendering(
                            progressHolder.progress,
                            "Exporting video..."
                        )
                    }
                }

                if (progressState == Transformer.PROGRESS_STATE_NOT_STARTED ||
                    progressState == Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
                    // Do nothing or wait
                }

                delay(500) // Update progress every 500ms
            }
        }
    }

    /**
     * Cancel the current rendering operation
     */
    fun cancelRendering() {
        transformer?.cancel()
        progressJob?.cancel()
        transformer = null
        _renderState.value = RenderState.Cancelled
    }

    /**
     * Sealed class representing render states
     */
    sealed class RenderState {
        object Idle : RenderState()
        data class Rendering(val progress: Int, val status: String) : RenderState()
        data class Completed(val outputPath: String) : RenderState()
        data class Failed(val error: String) : RenderState()
        object Cancelled : RenderState()
    }
}
