package com.routesnap.app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.routesnap.app.domain.model.SegmentType
import com.routesnap.app.domain.model.TripSegment
import com.routesnap.app.ui.theme.RouteSnapTheme

/**
 * UI State for the timeline screen
 */
data class TimelineUiState(
    val segments: List<TripSegment> = emptyList(),
    val clusters: List<TimelineCluster> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val tripId: String? = null
)

data class TimelineCluster(
    val id: String,
    val name: String,
    val segmentIndices: List<Int>
)

/**
 * Timeline Screen - Review and reorder photos
 */
@Composable
fun TimelineScreen(
    tripId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToStyle: () -> Unit,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(tripId) {
        tripId?.let { viewModel.loadTrip(it) }
    }

    RouteSnapTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Review Timeline") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onNavigateToStyle,
                            enabled = uiState.segments.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Done")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            if (uiState.segments.isEmpty()) {
                EmptyTimelineState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            } else {
                TimelineContent(
                    uiState = uiState,
                    onRemoveSegment = { viewModel.removeSegment(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun TimelineContent(
    uiState: TimelineUiState,
    onRemoveSegment: (TripSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Group by clusters
        val clusters = uiState.clusters
        val segments = uiState.segments

        if (clusters.isEmpty()) {
            // No clustering, show all segments
            itemsIndexed(segments) { index, segment ->
                TimelineItem(
                    segment = segment,
                    index = index,
                    onRemove = { onRemoveSegment(segment) }
                )
            }
        } else {
            // Show clustered segments
            clusters.forEach { cluster ->
                item {
                    ClusterHeader(clusterName = cluster.name)
                }

                items(cluster.segmentIndices) { index ->
                    TimelineItem(
                        segment = segments[index],
                        index = index,
                        onRemove = { onRemoveSegment(segments[index]) }
                    )
                }
            }
        }

        // Bottom spacer for FAB
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ClusterHeader(clusterName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = clusterName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TimelineItem(
    segment: TripSegment,
    index: Int,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Order number
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Thumbnail or map icon
            when (segment.type) {
                SegmentType.PHOTO -> {
                    segment.uri?.let { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                SegmentType.VIDEO -> {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                SegmentType.MAP_TRAVEL -> {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (segment.type) {
                        SegmentType.PHOTO -> "Photo"
                        SegmentType.VIDEO -> "Video"
                        SegmentType.MAP_TRAVEL -> "Map Transition"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${segment.durationMs / 1000}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Remove button
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptyTimelineState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Timeline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No segments to display",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
