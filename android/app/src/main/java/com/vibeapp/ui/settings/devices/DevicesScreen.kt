package com.vibeapp.ui.settings.devices

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeapp.data.repository.PairedDevice
import com.vibeapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    onAddDevice: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val devices by viewModel.pairedDevices.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сопряженные устройства", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад", tint = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = onAddDevice) {
                        Icon(Icons.Default.PersonAdd, "Добавить устройство", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddDevice,
                containerColor = Primary,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Default.Add, "Добавить устройство")
            }
        },
        containerColor = Background
    ) { padding ->

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhoneAndroid, null, tint = TextTertiary,
                        modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Нет сопряженных устройств", color = TextTertiary, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Нажмите +, чтобы добавить устройство", color = TextTertiary, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(devices, key = { it.deviceId }) { device ->
                    DeviceCard(
                        device = device,
                        onRename = { newName -> viewModel.renameDevice(device.deviceId, newName) },
                        onRemove = { viewModel.removeDevice(device.deviceId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: PairedDevice,
    onRename: (String) -> Unit,
    onRemove: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val isOnline = device.status == "ONLINE" || device.status == "ACTIVE"
    val statusColor = if (isOnline) ColorConnected else ColorDisconnected
    val statusLabel = if (isOnline) "В сети" else "Не в сети"

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar circle with initial
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(PrimaryDim),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        device.alias.firstOrNull()?.uppercase() ?: "?",
                        color = PrimaryVariant,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(device.alias, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(statusLabel, color = statusColor, fontSize = 12.sp)

                        device.lastConnectedAt?.let { ts ->
                            Text(
                                "  ·  Был(а) в сети ${formatRelativeTime(ts)}",
                                color = TextTertiary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.MoreVert,
                        null,
                        tint = TextSecondary
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showRenameDialog = true },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Переименовать")
                    }

                    OutlinedButton(
                        onClick = { showRemoveConfirm = true },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, ColorFailed.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorFailed)
                    ) {
                        Icon(Icons.Default.LinkOff, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Удалить")
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            currentName = device.alias,
            onConfirm = { onRename(it); showRenameDialog = false },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Удалить устройство?", color = TextPrimary) },
            text = { Text("Удалить ${device.alias}? Для повторного подключения потребуется снова выполнить сопряжение.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onRemove(); showRemoveConfirm = false }) {
                    Text("Удалить", color = ColorFailed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text("Отмена", color = TextSecondary)
                }
            },
            containerColor = SurfaceElevated
        )
    }
}

@Composable
private fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переименовать устройство", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = BorderSubtle,
                    cursorColor = Primary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) {
                Text("Сохранить", color = Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = TextSecondary) }
        },
        containerColor = SurfaceElevated
    )
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "только что"
        diff < 3_600_000 -> "${diff / 60_000}м назад"
        diff < 86_400_000 -> "${diff / 3_600_000}ч назад"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
    }
}
