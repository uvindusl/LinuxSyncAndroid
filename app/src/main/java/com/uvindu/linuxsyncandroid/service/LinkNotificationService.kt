package com.uvindu.linuxsyncandroid.service

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaController.TransportControls
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.uvindu.linuxsyncandroid.domain.model.MessageType
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import android.util.Base64

class LinkNotificationService : NotificationListenerService() {

    // ── media session ────────────────────────────────────────────
    private var mediaSessionManager: MediaSessionManager? = null
    private val registeredCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var sessionsComponent: ComponentName? = null
    @Volatile
    private var activeController: MediaController? = null
    private var lastNowPlayingKey = ""
    private var batteryReceiver: BroadcastReceiver? = null
    private var lastBatteryLevel = -1
    private var lastBatteryCharging = false
    private var lastBatterySendMs = 0L
    private var mediaControlReceiver: ((JSONObject) -> Unit)? = null
    private val replyActions = mutableMapOf<String, android.app.Notification.Action>()

    private val BLOCKED = setOf(
        "android", "com.android.systemui",
        "com.android.launcher3", "com.uvindu.linuxsyncandroid",
        "com.google.android.gms", "com.google.android.gsf",
        "com.android.vending", "com.android.providers.downloads"
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected!")
        // Post all active notifications
        activeNotifications.forEach { onNotificationPosted(it) }
        try {
            mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(packageName, this::class.java.name)
            sessionsComponent = component
            // Remove old listener before adding new one to prevent duplicates
            sessionsListener?.let { mediaSessionManager?.removeOnActiveSessionsChangedListener(it) }
            sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                updateControllers(controllers ?: emptyList())
            }
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsListener!!, component)
            updateControllers(mediaSessionManager?.getActiveSessions(component) ?: emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Media session error: ${e.message}")
        }
        setupBatteryListener()
        sendWallpaper()
        mediaControlReceiver?.let { WebSocketManager.removeMessageReceiver(it) }
        mediaControlReceiver = { data ->
            when (data.optString("type")) {
                MessageType.MEDIA_CONTROL -> handleMediaControl(data.optString("action"))
                MessageType.NOTIFICATION_REPLY -> handleNotificationReply(data)
            }
        }
        WebSocketManager.addMessageReceiver(mediaControlReceiver!!)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Unregister all media controller callbacks to prevent leaks
        registeredCallbacks.forEach { (controller, cb) ->
            try { controller.unregisterCallback(cb) } catch (_: Exception) {}
        }
        registeredCallbacks.clear()
        // Remove sessions listener
        sessionsListener?.let { mediaSessionManager?.removeOnActiveSessionsChangedListener(it) }
        sessionsListener = null
        sessionsComponent = null
        activeController = null
        mediaControlReceiver?.let { WebSocketManager.removeMessageReceiver(it) }
        mediaControlReceiver = null
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver)
            batteryReceiver = null
        }
    }

    // ── notifications ─────────────────────────────────────────────

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName in BLOCKED) return

        try {
            val extras = sbn.notification.extras
            val title  = extras.getString("android.title") ?: return
            val body   = extras.getCharSequence("android.text")?.toString() ?: ""

            val replyAction = sbn.notification.actions
                ?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }

            if (replyAction != null) {
                replyActions[sbn.key] = replyAction
            }

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
            WebSocketManager.sendMessage(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification: ${e.message}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        replyActions.remove(sbn.key)
        WebSocketManager.sendMessage(JSONObject().apply {
            put("type", MessageType.NOTIFICATION_REMOVED)
            put("id",   sbn.key)
        })
    }

    // ── media sessions ────────────────────────────────────────────

    private fun updateControllers(controllers: List<MediaController>) {
        val incoming = controllers.map { it }.toSet()
        // Unregister callbacks for controllers no longer active
        registeredCallbacks.keys.filter { it !in incoming }.forEach { old ->
            try { old.unregisterCallback(registeredCallbacks[old]!!) } catch (_: Exception) {}
            registeredCallbacks.remove(old)
        }
        controllers.forEach { controller ->
            activeController = controller
            if (controller !in registeredCallbacks) {
                val cb = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        metadata?.let { m ->
                            sendNowPlaying(m, controller, getPlaybackState(controller))
                        }
                    }
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        when (state?.state) {
                            PlaybackState.STATE_STOPPED,
                            PlaybackState.STATE_NONE -> sendStopped()
                            PlaybackState.STATE_PAUSED -> {
                                val meta = controller.metadata
                                if (meta != null) sendNowPlaying(meta, controller, false)
                                else sendStopped()
                            }
                        }
                    }
                }
                controller.registerCallback(cb)
                registeredCallbacks[controller] = cb
                controller.metadata?.let { m ->
                    sendNowPlaying(m, controller, getPlaybackState(controller))
                }
            }
        }
    }

    private fun getPlaybackState(controller: MediaController): Boolean {
        val state = controller.playbackState?.state ?: PlaybackState.STATE_NONE
        return state == PlaybackState.STATE_PLAYING
    }

    private fun sendNowPlaying(metadata: MediaMetadata, controller: MediaController, isPlaying: Boolean) {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val key = "$title|$isPlaying"
        if (key == lastNowPlayingKey) return
        lastNowPlayingKey = key
        WebSocketManager.sendMessage(JSONObject().apply {
            put("type",       MessageType.NOW_PLAYING)
            put("title",      title)
            put("artist",     metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "")
            put("album",      metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)  ?: "")
            put("duration",   metadata.getLong(MediaMetadata.METADATA_KEY_DURATION))
            put("app",        controller.packageName)
            put("is_playing", isPlaying)
        })
    }

    private fun sendStopped() {
        lastNowPlayingKey = ""
        WebSocketManager.sendMessage(JSONObject().apply {
            put("type",       MessageType.NOW_PLAYING)
            put("is_playing", false)
        })
    }

    private fun getAppName(pkg: String) = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun setupBatteryListener() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) sendBatteryStatus(intent)
            }
        }
        
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 34) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
        
        // Send initial reading
        registerReceiver(null, filter)?.let { sendBatteryStatus(it) }
    }

    private fun sendBatteryStatus(intent: Intent) {
        try {

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

            val percentage = if (level >= 0 && scale > 0) {
                (level * 100) / scale
            } else {
                0
            }

            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val chargingVia = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "None"
            }

            // Throttle: skip if level + charging state unchanged and <30s since last send
            val now = System.currentTimeMillis()
            val levelChanged = percentage != lastBatteryLevel
            val chargingChanged = isCharging != lastBatteryCharging
            if (!levelChanged && !chargingChanged && now - lastBatterySendMs < 30_000) {
                return
            }
            lastBatteryLevel = percentage
            lastBatteryCharging = isCharging
            lastBatterySendMs = now

            val msg = JSONObject().apply {
                put("type", MessageType.BATTERY)
                put("level", percentage)
                put("is_charging", isCharging)
                put("charging_via", chargingVia)
                put("status", when(status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                    BatteryManager.BATTERY_STATUS_UNKNOWN -> "unknown"
                    else -> "unknown"
                })
                put("timestamp", now)
            }
            WebSocketManager.sendMessage(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending battery: ${e.message}")
        }
    }

    private fun handleMediaControl(action: String) {
        try {
            val controller = activeController ?: run {
                Log.w(TAG, "Media control: no active controller")
                return
            }
            val transport = controller.transportControls
            when (action) {
                "next" -> transport.skipToNext()
                "prev" -> transport.skipToPrevious()
                "play" -> transport.play()
                "pause" -> transport.pause()
            }
            Log.d(TAG, "Media control: $action on ${controller.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "Media control error: ${e.message}")
        }
    }

    private fun sendWallpaper() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            
            var bitmap: Bitmap? = null
            
            try {
                val drawable = wallpaperManager.drawable
                if (drawable != null) bitmap = convertDrawableToBitmap(drawable)
            } catch (_: Exception) {}
            
            if (bitmap == null) {
                try {
                    val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        wallpaperManager.peekDrawable()
                    } else {
                        wallpaperManager.drawable
                    }
                    if (drawable != null) bitmap = convertDrawableToBitmap(drawable)
                } catch (_: Exception) {}
            }
            
            if (bitmap == null) {
                try {
                    val wallpaperFile = File("/data/data/com.android.systemui/files/wallpapers/wallpaper_0")
                    if (wallpaperFile.exists() && wallpaperFile.length() > 0) {
                        bitmap = BitmapFactory.decodeFile(wallpaperFile.absolutePath)
                    }
                } catch (_: Exception) {}
            }
            
            if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                sendWallpaperBitmap(bitmap)
            } else {
                sendDefaultWallpaper()
            }
        } catch (e: Exception) {
            sendDefaultWallpaper()
        }
    }

    private fun sendDefaultWallpaper() {
        try {
            Log.d(TAG, "Sending default wallpaper (black color)")
            val width = 1080
            val height = 1920
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.BLACK)
            sendWallpaperBitmap(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending default wallpaper: ${e.message}")
        }
    }

    private fun convertDrawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap? {
        return try {
            when (drawable) {
                is android.graphics.drawable.BitmapDrawable -> drawable.bitmap
                else -> {
                    val width = drawable.intrinsicWidth.coerceAtLeast(100)
                    val height = drawable.intrinsicHeight.coerceAtLeast(100)
                    if (width > 4096 || height > 4096) return null
                    val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(b)
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(canvas)
                    b
                }
            }
        } catch (_: Exception) { null }
    }

    private fun sendWallpaperBitmap(bitmap: Bitmap) {
        try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
            val data = baos.toByteArray()
            val base64 = Base64.encodeToString(data, Base64.NO_WRAP)
            
            val msg = JSONObject().apply {
                put("type", MessageType.WALLPAPER)
                put("data", base64)
                put("width", bitmap.width)
                put("height", bitmap.height)
                put("timestamp", System.currentTimeMillis())
            }
            WebSocketManager.sendMessage(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending wallpaper bitmap: ${e.message}")
        }
    }

    private fun handleNotificationReply(data: JSONObject) {
        val notifId = data.optString("id")
        val replyText = data.optString("text")
        if (notifId.isEmpty() || replyText.isEmpty()) return

        val action = replyActions[notifId] ?: run {
            Log.w(TAG, "No stored reply action for notification $notifId")
            return
        }

        try {
            val intent = Intent()
            val remoteInput = action.remoteInputs.firstOrNull() ?: return
            val results = Bundle().apply {
                putString(remoteInput.resultKey, replyText)
            }
            android.app.RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
            action.actionIntent.send(this, 0, intent)
            Log.d(TAG, "Reply sent to $notifId: $replyText")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send reply: ${e.message}", e)
        }
    }

    companion object { private const val TAG = "LinkNotifService" }
}