package com.uvindu.linuxsyncandroid.domain.model

data class PairedDevice(
    val deviceName: String,
    val ip: String,
    val port: Int,
    val token: String,
    val encKey: String // based encoded 32-byte AES key
)
