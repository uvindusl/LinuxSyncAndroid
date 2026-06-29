package com.uvindu.linuxsyncandroid.presentation

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel // Switched to AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.app.NotificationManagerCompat
import com.uvindu.linuxsyncandroid.domain.model.ConnectionState
import com.uvindu.linuxsyncandroid.domain.model.DashboardState
import com.uvindu.linuxsyncandroid.domain.model.PairedDevice
import com.uvindu.linuxsyncandroid.domain.model.QRCodePayload
import com.uvindu.linuxsyncandroid.domain.model.MessageType
import com.uvindu.linuxsyncandroid.domain.repository.DeviceRepository
import com.uvindu.linuxsyncandroid.service.WebSocketManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject

// 1. Changed base class to AndroidViewModel and added application constructor parameter
class DashboardViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    var uiState by mutableStateOf(DashboardState())
        private set

    private val messageHandler: (JSONObject) -> Unit = { msg -> handleIncoming(msg) }
    private var currentDevice: PairedDevice? = null
    private val TAG = "DashboardViewModel"

    init {
        // Load saved device on startup and auto-connect
        viewModelScope.launch {
            deviceRepository.getPairedDevice().collect { savedDevice ->
                if (savedDevice != null && currentDevice == null) {
                    currentDevice = savedDevice
                    Log.d(TAG, "Found saved device: ${savedDevice.deviceName}, attempting to connect...")

                    // 2. Used getApplication() to pass the global context here
                    WebSocketManager.connect(getApplication(), savedDevice)
                }
            }
        }

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
        val isListenerEnabled = com.uvindu.linuxsyncandroid.utils.isNotificationListenerEnabled(
            context,
            com.uvindu.linuxsyncandroid.service.LinkNotificationService::class.java
        )
        uiState = uiState.copy(areNotificationsEnabled = areNotificationsEnabled && isListenerEnabled)
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
            currentDevice = device
            // save to DataStore so it persists
            deviceRepository.savePairedDevice(device)

            // 3. Passed the incoming context parameter here
            WebSocketManager.connect(context, device)
        }
    }

    private fun handleIncoming(msg: JSONObject) {
        when (msg.optString("type")) {
            MessageType.BATTERY -> {
                val level = msg.optInt("level", 0)
                val charging = msg.optBoolean("is_charging", false)
                uiState = uiState.copy(batteryLevel = level, isCharging = charging)
            }
            MessageType.LAPTOP_NOW_PLAYING -> {
                uiState = uiState.copy(
                    laptopTrackTitle = msg.optString("title", null),
                    laptopTrackArtist = msg.optString("artist", null),
                    laptopTrackAlbum = msg.optString("album", null),
                    laptopIsPlaying = msg.optBoolean("is_playing", false),
                    laptopArtUrl = msg.optString("art_url", null)
                )
            }
            MessageType.NOW_PLAYING -> {
                val isPlaying = msg.optBoolean("is_playing", false)
                val title = msg.optString("title", null)
                uiState = uiState.copy(
                    nowPlayingTitle = title,
                    nowPlayingArtist = msg.optString("artist", null),
                    nowPlayingAlbum = msg.optString("album", null),
                    nowPlayingApp = msg.optString("app", null),
                    nowPlayingIsPlaying = isPlaying,
                    nowPlayingDuration = msg.optLong("duration", 0L)
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

    fun unpairDevice() {
        viewModelScope.launch {
            Log.d(TAG, "Unpairing device...")
            WebSocketManager.disconnect()
            deviceRepository.clearPairedDevice()
            currentDevice = null
            uiState = DashboardState()
        }
    }

    override fun onCleared() {
        super.onCleared()
        WebSocketManager.removeMessageReceiver(messageHandler)
    }
}