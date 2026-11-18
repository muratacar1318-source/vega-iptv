package com.tivimax.player.ui.favorites

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.MediaPosterCard
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.design.*
import com.tivimax.player.util.LocalIsTvDevice
import java.util.Locale

@Composable
fun FavoritesScreen(viewModel: MainViewModel) {
    // G-034: Seçili sekme ve konum bellekte saklanır; detaydan dönünce aynı yerden başlar
    val positionStore = viewModel.catalogPositions
    var selectedTab by remember {
        mutableIntStateOf((positionStore.get("fav:sekme")?.firstIndex ?: 0).coerceIn(0, 2))
    }

    val isTvDevice = LocalIsTvDevice.current
    BackHandler(enabled = !isTvDevice && selectedTab != 0) {
        selectedTab = 0
    }

    val favChannels by viewModel.favoriteLiveStreams.collectAsState()
    val favMovies by viewModel.favoriteVodStreams.collectAsState()
    val favSeries by viewModel.favoriteSeriesStreams.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()

    val tabs = listOf(
        "Canlı" to favChannels.size,
        "Filmler" to favMovies.size,
        "Diziler" to favSeries.size
    )

    val configuration = LocalConfiguration.current
    val isWideScreen = isTvDevice ||
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600
    val gridColumns = if (isWideScreen) (configuration.screenWidthDp / 148).coerceIn(5, 7) else 3
    val gridSpacing = if (isWideScreen) 12.dp else 10.dp

    val positionKey = "fav:$selectedTab"
    val savedPosition = remember(positionKey) { positionStore.get(positionKey) }

    val liveListState = rememberSaveable(
        "fav_live_list",
        saver = LazyListState.Saver
    ) {
        val savedLive = positionStore.get("fav:0")
        LazyListState(savedLive?.firstIndex ?: 0, savedLive?.offset ?: 0)
    }

    val gridState = rememberSaveable(
        positionKey,
        saver = LazyGridState.Saver
    ) {
        LazyGridState(savedPosition?.firstIndex ?: 0, savedPosition?.offset ?: 0)
    }

    DisposableEffect(positionKey) {
        onDispose {
            if (selectedTab == 0) {
                positionStore.save(positionKey, liveListState.firstVisibleItemIndex, liveListState.firstVisibleItemScrollOffset, 0)
            } else {
                positionStore.save(positionKey, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset, 0)
            }
            positionStore.save("fav:sekme", selectedTab, 0, 0)
        }
    }

    // G-034: TV'de dönüşte son açılan öğeye odak ver
    val restoreFocusKey = remember(positionKey) {
        if (isTvDevice) positionStore.lastOpenedItemKey?.takeIf { it.startsWith("fav_") } else null
    }
    val restoreFocusRequester = remember { FocusRequester() }
    LaunchedEffect(restoreFocusKey) {
        if (restoreFocusKey == null) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        if (!restoreFocusRequester.requestFocusSafely()) {
            withFrameNanos { }
            restoreFocusRequester.requestFocusSafely()
        }
        positionStore.lastOpenedItemKey = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!isWideScreen) Modifier.statusBarsPadding() else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Üst Başlık ve Segmentli Filtre Sekmeleri (Canlı / Filmler / Diziler)
        if (isWideScreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Favoriler",
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.ScreenTitle,
                    fontWeight = FontWeight.Bold
                )

                FavoritesSegmentedTabs(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    fillWidth = false,
                    onSelectTab = { selectedTab = it }
                )
            }
        } else {
            Text(
                text = "Favoriler",
                color = VegaColors.TextPrimary,
                fontSize = VegaText.ScreenTitle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            FavoritesSegmentedTabs(
                tabs = tabs,
                selectedTab = selectedTab,
                fillWidth = true,
                onSelectTab = { selectedTab = it }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        val isEmpty = when (selectedTab) {
            0 -> favChannels.isEmpty()
            1 -> favMovies.isEmpty()
            else -> favSeries.isEmpty()
        }

        if (isEmpty) {
            val emptyLabel = when (selectedTab) {
                0 -> "Favori canlı kanal yok"
                1 -> "Favori film yok"
                else -> "Favori dizi yok"
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = VegaColors.TextMuted,
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = emptyLabel,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.BodyProminent,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (selectedTab == 0) {
            // CANLI KANALLAR: İnce çizgi ayırıcılı temiz liste satırları
            LazyColumn(
                state = liveListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 2.dp, bottom = 96.dp)
            ) {
                itemsIndexed(favChannels, key = { idx, it -> "fav_live_${it.streamId}_$idx" }) { idx, channel ->
                    val itemKey = "fav_live_${channel.streamId}_$idx"
                    FocusRestoreSlot(itemKey, restoreFocusKey, restoreFocusRequester) {
                        FavoriteLiveChannelRow(
                            channel = channel,
                            showDivider = idx < favChannels.lastIndex,
                            onClick = {
                                positionStore.lastOpenedItemKey = itemKey
                                viewModel.playLiveChannel(channel)
                            }
                        )
                    }
                }
            }
        } else {
            // FİLMLER / DİZİLER: Mobilde 3 sütunlu temiz afiş ızgarası
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 2.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                verticalArrangement = Arrangement.spacedBy(gridSpacing)
            ) {
                if (selectedTab == 1) {
                    itemsIndexed(favMovies, key = { idx, it -> "fav_vod_${it.streamId}_$idx" }) { idx, movie ->
                        val rating = movie.rating ?: movie.rating5Based?.let { String.format(Locale.US, "%.1f", it * 2) }
                        val prog = watchProgressMap[movie.streamId.toString()]
                        val progressFraction = if (prog != null && !prog.isWatched && prog.durationMs > 0L) {
                            (prog.positionMs.toFloat() / prog.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val itemKey = "fav_vod_${movie.streamId}_$idx"
                        FocusRestoreSlot(itemKey, restoreFocusKey, restoreFocusRequester) {
                            MediaPosterCard(
                                imageUrl = movie.streamIcon,
                                title = movie.name,
                                subtitle = "Film",
                                rating = rating,
                                watchProgress = progressFraction,
                                isFavorite = true,
                                onClick = {
                                    positionStore.lastOpenedItemKey = itemKey
                                    viewModel.loadVodDetail(movie)
                                }
                            )
                        }
                    }
                } else {
                    itemsIndexed(favSeries, key = { idx, it -> "fav_series_${it.seriesId}_$idx" }) { idx, series ->
                        val rating = series.rating ?: series.rating5Based?.let { String.format(Locale.US, "%.1f", it * 2) }
                        val releaseYear = series.releaseDate?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
                        val seriesSubtitle = if (releaseYear != null) "$releaseYear • Dizi" else "Dizi"
                        val itemKey = "fav_series_${series.seriesId}_$idx"
                        FocusRestoreSlot(itemKey, restoreFocusKey, restoreFocusRequester) {
                            MediaPosterCard(
                                imageUrl = series.cover,
                                title = series.name,
                                subtitle = seriesSubtitle,
                                rating = rating,
                                isFavorite = true,
                                onClick = {
                                    positionStore.lastOpenedItemKey = itemKey
                                    viewModel.loadSeriesDetail(series)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteLiveChannelRow(
    channel: LiveStream,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = VegaShapes.Small,
            color = if (isFocused) VegaColors.SurfaceElevated else Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .dpadFocusable(
                    shape = VegaShapes.Small,
                    focusedBorderColor = VegaColors.FocusBorder,
                    onClick = onClick,
                    onFocusStateChange = { isFocused = it }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 58.dp)
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(VegaShapes.Small)
                        .background(VegaColors.SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncShimmerImage(
                        model = channel.streamIcon,
                        contentDescription = channel.name,
                        placeholderIcon = Icons.Default.Tv,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (channel.num > 0) {
                            Text(
                                text = "${channel.num}",
                                color = VegaColors.TextMuted,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        Text(
                            text = channel.name,
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Body,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val program = channel.currentProgram?.trim()?.takeIf { it.isNotBlank() }
                    if (program != null) {
                        Text(
                            text = program,
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = VegaColors.Gold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Oynat",
                    tint = if (isFocused) VegaColors.Accent else VegaColors.TextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 60.dp, end = 8.dp),
                color = VegaColors.Border.copy(alpha = 0.45f),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
private fun FavoritesSegmentedTabs(
    tabs: List<Pair<String, Int>>,
    selectedTab: Int,
    fillWidth: Boolean,
    onSelectTab: (Int) -> Unit
) {
    Surface(
        shape = VegaShapes.Pill,
        color = VegaColors.Surface,
        border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.65f)),
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
    ) {
        Row(
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            tabs.forEachIndexed { index, (title, count) ->
                val isSelected = selectedTab == index
                var isFocused by remember { mutableStateOf(false) }
                Surface(
                    shape = VegaShapes.Pill,
                    color = when {
                        isSelected -> VegaColors.Accent
                        isFocused -> VegaColors.SurfaceElevated
                        else -> Color.Transparent
                    },
                    modifier = Modifier
                        .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                        .height(36.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Pill,
                            focusedBorderColor = if (isSelected) Color.White else VegaColors.FocusBorder,
                            onClick = { onSelectTab(index) },
                            onFocusStateChange = { isFocused = it }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = if (fillWidth) 8.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = title,
                            fontSize = VegaText.Secondary,
                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) VegaColors.OnAccent else VegaColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (count > 0) {
                            Text(
                                text = "$count",
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) VegaColors.OnAccent.copy(alpha = 0.78f) else VegaColors.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusRestoreSlot(
    itemKey: String,
    restoreFocusKey: String?,
    requester: FocusRequester,
    content: @Composable () -> Unit
) {
    if (restoreFocusKey != null && itemKey == restoreFocusKey) {
        Box(modifier = Modifier.focusRequester(requester)) { content() }
    } else {
        content()
    }
}
