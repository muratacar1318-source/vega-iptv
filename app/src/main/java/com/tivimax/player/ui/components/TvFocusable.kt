package com.tivimax.player.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.theme.OpusCyan

fun Modifier.dpadFocusable(
    focusedBorderColor: Color = OpusCyan,
    shape: Shape = RoundedCornerShape(10.dp),
    scale: Float = 1.03f,
    onClick: (() -> Unit)? = null,
    onFocusStateChange: ((Boolean) -> Unit)? = null
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }

    this
        .onFocusChanged { state ->
            isFocused = state.isFocused
            onFocusStateChange?.invoke(state.isFocused)
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier.focusable()
            }
        )
        .graphicsLayer {
            scaleX = if (isFocused) scale else 1.0f
            scaleY = if (isFocused) scale else 1.0f
        }
        .then(
            if (isFocused) Modifier.border(2.dp, focusedBorderColor, shape) else Modifier
        )
}

fun extractQualityBadges(channelName: String): List<String> {
    val upper = channelName.uppercase()
    val badges = mutableListOf<String>()
    if (upper.contains("4K") || upper.contains("UHD")) {
        badges.add("4K")
    } else if (upper.contains("FHD") || upper.contains("1080")) {
        badges.add("FHD")
    } else if (upper.contains("HD") || upper.contains("720")) {
        badges.add("HD")
    }

    if (upper.contains("100FPS") || upper.contains("100 FPS")) {
        badges.add("100FPS")
    } else if (upper.contains("50FPS") || upper.contains("50 FPS") || upper.contains("60FPS") || upper.contains("60 FPS")) {
        badges.add("50FPS")
    }

    if (upper.contains("HEVC") || upper.contains("H.265") || upper.contains("H265")) {
        badges.add("HEVC")
    }
    return badges
}

@Composable
fun QualityBadge(tag: String) {
    val (bg, textColor) = when (tag) {
        "4K" -> Color(0xFFFFB300) to Color.Black
        "FHD" -> Color(0xFF6366F1) to Color.White
        "HD" -> Color(0xFF10B981) to Color.White
        "100FPS" -> Color(0xFF00E5FF) to Color.Black
        "50FPS" -> Color(0xFF38BDF8) to Color.Black
        else -> Color(0xFF334155) to Color.White
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = tag,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
