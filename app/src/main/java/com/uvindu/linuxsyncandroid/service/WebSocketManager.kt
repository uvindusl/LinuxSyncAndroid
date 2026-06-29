package com.uvindu.linuxsyncandroid.service

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.uvindu.linuxsyncandroid.domain.model.ConnectionState
import com.uvindu.linuxsyncandroid.domain.model.MessageType
import com.uvindu.linuxsyncandroid.domain.model.PairedDevice
import com.uvindu.linuxsyncandroid.utils.crypto.CryptoUtil
import com.uvindu.linuxsyncandroid.utils.network.MDNSResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

object WebSocketManager {

    private const val TAG = "WebSocketManager"
    @Volatile
    private var isSocketConnected = false
    @Volatile
    private var connectionAttemptInProgress = false
    @Volatile
    private var shouldAbortConnection = false
    private val messageQueue = ConcurrentLinkedQueue<JSONObject>()

    @Volatile
    private var webSocket: WebSocket? = null
    @Volatile
    private var currentDevice: PairedDevice? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    // We store a weak application context or pass context explicitly to prevent leaks
    private var appContext: Context? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val messageReceivers = CopyOnWriteArrayList<(JSONObject) -> Unit>()

    fun addMessageReceiver(receiver: (JSONObject) -> Unit) {
        messageReceivers.add(receiver)
    }

    fun removeMessageReceiver(receiver: (JSONObject) -> Unit) {
        messageReceivers.remove(receiver)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect(context: Context, device: PairedDevice) {
        if (_connectionState.value is ConnectionState.Connected) return
        if (connectionAttemptInProgress) return

        this.appContext = context.applicationContext
        currentDevice = device
        cancelReconnect()
        scope.launch {
            attemptConnect(device)
        }
    }

    private suspend fun attemptConnect(device: PairedDevice) {
        if (connectionAttemptInProgress) return
        connectionAttemptInProgress = true
        _connectionState.value = ConnectionState.Connecting

        var resolvedIp = device.ip
        if (!device.ip.contains(".") || device.ip.endsWith(".local") || device.ip.endsWith(".local.")) {
            Log.d(TAG, "Attempting mDNS resolution for ${device.ip}")
            val mdnsIp = MDNSResolver.resolveHostname(device.ip)
            if (mdnsIp != null) {
                Log.d(TAG, "mDNS resolved to: $mdnsIp")
                resolvedIp = mdnsIp
            } else {
                Log.w(TAG, "mDNS resolution failed, using original IP: ${device.ip}")
            }
        }

        val request = Request.Builder()
            .url("ws://$resolvedIp:${device.port}")
            .build()

        client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Socket open — sending auth")
                shouldAbortConnection = false
                webSocket = ws

                val context = appContext
                val deviceName = if (context != null) getUserDeviceName(context) else "Android Device"

                val auth = JSONObject().apply {
                    put("type",        MessageType.AUTH)
                    put("token",       device.token)
                    put("device_name", deviceName)
                }
                ws.send(auth.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    if (_connectionState.value is ConnectionState.Connecting) {
                        try {
                            val msg = JSONObject(text)
                            if (msg.optString("type") == MessageType.AUTH_OK) {
                                Log.d(TAG, "Auth OK — connected")

                                connectionAttemptInProgress = false
                                isSocketConnected = true
                                _connectionState.value = ConnectionState.Connected(device)
                                cancelReconnect()

                                scope.launch {
                                    while (messageQueue.isNotEmpty()) {
                                        messageQueue.poll()?.let { queuedMsg ->
                                            Log.d(TAG, "Sending queued message: ${queuedMsg.optString("type")}")
                                            sendMessage(queuedMsg)
                                        }
                                    }
                                }
                                return
                            }
                        } catch (_: Exception) {}
                        // Non-AUTH message while connecting — not encrypted yet, ignore
                        return
                    }

                    val keyBytes  = CryptoUtil.decodeKey(device.encKey)
                    val plaintext = CryptoUtil.decrypt(text, keyBytes)
                    val data      = JSONObject(plaintext)

                    messageReceivers.forEach { receiver ->
                        try {
                            receiver(data)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in message receiver: ${e.message}")
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Message error: ${e.message}")
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                handleDisconnect(shouldReconnect = code != 1000)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Failure: ${t.message}")
                handleDisconnect(shouldReconnect = true)
            }
        })
    }

    // 2. Moved helper function outside of the anonymous WebSocketListener
    private fun getUserDeviceName(context: Context): String {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        } else {
            Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        }
        return name ?: "Unknown Android Device"
    }

    fun sendMessage(msg: JSONObject) {
        if (!isSocketConnected) {
            messageQueue.add(msg)
            return
        }
        try {
            val device = currentDevice ?: return
            val keyBytes  = CryptoUtil.decodeKey(device.encKey)
            val encrypted = CryptoUtil.encrypt(msg.toString(), keyBytes)
            webSocket?.send(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Send error: ${e.message}")
        }
    }

    private fun handleDisconnect(shouldReconnect: Boolean) {
        connectionAttemptInProgress = false
        webSocket = null
        isSocketConnected = false
        while (messageQueue.poll() != null) { }
        _connectionState.value = ConnectionState.Disconnected
        if (shouldReconnect) startReconnectLoop()
    }

    private fun startReconnectLoop() {
        cancelReconnect()
        reconnectJob = scope.launch {
            var backoff = 2000L
            var attempts = 0
            while (isActive && _connectionState.value !is ConnectionState.Connected) {
                if (++attempts > 15) {
                    Log.w(TAG, "Giving up reconnection after $attempts attempts")
                    break
                }
                Log.d(TAG, "Reconnecting in ${backoff}ms (attempt $attempts)")
                delay(backoff)
                currentDevice?.let { attemptConnect(it) }
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    fun disconnect() {
        connectionAttemptInProgress = false
        cancelReconnect()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        isSocketConnected = false
        while (messageQueue.poll() != null) { }
        appContext = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }
}