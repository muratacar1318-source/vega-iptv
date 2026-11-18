package com.tivimax.player.ui.live.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
 * Sade "Son İzlenenler" ve "Favori Kanallar" üst yatay rafları.
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
    if (recentChannels.isEmpty() && favoriteChannels.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (recentChannels.isNotEmpty()) {
            ShelfBlock(
                title = "Son İzlenenler",
                channels = recentChannels,
                epgSummaryMap = epgSummaryMap,
                currentPlayingId = currentPlayingId,
                onChannelClick = onChannelClick
            )
        }

        if (favoriteChannels.isNotEmpty()) {
            ShelfBlock(
                title = "Favori Kanallar",
                channels = favoriteChannels,
                epgSummaryMap = epgSummaryMap,
                currentPlayingId = currentPlayingId,
                onChannelClick = onChannelClick
            )
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = VegaColors.Border.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ShelfBlock(
    title: String,
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
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = VegaColors.TextPrimary,
                fontSize = VegaText.BodyProminent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = channels.size.toString(),
                color = VegaColors.TextMuted,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = limitedChannels,
                key = { index, ch -> "shelf_${title}_${ch.streamId}_${ch.categoryId}_$index" }
            ) { _, channel ->
                val isPlaying = currentPlayingId == channel.streamId
                val epg = epgSummaryMap[channel.streamId]
                val programTitle = epg?.getDecodedTitle()?.takeIf { it.isNotBlank() && it != "Canlı Yayın" && it != "Canlı yayın" }
                    ?: channel.currentProgram?.takeIf { it.isNotBlank() }
                val progress = epg?.getProgressFraction() ?: (if (channel.programProgress > 0f) channel.programProgress else null)
                var isFocused by remember { mutableStateOf(false) }

                Card(
                    shape = VegaShapes.Medium,
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isFocused -> VegaColors.SurfaceVariant
                            isPlaying -> VegaColors.Accent.copy(alpha = 0.14f)
                            else -> VegaColors.SurfaceElevated
                        }
                    ),
                    border = when {
                        isFocused -> null
                        isPlaying -> BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.7f))
                        else -> BorderStroke(0.5.dp, VegaColors.Border.copy(alpha = 0.45f))
                    },
                    modifier = Modifier
                        .width(146.dp)
                        .height(82.dp)
                        .dpadFocusable(
                            focusedBorderColor = VegaColors.FocusBorder,
                            shape = VegaShapes.Medium,
                            onClick = { onChannelClick(channel) },
                            onFocusStateChange = { isFocused = it }
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(VegaShapes.Small)
                                        .background(VegaColors.SurfaceAccent)
                                        .padding(4.dp),
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

                                if (isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(VegaColors.Live, CircleShape)
                                    )
                                }
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = channel.name,
                                    color = if (isPlaying) VegaColors.Accent else VegaColors.TextPrimary,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (programTitle != null) {
                                    Text(
                                        text = programTitle,
                                        color = VegaColors.TextMuted,
                                        fontSize = VegaText.Caption,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = VegaColors.Accent,
                                trackColor = VegaColors.SurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
