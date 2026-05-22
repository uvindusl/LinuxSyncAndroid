package com.uvindu.linuxsyncandroid.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uvindu.linuxsyncandroid.domain.model.QRCodePayload

@Composable
fun ManualConnectScreen(
    onSaveConfig: (QRCodePayload) -> Unit,
    onBackToScanner: () -> Unit
) {
    var deviceName by remember { mutableStateOf("Linux Desktop") }
    var ipAddress by remember { mutableStateOf("192.168.1.") }
    var port by remember { mutableStateOf("8765") }
    var token by remember { mutableStateOf("") }
    var encKey by remember { mutableStateOf("") }

    var inputError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Manual Connection Tool",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Bypass the camera scanner for local network routing tests.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Device Name
        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Device Name (dn)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // IP Address
        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("Server IP Address (ip)") },
            placeholder = { Text("e.g. 192.168.1.50") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Port
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Target Port (pt)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Token
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Security Token (tk)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Encryption Key
        OutlinedTextField(
            value = encKey,
            onValueChange = { encKey = it },
            label = { Text("Base64 Encryption Key (ek)") },
            placeholder = { Text("Paste exact base64 payload string") },
            modifier = Modifier.fillMaxWidth()
        )

        if (inputError != null) {
            Text(
                text = inputError!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (ipAddress.isBlank() || port.isBlank() || encKey.isBlank()) {
                    inputError = "IP, Port, and Encryption Key are strictly required!"
                } else {
                    inputError = null
                    val parsedPort = port.toIntOrNull() ?: 8765

                    // Package into payload layer matching pipeline input
                    onSaveConfig(
                        QRCodePayload(
                            dn = deviceName,
                            ip = ipAddress.trim(),
                            pt = parsedPort,
                            tk = token.trim(),
                            ek = encKey.trim()
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Execute Sync Test")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onBackToScanner) {
            Text("Back to Camera View")
        }
    }
}