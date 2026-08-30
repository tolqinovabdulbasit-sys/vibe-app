package com.vibeapp.ui.settings.patterns

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeapp.data.model.*
import com.vibeapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibrationEditorScreen(
    onBack: () -> Unit,
    viewModel: VibrationEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.editingSlot != null) {
        val pattern = state.patterns.find { it.slot == state.editingSlot }
        if (pattern != null) {
            PatternEditDialog(
                pattern = pattern,
                onTest = { viewModel.testPattern(it) },
                onSave = { steps -> viewModel.updatePattern(pattern.slot, steps) },
                onDismiss = { viewModel.cancelEditing() }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vibration Patterns", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        },
        containerColor = Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.patterns, key = { it.slot }) { pattern ->
                PatternCard(
                    pattern = pattern,
                    onToggle = { viewModel.toggleEnabled(pattern.slot, it) },
                    onTest = { viewModel.testPattern(pattern) },
                    onEdit = { viewModel.startEditing(pattern.slot) }
                )
            }
        }
    }
}

@Composable
private fun PatternCard(
    pattern: VibrationPattern,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit
) {
    var showRename by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Slot number badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (pattern.enabled) PrimaryDim else SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "%02d".format(pattern.slot),
                    color = if (pattern.enabled) PrimaryVariant else TextTertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pattern.name,
                    color = if (pattern.enabled) TextPrimary else TextTertiary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(3.dp))
                // Pattern visualization
                PatternVisualizer(pattern.steps, pattern.enabled)
            }

            Spacer(Modifier.width(8.dp))

            // Test button
            IconButton(onClick = onTest, enabled = pattern.enabled) {
                Icon(Icons.Default.PlayArrow, "Test", tint = if (pattern.enabled) ColorConnected else TextTertiary)
            }

            // Edit button
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit", tint = Primary)
            }

            // Enable toggle
            Switch(
                checked = pattern.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextPrimary,
                    checkedTrackColor = Primary
                )
            )
        }
    }
}

@Composable
private fun PatternVisualizer(steps: List<PatternStep>, enabled: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(16.dp)
    ) {
        steps.take(12).forEach { step ->
            val widthDp = (step.durationMs / 100f).coerceIn(2f, 20f).dp
            Box(
                modifier = Modifier
                    .width(widthDp)
                    .height(if (step.type == StepType.VIBRATE) 12.dp else 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            !enabled -> TextTertiary
                            step.type == StepType.VIBRATE -> Primary
                            else -> BorderSubtle
                        }
                    )
            )
        }
        if (steps.size > 12) Text("…", color = TextTertiary, fontSize = 11.sp)
    }
}

@Composable
private fun PatternEditDialog(
    pattern: VibrationPattern,
    onTest: (VibrationPattern) -> Unit,
    onSave: (List<PatternStep>) -> Unit,
    onDismiss: () -> Unit
) {
    var steps by remember { mutableStateOf(pattern.steps.toMutableList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        modifier = Modifier.fillMaxWidth(),
        title = {
            Text("Edit: ${pattern.name}", color = TextPrimary, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                Text("Steps (ms):", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))

                // Steps editor
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.forEachIndexed { index, step ->
                        StepEditor(
                            step = step,
                            index = index,
                            onStepChange = { newStep ->
                                steps = steps.toMutableList().apply { set(index, newStep) }
                            },
                            onDelete = {
                                if (steps.size > 1) {
                                    steps = steps.toMutableList().apply { removeAt(index) }
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            steps = (steps + PatternStep(StepType.VIBRATE, 200)).toMutableList()
                        },
                        border = BorderStroke(1.dp, Primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Text("Vibrate")
                    }
                    OutlinedButton(
                        onClick = {
                            steps = (steps + PatternStep(StepType.PAUSE, 100)).toMutableList()
                        },
                        border = BorderStroke(1.dp, BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Text("Pause")
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onTest(VibrationPattern(pattern.slot, pattern.name, steps))
                }) {
                    Icon(Icons.Default.PlayArrow, null, tint = ColorConnected, modifier = Modifier.size(16.dp))
                    Text("Test", color = ColorConnected)
                }
                TextButton(onClick = { onSave(steps) }) {
                    Text("Save", color = Primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
private fun StepEditor(
    step: PatternStep,
    index: Int,
    onStepChange: (PatternStep) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Type indicator
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (step.type == StepType.VIBRATE) PrimaryDim else SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (step.type == StepType.VIBRATE) "VIBRATE" else "PAUSE",
                color = if (step.type == StepType.VIBRATE) PrimaryVariant else TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }

        // Duration slider
        Slider(
            value = step.durationMs.toFloat(),
            onValueChange = { onStepChange(step.copy(durationMs = it.toLong())) },
            valueRange = 50f..2000f,
            steps = 38,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary,
                inactiveTrackColor = BorderSubtle
            )
        )

        Text(
            "${step.durationMs}ms",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(42.dp)
        )

        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, "Delete", tint = ColorFailed, modifier = Modifier.size(16.dp))
        }
    }
}
