package com.uvindu.linuxsyncandroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.uvindu.linuxsyncandroid.domain.model.ConnectionState
import com.uvindu.linuxsyncandroid.service.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LinuxSyncService : Service() {

    private lateinit var clipboardService: ClipboardService
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var clipboardReceiver: ((org.json.JSONObject) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        clipboardService = ClipboardService(this)

        // Register clipboard as a message receiver
        clipboardReceiver = { msg -> clipboardService.handleIncomingMessage(msg) }
        WebSocketManager.addMessageReceiver(clipboardReceiver!!)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Waiting for connection..."),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Waiting for connection..."))
        }

        scope.launch {
            WebSocketManager.connectionState.collectLatest { state ->
                val text = when (state) {
                    is ConnectionState.Connected    -> "Connected to ${state.device.deviceName}"
                    is ConnectionState.Connecting   -> "Connecting..."
                    is ConnectionState.Disconnected -> "Disconnected"
                }
                updateNotification(text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        clipboardReceiver?.let { WebSocketManager.removeMessageReceiver(it) }
        clipboardReceiver = null
        clipboardService.cleanup()
        WebSocketManager.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LinuxSync")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        ContextCompat.getSystemService(this, NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LinuxSync Connection",
            NotificationManager.IMPORTANCE_LOW
        )
        ContextCompat.getSystemService(this, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "LinuxSyncForegroundServiceChannel"
        const val NOTIFICATION_ID = 1
    }
}
