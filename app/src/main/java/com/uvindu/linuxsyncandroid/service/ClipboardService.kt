package com.uvindu.linuxsyncandroid.service

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.uvindu.linuxsyncandroid.domain.model.MessageType
import org.json.JSONObject

class ClipboardService(context: Context) {

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            val clip = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
            if (clip != null && clip != lastSentClip) {
                Log.d(TAG, "New clipboard content: $clip")
                lastReceivedClip = clip
                sendClipboardContent(clip)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Clipboard access denied: ${e.message}. App must be in foreground for clipboard access on Android 13+")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading clipboard: ${e.message}", e)
        }
    }

    init {
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        Log.d(TAG, "ClipboardService initialized")
    }

    private fun sendClipboardContent(content: String) {
        val msg = JSONObject().apply {
            put("type", MessageType.CLIPBOARD)
            put("body", content)
        }
        WebSocketManager.sendMessage(msg)
        lastSentClip = content
    }

    fun handleIncomingMessage(msg: JSONObject) {
        if (msg.optString("type") == "clipboard") {
            val content = msg.optString("body")
            if (content != lastReceivedClip) {
                try {
                    Log.d(TAG, "Setting clipboard from remote: $content")
                    lastReceivedClip = content
                    val clip = android.content.ClipData.newPlainText("LinuxSync", content)
                    clipboardManager.setPrimaryClip(clip)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Cannot set clipboard: ${e.message}. App must be in foreground on Android 13+")
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting clipboard: ${e.message}", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ClipboardService"
        @Volatile
        private var lastSentClip = ""
        @Volatile
        private var lastReceivedClip = ""
    }
}
