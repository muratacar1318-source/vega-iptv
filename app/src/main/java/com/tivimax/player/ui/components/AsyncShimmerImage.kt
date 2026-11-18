package com.tivimax.player.ui.components

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
fun AsyncShimmerImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIcon: ImageVector = Icons.Default.Image,
    crossfade: Boolean = false,
    maxDecodeWidthPx: Int? = null,
    maxDecodeHeightPx: Int? = null
) {
    val context = LocalContext.current

    val imageRequest = remember(model, contentScale, placeholderIcon, crossfade, maxDecodeWidthPx, maxDecodeHeightPx) {
        when {
            model == null || (model is String && model.isBlank()) -> null
            model is ImageRequest -> model
            else -> {
                val isLogo = placeholderIcon == Icons.Default.Tv || contentScale == ContentScale.Fit
                ImageRequest.Builder(context)
                    .data(model)
                    .apply {
                        when {
                            maxDecodeWidthPx != null && maxDecodeHeightPx != null ->
                                size(maxDecodeWidthPx, maxDecodeHeightPx)
                            isLogo ->
                                size(144, 144)
                        }
                    }
                    .allowHardware(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(crossfade)
                    .build()
            }
        }
    }

    if (imageRequest == null) {
        Box(
            modifier = modifier
                .background(VegaColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = null,
                tint = VegaColors.TextMuted.copy(alpha = 0.45f),
                modifier = Modifier.size(24.dp)
            )
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
                    .background(VegaColors.Surface)
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
                    tint = VegaColors.TextMuted.copy(alpha = 0.45f),
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
