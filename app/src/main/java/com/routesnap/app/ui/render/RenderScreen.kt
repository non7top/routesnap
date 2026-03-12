package com.routesnap.app.ui.render

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    val outputPath: String? = null
)

/**
 * Render Screen - Show rendering progress
 */
@Composable
fun RenderScreen(
    tripId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToShare: () -> Unit,
    viewModel: RenderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(tripId) {
        tripId?.let { viewModel.startRendering(it) }
    }

    // Auto-navigate when complete
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            kotlinx.coroutines.delay(1500)
            onNavigateToShare()
        }
    }

    RouteSnapTheme {
        Scaffold(
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    // Animated icon
                    RenderIcon(
                        isRendering = uiState.isRendering,
                        isComplete = uiState.isComplete,
                        isError = uiState.error != null
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Status text
                    Text(
                        text = uiState.status,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    if (uiState.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // ETA
                    uiState.eta?.let { eta ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Estimated time: $eta",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Progress indicator
                    if (uiState.isRendering || uiState.isComplete) {
                        CircularProgressIndicator(
                            progress = { uiState.progress / 100f },
                            modifier = Modifier.size(120.dp),
                            strokeWidth = 8.dp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "${uiState.progress}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Retry button on error
                    if (uiState.error != null) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { /* TODO: Retry */ }) {
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
    isRendering: Boolean,
    isComplete: Boolean,
    isError: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "render")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val iconSize = 96.dp

    when {
        isError -> {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.error
            )
        }
        isComplete -> {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        else -> {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        rotationZ = rotation
                    },
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
