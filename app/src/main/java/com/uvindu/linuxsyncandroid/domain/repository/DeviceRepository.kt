package com.uvindu.linuxsyncandroid.domain.repository

import com.uvindu.linuxsyncandroid.domain.model.PairedDevice
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun getPairedDevice(): Flow<PairedDevice?>
    suspend fun savePairedDevice(device: PairedDevice)
    suspend fun clearPairedDevice()
}