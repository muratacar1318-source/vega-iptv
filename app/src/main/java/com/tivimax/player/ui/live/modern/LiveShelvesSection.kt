package com.tivimax.player.ui.live.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText

/**
 * Netflix tarzı "Devam Et" ve "Favoriler" üst yatay rafları.
 * Sadece "Tümü" kategorisi seçiliyken ve arama yapılmıyorken gösterilir.
 */
@Composable
fun LiveShelvesSection(
    recentChannels: List<LiveStream>,
    favoriteChannels: List<LiveStream>,
    epgSummaryMap: Map<Int, EpgProgram>,
    currentPlayingId: Int?,
    onChannelClick: (LiveStream) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Devam Et / Son İzlenenler Rafı
        if (recentChannels.isNotEmpty()) {
            ShelfBlock(
                title = "Devam Et (Son İzlenenler)",
                icon = Icons.Default.History,
                iconTint = VegaColors.Accent,
                channels = recentChannels,
                epgSummaryMap = epgSummaryMap,
                currentPlayingId = currentPlayingId,
                onChannelClick = onChannelClick
            )
        }

        // 2. Favori Kanallar Rafı
        if (favoriteChannels.isNotEmpty()) {
            ShelfBlock(
                title = "Favori Kanallar",
                icon = Icons.Default.Star,
                iconTint = VegaColors.Gold,
                channels = favoriteChannels,
                epgSummaryMap = epgSummaryMap,
                currentPlayingId = currentPlayingId,
                onChannelClick = onChannelClick
            )
        }
    }
}

@Composable
private fun ShelfBlock(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    channels: List<LiveStream>,
    epgSummaryMap: Map<Int, EpgProgram>,
    currentPlayingId: Int?,
    onChannelClick: (LiveStream) -> Unit
) {
    val limitedChannels = remember(channels) { channels.take(20) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = title,
                color = VegaColors.TextPrimary,
                fontSize = VegaText.BodyProminent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "(${channels.size})",
                color = VegaColors.TextSecondary,
                fontSize = VegaText.Caption
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = limitedChannels,
                key = { index, ch -> "shelf_${title}_${ch.streamId}_${ch.categoryId}_$index" }
            ) { _, channel ->
                val isPlaying = currentPlayingId == channel.streamId
                val epg = epgSummaryMap[channel.streamId]
                val programTitle = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı yayın"
                val progress = epg?.getProgressFraction() ?: (if (channel.programProgress > 0f) channel.programProgress else null)
                var isFocused by remember { mutableStateOf(false) }

                Card(
                    shape = VegaShapes.Medium,
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isFocused -> VegaColors.SurfaceVariant
                            isPlaying -> VegaColors.Accent.copy(alpha = 0.16f)
                            else -> VegaColors.SurfaceElevated
                        }
                    ),
                    border = if (isFocused) null else BorderStroke(
                        width = if (isPlaying) 1.5.dp else 1.dp,
                        color = if (isPlaying) VegaColors.Accent else VegaColors.Border
                    ),
                    modifier = Modifier
                        .width(148.dp)
                        .height(86.dp)
                        .dpadFocusable(
                            focusedBorderColor = VegaColors.FocusBorder,
                            shape = VegaShapes.Medium,
                            onClick = { onChannelClick(channel) },
                            onFocusStateChange = { isFocused = it }
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Logo ve Metin İçeriği
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Kanal Logosu
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(VegaShapes.Small)
                                        .background(VegaColors.SurfaceAccent)
                                        .padding(3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncShimmerImage(
                                        model = channel.streamIcon,
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                        placeholderIcon = Icons.Default.Tv
                                    )
                                }

                                // Kırmızı Canlı Noktası
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(VegaColors.Live, CircleShape)
                                    )
                                    Text(
                                        text = "Canlı",
                                        color = VegaColors.Live,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = channel.name,
                                    color = if (isPlaying) VegaColors.Accent else VegaColors.TextPrimary,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = programTitle,
                                    color = VegaColors.TextSecondary,
                                    fontSize = VegaText.Caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Alt İnce İlerleme Çubuğu (yalnızca geçerli EPG ilerlemesi varsa)
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(2.5.dp),
                                color = VegaColors.Accent,
                                trackColor = VegaColors.SurfaceAccent
                            )
                        }
                    }
                }
            }
        }
    }
}
