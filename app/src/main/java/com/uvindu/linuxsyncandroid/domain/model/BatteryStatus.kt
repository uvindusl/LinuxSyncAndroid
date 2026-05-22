package com.uvindu.linuxsyncandroid.domain.model

data class BatteryStatus(
    val level: Int,
    val isCharging: Boolean,
    val source: BatterySource
)

enum class BatterySource { PHONE, LAPTOP }
