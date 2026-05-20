package com.routesnap.app.rendering.service

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
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
            android.util.Log.w("RenderManager", "Already rendering, ignoring start request")
            return
        }

        android.util.Log.i("RenderManager", "Starting render for trip: ${trip.id} (${trip.name})")
        _renderState.value = RenderState.Rendering(0, "Initializing...")

        try {
            val composition = buildComposition(trip)

            if (composition.sequences.isEmpty() || composition.sequences[0].editedMediaItems.isEmpty()) {
                android.util.Log.e("RenderManager", "Composition is empty for trip: ${trip.id}")
                error("Composition is empty - no valid media items found")
            }

            android.util.Log.d("RenderManager", "Output file: ${outputFile.absolutePath}")
            if (outputFile.exists()) {
                android.util.Log.w("RenderManager", "Output file already exists, deleting: ${outputFile.name}")
                outputFile.delete()
            }

            val transformerInstance = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        android.util.Log.i("RenderManager", "Rendering completed successfully: ${outputFile.name}")
                        progressJob?.cancel()
                        _renderState.value = RenderState.Completed(outputFile.absolutePath)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        android.util.Log.e("RenderManager", "Transformer error: ${exportException.message}", exportException)
                        progressJob?.cancel()
                        _renderState.value = RenderState.Failed(
                            exportException.message ?: "Unknown rendering error"
                        )
                    }
                })
                .build()

            this.transformer = transformerInstance
            transformerInstance.start(composition, outputFile.absolutePath)
            android.util.Log.i("RenderManager", "Transformer started successfully")

            // Start progress tracking
            startProgressTracking(transformerInstance)

        } catch (e: Exception) {
            android.util.Log.e("RenderManager", "Failed to start rendering: ${e.message}", e)
            _renderState.value = RenderState.Failed(e.message ?: "Failed to start rendering")
        }
    }

    /**
     * Reset the render state to Idle (useful for retries)
     */
    fun reset() {
        cancelRendering()
        _renderState.value = RenderState.Idle
    }

    private fun buildComposition(trip: TripManifest): Composition {
        var photoIndex = 0
        val editedMediaItems = trip.segments.mapNotNull { segment ->
            val uri = segment.uri ?: return@mapNotNull null
            android.util.Log.d("RenderManager", "Adding segment: ${segment.type} uri: $uri duration: ${segment.durationMs}")

            when (segment.type) {
                SegmentType.PHOTO -> {
                    val duration = if (segment.durationMs > 0) segment.durationMs else 5000L
                    android.util.Log.d("RenderManager", "PHOTO duration: $duration ms")
                    val mediaItem = MediaItem.Builder()
                        .setUri(uri)
                        .setImageDurationMs(duration)
                        .build()
                    val item = EditedMediaItem.Builder(mediaItem)
                        .setFrameRate(30)
                        .setEffects(Effects(emptyList(), listOf(kenBurnsZoom(duration, photoIndex), portraitPresentation())))
                        .build()
                    photoIndex++
                    item
                }
                SegmentType.VIDEO -> {
                    EditedMediaItem.Builder(MediaItem.fromUri(uri))
                        .setFrameRate(30)
                        .setEffects(Effects(emptyList(), listOf(portraitPresentation())))
                        .build()
                }
                SegmentType.MAP_TRAVEL -> { null }
            }
        }

        val sequence = EditedMediaItemSequence(editedMediaItems)
        return Composition.Builder(listOf(sequence)).build()
    }

    private fun portraitPresentation(): Presentation =
        Presentation.createForWidthAndHeight(1080, 1920, Presentation.LAYOUT_SCALE_TO_FIT)

    private fun kenBurnsZoom(durationMs: Long, index: Int): MatrixTransformation {
        val durationUs = durationMs * 1000L
        var startUs = -1L
        val pan = PAN_DIRECTIONS[index % PAN_DIRECTIONS.size]
        return MatrixTransformation { presentationTimeUs ->
            if (startUs < 0L) startUs = presentationTimeUs
            val elapsed = presentationTimeUs - startUs
            val progress = (elapsed.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
            val scale = 1.0f + (0.2f * progress)
            val tx = pan[0] + (pan[2] - pan[0]) * progress
            val ty = pan[1] + (pan[3] - pan[1]) * progress
            android.graphics.Matrix().apply {
                setScale(scale, scale)
                postTranslate(tx, ty)
            }
        }
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

    companion object {
        // [startX, startY, endX, endY] — pixel offsets cycling 4 diagonal directions
        private val PAN_DIRECTIONS = arrayOf(
            floatArrayOf(-40f, -40f,  40f,  40f),  // TL→BR
            floatArrayOf( 40f, -40f, -40f,  40f),  // TR→BL
            floatArrayOf(-40f,  40f,  40f, -40f),  // BL→TR
            floatArrayOf( 40f,  40f, -40f, -40f),  // BR→TL
        )
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
