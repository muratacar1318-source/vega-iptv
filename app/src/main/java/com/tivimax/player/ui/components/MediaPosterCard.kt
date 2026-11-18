package com.tivimax.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaText

private val PosterShape = RoundedCornerShape(10.dp)
private val PosterBadgeShape = RoundedCornerShape(6.dp)

/**
 * Sinematik 2:3 Afiş Kartı
 * - Tam kenarlıksız 2:3 afiş, 10dp keskin köşe yuvarlaklığı
 * - Odaklanmadığında sıfır çerçeve, odaklandığında net 2dp odak çerçevesi
 * - Puan > 0 olduğunda sağ üstte minimal koyu yarı saydam puan rozeti
 * - Afiş altında tek satır net başlık ve isteğe bağlı sade alt bilgi
 */
@Composable
fun MediaPosterCard(
    imageUrl: String?,
    title: String,
    subtitle: String,
    rating: String? = null,
    watchProgress: Float = 0f,
    isFavorite: Boolean = false,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    MediaPosterCardBox(
        imageUrl = imageUrl,
        title = title,
        subtitle = subtitle,
        rating = rating,
        watchProgress = watchProgress,
        isFavorite = isFavorite,
        onFocusChange = onFocusChange,
        onClick = onClick,
        modifier = Modifier
    )
}

/**
 * `FocusRequester` gibi ek modifier gereken katalog ızgaraları için dahili tek-hedef kart bileşeni.
 * DİKKAT: Tek bir odak + tıklama hedefi (`dpadFocusable(onClick = ...)`) içerir.
 */
@Composable
internal fun MediaPosterCardBox(
    imageUrl: String?,
    title: String,
    subtitle: String,
    rating: String? = null,
    watchProgress: Float = 0f,
    isFavorite: Boolean = false,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanRating = remember(rating) { formatRatingBadge(rating) }
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = PosterShape,
                scale = 1.045f,
                drawFocusBorder = false,
                onClick = onClick,
                onFocusStateChange = { focused ->
                    isFocused = focused
                    onFocusChange?.invoke(focused)
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(PosterShape)
                .background(VegaColors.Surface)
                .then(
                    if (isFocused) {
                        Modifier.border(2.dp, VegaColors.FocusBorder, PosterShape)
                    } else {
                        Modifier
                    }
                )
        ) {
            AsyncShimmerImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                placeholderIcon = Icons.Default.Movie,
                modifier = Modifier.fillMaxSize()
            )

            // Sağ Üst: Minimal koyu yarı saydam puan rozeti (yalnızca puan > 0)
            if (cleanRating != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(PosterBadgeShape)
                        .background(VegaColors.OverlayPill)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = VegaColors.Gold,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = cleanRating,
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // Sol Üst: Sade favori yıldızı
            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(PosterBadgeShape)
                        .background(VegaColors.OverlayPill)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = VegaColors.Gold,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            // Alt Kenar: İnce izleme ilerleme çubuğu
            if (watchProgress > 0.02f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(watchProgress.coerceIn(0f, 1f))
                            .background(VegaColors.Accent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            color = if (isFocused) VegaColors.TextPrimary else VegaColors.TextPrimary.copy(alpha = 0.90f),
            fontSize = VegaText.Secondary,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        if (subtitle.isNotBlank() && subtitle != "Film" && subtitle != "Dizi") {
            Text(
                text = subtitle,
                color = VegaColors.TextMuted,
                fontSize = VegaText.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
            )
        }
    }
}

private fun formatRatingBadge(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val num = trimmed.substringBefore("/").replace(',', '.').toFloatOrNull() ?: return null
    if (num <= 0f) return null
    return if (num == num.toInt().toFloat()) {
        "${num.toInt()}"
    } else {
        String.format(java.util.Locale.US, "%.1f", num)
    }
}
