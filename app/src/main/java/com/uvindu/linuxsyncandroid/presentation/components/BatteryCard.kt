package com.uvindu.linuxsyncandroid.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BatteryCard(level: Int, isCharging: Boolean) {
    val progressAnimation by animateFloatAsState(targetValue = level / 100f, label = "levelBar")
    val dynamicThemeColor by animateColorAsState(
        targetValue = when {
            isCharging -> MaterialTheme.colorScheme.primary
            level <= 18 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.tertiary
        }, label = "tintColor"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BatteryFull, contentDescription = null, tint = dynamicThemeColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Laptop Battery", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
                Text(
                    text = if (isCharging) "$level% ⚡" else "$level%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = dynamicThemeColor
                )
            }
            LinearProgressIndicator(
                progress = { progressAnimation },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = dynamicThemeColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}
