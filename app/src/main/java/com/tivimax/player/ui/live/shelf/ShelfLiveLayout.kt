package com.tivimax.player.ui.live.shelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tivimax.player.R
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.vegaBackdrop
import com.tivimax.player.ui.tv.StreamVaultSearchField

/**
 * Vega IPTV Raf Düzeni (StreamVault görsel dili).
 * Öne çıkan kanal alanı, yatay raflar, hızlı arama ızgarası, yenileme ve düzen değiştirici içerir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfLiveLayout(
    allLiveStreams: List<LiveStream>,
    visibleCategories: List<LiveCategory>,
    recentChannels: List<LiveStream>,
    favoriteIds: Set<String>,
    epgSummaryMap: Map<Int, EpgProgram>,
    currentPlayingId: Int?,
    isLoading: Boolean,
    onChannelClick: (LiveStream) -> Unit,
    onFavoriteToggle: (LiveStream) -> Unit,
    onLoadEpg: (Int) -> Unit,
    onRefresh: () -> Unit,
    onToggleLayout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    BackHandler(enabled = searchQuery.isNotBlank()) {
        searchQuery = ""
    }

    var selectedSeeAllCategory by remember { mutableStateOf<Pair<String, List<LiveStream>>?>(null) }

    if (selectedSeeAllCategory != null) {
        val (catTitle, catChannels) = selectedSeeAllCategory!!
        SeeAllCategoryView(
            categoryTitle = catTitle,
            channels = catChannels,
            epgSummaryMap = epgSummaryMap,
            favoriteIds = favoriteIds,
            currentPlayingId = currentPlayingId,
            onChannelClick = onChannelClick,
            onFavoriteToggle = onFavoriteToggle,
            onLoadEpg = onLoadEpg,
            onBack = { selectedSeeAllCategory = null },
            modifier = modifier
        )
        return
    }

    val isSearching = searchQuery.isNotBlank()
    val searchResults = remember(allLiveStreams, searchQuery) {
        if (!isSearching) emptyList()
        else {
            val q = searchQuery.trim().lowercase()
            allLiveStreams.filter { it.name.lowercase().contains(q) }
        }
    }

    val streamsByCategory = remember(allLiveStreams) {
        allLiveStreams.groupBy { it.categoryId }
    }

    val favoriteChannels = remember(allLiveStreams, favoriteIds) {
        allLiveStreams.filter { favoriteIds.contains(it.streamId.toString()) }
    }

    val nowPlayingChannels = remember(allLiveStreams, epgSummaryMap) {
        val withEpg = allLiveStreams.filter { epgSummaryMap.containsKey(it.streamId) }
        if (withEpg.isNotEmpty()) withEpg else allLiveStreams.take(20)
    }

    val heroChannel = remember(recentChannels, favoriteChannels, allLiveStreams) {
        recentChannels.firstOrNull() ?: favoriteChannels.firstOrNull() ?: allLiveStreams.firstOrNull()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .vegaBackdrop()
    ) {
        // Üst Çubuk
        Surface(
            color = VegaColors.Surface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, VegaColors.Border.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Canlı TV",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.SectionTitle,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = VegaShapes.Small,
                        color = VegaColors.Accent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "Raf düzeni",
                            color = VegaColors.Accent,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                // Sağ Hızlı İşlem Butonları (Tek odak hedefli)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShelfTopActionButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Yenile",
                        tint = VegaColors.TextPrimary,
                        onClick = onRefresh
                    )
                    ShelfTopActionButton(
                        icon = Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = "Klasik düzene geç",
                        tint = VegaColors.Accent,
                        onClick = onToggleLayout
                    )
                }
            }
        }

        // StreamVault TV-güvenli Arama Alanı
        StreamVaultSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = stringResource(R.string.live_shelf_search_hint),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Arama Sonuçları veya Raflar
        if (isSearching) {
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aramanla eşleşen canlı kanal bulunamadı.",
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Body
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = searchResults,
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else if (isLoading && allLiveStreams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = VegaColors.Accent,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Kanallar hazırlanıyor...",
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Secondary
                    )
                }
            }
        } else if (allLiveStreams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = VegaColors.TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.live_shelf_empty),
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Body
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (heroChannel != null) {
                    item(key = "hero_section") {
                        HeroCard(
                            channel = heroChannel,
                            epg = epgSummaryMap[heroChannel.streamId],
                            isFavorite = favoriteIds.contains(heroChannel.streamId.toString()),
                            onPlay = onChannelClick,
                            onFavoriteToggle = onFavoriteToggle,
                            onLoadEpg = { onLoadEpg(heroChannel.streamId) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                if (recentChannels.isNotEmpty()) {
                    item(key = "shelf_recent") {
                        ShelfRow(
                            title = stringResource(R.string.live_shelf_continue),
                            channels = recentChannels,
                            epgSummaryMap = epgSummaryMap,
                            favoriteIds = favoriteIds,
                            currentPlayingId = currentPlayingId,
                            onChannelClick = onChannelClick,
                            onFavoriteToggle = onFavoriteToggle,
                            onLoadEpg = onLoadEpg,
                            onSeeAllClick = {
                                selectedSeeAllCategory = Pair("Devam Et (Son İzlenenler)", recentChannels)
                            }
                        )
                    }
                }

                if (favoriteChannels.isNotEmpty()) {
                    item(key = "shelf_favorites") {
                        ShelfRow(
                            title = stringResource(R.string.live_shelf_favorites),
                            channels = favoriteChannels,
                            epgSummaryMap = epgSummaryMap,
                            favoriteIds = favoriteIds,
                            currentPlayingId = currentPlayingId,
                            onChannelClick = onChannelClick,
                            onFavoriteToggle = onFavoriteToggle,
                            onLoadEpg = onLoadEpg,
                            onSeeAllClick = {
                                selectedSeeAllCategory = Pair("Favori Kanallar", favoriteChannels)
                            }
                        )
                    }
                }

                if (nowPlayingChannels.isNotEmpty()) {
                    item(key = "shelf_now_playing") {
                        ShelfRow(
                            title = stringResource(R.string.live_shelf_now_playing),
                            channels = nowPlayingChannels,
                            epgSummaryMap = epgSummaryMap,
                            favoriteIds = favoriteIds,
                            currentPlayingId = currentPlayingId,
                            onChannelClick = onChannelClick,
                            onFavoriteToggle = onFavoriteToggle,
                            onLoadEpg = onLoadEpg,
                            onSeeAllClick = {
                                selectedSeeAllCategory = Pair("Şu An Canlı", nowPlayingChannels)
                            }
                        )
                    }
                }

                val contentCategories = visibleCategories.filter {
                    it.categoryId != "all" && it.categoryId != "favorites" && it.categoryId != "recents"
                }

                items(
                    items = contentCategories,
                    key = { "shelf_cat_${it.categoryId}" }
                ) { cat ->
                    val catChannels = streamsByCategory[cat.categoryId] ?: emptyList()
                    if (catChannels.isNotEmpty()) {
                        ShelfRow(
                            title = cat.categoryName,
                            channels = catChannels,
                            epgSummaryMap = epgSummaryMap,
                            favoriteIds = favoriteIds,
                            currentPlayingId = currentPlayingId,
                            onChannelClick = onChannelClick,
                            onFavoriteToggle = onFavoriteToggle,
                            onLoadEpg = onLoadEpg,
                            onSeeAllClick = {
                                selectedSeeAllCategory = Pair(cat.categoryName, catChannels)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfTopActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Small,
        color = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated,
        border = if (isFocused) null else BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .size(38.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Small,
                scale = 1.04f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
