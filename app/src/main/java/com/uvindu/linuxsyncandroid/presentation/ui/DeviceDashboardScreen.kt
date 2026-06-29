package com.uvindu.linuxsyncandroid.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uvindu.linuxsyncandroid.domain.model.DashboardState
import com.uvindu.linuxsyncandroid.presentation.components.DeviceConnectionCard
import com.uvindu.linuxsyncandroid.presentation.components.NowPlayingCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDashboardScreen(
    state: DashboardState,
    onDisconnectRequested: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "LinuxSync",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onDisconnectRequested) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Disconnect",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.areNotificationsEnabled) {
                item {
                    NotificationPermissionWarning()
                }
            }

            item {
                DeviceConnectionCard(
                    deviceName = state.deviceName,
                    connectionMessage = state.connectionMessage,
                    isConnected = state.isConnected
                )
            }

            if (state.isConnected) {
                item {
                    NowPlayingCard(
                        phoneTitle = state.nowPlayingTitle,
                        phoneArtist = state.nowPlayingArtist,
                        phoneApp = state.nowPlayingApp,
                        phoneIsPlaying = state.nowPlayingIsPlaying,
                        laptopTrackTitle = state.laptopTrackTitle,
                        laptopTrackArtist = state.laptopTrackArtist,
                        laptopTrackAlbum = state.laptopTrackAlbum,
                        laptopIsPlaying = state.laptopIsPlaying,
                        laptopArtUrl = state.laptopArtUrl,
                    )
                }
            }
        }
    }
}
