package com.uvindu.linuxsyncandroid.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uvindu.linuxsyncandroid.domain.model.QRCodePayload
import com.uvindu.linuxsyncandroid.presentation.ManualConnectViewModel

@Composable
fun ManualConnectScreen(
    viewModel: ManualConnectViewModel,
    onSaveConfig: (QRCodePayload) -> Unit,
    onBackToScanner: () -> Unit
) {


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Icon(
            imageVector = Icons.Default.Computer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Manual Connection Tool",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Bypass the camera scanner for local network routing tests.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Device Name (dn)
        OutlinedTextField(
            value = viewModel.deviceName,
            onValueChange = { viewModel.updateDeviceName(it) },
            label = { Text("Device Name (dn)") },
            leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // IP Address (ip) and Port (pt) structured cleanly inside an aligned Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
            value = viewModel.ipAddress,
            onValueChange = { viewModel.updateIpAddress(it) },
                label = { Text("Server IP (ip)") },
                placeholder = { Text("192.168.1.X") },
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                modifier = Modifier.weight(1.4f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            OutlinedTextField(
            value = viewModel.port,
            onValueChange = { viewModel.updatePort(it) },
                label = { Text("Port (pt)") },
                placeholder = { Text("8765") },
                modifier = Modifier.weight(0.8f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Security Token (tk)
        OutlinedTextField(
            value = viewModel.token,
            onValueChange = { viewModel.updateToken(it) },
            label = { Text("Security Token (tk)") },
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Base64 Encryption Key (ek)
        OutlinedTextField(
            value = viewModel.encKey,
            onValueChange = { viewModel.updateEncKey(it) },
            label = { Text("Base64 Encryption Key (ek)") },
            placeholder = { Text("Paste exact base64 payload string") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )


        Spacer(modifier = Modifier.height(28.dp))

        // Save & Sync Launcher Action button
        Button(
            onClick = { viewModel.onConnectClicked(onSaveConfig) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save & Execute Sync Test", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onBackToScanner) {
            Text("Back to Camera View", color = MaterialTheme.colorScheme.secondary)
        }
    }
}