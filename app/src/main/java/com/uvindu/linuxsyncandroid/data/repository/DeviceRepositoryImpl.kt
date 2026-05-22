package com.uvindu.linuxsyncandroid.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.uvindu.linuxsyncandroid.domain.model.PairedDevice
import com.uvindu.linuxsyncandroid.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import androidx.datastore.preferences.core.Preferences

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "device_prefs")

class DeviceRepositoryImpl(private val context: Context) : DeviceRepository {

    private object Keys {
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val IP = stringPreferencesKey("ip")
        val PORT = intPreferencesKey("port")
        val TOKEN = stringPreferencesKey("token")
        val ENC_KEY = stringPreferencesKey("enc_key")
    }

    override suspend fun savePairedDevice(device: PairedDevice) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVICE_NAME] = device.deviceName
            prefs[Keys.IP] = device.ip
            prefs[Keys.PORT] = device.port
            prefs[Keys.TOKEN] = device.token
            prefs[Keys.ENC_KEY] = device.encKey
        }
    }

    override fun getPairedDevice(): Flow<PairedDevice> {
        return context.dataStore.data.map { prefs ->
            val name = prefs[Keys.DEVICE_NAME] ?: return@map null
            val ip = prefs[Keys.IP] ?: return@map null
            val port = prefs[Keys.PORT] ?: return@map null
            val token = prefs[Keys.TOKEN] ?: return@map null
            val encKey = prefs[Keys.ENC_KEY] ?: return@map null

            PairedDevice(name, ip, port, token, encKey)
        }.filterNotNull()
    }

    override suspend fun deletePairedDevice() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}