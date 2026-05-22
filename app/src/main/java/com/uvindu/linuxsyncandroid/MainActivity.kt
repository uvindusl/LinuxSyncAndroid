package com.uvindu.linuxsyncandroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.uvindu.linuxsyncandroid.domain.model.PairedDevice
import com.uvindu.linuxsyncandroid.domain.model.QRCodePayload
import com.uvindu.linuxsyncandroid.domain.repository.DeviceRepository
import com.uvindu.linuxsyncandroid.presentation.ui.ManualConnectScreen
import com.uvindu.linuxsyncandroid.presentation.ui.QRCodeScannerScreen
import com.uvindu.linuxsyncandroid.service.LinuxSyncService
import com.uvindu.linuxsyncandroid.ui.theme.LinuxSyncAndroidTheme
import kotlinx.coroutines.launch

enum class AppScreen { Scanner, ManualInput }

class MainActivity : ComponentActivity() {
    lateinit var deviceRepository: DeviceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as LinuxSync
        deviceRepository = app.deviceRepository

        setContent {
            LinuxSyncAndroidTheme {
                // CHANGE: Default this to ManualInput instead of Scanner
                var currentScreen by remember { mutableStateOf(AppScreen.ManualInput) }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        AppScreen.Scanner -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                QRCodeScannerScreen(onResultScanned = { qrPayload ->
                                    handleConnectionPipeline(qrPayload)
                                })

                                Button(
                                    onClick = { currentScreen = AppScreen.ManualInput },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 48.dp)
                                ) {
                                    Text("Switch to Manual Configuration")
                                }
                            }
                        }
                        AppScreen.ManualInput -> {
                            ManualConnectScreen(
                                onSaveConfig = { manuallyTypedPayload ->
                                    handleConnectionPipeline(manuallyTypedPayload)
                                },
                                onBackToScanner = {
                                    currentScreen = AppScreen.Scanner
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleConnectionPipeline(payload: QRCodePayload) {
        lifecycleScope.launch {
            val pairedDevice = PairedDevice(
                deviceName = payload.dn,
                ip = payload.ip,
                port = payload.pt,
                token = payload.tk,
                encKey = payload.ek
            )

            deviceRepository.savePairedDevice(pairedDevice)

            // Start service to establish connection
            val serviceIntent = Intent(this@MainActivity, LinuxSyncService::class.java)
            startService(serviceIntent)

            // Fire a test frame ping down the pipeline
            lifecycleScope.launch {
                kotlinx.coroutines.delay(2500)

                val testJsonPayload = """
                    {
                        "event": "MANUAL_TEST",
                        "message": "Manual pipeline bypass successful! Frame tracking alive."
                    }
                """.trimIndent()

                val intent = Intent(this@MainActivity, LinuxSyncService::class.java).apply {
                    action = "ACTION_SEND_TEST_PAYLOAD" // Explicit action to handle inside service
                    putExtra("EXTRA_PAYLOAD", testJsonPayload)
                    putExtra("EXTRA_KEY", pairedDevice.encKey)
                }
                startService(intent)
            }
        }
    }
}