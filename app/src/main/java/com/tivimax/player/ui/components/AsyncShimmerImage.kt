package com.tivimax.player.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.tivimax.player.ui.design.VegaColors

private enum class ImageLoadStatus { LOADING, SUCCESS, ERROR }

@Composable
fun rememberShimmerBrush(
    targetValue: Float = 1000f
): Brush {
    val shimmerColors = listOf(
        VegaColors.Card,
        VegaColors.SurfaceVariant,
        VegaColors.Card
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
    val context = LocalContext.current

    val imageRequest = remember(model, contentScale, placeholderIcon) {
        when {
            model == null || (model is String && model.isBlank()) -> null
            model is ImageRequest -> model
            else -> {
                val isLogo = placeholderIcon == Icons.Default.Tv || contentScale == ContentScale.Fit
                ImageRequest.Builder(context)
                    .data(model)
                    .apply {
                        if (isLogo) {
                            size(160, 160)
                        }
                    }
                    .allowHardware(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(150)
                    .build()
            }
        }
    }

    if (imageRequest == null) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VegaColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = null,
                    tint = VegaColors.TextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        return
    }

    var loadStatus by remember(imageRequest) { mutableStateOf(ImageLoadStatus.LOADING) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (loadStatus == ImageLoadStatus.LOADING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VegaColors.Card)
            )
        }

        if (loadStatus == ImageLoadStatus.ERROR) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VegaColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = null,
                    tint = VegaColors.TextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onLoading = { loadStatus = ImageLoadStatus.LOADING },
            onSuccess = { loadStatus = ImageLoadStatus.SUCCESS },
            onError = { loadStatus = ImageLoadStatus.ERROR },
            modifier = Modifier.fillMaxSize()
        )
    }
}
