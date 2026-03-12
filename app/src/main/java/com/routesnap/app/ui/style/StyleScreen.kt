package com.routesnap.app.ui.style

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.routesnap.app.domain.model.AspectRatio
import com.routesnap.app.domain.model.TemplatePreset
import com.routesnap.app.ui.theme.RouteSnapTheme

/**
 * UI State for the style screen
 */
data class StyleUiState(
    val selectedAspectRatio: AspectRatio = AspectRatio.SQUARE,
    val selectedTemplate: TemplatePreset = TemplatePreset.BALANCED,
    val musicSelected: Boolean = false,
    val musicTitle: String? = null,
    val isProcessing: Boolean = false
)

/**
 * Style Screen - Select aspect ratio, template, and music
 */
@Composable
fun StyleScreen(
    tripId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToRender: () -> Unit,
    viewModel: StyleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    RouteSnapTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Style Video") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onNavigateToRender,
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Render")
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Aspect Ratio Section
                item {
                    SectionTitle(title = "Aspect Ratio", icon = Icons.Default.Crop)
                    AspectRatioSelector(
                        selectedAspectRatio = uiState.selectedAspectRatio,
                        onAspectRatioSelected = { viewModel.updateAspectRatio(it) }
                    )
                }

                // Template Section
                item {
                    SectionTitle(title = "Template", icon = Icons.Default.Movie)
                    TemplateSelector(
                        selectedTemplate = uiState.selectedTemplate,
                        onTemplateSelected = { viewModel.updateTemplate(it) }
                    )
                }

                // Music Section
                item {
                    SectionTitle(title = "Music", icon = Icons.Default.MusicNote)
                    MusicSelector(
                        musicSelected = uiState.musicSelected,
                        musicTitle = uiState.musicTitle,
                        onMusicSelected = { viewModel.selectMusic() }
                    )
                }

                // Preview section
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Preview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aspect Ratio: ${uiState.selectedAspectRatio.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Template: ${uiState.selectedTemplate.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Photo Duration: ${uiState.selectedTemplate.photoDurationMs / 1000}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AspectRatioSelector(
    selectedAspectRatio: AspectRatio,
    onAspectRatioSelected: (AspectRatio) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AspectRatio.values().forEach { ratio ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selectedAspectRatio == ratio,
                        onClick = { onAspectRatioSelected(ratio) }
                    ),
                border = if (selectedAspectRatio == ratio) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedAspectRatio == ratio) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ratio.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (selectedAspectRatio == ratio) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateSelector(
    selectedTemplate: TemplatePreset,
    onTemplateSelected: (TemplatePreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TemplatePreset.values().forEach { template ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selectedTemplate == template,
                        onClick = { onTemplateSelected(template) }
                    ),
                border = if (selectedTemplate == template) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedTemplate == template) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = template.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedTemplate == template) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Photos: ${template.photoDurationMs / 1000}s | Videos: ${template.videoHighlightDurationMs / 1000}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicSelector(
    musicSelected: Boolean,
    musicTitle: String?,
    onMusicSelected: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onMusicSelected,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (musicSelected) Icons.Default.MusicNote else Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (musicSelected) musicTitle ?: "Music Selected" else "Select Music",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (musicSelected) {
                        Text(
                            text = "Tap to change",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Add background music to your video",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (musicSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
