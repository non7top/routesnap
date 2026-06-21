package com.routesnap.app.rendering.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.routesnap.app.domain.model.AspectRatio
import com.routesnap.app.domain.model.MusicTrack
import com.routesnap.app.domain.model.SegmentType
import com.routesnap.app.domain.model.TemplatePreset
import com.routesnap.app.domain.model.TransitionType
import com.routesnap.app.domain.model.TripManifest
import com.routesnap.app.domain.model.TripSegment
import com.routesnap.app.domain.model.ZoomRect
import com.routesnap.app.rendering.audio.FadeAudioProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages video rendering using Media3 Transformer
 */
@UnstableApi
@Singleton
class RenderManager
    @Inject
    constructor(
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
        fun startRendering(
            trip: TripManifest,
            outputFile: File,
        ) {
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

                val transformerInstance =
                    Transformer
                        .Builder(context)
                        .addListener(
                            object : Transformer.Listener {
                                override fun onCompleted(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                ) {
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
                                    _renderState.value =
                                        RenderState.Failed(
                                            exportException.message ?: "Unknown rendering error",
                                        )
                                }
                            },
                        ).build()

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

        private data class TransitionParams(
            val type: TransitionType,
            val durationMs: Long,
        )

        private fun effectiveTransition(
            segment: TripSegment,
            trip: TripManifest,
        ): TransitionParams =
            TransitionParams(
                type = segment.transitionType ?: trip.transitionOverride ?: trip.template.defaultTransitionType,
                durationMs = segment.transitionDurationMs ?: trip.template.defaultTransitionDurationMs,
            )

        private fun buildComposition(trip: TripManifest): Composition {
            var photoIndex = 0
            val editedMediaItems =
                trip.segments.mapNotNull { segment ->
                    val uri = segment.uri ?: return@mapNotNull null
                    android.util.Log.d("RenderManager", "Adding segment: ${segment.type} uri: $uri duration: ${segment.durationMs}")
                    val transition = effectiveTransition(segment, trip)
                    when (segment.type) {
                        SegmentType.PHOTO -> {
                            buildPhotoSegment(segment, photoIndex, transition)
                                .also { photoIndex++ }
                        }

                        SegmentType.VIDEO -> {
                            buildVideoSegment(uri, transition)
                        }

                        SegmentType.MAP_TRAVEL -> {
                            buildMapTravelSegment(segment, trip)
                        }
                    }
                }
            val mainSequence = EditedMediaItemSequence(editedMediaItems)
            val musicSeq = if (trip.musicTracks.isNotEmpty()) buildMusicSequence(trip) else null
            val sequences = if (musicSeq != null) listOf(mainSequence, musicSeq) else listOf(mainSequence)
            return Composition
                .Builder(sequences)
                .build()
        }

        /**
         * Builds a music sequence from the playlist:
         *  - each track plays its trimmed region once, with fadeIn/fadeOut
         *  - the last track loops to fill the remaining video duration
         */
        private fun buildMusicSequence(trip: TripManifest): EditedMediaItemSequence {
            val videoMs = trip.totalDurationMs.coerceAtLeast(1L)
            val tracks = trip.musicTracks
            val items = mutableListOf<EditedMediaItem>()

            val allButLast = tracks.dropLast(1)
            allButLast.forEach { track -> items.add(buildTrackItem(track, track.clipMs, track.fadeInMs, track.fadeOutMs)) }

            val lastTrack = tracks.last()
            val usedMs = allButLast.sumOf { it.clipMs }
            val remainingMs = (videoMs - usedMs).coerceAtLeast(1L)
            val lastClipMs = lastTrack.clipMs
            val repeatCount = ((remainingMs + lastClipMs - 1) / lastClipMs).toInt().coerceAtLeast(1)

            repeat(repeatCount) { index ->
                val isFirst = index == 0
                val isLast = index == repeatCount - 1
                val thisMs =
                    if (isLast) {
                        (remainingMs - index.toLong() * lastClipMs).coerceIn(1L, lastClipMs)
                    } else {
                        lastClipMs
                    }
                val fadeIn = if (isFirst) lastTrack.fadeInMs else 0L
                val fadeOut = if (isLast) lastTrack.fadeOutMs else 0L
                items.add(buildTrackItem(lastTrack, thisMs, fadeIn, fadeOut))
            }

            return EditedMediaItemSequence(items)
        }

        private fun buildTrackItem(
            track: MusicTrack,
            clipMs: Long,
            fadeIn: Long,
            fadeOut: Long,
        ): EditedMediaItem {
            val clipping =
                MediaItem.ClippingConfiguration
                    .Builder()
                    .setStartPositionMs(track.startMs)
                    .setEndPositionMs(track.startMs + clipMs)
                    .build()
            return EditedMediaItem
                .Builder(MediaItem.Builder().setUri(track.uri).setClippingConfiguration(clipping).build())
                .setRemoveVideo(true)
                .setEffects(Effects(listOf(FadeAudioProcessor(fadeIn, fadeOut, clipMs)), emptyList()))
                .build()
        }

        private val MusicTrack.clipMs: Long get() = (endMs - startMs).coerceAtLeast(1L)

        private fun buildPhotoSegment(
            segment: TripSegment,
            photoIndex: Int,
            transition: TransitionParams,
        ): EditedMediaItem {
            val uri = requireNotNull(segment.uri)
            val baseDuration = if (segment.durationMs > 0) segment.durationMs else 5000L
            val duration =
                if (segment.endZoomRect != null) {
                    val rectW = segment.endZoomRect.right - segment.endZoomRect.left
                    val rectH = segment.endZoomRect.bottom - segment.endZoomRect.top
                    val area = rectW * rectH
                    if (area > 0f) (baseDuration / area).toLong().coerceAtMost(12000L) else baseDuration
                } else {
                    baseDuration
                }
            android.util.Log.d("RenderManager", "PHOTO duration: $duration ms aspect: ${segment.photoAspectRatio} endZoomRect: ${segment.endZoomRect}")
            val mediaItem =
                MediaItem
                    .Builder()
                    .setUri(uri)
                    .setImageDurationMs(duration)
                    .build()
            val isLandscape = (segment.photoAspectRatio ?: 1f) > 1f
            val videoEffects =
                buildList {
                    add(portraitPresentation())
                    if (transition.type != TransitionType.NONE) {
                        val durationUs = duration * 1000L
                        val fadeDurationUs = transition.durationMs * 1000L
                        val headUs = if (photoIndex == 0) 0L else fadeDurationUs
                        add(FadeRgbMatrix(durationUs, headUs, fadeDurationUs, transition.type))
                    }
                    when {
                        isLandscape -> {
                            add(kenBurnsPanHorizontal(duration, photoIndex, segment.photoAspectRatio!!))
                        }

                        else -> {
                            // Use stored rects (user-defined or default), falling back to
                            // computed defaults if the segment predates this feature.
                            val (defStart, defEnd) = ZoomRect.defaultPair(photoIndex)
                            val start = segment.startZoomRect ?: defStart
                            val end = segment.endZoomRect ?: defEnd
                            add(kenBurnsZoomToRect(start, end, duration))
                        }
                    }
                }
            return EditedMediaItem
                .Builder(mediaItem)
                .setFrameRate(30)
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
        }

        private fun buildVideoSegment(
            uri: Uri,
            transition: TransitionParams,
        ): EditedMediaItem {
            val videoEffects =
                buildList {
                    add(portraitPresentation())
                    // Video duration unknown — pass -1 so tail fade is skipped.
                    if (transition.type != TransitionType.NONE) {
                        add(FadeRgbMatrix(-1L, 0L, 0L, transition.type))
                    }
                }
            return EditedMediaItem
                .Builder(MediaItem.fromUri(uri))
                .setFrameRate(30)
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
        }

        private fun buildMapTravelSegment(
            segment: TripSegment,
            trip: TripManifest,
        ): EditedMediaItem {
            val portrait = trip.aspectRatio != AspectRatio.LANDSCAPE
            val destIndex =
                segment.clusterId
                    ?.substringAfter("cluster_")
                    ?.toIntOrNull()
                    ?: 1
            val fromName = trip.clusters.getOrNull(destIndex - 1)?.name ?: "Start"
            val toName = trip.clusters.getOrNull(destIndex)?.name ?: "End"
            val durationMs = if (segment.durationMs > 0) segment.durationMs else 2000L
            val bitmap = buildTransitionBitmap(fromName, toName, portrait)
            val tempFile =
                File(context.cacheDir, "transition_$destIndex.jpg").also {
                    it.outputStream().use { s -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, s) }
                    bitmap.recycle()
                }
            val mediaItem =
                MediaItem
                    .Builder()
                    .setUri(Uri.fromFile(tempFile))
                    .setImageDurationMs(durationMs)
                    .build()
            val durationUs = durationMs * 1000L
            val fadeDurationUs = (durationMs * 0.3).toLong() * 1000L
            return EditedMediaItem
                .Builder(mediaItem)
                .setFrameRate(30)
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(
                            portraitPresentation(),
                            // Title cards fade in AND out (both head and tail)
                            FadeRgbMatrix(durationUs, fadeDurationUs, fadeDurationUs, TransitionType.FADE_BLACK),
                        ),
                    ),
                ).build()
        }

        private fun portraitPresentation(): Presentation = Presentation.createForWidthAndHeight(1080, 1920, Presentation.LAYOUT_SCALE_TO_FIT)

        /**
         * Horizontal pan for landscape photos. Scales so height fills the portrait frame,
         * then slides left↔right (direction alternates by index) with a subtle zoom ramp
         * so each photo feels dynamic rather than a flat slide.
         *
         * Even-indexed photos zoom in (1.0→1.1×) while odd-indexed zoom out (1.1→1.0×),
         * giving adjacent photos opposite motion that avoids a monotonous rhythm.
         */
        private fun kenBurnsPanHorizontal(
            durationMs: Long,
            index: Int,
            aspectRatio: Float,
        ): MatrixTransformation {
            val durationUs = durationMs * 1000L
            var startUs = -1L
            val panRange = (aspectRatio - 1f).coerceAtLeast(0f)
            val startX = if (index % 2 == 0) -panRange else panRange
            val endX = -startX
            val zoomStart = if (index % 2 == 0) 1.00f else 1.10f
            val zoomEnd = if (index % 2 == 0) 1.10f else 1.00f
            return MatrixTransformation { presentationTimeUs ->
                if (startUs < 0L) startUs = presentationTimeUs
                val progress = ((presentationTimeUs - startUs).toFloat() / durationUs).coerceIn(0f, 1f)
                val zoom = zoomStart + (zoomEnd - zoomStart) * progress
                val scale = aspectRatio * zoom
                val tx = (startX + (endX - startX) * progress) * zoom
                android.graphics.Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(tx, 0f)
                }
            }
        }

        /**
         * Ken Burns interpolation from startRect to endRect.
         * Rects use normalized 0–1 coordinates over the photo; scale capped at 8×.
         */
        private fun kenBurnsZoomToRect(
            startRect: ZoomRect,
            endRect: ZoomRect,
            durationMs: Long,
        ): MatrixTransformation {
            val durationUs = durationMs * 1000L
            var startUs = -1L
            return MatrixTransformation { presentationTimeUs ->
                if (startUs < 0L) startUs = presentationTimeUs
                val progress = ((presentationTimeUs - startUs).toFloat() / durationUs).coerceIn(0f, 1f)
                val rect =
                    ZoomRect(
                        left = startRect.left + (endRect.left - startRect.left) * progress,
                        top = startRect.top + (endRect.top - startRect.top) * progress,
                        right = startRect.right + (endRect.right - startRect.right) * progress,
                        bottom = startRect.bottom + (endRect.bottom - startRect.bottom) * progress,
                    )
                val rectW = (rect.right - rect.left).coerceAtLeast(0.05f)
                val rectH = (rect.bottom - rect.top).coerceAtLeast(0.05f)
                val scale = (1f / minOf(rectW, rectH)).coerceIn(1f, 8f)
                val cx = (rect.left + rect.right) - 1f
                val cy = (rect.top + rect.bottom) - 1f
                // tx: negative cx → shows right content (x-axis matches Android y-down)
                // ty: positive cy → shows bottom content (y-axis is flipped in Media3's GL pipeline)
                android.graphics.Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(-cx * scale, cy * scale)
                }
            }
        }

        private fun buildTransitionBitmap(
            fromName: String,
            toName: String,
            portrait: Boolean,
        ): Bitmap {
            val width = if (portrait) 1080 else 1920
            val height = if (portrait) 1920 else 1080
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.argb(230, 15, 15, 15))

            val textPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textAlign = Paint.Align.CENTER
                    textSize = if (portrait) 96f else 72f
                    typeface = Typeface.DEFAULT_BOLD
                }
            val arrowPaint =
                Paint(textPaint).apply {
                    textSize = if (portrait) 80f else 60f
                    alpha = 180
                }
            val cx = width / 2f
            val cy = height / 2f
            if (portrait) {
                canvas.drawText(fromName, cx, cy - 140f, textPaint)
                canvas.drawText("↓", cx, cy + textPaint.textSize / 2, arrowPaint)
                canvas.drawText(toName, cx, cy + 200f, textPaint)
            } else {
                canvas.drawText("$fromName  →  $toName", cx, cy + textPaint.textSize / 3, textPaint)
            }
            return bitmap
        }

        private fun startProgressTracking(transformer: Transformer) {
            progressJob?.cancel()
            progressJob =
                scope.launch {
                    while (true) {
                        val progressHolder = androidx.media3.transformer.ProgressHolder()
                        val progressState = transformer.getProgress(progressHolder)

                        if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val currentRendering = _renderState.value as? RenderState.Rendering
                            if (currentRendering != null) {
                                _renderState.value =
                                    RenderState.Rendering(
                                        progressHolder.progress,
                                        "Exporting video...",
                                    )
                            }
                        }

                        if (progressState == Transformer.PROGRESS_STATE_NOT_STARTED ||
                            progressState == Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY
                        ) {
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
         * RgbMatrix-based fade transition. getMatrix() is called per-frame by Media3,
         * making it work correctly for both video and image (setImageDurationMs) segments,
         * unlike BitmapOverlay.getOverlaySettings() which is not called per-frame on images.
         *
         * @param segmentDurationUs total segment duration; -1 = unknown — tail is skipped
         * @param headFadeDurationUs ramp at segment start; 0 = no head fade
         * @param tailFadeDurationUs ramp at segment end; 0 or segmentDurationUs<=0 = no tail fade
         * @param type FADE_BLACK, FADE_WHITE, or FLASH
         */
        private class FadeRgbMatrix(
            private val segmentDurationUs: Long,
            private val headFadeDurationUs: Long,
            private val tailFadeDurationUs: Long,
            private val type: TransitionType,
        ) : RgbMatrix {
            private var startUs = -1L

            override fun getMatrix(
                presentationTimeUs: Long,
                useHdr: Boolean,
            ): FloatArray {
                if (startUs < 0L) startUs = presentationTimeUs
                val elapsed = presentationTimeUs - startUs
                val alpha = maxOf(headAlpha(elapsed), tailAlpha(elapsed)).coerceIn(0f, MAX_ALPHA)
                val p = 1f - alpha
                return if (type == TransitionType.FADE_WHITE || type == TransitionType.FLASH) {
                    floatArrayOf(p, 0f, 0f, 0f, 0f, p, 0f, 0f, 0f, 0f, p, 0f, alpha, alpha, alpha, 1f)
                } else {
                    floatArrayOf(p, 0f, 0f, 0f, 0f, p, 0f, 0f, 0f, 0f, p, 0f, 0f, 0f, 0f, 1f)
                }
            }

            private fun headAlpha(elapsed: Long): Float {
                if (headFadeDurationUs <= 0L || elapsed >= headFadeDurationUs) return 0f
                val t = elapsed.toFloat() / headFadeDurationUs
                return if (type == TransitionType.FLASH) (1f - t) * (1f - t) else 1f - t
            }

            private fun tailAlpha(elapsed: Long): Float {
                if (tailFadeDurationUs <= 0L || segmentDurationUs <= 0L) return 0f
                val tailStart = segmentDurationUs - tailFadeDurationUs
                return if (elapsed <= tailStart) 0f else (elapsed - tailStart).toFloat() / tailFadeDurationUs
            }

            companion object {
                // Keep fade semi-transparent so the underlying image stays visible
                private const val MAX_ALPHA = 0.5f
            }
        }

        /**
         * Sealed class representing render states
         */
        sealed class RenderState {
            object Idle : RenderState()

            data class Rendering(
                val progress: Int,
                val status: String,
            ) : RenderState()

            data class Completed(
                val outputPath: String,
            ) : RenderState()

            data class Failed(
                val error: String,
            ) : RenderState()

            object Cancelled : RenderState()
        }
    }
