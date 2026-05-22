package com.uvindu.linuxsyncandroid

import android.app.Application
import com.uvindu.linuxsyncandroid.data.repository.DeviceRepositoryImpl
import com.uvindu.linuxsyncandroid.domain.repository.DeviceRepository

class LinuxSync : Application() {

    // A central service locator pattern to provide the single source of truth for your DataStore
    lateinit var deviceRepository: DeviceRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize your data storage layer instantly at application launch
        deviceRepository = DeviceRepositoryImpl(applicationContext)
    }
}