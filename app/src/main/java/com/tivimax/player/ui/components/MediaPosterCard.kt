package com.tivimax.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes

private val PosterShape = RoundedCornerShape(10.dp)

/**
 * StreamVault imzalı 2:3 Afiş Kartı (Movies.png ile birebir görsel dil)
 * - Sol üstte koyu zeminli altın puan rozeti (★ 8.2/10)
 * - Alt kısımda yumuşak karartma üstünde 2 satır başlık
 * - İsteğe bağlı izleme ilerleme çubuğu ve favori yıldızı
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
    val cleanRating = formatRatingBadge(rating)

    Card(
        shape = PosterShape,
        colors = CardDefaults.cardColors(containerColor = VegaColors.SurfaceElevated),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = PosterShape,
                scale = 1.035f,
                onClick = onClick,
                onFocusStateChange = onFocusChange
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncShimmerImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                placeholderIcon = Icons.Default.Movie,
                modifier = Modifier.fillMaxSize()
            )

            // Alt kısımda başlığın rahat okunması için sinematik karartma
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.54f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            // Sol Üst: StreamVault ★ 8.2/10 Puan Rozeti
            if (cleanRating != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.68f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = VegaColors.Gold,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = cleanRating,
                            color = VegaColors.Gold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Sağ Üst: Favori Yıldızı
            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = VegaColors.Gold,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Alt: Başlık + İsteğe Bağlı Alt Bilgi
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
                if (subtitle.isNotBlank() && subtitle != "Film" && subtitle != "Dizi") {
                    Text(
                        text = subtitle,
                        color = VegaColors.TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Alt Kenar: İzleme İlerleme Çubuğu
            if (watchProgress > 0.02f) {
                LinearProgressIndicator(
                    progress = { watchProgress.coerceIn(0f, 1f) },
                    color = VegaColors.Accent,
                    trackColor = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
        }
    }
}

private fun formatRatingBadge(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val num = trimmed.substringBefore("/").replace(',', '.').toFloatOrNull() ?: return null
    if (num <= 0f) return null
    val formatted = if (num == num.toInt().toFloat()) "${num.toInt()}" else String.format(java.util.Locale.US, "%.1f", num)
    return if (num <= 5f) "$formatted/5" else "$formatted/10"
}
