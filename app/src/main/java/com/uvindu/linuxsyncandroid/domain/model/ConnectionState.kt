package com.uvindu.linuxsyncandroid.domain.model

sealed class ConnectionState {
    object Disconnected: ConnectionState()
    object Connecting: ConnectionState()
    data class Connected(val device: PairedDevice): ConnectionState()
}
