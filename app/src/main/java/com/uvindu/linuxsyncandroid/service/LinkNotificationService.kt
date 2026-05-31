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
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.uvindu.linuxsyncandroid.domain.model.MessageType
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

class LinkNotificationService : NotificationListenerService() {

    // ── media session ────────────────────────────────────────────
    private var mediaSessionManager: MediaSessionManager? = null
    private val controllerCallbacks = mutableMapOf<String, MediaController.Callback>()
    private var lastTitle = ""
    private var batteryReceiver: BroadcastReceiver? = null

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
        setupBatteryListener()
        sendWallpaper()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        controllerCallbacks.clear()
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver)
            batteryReceiver = null
        }
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

    private fun setupBatteryListener() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                sendBatteryStatus()
            }
        }
        
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
        
        sendBatteryStatus()
    }

    private fun sendBatteryStatus() {
        try {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent == null) {
                Log.w(TAG, "Could not get battery intent")
                return
            }

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
                put("timestamp", System.currentTimeMillis())
            }
            WebSocketManager.sendMessage(msg)
            Log.d(TAG, "Battery sent: $percentage% - Charging: $isCharging via $chargingVia")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending battery: ${e.message}")
        }
    }

    private fun sendWallpaper() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            
            var bitmap: Bitmap? = null
            
            // Try method 1: getDrawable()
            try {
                val drawable = wallpaperManager.drawable
                if (drawable != null) {
                    Log.d(TAG, "Method 1: getDrawable() succeeded")
                    bitmap = convertDrawableToBitmap(drawable)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Method 1 (getDrawable) failed: ${e.message}")
            }
            
            // Try method 2: peekDrawable()
            if (bitmap == null) {
                try {
                    val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        wallpaperManager.peekDrawable()
                    } else {
                        wallpaperManager.drawable
                    }
                    if (drawable != null) {
                        Log.d(TAG, "Method 2: peekDrawable() succeeded")
                        bitmap = convertDrawableToBitmap(drawable)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Method 2 (peekDrawable) failed: ${e.message}")
                }
            }
            
            // Try method 3: Get from file system (Samsung stores wallpapers)
            if (bitmap == null) {
                try {
                    val wallpaperFile = File("/data/data/com.android.systemui/files/wallpapers/wallpaper_0")
                    
                    if (wallpaperFile.exists() && wallpaperFile.length() > 0) {
                        Log.d(TAG, "Method 3: Found wallpaper file")
                        bitmap = BitmapFactory.decodeFile(wallpaperFile.absolutePath)
                        if (bitmap != null) {
                            Log.d(TAG, "Method 3: Successfully decoded wallpaper file")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Method 3 (file system) failed: ${e.message}")
                }
            }
            
            if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                sendWallpaperBitmap(bitmap)
            } else {
                Log.w(TAG, "No wallpaper bitmap obtained, sending default")
                sendDefaultWallpaper()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendWallpaper: ${e.message}")
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
                is android.graphics.drawable.BitmapDrawable -> {
                    Log.d(TAG, "Converting BitmapDrawable directly")
                    drawable.bitmap
                }
                else -> {
                    val width = drawable.intrinsicWidth.coerceAtLeast(100)
                    val height = drawable.intrinsicHeight.coerceAtLeast(100)
                    
                    Log.d(TAG, "Converting generic drawable: ${width}x${height}")
                    
                    if (width > 4096 || height > 4096) {
                        Log.w(TAG, "Drawable too large: ${width}x${height}")
                        return null
                    }
                    
                    val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(b)
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(canvas)
                    b
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting drawable: ${e.message}")
            null
        }
    }

    private fun sendWallpaperBitmap(bitmap: Bitmap) {
        try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val data = baos.toByteArray()
            val base64 = Base64.getEncoder().encodeToString(data)
            
            val msg = JSONObject().apply {
                put("type", "wallpaper")
                put("data", base64)
                put("width", bitmap.width)
                put("height", bitmap.height)
                put("timestamp", System.currentTimeMillis())
            }
            WebSocketManager.sendMessage(msg)
            Log.d(TAG, "Wallpaper sent: ${bitmap.width}x${bitmap.height}, size: ${data.size / 1024}KB")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending wallpaper bitmap: ${e.message}")
        }
    }

    companion object { private const val TAG = "LinkNotifService" }
}