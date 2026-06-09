package com.routesnap.app.ui.style

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.routesnap.app.domain.model.AspectRatio
import com.routesnap.app.domain.model.TemplatePreset
import com.routesnap.app.domain.model.TransitionType
import com.routesnap.app.ui.theme.RouteSnapTheme

/**
 * UI State for the style screen
 */
@Suppress("LongParameterList")
data class StyleUiState(
    val selectedAspectRatio: AspectRatio = AspectRatio.PORTRAIT,
    val selectedTemplate: TemplatePreset = TemplatePreset.BALANCED,
    val selectedTransition: TransitionType? = null,
    val musicUri: Uri? = null,
    val musicTitle: String? = null,
    val isProcessing: Boolean = false,
    val waveform: List<Float> = emptyList(),
    val isLoadingWaveform: Boolean = false,
    val trackDurationMs: Long = 0,
    val musicStartMs: Long = 0,
    val musicEndMs: Long? = null,
    val musicFadeInMs: Long = 1000,
    val musicFadeOutMs: Long = 2000,
    val videoDurationMs: Long = 0,
) {
    val musicSelected: Boolean get() = musicUri != null
    val effectiveEndMs: Long get() = musicEndMs ?: trackDurationMs
}

/**
 * Style Screen - Select aspect ratio, template, and music
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRender: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StyleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val musicPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val displayName =
                    context.contentResolver
                        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: uri.lastPathSegment ?: "Music"
                viewModel.setMusicUri(uri, displayName)
            }
        }

    RouteSnapTheme {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text("Style Video") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { viewModel.saveAndRender(onNavigateToRender) },
                    containerColor = MaterialTheme.colorScheme.secondary,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Render")
                }
            },
        ) { paddingValues ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Aspect Ratio Section
                item {
                    SectionTitle(title = "Aspect Ratio", icon = Icons.Default.Crop)
                    AspectRatioSelector(
                        selectedAspectRatio = uiState.selectedAspectRatio,
                        onAspectRatioSelect = { viewModel.updateAspectRatio(it) },
                    )
                }

                // Template Section
                item {
                    SectionTitle(title = "Template", icon = Icons.Default.Movie)
                    TemplateSelector(
                        selectedTemplate = uiState.selectedTemplate,
                        onTemplateSelect = { viewModel.updateTemplate(it) },
                    )
                }

                // Transition Section
                item {
                    SectionTitle(title = "Transition", icon = Icons.Default.Slideshow)
                    TransitionSelector(
                        selectedTemplate = uiState.selectedTemplate,
                        selectedTransition = uiState.selectedTransition,
                        onTransitionSelect = { viewModel.updateTransition(it) },
                    )
                }

                // Music Section
                item {
                    SectionTitle(title = "Music", icon = Icons.Default.MusicNote)
                    MusicSelector(
                        musicSelected = uiState.musicSelected,
                        musicTitle = uiState.musicTitle,
                        onMusicSelect = { musicPickerLauncher.launch(arrayOf("audio/*")) },
                        onMusicRemove = { viewModel.removeMusic() },
                    )
                    if (uiState.musicSelected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (uiState.isLoadingWaveform) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        } else if (uiState.trackDurationMs > 0) {
                            MusicTrimBar(
                                waveform = uiState.waveform,
                                trackDurationMs = uiState.trackDurationMs,
                                videoDurationMs = uiState.videoDurationMs,
                                startMs = uiState.musicStartMs,
                                endMs = uiState.effectiveEndMs,
                                onStartChanged = { viewModel.setMusicTrim(it, uiState.effectiveEndMs) },
                                onEndChanged = { viewModel.setMusicTrim(uiState.musicStartMs, it) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // Preview section
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text(
                                text = "Preview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aspect Ratio: ${uiState.selectedAspectRatio.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Template: ${uiState.selectedTemplate.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Photo Duration: ${uiState.selectedTemplate.photoDurationMs / 1000}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AspectRatioSelector(
    selectedAspectRatio: AspectRatio,
    onAspectRatioSelect: (AspectRatio) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AspectRatio.values().forEach { ratio ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedAspectRatio == ratio,
                            onClick = { onAspectRatioSelect(ratio) },
                        ),
                border =
                    if (selectedAspectRatio == ratio) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (selectedAspectRatio == ratio) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = ratio.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (selectedAspectRatio == ratio) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
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
    onTemplateSelect: (TemplatePreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TemplatePreset.values().forEach { template ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedTemplate == template,
                            onClick = { onTemplateSelect(template) },
                        ),
                border =
                    if (selectedTemplate == template) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (selectedTemplate == template) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                    ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = template.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (selectedTemplate == template) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Photos: ${template.photoDurationMs / 1000}s | Videos: ${template.videoHighlightDurationMs / 1000}s | ${template.defaultTransitionType.label} ${template.defaultTransitionDurationMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransitionSelector(
    selectedTemplate: TemplatePreset,
    selectedTransition: TransitionType?,
    onTransitionSelect: (TransitionType?) -> Unit,
) {
    val options =
        listOf(
            null to "Auto (${selectedTemplate.defaultTransitionType.label})",
            TransitionType.FADE_BLACK to TransitionType.FADE_BLACK.label,
            TransitionType.FADE_WHITE to TransitionType.FADE_WHITE.label,
            TransitionType.FLASH to TransitionType.FLASH.label,
            TransitionType.NONE to TransitionType.NONE.label,
        )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Override per-trip or leave Auto to use the template default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (type, label) ->
                FilterChip(
                    selected = selectedTransition == type,
                    onClick = { onTransitionSelect(type) },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicSelector(
    musicSelected: Boolean,
    musicTitle: String?,
    onMusicSelect: () -> Unit,
    onMusicRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onMusicSelect,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (musicSelected) Icons.Default.MusicNote else Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (musicSelected) musicTitle ?: "Music Selected" else "Select Music",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = if (musicSelected) "Tap to change" else "Add background music to your video",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (musicSelected) {
                IconButton(onClick = onMusicRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove music",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
