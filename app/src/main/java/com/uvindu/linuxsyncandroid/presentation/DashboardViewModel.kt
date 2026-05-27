package com.uvindu.linuxsyncandroid.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.app.NotificationManagerCompat
import com.uvindu.linuxsyncandroid.domain.model.ConnectionState
import com.uvindu.linuxsyncandroid.domain.model.DashboardState
import com.uvindu.linuxsyncandroid.domain.model.PairedDevice
import com.uvindu.linuxsyncandroid.domain.model.QRCodePayload
import com.uvindu.linuxsyncandroid.domain.repository.DeviceRepository
import com.uvindu.linuxsyncandroid.service.WebSocketManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject

class DashboardViewModel(
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    var uiState by mutableStateOf(DashboardState())
        private set

    private val messageHandler: (JSONObject) -> Unit = { msg -> handleIncoming(msg) }

    init {
        // observe WebSocketManager connection state
        WebSocketManager.connectionState.onEach { state ->
            uiState = when (state) {
                is ConnectionState.Connected -> uiState.copy(
                    isConnected       = true,
                    deviceName        = state.device.deviceName,
                    connectionMessage = "Connected to ${state.device.deviceName}"
                )
                is ConnectionState.Connecting -> uiState.copy(
                    isConnected       = false,
                    connectionMessage = "Connecting..."
                )
                is ConnectionState.Disconnected -> uiState.copy(
                    isConnected       = false,
                    connectionMessage = "Disconnected"
                )
            }
        }.launchIn(viewModelScope)

        // Register as a message receiver
        WebSocketManager.addMessageReceiver(messageHandler)
    }

    fun checkNotificationPermission(context: Context) {
        val areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        uiState = uiState.copy(areNotificationsEnabled = areNotificationsEnabled)
    }

    fun establishConnection(payload: QRCodePayload, context: Context) {
        viewModelScope.launch {
            val device = PairedDevice(
                deviceName = payload.dn,
                ip         = payload.ip,
                port       = payload.pt,
                token      = payload.tk,
                encKey     = payload.ek
            )
            // save to DataStore so it persists
            deviceRepository.savePairedDevice(device)

            // connect via WebSocketManager
            WebSocketManager.connect(device)
        }
    }

    private fun handleIncoming(msg: JSONObject) {
        when (msg.optString("type")) {
            "battery" -> {
                uiState = uiState.copy(
                    batteryLevel = msg.optInt("level", 0),
                    isCharging = msg.optBoolean("is_charging", false)
                )
            }
            "notification_reply" -> {
                // TODO: fire reply PendingIntent
            }
            "call_action" -> {
                // TODO: accept/decline via TelecomManager
            }
        }
    }

    fun updateRemoteConfig(actionKey: String, value: Boolean) {
        uiState = when (actionKey) {
            "mute_audio" -> uiState.copy(isMuted = value)
            "dnd_mode"   -> uiState.copy(isDndEnabled = value)
            else         -> uiState
        }
        val msg = JSONObject().apply {
            put("type",   "config")
            put("action", actionKey)
            put("value",  value)
        }
        WebSocketManager.sendMessage(msg)
    }

    fun terminateConnection() {
        WebSocketManager.disconnect()
        uiState = DashboardState()
    }

    override fun onCleared() {
        super.onCleared()
        WebSocketManager.removeMessageReceiver(messageHandler)
    }
}