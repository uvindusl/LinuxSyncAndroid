package com.uvindu.linuxsyncandroid.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class QRCodePayload(
    val dn: String, // Device Name
    val ip: String, // IP Address
    val pt: Int,    // Port
    val tk: String, // Token
    val ek: String  // Encryption Key (Base64)
)