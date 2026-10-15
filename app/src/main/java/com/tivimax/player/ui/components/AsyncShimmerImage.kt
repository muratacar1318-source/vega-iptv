package com.tivimax.player.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.tivimax.player.theme.TiviSurface
import com.tivimax.player.theme.TiviTextMuted

@Composable
fun rememberShimmerBrush(
    targetValue: Float = 1000f
): Brush {
    val shimmerColors = listOf(
        Color(0xFF0F141C),
        Color(0xFF1E2638),
        Color(0xFF0F141C)
    )
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - targetValue, translateAnim - targetValue),
        end = Offset(translateAnim, translateAnim)
    )
}

@Composable
fun AsyncShimmerImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIcon: ImageVector = Icons.Default.Image
) {
    val shimmer = rememberShimmerBrush()
    val context = LocalContext.current

    val imageRequest = remember(model, contentScale, placeholderIcon) {
        if (model == null || (model is String && model.isBlank())) {
            null
        } else if (model is ImageRequest) {
            model
        } else {
            val isLogo = placeholderIcon == Icons.Default.Tv || contentScale == ContentScale.Fit
            val (targetWidth, targetHeight) = if (isLogo) Pair(120, 120) else Pair(240, 360)

            ImageRequest.Builder(context)
                .data(model)
                .size(targetWidth, targetHeight)
                .allowHardware(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(150)
                .build()
        }
    }

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(shimmer)
            )
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TiviSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = null,
                    tint = TiviTextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    )
}
