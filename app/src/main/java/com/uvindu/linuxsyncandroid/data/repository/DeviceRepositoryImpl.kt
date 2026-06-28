package com.uvindu.linuxsyncandroid.data.repository

import androidx.datastore.preferences.core.edit
import com.uvindu.linuxsyncandroid.data.local.DataStoreManager
import com.uvindu.linuxsyncandroid.data.local.PreferenceKeys
import com.uvindu.linuxsyncandroid.domain.model.PairedDevice
import com.uvindu.linuxsyncandroid.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DeviceRepositoryImpl(private val dataStoreManager: DataStoreManager) : DeviceRepository {

    private val dataStore = dataStoreManager.getDataStore()

    override fun getPairedDevice(): Flow<PairedDevice?> =
        dataStore.data.map { prefs ->
            val name = prefs[PreferenceKeys.DEVICE_NAME] ?: return@map null
            val ip = prefs[PreferenceKeys.DEVICE_IP] ?: return@map null
            val port = prefs[PreferenceKeys.DEVICE_PORT] ?: return@map null
            val tok = prefs[PreferenceKeys.TOKEN] ?: return@map null
            val key = prefs[PreferenceKeys.ENC_KEY] ?: return@map null
            PairedDevice(deviceName = name, ip = ip, port = port, token = tok, encKey = key)
        }

    override suspend fun savePairedDevice(device: PairedDevice) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.DEVICE_NAME] = device.deviceName
            prefs[PreferenceKeys.DEVICE_IP] = device.ip
            prefs[PreferenceKeys.DEVICE_PORT] = device.port
            prefs[PreferenceKeys.TOKEN] = device.token
            prefs[PreferenceKeys.ENC_KEY] = device.encKey
        }
    }

    override suspend fun clearPairedDevice() {
        dataStore.edit { prefs ->
            prefs.remove(PreferenceKeys.DEVICE_NAME)
            prefs.remove(PreferenceKeys.DEVICE_IP)
            prefs.remove(PreferenceKeys.DEVICE_PORT)
            prefs.remove(PreferenceKeys.TOKEN)
            prefs.remove(PreferenceKeys.ENC_KEY)
        }
    }
}