package com.routesnap.app.ui.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Telegram-style audio trim bar.
 *
 * Shows the full waveform dimmed. The selected [startMs]..[endMs] region is
 * highlighted with bracket handles at each edge and a draggable body in between.
 *
 * Gesture zones:
 *   [start handle zone] [── middle shifts both ──] [end handle zone]
 */
@Suppress("LongParameterList")
@Composable
fun MusicTrimBar(
    waveform: List<Float>,
    trackDurationMs: Long,
    videoDurationMs: Long,
    startMs: Long,
    endMs: Long,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    handleWidthDp: Dp = 12.dp,
) {
    if (trackDurationMs <= 0 || waveform.isEmpty()) return

    // Use rememberUpdatedState so pointerInput(Unit) always sees latest values
    val currentStart by rememberUpdatedState(startMs)
    val currentEnd by rememberUpdatedState(endMs)
    val currentTrackDurationMs by rememberUpdatedState(trackDurationMs)
    val currentOnStartChange by rememberUpdatedState(onStartChange)
    val currentOnEndChange by rememberUpdatedState(onEndChange)

    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var dragZone by remember { mutableStateOf(DragZone.NONE) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }

    val barDim = Color(0xFF90CAF9)
    val barBright = Color(0xFF42A5F5)
    val selectionOverlay = Color(0xFF42A5F5)
    val handleCol = Color(0xFFFFFFFF)
    val videoCursorCol = Color(0xFFFFEB3B)
    val bgCol = Color(0xFF1A1A2E)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bgCol),
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasWidth = it.width.toFloat() }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (canvasWidth <= 0f) return@detectDragGestures
                                val startX = msToX(currentStart, currentTrackDurationMs, canvasWidth)
                                val endX = msToX(currentEnd, currentTrackDurationMs, canvasWidth)
                                val handlePx = handleWidthDp.toPx() * 1.5f
                                dragZone =
                                    when {
                                        offset.x < startX + handlePx -> DragZone.START_HANDLE
                                        offset.x > endX - handlePx -> DragZone.END_HANDLE
                                        else -> DragZone.MIDDLE
                                    }
                                accumulatedDragX = 0f
                            },
                            onDrag = { _, dragAmount ->
                                if (canvasWidth <= 0f) return@detectDragGestures
                                accumulatedDragX += dragAmount.x
                                val deltaMs = (dragAmount.x / canvasWidth * currentTrackDurationMs).toLong()

                                when (dragZone) {
                                    DragZone.START_HANDLE -> {
                                        val newStart =
                                            (currentStart + deltaMs)
                                                .coerceIn(0, currentEnd - MIN_SELECTION_MS)
                                        currentOnStartChange(newStart)
                                    }

                                    DragZone.END_HANDLE -> {
                                        val newEnd =
                                            (currentEnd + deltaMs)
                                                .coerceIn(currentStart + MIN_SELECTION_MS, currentTrackDurationMs)
                                        currentOnEndChange(newEnd)
                                    }

                                    DragZone.MIDDLE -> {
                                        val selMs = currentEnd - currentStart
                                        val newStart = (currentStart + deltaMs).coerceIn(0, currentTrackDurationMs - selMs)
                                        currentOnStartChange(newStart)
                                        currentOnEndChange(newStart + selMs)
                                    }

                                    DragZone.NONE -> {
                                        // no-op
                                    }
                                }
                            },
                            onDragEnd = { dragZone = DragZone.NONE },
                        )
                    },
        ) {
            if (canvasWidth <= 0f) return@Canvas

            val h = size.height
            val startX = msToX(currentStart, currentTrackDurationMs, canvasWidth)
            val endX = msToX(currentEnd, currentTrackDurationMs, canvasWidth)
            val barW = canvasWidth / waveform.size

            // Full waveform (dimmed background)
            drawWaveformBars(waveform, barW, h, barDim.copy(alpha = 0.3f))

            // Selection tint
            drawRect(
                color = selectionOverlay.copy(alpha = 0.12f),
                topLeft = Offset(startX, 0f),
                size = Size(endX - startX, h),
            )

            // Selected region waveform (bright)
            waveform.forEachIndexed { i, amp ->
                val bx = i * barW
                if (bx + barW >= startX && bx <= endX) {
                    val bh = amp * h * 0.85f
                    drawRoundRect(
                        color = barBright,
                        topLeft = Offset(bx + 1f, (h - bh) / 2f),
                        size = Size(barW - 2f, bh),
                        cornerRadius = CornerRadius(2f),
                    )
                }
            }

            // Video-length cursor
            val videoEndMs = videoDurationMs.coerceAtMost(currentTrackDurationMs)
            if (videoEndMs < currentTrackDurationMs) {
                val vx = msToX(videoEndMs, currentTrackDurationMs, canvasWidth)
                drawLine(videoCursorCol.copy(alpha = 0.7f), Offset(vx, 0f), Offset(vx, h), 2f)
            }

            // Handles
            drawTrimHandle(startX, h, handleWidthDp.toPx(), handleCol, isLeft = true)
            drawTrimHandle(endX, h, handleWidthDp.toPx(), handleCol, isLeft = false)
        }

        // Time labels
        Text(
            text = formatMs(startMs),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 9.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = handleWidthDp + 2.dp, bottom = 2.dp),
        )
        Text(
            text = formatMs(endMs),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 9.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = handleWidthDp + 2.dp, bottom = 2.dp),
        )
    }
}

private fun msToX(
    ms: Long,
    trackMs: Long,
    width: Float,
): Float = (ms.toFloat() / trackMs) * width

private fun DrawScope.drawWaveformBars(
    waveform: List<Float>,
    barW: Float,
    height: Float,
    color: Color,
) {
    waveform.forEachIndexed { i, amp ->
        val bh = amp * height * 0.85f
        drawRoundRect(
            color = color,
            topLeft = Offset(i * barW + 1f, (height - bh) / 2f),
            size = Size(barW - 2f, bh),
            cornerRadius = CornerRadius(2f),
        )
    }
}

private fun DrawScope.drawTrimHandle(
    x: Float,
    height: Float,
    handleW: Float,
    color: Color,
    isLeft: Boolean,
) {
    val left = if (isLeft) x else x - handleW
    drawRoundRect(color = color, topLeft = Offset(left, 0f), size = Size(handleW, height), cornerRadius = CornerRadius(4f))
    // Grip notch
    val notchX = if (isLeft) left + handleW * 0.6f else left + handleW * 0.4f
    val mid = height / 2f
    drawLine(Color.Black.copy(alpha = 0.4f), Offset(notchX, mid - 5f), Offset(notchX, mid + 5f), 2f)
}

private enum class DragZone { NONE, START_HANDLE, END_HANDLE, MIDDLE }

private const val MIN_SELECTION_MS = 2_000L

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
