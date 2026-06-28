package com.uvindu.linuxsyncandroid.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uvindu.linuxsyncandroid.domain.model.DashboardState
import com.uvindu.linuxsyncandroid.presentation.components.ActionToggleCard
import com.uvindu.linuxsyncandroid.presentation.components.BatteryCard
import com.uvindu.linuxsyncandroid.presentation.components.DeviceConnectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDashboardScreen(
    state: DashboardState,
    onConfigToggle: (String, Boolean) -> Unit,
    onDisconnectRequested: () -> Unit,
    onUnpairRequested: () -> Unit
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Linux Node Dashboard", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onDisconnectRequested) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Disconnect",
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
                .padding(horizontal = 20.dp)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!state.areNotificationsEnabled) {
                NotificationPermissionWarning()
            }
            
            DeviceConnectionCard(
                deviceName = state.deviceName,
                connectionMessage = state.connectionMessage,
                isConnected = state.isConnected
            )

            if (state.isConnected) {
                BatteryCard(level = state.batteryLevel, isCharging = state.isCharging)

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
                        ActionToggleCard(
                            title = "Mute Volume",
                            icon = Icons.AutoMirrored.Filled.VolumeOff,
                            active = state.isMuted,
                            onToggleChanged = { onConfigToggle("mute_audio", it) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ActionToggleCard(
                            title = "Block Alerts (DND)",
                            icon = Icons.Default.NotificationsOff,
                            active = state.isDndEnabled,
                            onToggleChanged = { onConfigToggle("dnd_mode", it) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onUnpairRequested,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        "Unpair Device",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}