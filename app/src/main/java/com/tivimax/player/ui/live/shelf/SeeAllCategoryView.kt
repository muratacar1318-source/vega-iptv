package com.tivimax.player.ui.live.shelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tivimax.player.R
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.vegaBackdrop
import com.tivimax.player.ui.tv.StreamVaultSearchField

/**
 * "Tümünü Gör" tıklandığında açılan tam kategori ızgara/liste görünümü.
 * Geri tuşuna basıldığında raf düzenine geri döner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeeAllCategoryView(
    categoryTitle: String,
    channels: List<LiveStream>,
    epgSummaryMap: Map<Int, EpgProgram>,
    favoriteIds: Set<String>,
    currentPlayingId: Int?,
    onChannelClick: (LiveStream) -> Unit,
    onFavoriteToggle: (LiveStream) -> Unit,
    onLoadEpg: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    nowEpochSec: Long = System.currentTimeMillis() / 1000L
) {
    var localQuery by remember { mutableStateOf("") }

    // Donanım veya sistem geri tuşunu yakala: Arama varsa önce aramayı temizle, yoksa geri dön
    BackHandler {
        if (localQuery.isNotBlank()) {
            localQuery = ""
        } else {
            onBack()
        }
    }

    val filteredList = remember(channels, localQuery) {
        if (localQuery.isBlank()) {
            channels
        } else {
            val q = localQuery.trim().lowercase()
            channels.filter { it.name.lowercase().contains(q) }
        }
    }

    var visibleLimit by remember(filteredList) { mutableIntStateOf(50) }
    val pagedList = remember(filteredList, visibleLimit) {
        filteredList.take(visibleLimit)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .vegaBackdrop()
    ) {
        // Üst Bar
        Surface(
            color = VegaColors.Surface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, VegaColors.Border.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var isBackFocused by remember { mutableStateOf(false) }
                Surface(
                    shape = VegaShapes.Small,
                    color = if (isBackFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated,
                    border = if (isBackFocused) null else BorderStroke(1.dp, VegaColors.Border),
                    modifier = Modifier
                        .size(40.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Small,
                            focusedBorderColor = VegaColors.FocusBorder,
                            scale = 1.04f,
                            onClick = onBack,
                            onFocusStateChange = { isBackFocused = it }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri dön",
                            tint = VegaColors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = categoryTitle,
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.SectionTitle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${filteredList.size} kanal",
                        color = VegaColors.Accent,
                        fontSize = VegaText.Caption
                    )
                }
            }
        }

        // TV-güvenli Kategori İçi Arama Çubuğu
        StreamVaultSearchField(
            value = localQuery,
            onValueChange = { localQuery = it },
            placeholder = "$categoryTitle içinde ara...",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Kanal Izgarası (Grid)
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.live_shelf_empty),
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Body
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 90.dp // Bottom bar arkasında kalmaması için
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = pagedList,
                    key = { index, ch -> "seeall_${ch.streamId}_${ch.categoryId}_$index" }
                ) { _, channel ->
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
                        modifier = Modifier.fillMaxWidth(),
                        nowEpochSec = nowEpochSec
                    )
                }

                if (visibleLimit < filteredList.size) {
                    item(
                        key = "more_seeall_$visibleLimit",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        LaunchedEffect(visibleLimit) {
                            visibleLimit += 50
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = VegaColors.Accent,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
