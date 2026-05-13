package com.uvindu.linuxsyncandroid.presentation.ui.screens.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uvindu.linuxsyncandroid.domain.model.Device


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen() {
    // Temporary fake data
    val fakeDevices = listOf(
        Device("1", "Uvindu's Workstation", "192.168.1.10", 8080, true),
        Device("2", "Home-Server (Ubuntu)", "192.168.1.15", 8080, false)
    )

    Scaffold(
        topBar = {
            LargeTopAppBar(title = { Text("LinuxSync") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(fakeDevices) { device ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (device.isPaired)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    ListItem(
                        headlineContent = { Text(device.name) },
                        supportingContent = { Text(device.ipAddress) },
                        trailingContent = {
                            if (device.isPaired) {
                                Text("Paired", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }
        }
    }
}