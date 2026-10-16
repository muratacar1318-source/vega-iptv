package com.tivimax.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.tivimax.player.theme.TiviCard
import com.tivimax.player.theme.TiviCardBorder

@Composable
fun MediaPosterCard(
    imageUrl: String?,
    title: String,
    subtitle: String,
    rating: String? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(
                focusedBorderColor = Color(0xFF00E5FF),
                shape = RoundedCornerShape(10.dp),
                onClick = onClick
            )
            .clickable(onClick = onClick)
    ) {
        // Afiş Görseli (2:3 En-Boy Oranı, 10.dp Köşe Yuvarlatmalı)
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = TiviCard),
            border = BorderStroke(1.dp, TiviCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncShimmerImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    placeholderIcon = Icons.Default.Movie,
                    modifier = Modifier.fillMaxSize()
                )

                // Afiş Üzerinde Alt Gölgelendirme Gradient'ı
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                )

                // Puan Rozeti (Badge) - Afişin Sağ Üst Köşesi (Yalnızca puan varsa gösterilir, sahte puan engellendi)
                if (!rating.isNullOrBlank() && rating != "0" && rating != "0.0") {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = rating,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Başlık (Maksimum 2 satır, sabit minLines = 2 ile alt hiza korunur)
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Yıl ve Tür Metni
        Text(
            text = subtitle,
            color = Color(0xFF9E9E9E),
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
