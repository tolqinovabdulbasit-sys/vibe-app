package com.vibeapp.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeapp.core.network.ConnectionState
import com.vibeapp.data.model.DeliveryStatus
import com.vibeapp.data.repository.HistoryEntry
import com.vibeapp.data.repository.PairedDevice
import com.vibeapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    onOpenPairing: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // ─── Header ───────────────────────────────────────────────
            MainHeader(
                connectionState = state.connectionState,
                pairedDevices = state.pairedDevices,
                selectedDeviceIds = state.selectedDeviceIds,
                onDeviceSelected = { id, multi -> viewModel.selectDevice(id, multi) },
                onOpenSettings = onOpenSettings,
                onAddDevice = onOpenPairing
            )

            // ─── Main Buttons (2×2 grid) ──────────────────────────────
            MainButtonGrid(
                buttons = state.mainButtons,
                isConnected = state.connectionState == ConnectionState.CONNECTED,
                onButtonClick = { btn -> viewModel.sendPattern(btn.patternSlot, btn.label) }
            )

            Spacer(Modifier.height(12.dp))

            // ─── Middle zone: Side buttons + Manual hold ───────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SideButtonColumn(
                    buttons = state.sideButtons,
                    isConnected = state.connectionState == ConnectionState.CONNECTED,
                    onButtonClick = { btn -> viewModel.sendPattern(btn.patternSlot, btn.label) },
                    modifier = Modifier.width(80.dp)
                )

                ManualHoldButton(
                    isActive = state.isManualActive,
                    isConnected = state.connectionState == ConnectionState.CONNECTED,
                    onPress = { viewModel.startManualVibration() },
                    onRelease = { viewModel.stopManualVibration() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            Spacer(Modifier.height(12.dp))

            // ─── History ───────────────────────────────────────────────
            HistorySection(
                history = state.history,
                onClear = { viewModel.clearHistory() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainHeader(
    connectionState: ConnectionState,
    pairedDevices: List<PairedDevice>,
    selectedDeviceIds: Set<String>,
    onDeviceSelected: (String, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onAddDevice: () -> Unit
) {
    var showDeviceDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Connection status indicator
        ConnectionStatusChip(connectionState)

        Spacer(Modifier.weight(1f))

        // Device selector
        if (pairedDevices.isNotEmpty()) {
            Box {
                val displayName = when {
                    selectedDeviceIds.size == 1 ->
                        pairedDevices.find { it.deviceId == selectedDeviceIds.first() }?.alias ?: "Device"
                    selectedDeviceIds.size > 1 -> "${selectedDeviceIds.size} devices"
                    else -> "Select device"
                }

                OutlinedButton(
                    onClick = { showDeviceDropdown = true },
                    border = BorderStroke(1.dp, BorderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(displayName, fontSize = 13.sp, maxLines = 1)
                    Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                }

                DropdownMenu(
                    expanded = showDeviceDropdown,
                    onDismissRequest = { showDeviceDropdown = false },
                    containerColor = SurfaceElevated
                ) {
                    pairedDevices.forEach { device ->
                        val isSelected = selectedDeviceIds.contains(device.deviceId)
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    DeviceStatusDot(device.status)
                                    Spacer(Modifier.width(8.dp))
                                    Text(device.alias, color = TextPrimary)
                                }
                            },
                            trailingIcon = {
                                if (isSelected) Icon(Icons.Default.Check, null, tint = Primary)
                            },
                            onClick = {
                                onDeviceSelected(device.deviceId, false)
                                showDeviceDropdown = false
                            }
                        )
                    }
                    HorizontalDivider(color = BorderSubtle)
                    DropdownMenuItem(
                        text = { Text("Add device", color = PrimaryVariant) },
                        leadingIcon = { Icon(Icons.Default.Add, null, tint = PrimaryVariant) },
                        onClick = { showDeviceDropdown = false; onAddDevice() }
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
        }

        // Settings icon
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, "Settings", tint = TextSecondary)
        }
    }
}

@Composable
private fun ConnectionStatusChip(state: ConnectionState) {
    val (dotColor, label) = when (state) {
        ConnectionState.CONNECTED -> ColorConnected to "Connected"
        ConnectionState.CONNECTING -> ColorConnecting to "Connecting"
        ConnectionState.RECONNECTING -> ColorReconnecting to "Reconnecting"
        ConnectionState.DISCONNECTED -> ColorDisconnected to "Offline"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == ConnectionState.CONNECTED) 0.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = alpha))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = dotColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DeviceStatusDot(status: String) {
    val color = when (status) {
        "ONLINE", "ACTIVE" -> ColorConnected
        "OFFLINE" -> ColorDisconnected
        else -> TextTertiary
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Main 2×2 Button Grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MainButtonGrid(
    buttons: List<MainButton>,
    isConnected: Boolean,
    onButtonClick: (MainButton) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        buttons.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { btn ->
                    MainPatternButton(
                        label = btn.label,
                        enabled = isConnected,
                        onClick = { onButtonClick(btn) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MainPatternButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f, label = "btn_scale")

    Box(
        modifier = modifier
            .height(72.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = if (enabled) Brush.linearGradient(
                    listOf(Primary, PrimaryVariant)
                ) else Brush.linearGradient(
                    listOf(SurfaceVariant, SurfaceVariant)
                )
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        tryAwaitRelease()
                        isPressed = false
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) TextPrimary else TextTertiary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Side Button Column
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SideButtonColumn(
    buttons: List<SideButton>,
    isConnected: Boolean,
    onButtonClick: (SideButton) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        buttons.forEach { btn ->
            SidePatternButton(
                label = btn.label,
                enabled = isConnected,
                onClick = { onButtonClick(btn) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SidePatternButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.92f else 1f, label = "side_scale")

    Box(
        modifier = modifier
            .aspectRatio(1.6f)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) SurfaceElevated else SurfaceVariant)
            .border(1.dp, if (enabled) BorderSubtle else Color.Transparent, RoundedCornerShape(12.dp))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tryAwaitRelease()
                        isPressed = false
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) TextPrimary else TextTertiary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Manual Hold Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ManualHoldButton(
    isActive: Boolean,
    isConnected: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "manual_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isActive) ButtonManualActive.copy(alpha = 0.15f)
        else SurfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "manual_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) ButtonManualActive else BorderSubtle,
        animationSpec = tween(200),
        label = "manual_border"
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) ButtonManualActive else TextSecondary,
        animationSpec = tween(200),
        label = "manual_text"
    )

    Box(
        modifier = modifier
            .scale(if (isActive) pulseScale else 1f)
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(20.dp))
            .pointerInput(isConnected) {
                if (!isConnected) return@pointerInput
                detectTapGestures(
                    onPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPress()
                        tryAwaitRelease()
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isActive) Icons.Default.Vibration else Icons.Default.TouchApp,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isActive) "VIBRATING" else "HOLD TO VIBRATE",
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )
            if (!isConnected) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Not connected",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// History Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistorySection(
    history: List<HistoryEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "History",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Clear", color = TextTertiary, fontSize = 12.sp)
                }
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No signals yet", color = TextTertiary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(history, key = { it.commandId }) { entry ->
                    HistoryRow(entry)
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    val (statusIcon, statusColor) = when (entry.deliveryStatus) {
        "VIBRATION_STARTED", "CONFIRMED" -> "✓" to ColorVibrationStarted
        "DELIVERED", "RECEIVED" -> "✓" to ColorDelivered
        "SENT" -> "→" to ColorConnecting
        "FAILED" -> "×" to ColorFailed
        "TIMEOUT" -> "⏱" to ColorTimeout
        else -> "·" to TextTertiary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Direction arrow
        Text(
            text = if (entry.direction == "OUTGOING") "↑" else "↓",
            color = if (entry.direction == "OUTGOING") Primary else ColorConnected,
            fontSize = 12.sp
        )

        // Time
        Text(
            text = timeFormat.format(Date(entry.createdAt)),
            color = TextTertiary,
            fontSize = 11.sp,
            modifier = Modifier.width(60.dp)
        )

        // Pattern name
        Text(
            text = "\"${entry.patternName}\"",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Device alias
        Text(
            text = entry.deviceAlias ?: entry.targetDeviceId.take(6),
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.widthIn(max = 70.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Status + latency
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = statusIcon, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (entry.latencyMs != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${entry.latencyMs}ms",
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
