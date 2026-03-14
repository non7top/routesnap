package com.routesnap.app.ui.picker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.routesnap.app.ui.theme.RouteSnapTheme

/**
 * Photo Picker Screen - Main entry point for selecting media
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPickerScreen(
    onNavigateToTimeline: (String) -> Unit,
    viewModel: PickerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Photo Picker launcher
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addSelectedUris(uris)
        }
    }

    RouteSnapTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Create New Trip") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                if (uiState.selectedUris.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            // Navigate to timeline with trip ID
                            // For now, we'll just show a snackbar
                        },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done")
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // Trip name input
                OutlinedTextField(
                    value = uiState.tripName,
                    onValueChange = { viewModel.updateTripName(it) },
                    label = { Text("Trip Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Stats card
                if (uiState.selectedUris.isNotEmpty()) {
                    StatsCard(
                        photoCount = uiState.selectedUris.size,
                        clusterCount = uiState.clusterCount,
                        estimatedDuration = uiState.estimatedDurationSeconds,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Photo grid or empty state
                if (uiState.selectedUris.isEmpty()) {
                    EmptyState(
                        onPickPhotos = { pickMedia.launch("image/*") }
                    )
                } else {
                    // Add more photos button
                    Button(
                        onClick = { pickMedia.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add More Photos")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Photo grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(
                            count = uiState.metadata.size,
                            key = { uiState.metadata[it].uri }
                        ) { index ->
                            val item = uiState.metadata[index]
                            PhotoGridItem(
                                uri = item.uri,
                                hasGps = item.hasLocation,
                                clusterId = item.clusterId,
                                totalClusters = uiState.clusterCount,
                                onRemove = { viewModel.removeUri(item.uri) }
                            )
                        }
                    }
                }

                // Processing indicator
                if (uiState.isProcessing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Processing photos...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Error message
                uiState.error?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(
    photoCount: Int,
    clusterCount: Int,
    estimatedDuration: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Default.Photo,
                value = photoCount.toString(),
                label = "Photos"
            )
            StatItem(
                icon = Icons.Default.LocationOn,
                value = clusterCount.toString(),
                label = "Stops"
            )
            StatItem(
                icon = Icons.Default.Timer,
                value = "${estimatedDuration}s",
                label = "Duration"
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState(onPickPhotos: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No photos selected yet",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap the button below to add photos and videos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onPickPhotos) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Photos & Videos")
            }
        }
    }
}

@Composable
private fun PhotoGridItem(
    uri: Uri,
    hasGps: Boolean,
    clusterId: String?,
    totalClusters: Int,
    onRemove: () -> Unit
) {
    // Generate consistent color from cluster ID
    val clusterColor = clusterId?.let { id ->
        val hash = id.hashCode()
        Color(
            red = ((hash and 0xFF0000) shr 16) / 255f * 0.7f + 0.3f,
            green = ((hash and 0x00FF00) shr 8) / 255f * 0.7f + 0.3f,
            blue = (hash and 0x0000FF) / 255f * 0.7f + 0.3f,
            alpha = 1f
        )
    } ?: Color.Gray
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // GPS indicator overlay (bottom-left)
        if (hasGps) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(
                        clusterColor,
                        RoundedCornerShape(4.dp)
                    )
                    .size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Has GPS",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Remove button overlay (top-right)
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    RoundedCornerShape(50)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
