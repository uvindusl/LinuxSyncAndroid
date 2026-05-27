package com.uvindu.linuxsyncandroid.presentation.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uvindu.linuxsyncandroid.domain.model.DashboardState
import com.uvindu.linuxsyncandroid.utils.openNotificationSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDashboardScreen(
    state: DashboardState,
    onConfigToggle: (String, Boolean) -> Unit,
    onDisconnectRequested: () -> Unit
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Linux Node Dashboard", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onDisconnectRequested) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Kill Link Connection",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!state.areNotificationsEnabled) {
                NotificationPermissionWarning()
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isConnected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = null,
                        tint = if (state.isConnected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = state.deviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.connectionMessage,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (state.isConnected) {
                LaptopBatteryVisualizer(level = state.batteryLevel, isCharging = state.isCharging)

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "System Operations",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ActionToggleTile(
                            title = "Mute Volume",
                            icon = Icons.AutoMirrored.Filled.VolumeOff,
                            active = state.isMuted,
                            onToggleChanged = { onConfigToggle("mute_audio", it) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ActionToggleTile(
                            title = "Block Alerts (DND)",
                            icon = Icons.Default.NotificationsOff,
                            active = state.isDndEnabled,
                            onToggleChanged = { onConfigToggle("dnd_mode", it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LaptopBatteryVisualizer(level: Int, isCharging: Boolean) {
    val progressAnimation by animateFloatAsState(targetValue = level / 100f, label = "levelBar")
    val dynamicThemeColor by animateColorAsState(
        targetValue = when {
            isCharging -> MaterialTheme.colorScheme.primary
            level <= 18 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.tertiary
        }, label = "tintColor"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BatteryFull, contentDescription = null, tint = dynamicThemeColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Laptop Battery", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                Text(
                    text = if (isCharging) "$level% ⚡" else "$level%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = dynamicThemeColor
                )
            }
            LinearProgressIndicator(
                progress = { progressAnimation },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = dynamicThemeColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}

@Composable
fun ActionToggleTile(title: String, icon: ImageVector, active: Boolean, onToggleChanged: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth().height(115.dp),
        onClick = { onToggleChanged(!active) }
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(checked = active, onCheckedChange = onToggleChanged, modifier = Modifier.scale(0.75f))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}