package com.tivimax.player.ui.live.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.R
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.theme.OpusCyan
import com.tivimax.player.theme.TiviTextSecondary
import com.tivimax.player.ui.components.dpadFocusable

/**
 * Tek bir yatay raf bileşeni (Shelf Row).
 * Başlık, kanal sayısı, "Tümünü Gör >" butonu ve en fazla 30 kanallık LazyRow içerir.
 */
@Composable
fun ShelfRow(
    title: String,
    channels: List<LiveStream>,
    epgSummaryMap: Map<Int, EpgProgram>,
    favoriteIds: Set<String>,
    currentPlayingId: Int?,
    onChannelClick: (LiveStream) -> Unit,
    onFavoriteToggle: (LiveStream) -> Unit,
    onLoadEpg: (Int) -> Unit,
    onSeeAllClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onHideChannel: ((LiveStream) -> Unit)? = null
) {
    if (channels.isEmpty()) return

    val displayedChannels = remember(channels) { channels.take(30) }
    val rowState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        // Başlık ve "Tümünü Gör" Satırı
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sol: Kategori Adı ve Kanal Sayısı
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "(${channels.size})",
                    color = TiviTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Sağ: Tümünü Gör Butonu
            if (onSeeAllClick != null && channels.size > 1) {
                Row(
                    modifier = Modifier
                        .dpadFocusable(
                            focusedBorderColor = OpusCyan,
                            onClick = onSeeAllClick
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSeeAllClick
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.live_shelf_see_all),
                        color = OpusCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Tümünü Gör",
                        tint = OpusCyan,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Yatay Kart Listesi (LazyRow)
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = displayedChannels,
                key = { it.streamId }
            ) { channel ->
                val isFav = favoriteIds.contains(channel.streamId.toString())
                val isPlaying = currentPlayingId == channel.streamId

                ShelfChannelCard(
                    channel = channel,
                    epg = epgSummaryMap[channel.streamId],
                    isFavorite = isFav,
                    isPlaying = isPlaying,
                    onChannelClick = { onChannelClick(channel) },
                    onFavoriteToggle = { onFavoriteToggle(channel) },
                    onLoadEpg = { onLoadEpg(channel.streamId) },
                    onHideChannel = if (onHideChannel != null) { { onHideChannel(channel) } } else null
                )
            }
        }
    }
}
