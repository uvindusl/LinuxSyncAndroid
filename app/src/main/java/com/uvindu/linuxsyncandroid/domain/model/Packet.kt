package com.uvindu.linuxsyncandroid.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Packet(
    val type: PacketType,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis(),
    val senderId: String
)

enum class PacketType {
    PAIR_REQUEST,
    PAIR_RESPONSE,
    CLIPBOARD_SYNC,
    FILE_HEADER,
    NOTIFICATION,
    PING
}