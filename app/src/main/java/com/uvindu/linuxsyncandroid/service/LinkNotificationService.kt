package com.uvindu.linuxsyncandroid.service

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.uvindu.linuxsyncandroid.domain.model.MessageType
import org.json.JSONObject

class LinkNotificationService : NotificationListenerService() {

    // ── media session ────────────────────────────────────────────
    private var mediaSessionManager: MediaSessionManager? = null
    private val controllerCallbacks = mutableMapOf<String, MediaController.Callback>()
    private var lastTitle = ""

//    private val BLOCKED = setOf(
//        "android", "com.android.systemui",
//        "com.android.launcher3", "com.uvindu.linuxsyncandroid"
//    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected!")
        // Post all active notifications
        activeNotifications.forEach { onNotificationPosted(it) }
        try {
            mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(packageName, this::class.java.name)
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                { controllers -> updateControllers(controllers ?: emptyList()) },
                component
            )
            updateControllers(mediaSessionManager?.getActiveSessions(component) ?: emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Media session error: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        controllerCallbacks.clear()
    }

    // ── notifications ─────────────────────────────────────────────

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "onNotificationPosted: ${sbn.packageName}")
//        if (sbn.packageName in BLOCKED) {
//            Log.d(TAG, "Notification blocked by package name.")
//            return
//        }

        try {
            val extras = sbn.notification.extras
            val title  = extras.getString("android.title") ?: return
            val body   = extras.getCharSequence("android.text")?.toString() ?: ""

            val replyAction = sbn.notification.actions
                ?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }

            val msg = JSONObject().apply {
                put("type",         MessageType.NOTIFICATION)
                put("id",           sbn.key)
                put("app_name",     getAppName(sbn.packageName))
                put("pkg",          sbn.packageName)
                put("title",        title)
                put("body",         body)
                put("is_replyable", replyAction != null)
                put("reply_key",    replyAction?.remoteInputs?.firstOrNull()?.resultKey ?: "")
                put("timestamp",    System.currentTimeMillis())
            }
            Log.d(TAG, "Sending notification: $msg")
            WebSocketManager.sendMessage(msg)
            Log.d(TAG, "Notification from ${sbn.packageName}: $title - sent to server")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification: ${e.message}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        WebSocketManager.sendMessage(JSONObject().apply {
            put("type", "notification_removed")
            put("id",   sbn.key)
        })
    }

    // ── media sessions ────────────────────────────────────────────

    private fun updateControllers(controllers: List<MediaController>) {
        val active = controllers.map { it.packageName }.toSet()
        controllerCallbacks.keys.filter { it !in active }.forEach {
            controllerCallbacks.remove(it)
        }
        controllers.forEach { controller ->
            if (controller.packageName !in controllerCallbacks) {
                val cb = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        metadata?.let { sendNowPlaying(it, controller) }
                    }
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        if (state?.state == PlaybackState.STATE_STOPPED ||
                            state?.state == PlaybackState.STATE_NONE) sendStopped()
                    }
                }
                controller.registerCallback(cb)
                controllerCallbacks[controller.packageName] = cb
                controller.metadata?.let { sendNowPlaying(it, controller) }
            }
        }
    }

    private fun sendNowPlaying(metadata: MediaMetadata, controller: MediaController) {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        if (title == lastTitle) return
        lastTitle = title
        WebSocketManager.sendMessage(JSONObject().apply {
            put("type",       MessageType.NOW_PLAYING)
            put("title",      title)
            put("artist",     metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "")
            put("album",      metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)  ?: "")
            put("duration",   metadata.getLong(MediaMetadata.METADATA_KEY_DURATION))
            put("app",        controller.packageName)
            put("is_playing", true)
        })
        Log.d(TAG, "Now playing: $title")
    }

    private fun sendStopped() {
        lastTitle = ""
        WebSocketManager.sendMessage(JSONObject().apply {
            put("type",       MessageType.NOW_PLAYING)
            put("is_playing", false)
        })
    }

    private fun getAppName(pkg: String) = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    companion object { private const val TAG = "LinkNotifService" }
}