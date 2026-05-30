package com.routesnap.app.ui.render

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.routesnap.app.ui.theme.RouteSnapTheme

/**
 * UI State for the render screen
 */
data class RenderUiState(
    val isRendering: Boolean = false,
    val progress: Int = 0,
    val status: String = "Preparing...",
    val eta: String? = null,
    val isComplete: Boolean = false,
    val error: String? = null,
    val outputPath: String? = null,
)

/**
 * Render Screen - Show rendering progress
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun RenderScreen(
    tripId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RenderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(tripId) {
        tripId?.let { viewModel.startRendering(it) }
    }

    // Auto-navigate when complete
    val currentOnNavigateToShare by rememberUpdatedState(onNavigateToShare)
    LaunchedEffect(uiState.isComplete) {
        val outputPath = uiState.outputPath
        if (uiState.isComplete && outputPath != null) {
            kotlinx.coroutines.delay(1500)
            currentOnNavigateToShare(outputPath)
        }
    }

    RouteSnapTheme {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text("Rendering") },
                    navigationIcon = {
                        if (!uiState.isRendering && !uiState.isComplete) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                            }
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                )
            },
        ) { paddingValues ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    // Render icon
                    RenderIcon(
                        isComplete = uiState.isComplete,
                        isError = uiState.error != null,
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Status text
                    Text(
                        text = uiState.status,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )

                    if (uiState.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    // ETA
                    uiState.eta?.let { eta ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Estimated time: $eta",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Progress indicator
                    if (uiState.isRendering || uiState.isComplete) {
                        CircularProgressIndicator(
                            progress = uiState.progress / 100f,
                            modifier = Modifier.size(120.dp),
                            strokeWidth = 8.dp,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "${uiState.progress}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Retry button on error
                    if (uiState.error != null) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                tripId?.let { viewModel.retryRendering(it) }
                            },
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderIcon(
    isComplete: Boolean,
    isError: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "render")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rotation",
    )

    val iconSize = 96.dp

    when {
        isError -> {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.error,
            )
        }

        isComplete -> {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }

        else -> {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            rotationZ = rotation
                        },
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
