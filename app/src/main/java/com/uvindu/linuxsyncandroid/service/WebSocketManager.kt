package com.uvindu.linuxsyncandroid.service

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
import java.util.concurrent.TimeUnit

object WebSocketManager {

    private const val TAG = "WebSocketManager"
    @Volatile
    private var isSocketConnected = false
    private val messageQueue = ConcurrentLinkedQueue<JSONObject>()

    private var webSocket: WebSocket? = null
    private var currentDevice: PairedDevice? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val messageReceivers = mutableListOf<(JSONObject) -> Unit>()

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

    fun connect(device: PairedDevice) {
        if (_connectionState.value is ConnectionState.Connected ||
            _connectionState.value is ConnectionState.Connecting) return

        currentDevice = device
        cancelReconnect()
        scope.launch {
            attemptConnect(device)
        }
    }

    private suspend fun attemptConnect(device: PairedDevice) {
        _connectionState.value = ConnectionState.Connecting
        
        // Try to resolve mDNS hostname first for cross-network support
        var resolvedIp = device.ip
        if (device.ip.contains(".") == false || device.ip.endsWith(".local") || device.ip.endsWith(".local.")) {
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
                webSocket = ws
                val auth = JSONObject().apply {
                    put("type",        MessageType.AUTH)
                    put("token",       device.token)
                    put("device_name", android.os.Build.MODEL)
                }
                ws.send(auth.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    // auth_ok is plain JSON — encryption starts after this
                    if (_connectionState.value is ConnectionState.Connecting) {
                        val msg = JSONObject(text)
                        if (msg.optString("type") == MessageType.AUTH_OK) {
                            Log.d(TAG, "Auth OK — connected")
                            _connectionState.value = ConnectionState.Connected(device)
                            isSocketConnected = true
                            cancelReconnect()
                            // Process any queued messages
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
                    }

                    // all subsequent messages are encrypted
                    val keyBytes  = CryptoUtil.decodeKey(device.encKey)
                    val plaintext = CryptoUtil.decrypt(text, keyBytes)
                    val data      = JSONObject(plaintext)
                    
                    // Notify all receivers
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

    fun sendMessage(msg: JSONObject) {
        Log.d(TAG, "sendMessage called for type: ${msg.optString("type")}")
        if (!isSocketConnected) {
            messageQueue.add(msg)
            Log.d(TAG, "Socket not connected, queuing message. Queue size: ${messageQueue.size}")
            return
        }
        try {
            val device = currentDevice ?: run {
                Log.w(TAG, "sendMessage failed: currentDevice is null")
                return
            }
            Log.d(TAG, "Sending message directly: ${msg.optString("type")}")
            val keyBytes  = CryptoUtil.decodeKey(device.encKey)
            val encrypted = CryptoUtil.encrypt(msg.toString(), keyBytes)
            Log.d(TAG, "Message encrypted, sending to server...")
            webSocket?.send(encrypted)
            Log.d(TAG, "Message sent successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Send error: ${e.message}", e)
        }
    }

    private fun handleDisconnect(shouldReconnect: Boolean) {
        webSocket = null
        isSocketConnected = false
        _connectionState.value = ConnectionState.Disconnected
        if (shouldReconnect) startReconnectLoop()
    }

    private fun startReconnectLoop() {
        cancelReconnect()
        reconnectJob = scope.launch {
            var backoff = 2000L
            while (isActive && _connectionState.value !is ConnectionState.Connected) {
                Log.d(TAG, "Reconnecting in ${backoff}ms")
                delay(backoff)
                currentDevice?.let { attemptConnect(it) }
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    fun disconnect() {
        cancelReconnect()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        isSocketConnected = false
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }
}