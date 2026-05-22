package com.uvindu.linuxsyncandroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.uvindu.linuxsyncandroid.LinuxSync
import com.uvindu.linuxsyncandroid.domain.model.ConnectionState
import com.uvindu.linuxsyncandroid.domain.model.PairedDevice
import com.uvindu.linuxsyncandroid.domain.repository.DeviceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.take
import okhttp3.*
import java.util.concurrent.TimeUnit

class LinuxSyncService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var connectionJob: Job? = null

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep connection streaming open indefinitely
        .retryOnConnectionFailure(true)
        .build()

    // Must be initialized by dependency injection (Hilt) or manually inside onCreate()
    lateinit var deviceRepository: DeviceRepository

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "linux_sync_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Grab the initialized repository instance directly from the global Application context safely
        val app = application as LinuxSync
        deviceRepository = app.deviceRepository

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Initializing LinuxSync..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Initializing LinuxSync..."))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Check if the incoming intent is our test payload delivery
        if (intent != null && intent.action == "ACTION_SEND_TEST_PAYLOAD") {
            val payload = intent.getStringExtra("EXTRA_PAYLOAD")
            val key = intent.getStringExtra("EXTRA_KEY") ?: ""
            if (payload != null) {
                sendEncryptedData(payload, key)
            }
        } else {
            // 2. Otherwise, it's a cold boot request; trigger the tracking loop
            observeDeviceAndConnect()
        }

        return START_STICKY
    }

    private fun observeDeviceAndConnect() {
        // Cancel any pending connection setups before triggering a new collector thread
        connectionJob?.cancel()

        connectionJob = serviceScope.launch {
            // Using take(1) gets the current stored pair configurations safely without an infinite state leak
            deviceRepository.getPairedDevice().take(1).collect { device ->
                webSocket?.close(1000, "Re-establishing socket connection profile")

                _connectionState.value = ConnectionState.Connecting
                connectToWebSocket(device.ip, device.port, device.token, device.encKey)
            }
        }
    }

    private fun connectToWebSocket(ip: String, port: Int, token: String, encKey: String) {
        // 1. Remove the HTTP header. The Python server doesn't read it.
        val request = Request.Builder()
            .url("ws://$ip:$port")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                android.util.Log.d("LinuxSyncService", " [CONNECTED] WebSocket opened successfully. Sending auth...")
                val authPayload = """{"type": "auth", "token": "$token", "device_name": "Android Phone"}"""
                webSocket.send(authPayload)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                android.util.Log.d("LinuxSyncService", " [MESSAGE RECEIVED] From Linux: $text")
                if (text.contains("\"type\":\"auth_ok\"") || text.contains("auth_ok")) {
                    _connectionState.value = ConnectionState.Connected(
                        PairedDevice(deviceName = "Linux Desktop", ip = ip, port = port, token = token, encKey = encKey)
                    )
                    updateNotification("Connected to Linux Desktop")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.w("LinuxSyncService", " [CLOSING] Linux server is closing socket: $reason (Code: $code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.Disconnected
                updateNotification("Connection lost. Retrying...")

                // CRITICAL DEBUG LOG ADDED HERE:
                android.util.Log.e("LinuxSyncService", "❌ [SOCKET FAILURE] Reason: ${t.message}", t)
                response?.let {
                    android.util.Log.e("LinuxSyncService", "❌ [SOCKET FAILURE] HTTP Response Code: ${it.code}")
                }

                serviceScope.launch {
                    delay(5000)
                    observeDeviceAndConnect()
                }
            }
        })
    }

    fun sendEncryptedData(payloadJson: String, encKey: String) {
        serviceScope.launch {
            // Check if socket is open and ready
            if (webSocket != null) {
                // NOTE: Bypassing encryption wrap temporarily for manual testing setup
                val debugPayload = payloadJson

                val success = webSocket?.send(debugPayload) == true
                if (success) {
                    android.util.Log.d("LinuxSyncService", " Successfully pushed payload across WebSocket!")
                } else {
                    android.util.Log.e("LinuxSyncService", " Failed to push payload. Buffer full or socket disconnected.")
                }
            } else {
                android.util.Log.e("LinuxSyncService", " Cannot send payload. WebSocket connection instance is null.")
            }
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LinuxSync Client Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LinuxSync Background Communication Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = ContextCompat.getSystemService(this, NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "App Sync Service Lifecycle Terminated")
        serviceScope.cancel()
    }
}