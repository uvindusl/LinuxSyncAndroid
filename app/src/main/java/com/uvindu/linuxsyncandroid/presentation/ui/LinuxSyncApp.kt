package com.uvindu.linuxsyncandroid.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uvindu.linuxsyncandroid.LinuxSync
import com.uvindu.linuxsyncandroid.presentation.DashboardViewModel
import com.uvindu.linuxsyncandroid.presentation.ManualConnectViewModel

@Composable
fun LinuxSyncApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext as LinuxSync
    val repository = appContext.deviceRepository

    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(repository) as T
            }
        }
    )

    val manualConnectViewModel: ManualConnectViewModel = viewModel()

    val uiState = dashboardViewModel.uiState

    LaunchedEffect(Unit) {
        dashboardViewModel.checkNotificationPermission(context)
    }

    if (uiState.isConnected || uiState.connectionMessage == "Connecting...") {
        DeviceDashboardScreen(
            state = uiState,
            onConfigToggle = { actionKey, value ->
                dashboardViewModel.updateRemoteConfig(actionKey, value)
            },
            onDisconnectRequested = {
                dashboardViewModel.terminateConnection()
            },
            onUnpairRequested = {
                dashboardViewModel.unpairDevice()
            }
        )
    } else {
        ManualConnectScreen(
            viewModel = manualConnectViewModel,
            onSaveConfig = { payload ->
                dashboardViewModel.establishConnection(payload, context)
            },
            onBackToScanner = {}
        )
    }
}
