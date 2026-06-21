package com.routesnap.app.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.routesnap.app.domain.model.OverlayType
import com.routesnap.app.domain.model.SegmentOverlay
import com.routesnap.app.domain.model.TripSegment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayBottomSheet(
    segment: TripSegment,
    locationName: String?,
    onDismiss: () -> Unit,
    onConfirm: (SegmentOverlay) -> Unit,
    onRemove: () -> Unit,
) {
    val existing = segment.overlay
    var overlayType by remember { mutableStateOf(existing?.type ?: OverlayType.COMMENT) }
    var text by remember(overlayType) {
        mutableStateOf(
            when {
                existing != null && existing.type == overlayType -> existing.text
                overlayType == OverlayType.LOCATION -> locationName ?: ""
                else -> ""
            },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add text overlay", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = overlayType == OverlayType.COMMENT,
                    onClick = { overlayType = OverlayType.COMMENT },
                    label = { Text("Comment") },
                )
                FilterChip(
                    selected = overlayType == OverlayType.LOCATION,
                    onClick = { overlayType = OverlayType.LOCATION },
                    label = { Text("Location") },
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(if (overlayType == OverlayType.LOCATION) "Location name" else "Comment") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 2,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (existing != null) {
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
                Button(
                    onClick = { if (text.isNotBlank()) onConfirm(SegmentOverlay(text.trim(), overlayType)) },
                    enabled = text.isNotBlank(),
                ) { Text("Confirm") }
            }
        }
    }
}
