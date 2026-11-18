package com.tivimax.player.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tivimax.player.ui.design.VegaText
import java.util.Locale

internal fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

enum class SeekDirection { FORWARD, REWIND }

@Composable
internal fun SeekIndicator(
    direction: SeekDirection,
    seconds: Int,
    isPortrait: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.56f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f)),
        modifier = modifier.size(if (isPortrait) 64.dp else 74.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (direction == SeekDirection.FORWARD) Icons.Default.Forward10 else Icons.Default.Replay10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (isPortrait) 24.dp else 28.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (direction == SeekDirection.FORWARD) "+$seconds sn" else "-$seconds sn",
                color = Color.White.copy(alpha = 0.90f),
                fontSize = VegaText.Caption,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
