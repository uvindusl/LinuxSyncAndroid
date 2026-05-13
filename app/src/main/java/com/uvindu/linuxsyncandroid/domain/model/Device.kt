package com.uvindu.linuxsyncandroid.domain.model

data class Device(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val isPaired: Boolean = false,
    val deviceType: DeviceType = DeviceType.LINUX
)

enum class DeviceType {
    LINUX, ANDROID
}