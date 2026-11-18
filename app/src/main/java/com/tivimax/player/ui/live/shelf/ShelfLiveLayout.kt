package com.tivimax.player.ui.live.shelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.delay

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

    var nowEpochSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowEpochSec = System.currentTimeMillis() / 1000L
        }
    }

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
            modifier = modifier,
            nowEpochSec = nowEpochSec
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

    var searchVisibleLimit by remember(searchResults) { mutableIntStateOf(50) }
    val pagedSearchResults = remember(searchResults, searchVisibleLimit) {
        searchResults.take(searchVisibleLimit)
    }

    val streamsByCategory = remember(allLiveStreams) {
        allLiveStreams.groupBy { it.categoryId }
    }

    val contentCategories = remember(visibleCategories, streamsByCategory) {
        visibleCategories
            .filter {
                it.categoryId != "all" && it.categoryId != "favorites" && it.categoryId != "recents" &&
                    !streamsByCategory[it.categoryId].isNullOrEmpty()
            }
            .distinctBy { it.categoryId }
    }

    var categoryVisibleLimit by remember(contentCategories) { mutableIntStateOf(15) }
    val pagedContentCategories = remember(contentCategories, categoryVisibleLimit) {
        contentCategories.take(categoryVisibleLimit)
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
        // Sade üst çubuk (kutu çerçevesi ve dekoratif rozet yok)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VegaColors.Background.copy(alpha = 0.94f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamVaultSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = stringResource(R.string.live_shelf_search_hint),
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ShelfTopActionButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Yenile",
                        tint = VegaColors.TextSecondary,
                        onClick = onRefresh
                    )
                    ShelfTopActionButton(
                        icon = Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = "Liste düzenine geç",
                        tint = VegaColors.Accent,
                        onClick = onToggleLayout
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = VegaColors.Border.copy(alpha = 0.55f)
            )
        }

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
                    gridItemsIndexed(
                        items = pagedSearchResults,
                        key = { index, ch -> "shelf_search_${ch.streamId}_${ch.categoryId}_$index" }
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

                    if (searchVisibleLimit < searchResults.size) {
                        item(
                            key = "more_search_$searchVisibleLimit",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            LaunchedEffect(searchVisibleLimit) {
                                searchVisibleLimit += 50
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
                            onLoadEpg = { onLoadEpg(heroChannel.streamId) },
                            nowEpochSec = nowEpochSec
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
                                selectedSeeAllCategory = Pair("Son İzlenenler", recentChannels)
                            },
                            nowEpochSec = nowEpochSec
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
                            },
                            nowEpochSec = nowEpochSec
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
                            },
                            nowEpochSec = nowEpochSec
                        )
                    }
                }

                itemsIndexed(
                    items = pagedContentCategories,
                    key = { index, cat -> "shelf_cat_${cat.categoryId}_$index" }
                ) { _, cat ->
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
                            },
                            nowEpochSec = nowEpochSec
                        )
                    }
                }

                if (categoryVisibleLimit < contentCategories.size) {
                    item(key = "more_shelf_cats_$categoryVisibleLimit") {
                        LaunchedEffect(categoryVisibleLimit) {
                            categoryVisibleLimit += 15
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

@Composable
private fun ShelfTopActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(VegaShapes.Pill)
            .background(if (isFocused) VegaColors.SurfaceVariant else Color.Transparent)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Pill,
                scale = 1.04f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) VegaColors.Accent else tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
