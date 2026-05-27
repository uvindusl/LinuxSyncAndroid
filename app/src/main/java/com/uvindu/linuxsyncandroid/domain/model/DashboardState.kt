package com.uvindu.linuxsyncandroid.domain.model

data class DashboardState(
    val isConnected: Boolean = false,
    val deviceName: String = "Not Connected",
    val connectionMessage: String = "Disconnected",
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val isMuted: Boolean = false,
    val isDndEnabled: Boolean = false,
    val areNotificationsEnabled: Boolean = true
)