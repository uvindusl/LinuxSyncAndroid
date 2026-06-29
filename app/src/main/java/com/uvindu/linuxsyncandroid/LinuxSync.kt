package com.uvindu.linuxsyncandroid

import android.app.Application
import android.content.Intent
import android.os.Build
import com.uvindu.linuxsyncandroid.data.local.DataStoreManager
import com.uvindu.linuxsyncandroid.data.repository.DeviceRepositoryImpl
import com.uvindu.linuxsyncandroid.domain.repository.DeviceRepository
import com.uvindu.linuxsyncandroid.service.LinuxSyncService

class LinuxSync : Application() {

    lateinit var deviceRepository: DeviceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize DataStore and Repository with dependency injection
        val dataStoreManager = DataStoreManager(this)
        deviceRepository = DeviceRepositoryImpl(dataStoreManager)

        // start connection service so app stays alive in background
        val intent = Intent(this, LinuxSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}