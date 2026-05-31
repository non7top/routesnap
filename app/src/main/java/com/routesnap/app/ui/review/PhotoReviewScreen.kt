package com.routesnap.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.routesnap.app.domain.model.ZoomRect
import com.routesnap.app.ui.theme.RouteSnapTheme
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private val START_COLOR = Color(0xFF2196F3)
private val END_COLOR = Color(0xFFFF9800)
private const val HANDLE_DP = 14
private const val MIN_RECT_FRAC = 0.1f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoReviewScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotoReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        onDispose { viewModel.saveCurrentRects() }
    }

    RouteSnapTheme {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = {
                        val index = uiState.currentIndex
                        val total = uiState.segments.size
                        Text("Photo ${index + 1} / $total")
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.saveCurrentRects()
                            onNavigateBack()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.saveCurrentRects()
                            onNavigateBack()
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Done")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Legend
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(16.dp).background(START_COLOR))
                    Spacer(Modifier.width(4.dp))
                    Text("Start", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.size(16.dp).background(END_COLOR))
                    Spacer(Modifier.width(4.dp))
                    Text("End", style = MaterialTheme.typography.bodySmall)
                }

                // Photo with rect overlays — takes up all remaining space
                var imageSize by remember { mutableStateOf(IntSize.Zero) }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { imageSize = it },
                ) {
                    uiState.current?.uri?.let { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    if (imageSize != IntSize.Zero) {
                        ZoomRectOverlay(
                            rect = uiState.startRect,
                            color = START_COLOR,
                            containerSize = imageSize,
                            onRectChange = viewModel::updateStartRect,
                        )
                        ZoomRectOverlay(
                            rect = uiState.endRect,
                            color = END_COLOR,
                            containerSize = imageSize,
                            onRectChange = viewModel::updateEndRect,
                        )
                    }
                }

                // Prev / Next navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { viewModel.navigateTo(uiState.currentIndex - 1) },
                        enabled = uiState.hasPrev,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    ) {
                        Icon(
                            Icons.Default.ArrowBackIosNew,
                            contentDescription = "Previous",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.navigateTo(uiState.currentIndex + 1) },
                        enabled = uiState.hasNext,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    ) {
                        Icon(
                            Icons.Default.ArrowForwardIos,
                            contentDescription = "Next",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomRectOverlay(
    rect: ZoomRect,
    color: Color,
    containerSize: IntSize,
    onRectChange: (ZoomRect) -> Unit,
) {
    val density = LocalDensity.current
    val handlePx = with(density) { HANDLE_DP.dp.toPx() }

    val left = rect.left * containerSize.width
    val top = rect.top * containerSize.height
    val right = rect.right * containerSize.width
    val bottom = rect.bottom * containerSize.height

    // Body — drag to move entire rect
    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(
                width = with(density) { (right - left).toDp() },
                height = with(density) { (bottom - top).toDp() },
            )
            .border(2.dp, color)
            .pointerInput(rect) {
                detectDragGestures { _, drag ->
                    val dx = drag.x / containerSize.width
                    val dy = drag.y / containerSize.height
                    val w = rect.right - rect.left
                    val h = rect.bottom - rect.top
                    val newL = (rect.left + dx).coerceIn(0f, 1f - w)
                    val newT = (rect.top + dy).coerceIn(0f, 1f - h)
                    onRectChange(ZoomRect(newL, newT, newL + w, newT + h))
                }
            },
    )

    // Corner handles
    Handle(Offset(left, top), color, handlePx) { drag ->
        onRectChange(ZoomRect(
            left = (rect.left + drag.x / containerSize.width).coerceIn(0f, rect.right - MIN_RECT_FRAC),
            top = (rect.top + drag.y / containerSize.height).coerceIn(0f, rect.bottom - MIN_RECT_FRAC),
            right = rect.right, bottom = rect.bottom,
        ))
    }
    Handle(Offset(right - handlePx, top), color, handlePx) { drag ->
        onRectChange(ZoomRect(
            left = rect.left, top = (rect.top + drag.y / containerSize.height).coerceIn(0f, rect.bottom - MIN_RECT_FRAC),
            right = (rect.right + drag.x / containerSize.width).coerceIn(rect.left + MIN_RECT_FRAC, 1f),
            bottom = rect.bottom,
        ))
    }
    Handle(Offset(left, bottom - handlePx), color, handlePx) { drag ->
        onRectChange(ZoomRect(
            left = (rect.left + drag.x / containerSize.width).coerceIn(0f, rect.right - MIN_RECT_FRAC),
            top = rect.top, right = rect.right,
            bottom = (rect.bottom + drag.y / containerSize.height).coerceIn(rect.top + MIN_RECT_FRAC, 1f),
        ))
    }
    Handle(Offset(right - handlePx, bottom - handlePx), color, handlePx) { drag ->
        onRectChange(ZoomRect(
            left = rect.left, top = rect.top,
            right = (rect.right + drag.x / containerSize.width).coerceIn(rect.left + MIN_RECT_FRAC, 1f),
            bottom = (rect.bottom + drag.y / containerSize.height).coerceIn(rect.top + MIN_RECT_FRAC, 1f),
        ))
    }

    // Edge handles
    val midX = (left + right) / 2 - handlePx / 2
    val midY = (top + bottom) / 2 - handlePx / 2
    Handle(Offset(midX, top), color, handlePx) { drag ->
        onRectChange(rect.copy(top = (rect.top + drag.y / containerSize.height).coerceIn(0f, rect.bottom - MIN_RECT_FRAC)))
    }
    Handle(Offset(midX, bottom - handlePx), color, handlePx) { drag ->
        onRectChange(rect.copy(bottom = (rect.bottom + drag.y / containerSize.height).coerceIn(rect.top + MIN_RECT_FRAC, 1f)))
    }
    Handle(Offset(left, midY), color, handlePx) { drag ->
        onRectChange(rect.copy(left = (rect.left + drag.x / containerSize.width).coerceIn(0f, rect.right - MIN_RECT_FRAC)))
    }
    Handle(Offset(right - handlePx, midY), color, handlePx) { drag ->
        onRectChange(rect.copy(right = (rect.right + drag.x / containerSize.width).coerceIn(rect.left + MIN_RECT_FRAC, 1f)))
    }
}

@Composable
private fun Handle(
    position: Offset,
    color: Color,
    sizePx: Float,
    onDrag: (Offset) -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .size(with(density) { sizePx.toDp() })
            .clip(RectangleShape)
            .background(color)
            .pointerInput(Unit) {
                detectDragGestures { _, drag -> onDrag(drag) }
            },
    )
}
