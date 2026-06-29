package com.uvindu.linuxsyncandroid.presentation.components

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uvindu.linuxsyncandroid.domain.model.MessageType
import com.uvindu.linuxsyncandroid.service.WebSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Composable
fun NowPlayingCard(
    phoneTitle: String?,
    phoneArtist: String?,
    phoneApp: String?,
    phoneIsPlaying: Boolean,
    laptopTrackTitle: String?,
    laptopTrackArtist: String?,
    laptopTrackAlbum: String?,
    laptopIsPlaying: Boolean,
    laptopArtUrl: String? = null,
) {
    LaunchedEffect(Unit) {
        sendLaptopNowPlayingRequest()
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // ── Phone's Now Playing ──
            if (phoneTitle != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Phone Media",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (phoneApp != null) {
                        Text(
                            text = phoneApp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (phoneIsPlaying) "▶" else "⏸",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = phoneTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (phoneArtist != null) {
                    Text(
                        text = phoneArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                MediaTransportRow(
                    isPlaying = phoneIsPlaying,
                    onPrev = { sendMediaControl("prev") },
                    onPlayPause = { sendMediaControl(if (phoneIsPlaying) "pause" else "play") },
                    onNext = { sendMediaControl("next") },
                )

                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Laptop Media ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Laptop Media",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (laptopTrackTitle != null) {
                Spacer(modifier = Modifier.height(12.dp))

                AlbumArt(
                    url = laptopArtUrl,
                    isPlaying = laptopIsPlaying,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = laptopTrackTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (laptopTrackArtist != null) {
                    Text(
                        text = laptopTrackArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Nothing playing on desktop",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Volume controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { sendLaptopMedia("volume_down") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeDown,
                        contentDescription = "Volume Down",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Volume",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(
                    onClick = { sendLaptopMedia("volume_up") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Volume Up",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            MediaTransportRow(
                isPlaying = null,
                onPrev = { sendLaptopMedia("previous") },
                onPlayPause = { sendLaptopMedia("play_pause") },
                onNext = { sendLaptopMedia("next") },
            )
        }
    }
}

@Composable
private fun MediaTransportRow(
    isPlaying: Boolean?,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrev,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        if (isPlaying != null) {
            IconButton(
                onClick = onPlayPause,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                ),
                modifier = Modifier.size(56.dp)
            ) {
                Text(
                    text = if (isPlaying) "⏸" else "▶",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        } else {
            IconButton(
                onClick = onPlayPause,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                ),
                modifier = Modifier.size(56.dp)
            ) {
                Text(
                    text = "▶",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
        Spacer(modifier = Modifier.width(24.dp))
        IconButton(
            onClick = onNext,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun AlbumArt(url: String?, isPlaying: Boolean, modifier: Modifier = Modifier) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(url) {
        if (url != null) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                val inputStream = response.body?.byteStream()
                if (inputStream != null) {
                    bitmap = BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: Exception) {
                Log.e("AlbumArt", "Failed to load album art: ${e.message}")
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Album Art",
            modifier = modifier.clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isPlaying) "▶" else "⏸",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

private fun sendMediaControl(action: String) {
    WebSocketManager.sendMessage(JSONObject().apply {
        put("type", MessageType.MEDIA_CONTROL)
        put("action", action)
    })
}

private fun sendLaptopMedia(action: String) {
    WebSocketManager.sendMessage(JSONObject().apply {
        put("type", MessageType.LAPTOP_MEDIA)
        put("action", action)
    })
}

private fun sendLaptopNowPlayingRequest() {
    WebSocketManager.sendMessage(JSONObject().apply {
        put("type", MessageType.LAPTOP_NOW_PLAYING)
    })
}
