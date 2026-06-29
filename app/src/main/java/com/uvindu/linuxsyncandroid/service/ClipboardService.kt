package com.uvindu.linuxsyncandroid.service

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.uvindu.linuxsyncandroid.domain.model.MessageType
import org.json.JSONObject

class ClipboardService(context: Context) {

    private val appContext = context.applicationContext
    private val clipboardManager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        readAndSendClipboard()
    }

    @Volatile
    private var lastSentClip = ""
    @Volatile
    private var lastReceivedClip = ""
    private var monitoring = false

    private val lifecycleCallback = object : Application.ActivityLifecycleCallbacks {
        private var resumedCount = 0

        override fun onActivityResumed(activity: Activity) {
            resumedCount++
            if (resumedCount == 1 && !monitoring) {
                startMonitoring()
            }
        }

        override fun onActivityPaused(activity: Activity) {
            resumedCount--
            if (resumedCount == 0 && monitoring) {
                stopMonitoring()
            }
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    init {
        (appContext as Application).registerActivityLifecycleCallbacks(lifecycleCallback)
        Log.d(TAG, "ClipboardService initialized")
    }

    private fun startMonitoring() {
        monitoring = true
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        readAndSendClipboard()
        Log.d(TAG, "Clipboard monitoring started (app in foreground)")
    }

    private fun stopMonitoring() {
        monitoring = false
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        Log.d(TAG, "Clipboard monitoring stopped (app backgrounded)")
    }

    private fun readAndSendClipboard() {
        try {
            val clip = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
            if (clip != null && clip != lastSentClip) {
                Log.d(TAG, "Sending clipboard: $clip")
                lastReceivedClip = clip
                sendClipboardContent(clip)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Clipboard access denied: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading clipboard: ${e.message}", e)
        }
    }

    private fun sendClipboardContent(content: String) {
        val msg = JSONObject().apply {
            put("type", MessageType.CLIPBOARD)
            put("body", content)
        }
        WebSocketManager.sendMessage(msg)
        lastSentClip = content
    }

    fun cleanup() {
        (appContext as Application).unregisterActivityLifecycleCallbacks(lifecycleCallback)
        stopMonitoring()
    }

    fun handleIncomingMessage(msg: JSONObject) {
        if (msg.optString("type") == MessageType.CLIPBOARD) {
            val content = msg.optString("body")
            if (content != lastReceivedClip) {
                try {
                    Log.d(TAG, "Setting clipboard from laptop: $content")
                    lastReceivedClip = content
                    val clip = android.content.ClipData.newPlainText("LinuxSync", content)
                    clipboardManager.setPrimaryClip(clip)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Cannot set clipboard: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting clipboard: ${e.message}", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ClipboardService"
    }
}
