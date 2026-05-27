package com.uvindu.linuxsyncandroid.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
