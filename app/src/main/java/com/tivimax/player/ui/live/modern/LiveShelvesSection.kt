package com.tivimax.player.ui.live.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.theme.*
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable

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
                iconTint = OpusCyan,
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
                iconTint = TiviGold,
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
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "(${channels.size})",
                color = TiviTextSecondary,
                fontSize = 11.5.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(channels.take(20), key = { "shelf_${title}_${it.streamId}" }) { channel ->
                val isPlaying = currentPlayingId == channel.streamId
                val epg = epgSummaryMap[channel.streamId]
                val programTitle = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı Yayın"
                val progress = epg?.getProgressFraction() ?: (if (channel.programProgress > 0f) channel.programProgress else 0.45f)

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPlaying) OpusCyan.copy(alpha = 0.16f) else TiviCard
                    ),
                    border = BorderStroke(
                        width = if (isPlaying) 1.5.dp else 1.dp,
                        color = if (isPlaying) OpusCyan else TiviCardBorder
                    ),
                    modifier = Modifier
                        .width(148.dp)
                        .height(86.dp)
                        .dpadFocusable(
                            focusedBorderColor = OpusCyan,
                            shape = RoundedCornerShape(12.dp),
                            onClick = { onChannelClick(channel) }
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onChannelClick(channel) }
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
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    AsyncShimmerImage(
                                        model = channel.streamIcon,
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(3.dp),
                                        placeholderIcon = Icons.Default.Tv
                                    )
                                }

                                // Kırmızı CANLI Noktası
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(Color(0xFFFF1744), CircleShape)
                                    )
                                    Text(
                                        text = "CANLI",
                                        color = Color(0xFFFF1744),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = channel.name,
                                    color = if (isPlaying) OpusCyan else Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = programTitle,
                                    color = TiviTextSecondary,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Alt İnce İlerleme Çubuğu
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.5.dp),
                            color = OpusCyan,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}
