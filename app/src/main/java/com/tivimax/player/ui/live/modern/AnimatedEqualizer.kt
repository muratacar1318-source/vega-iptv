package com.tivimax.player.ui.live.modern

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tivimax.player.theme.OpusCyan

/**
 * Şu an oynatılan kanalı göstermek için modern, ritmik ekolayzer animasyonu.
 * 3 dikey çubuk farklı fazlarda yükselip alçalır.
 */
@Composable
fun AnimatedEqualizer(
    modifier: Modifier = Modifier,
    barColor: Color = OpusCyan,
    maxHeight: Dp = 14.dp,
    barWidth: Dp = 2.5.dp,
    spacing: Dp = 2.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer_transition")

    val height1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1_height"
    )

    val height2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, delayMillis = 100, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2_height"
    )

    val height3 by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, delayMillis = 50, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3_height"
    )

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight(height1)
                .clip(RoundedCornerShape(1.dp))
                .background(barColor)
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight(height2)
                .clip(RoundedCornerShape(1.dp))
                .background(barColor)
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight(height3)
                .clip(RoundedCornerShape(1.dp))
                .background(barColor)
        )
    }
}
