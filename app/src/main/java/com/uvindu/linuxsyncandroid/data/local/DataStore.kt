package com.uvindu.linuxsyncandroid.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// DataStore extension
private val Context.phonelinksDataStore: DataStore<Preferences> by preferencesDataStore(name = "phonelink_prefs")

/**
 * Object holding all preference keys for DataStore/home/uvindu/Documents/Personal/Kotlin/LinuxSyncLinux
 */
object PreferenceKeys {
    val DEVICE_NAME = stringPreferencesKey("device_name")
    val DEVICE_IP = stringPreferencesKey("device_ip")
    val DEVICE_PORT = intPreferencesKey("device_port")
    val TOKEN = stringPreferencesKey("token")
    val ENC_KEY = stringPreferencesKey("enc_key")
}

class DataStoreManager(private val context: Context) {
    fun getDataStore(): DataStore<Preferences> = context.phonelinksDataStore
}
