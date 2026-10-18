package com.tivimax.player.ui.live.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.theme.*
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors

/**
 * 3 Sütunlu Izgara (Grid) Görünümü Kanal Kartı (~110dp yükseklik).
 * Logo, kanal adı, canlı rozeti ve favori yıldızı içerir.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelGridCard(
    channel: LiveStream,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onChannelClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onHideChannel: (() -> Unit)? = null
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
            .dpadFocusable(
                focusedBorderColor = OpusCyan,
                shape = RoundedCornerShape(12.dp),
                scale = 1.04f,
                onClick = onChannelClick
            )
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onChannelClick,
                onLongClick = { showContextMenu = true }
            )
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isPlaying) OpusCyan.copy(alpha = 0.14f) else TiviCard
            ),
            border = BorderStroke(
                width = if (isPlaying) 1.5.dp else 1.dp,
                color = if (isPlaying) OpusCyan else TiviCardBorder
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Üst Satır: Canlı Noktası & Favori
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(VegaColors.Live, CircleShape)
                    )

                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favori",
                            tint = if (isFavorite) VegaColors.Gold else VegaColors.TextMuted.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Orta: Logo
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.size(46.dp)
                ) {
                    AsyncShimmerImage(
                        model = channel.streamIcon,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        placeholderIcon = Icons.Default.Tv
                    )
                }

                // Alt: Kanal Adı & Equalizer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isPlaying) {
                        AnimatedEqualizer(
                            modifier = Modifier.padding(end = 4.dp),
                            maxHeight = 11.dp,
                            barWidth = 2.dp
                        )
                    }

                    Text(
                        text = channel.name,
                        color = if (isPlaying) OpusCyan else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Hızlı Menü
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier.background(TiviSurfaceVariant)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (isFavorite) "Favorilerden Çıkar" else "Favorilere Ekle",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.StarBorder else Icons.Default.Star,
                        contentDescription = null,
                        tint = if (isFavorite) TiviTextSecondary else TiviGold
                    )
                },
                onClick = {
                    showContextMenu = false
                    onFavoriteToggle()
                }
            )
            if (onHideChannel != null) {
                DropdownMenuItem(
                    text = { Text("Kanalı gizle", color = VegaColors.Danger, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = VegaColors.Danger
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onHideChannel()
                    }
                )
            }
        }
    }
}
