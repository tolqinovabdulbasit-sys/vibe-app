package com.vibeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenVibrationEditor: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("CONNECTIONS", color = TextTertiary, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))

            SettingsRow(
                icon = Icons.Default.Devices,
                title = "Paired Devices",
                subtitle = "Manage connected phones",
                onClick = onOpenDevices
            )

            Spacer(Modifier.height(8.dp))
            Text("VIBRATIONS", color = TextTertiary, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))

            SettingsRow(
                icon = Icons.Default.Vibration,
                title = "Vibration Patterns",
                subtitle = "Configure all 10 patterns",
                onClick = onOpenVibrationEditor
            )

            Spacer(Modifier.height(8.dp))
            Text("ABOUT", color = TextTertiary, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))

            SettingsInfoRow(label = "Version", value = "1.0.0")
            SettingsInfoRow(label = "Package", value = "com.vibeapp")
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimaryDim),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = PrimaryVariant, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextTertiary)
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp)
        Text(value, color = TextTertiary, fontSize = 14.sp)
    }
}
