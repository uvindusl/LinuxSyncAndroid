package com.uvindu.linuxsyncandroid.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uvindu.linuxsyncandroid.domain.model.QRCodePayload
import kotlinx.coroutines.launch

class ManualConnectViewModel : ViewModel() {

    var deviceName by mutableStateOf("Linux Desktop")
        private set
    var ipAddress by mutableStateOf("192.168.1.")
        private set
    var port by mutableStateOf("8765")
        private set
    var token by mutableStateOf("")
        private set
    var encKey by mutableStateOf("")
        private set

    var inputError by mutableStateOf<String?>(null)
        private set

    fun updateDeviceName(name: String) {
        deviceName = name
    }

    fun updateIpAddress(ip: String) {
        ipAddress = ip
    }

    fun updatePort(pt: String) {
        port = pt
    }

    fun updateToken(tk: String) {
        token = tk
    }

    fun updateEncKey(ek: String) {
        encKey = ek
    }

    fun onConnectClicked(onSaveConfig: (QRCodePayload) -> Unit) {
        viewModelScope.launch {
            val parsedPort = port.toIntOrNull()
            if (ipAddress.isBlank() || port.isBlank() || encKey.isBlank()) {
                inputError = "IP, Port, and Encryption Key are strictly required!"
            } else if (parsedPort == null) {
                inputError = "Target Port must be a valid numeric value!"
            } else {
                inputError = null

                onSaveConfig(
                    QRCodePayload(
                        dn = deviceName.trim(),
                        ip = ipAddress.trim(),
                        pt = parsedPort,
                        tk = token.trim(),
                        ek = encKey.trim()
                    )
                )
            }
        }
    }
}
