package com.tivimax.player.ui.favorites

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.MediaPosterCard
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.design.*
import com.tivimax.player.util.LocalIsTvDevice
import java.util.Locale

@Composable
fun FavoritesScreen(viewModel: MainViewModel) {
    // G-034: Seçili sekme ve ızgara konumu bellekte saklanır; detaydan dönünce aynı yerden başlar
    val positionStore = viewModel.catalogPositions
    var selectedTab by remember { mutableIntStateOf(positionStore.get("fav:sekme")?.firstIndex ?: 0) }

    val isTvDevice = LocalIsTvDevice.current
    BackHandler(enabled = !isTvDevice && selectedTab != 0) {
        selectedTab = 0
    }

    val favChannels by viewModel.favoriteLiveStreams.collectAsState()
    val favMovies by viewModel.favoriteVodStreams.collectAsState()
    val favSeries by viewModel.favoriteSeriesStreams.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()

    val totalFavorites = favChannels.size + favMovies.size + favSeries.size
    val vodAndSeriesCount = favMovies.size + favSeries.size
    val tabs = listOf(
        "Tümü" to totalFavorites,
        "Canlı TV" to favChannels.size,
        "Filmler ve Diziler" to vodAndSeriesCount
    )

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val gridColumns = if (isWideScreen) (configuration.screenWidthDp / 148).coerceIn(5, 7) else 3

    val positionKey = "fav:$selectedTab"
    val savedPosition = remember(positionKey) { positionStore.get(positionKey) }
    val gridState = rememberSaveable(
        positionKey,
        saver = LazyGridState.Saver
    ) {
        LazyGridState(savedPosition?.firstIndex ?: 0, savedPosition?.offset ?: 0)
    }
    DisposableEffect(positionKey) {
        onDispose {
            positionStore.save(positionKey, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset, 0)
            positionStore.save("fav:sekme", selectedTab, 0, 0)
        }
    }
    // G-034: TV'de dönüşte son açılan afişe odak ver (G-033/G-042/G-045 odak koduna dokunmadan)
    val restoreFocusKey = remember(positionKey) { if (isTvDevice) positionStore.lastOpenedItemKey?.takeIf { it.startsWith("fav_") } else null }
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ÜST BAŞLIK VE STREAMVAULT KAPSÜL FİLTRE SEKMELERİ
        if (isWideScreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = VegaShapes.Small,
                        color = VegaColors.Gold.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, VegaColors.Gold.copy(alpha = 0.35f)),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = VegaColors.Gold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "Favoriler",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.SectionTitle,
                        fontWeight = FontWeight.Bold
                    )
                    if (totalFavorites > 0) {
                        Surface(
                            shape = VegaShapes.Pill,
                            color = VegaColors.SurfaceElevated,
                            border = BorderStroke(1.dp, VegaColors.Border)
                        ) {
                            Text(
                                text = "$totalFavorites",
                                color = VegaColors.Accent,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                FavoritesSegmentedTabs(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FavoritesSegmentedTabs(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // İÇERİK VEYA BOŞ DURUM (STREAMVAULT BOŞ DURUM KARTI)
        val isEmpty = when (selectedTab) {
            0 -> totalFavorites == 0
            1 -> favChannels.isEmpty()
            2 -> favMovies.isEmpty() && favSeries.isEmpty()
            else -> false
        }

        if (isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth(0.9f)
                        .vegaPanel(VegaShapes.Hero)
                        .padding(horizontal = 28.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = VegaColors.Gold.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, VegaColors.Gold.copy(alpha = 0.30f)),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = VegaColors.Gold,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Henüz favori içerik eklemedin",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.SectionTitle,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Beğendiğin canlı kanal, film veya dizileri yıldız simgesini kullanarak favorilerine ekleyebilirsin.",
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Secondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // CANLI TV FAVORİLERİ
                if (selectedTab == 0 || selectedTab == 1) {
                    itemsIndexed(favChannels, key = { idx, it -> "fav_live_${it.streamId}_$idx" }) { idx, channel ->
                        val itemKey = "fav_live_${channel.streamId}_$idx"
                        FocusRestoreSlot(itemKey, restoreFocusKey, restoreFocusRequester) {
                            MediaPosterCard(
                                imageUrl = channel.streamIcon,
                                title = channel.name,
                                subtitle = "Canlı TV",
                                rating = null,
                                isFavorite = true,
                                onClick = {
                                    positionStore.lastOpenedItemKey = itemKey
                                    viewModel.playLiveChannel(channel)
                                }
                            )
                        }
                    }
                }

                // FİLM FAVORİLERİ
                if (selectedTab == 0 || selectedTab == 2) {
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
                }

                // DİZİ FAVORİLERİ
                if (selectedTab == 0 || selectedTab == 2) {
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
private fun FavoritesSegmentedTabs(
    tabs: List<Pair<String, Int>>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    Surface(
        shape = VegaShapes.Pill,
        color = VegaColors.Surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = VegaText.Secondary,
                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) VegaColors.OnAccent else VegaColors.TextPrimary
                        )
                        if (count > 0) {
                            Text(
                                text = "$count",
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) VegaColors.OnAccent.copy(alpha = 0.78f) else VegaColors.Accent
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * G-034: Dönüşte odak verilecek kartı (yalnızca eşleşen kartı) odak istemcisiyle sarar; diğer kartlar aynen çizilir.
 * İstemci, kartın içindeki ilk odaklanabilir öğeye (dpadFocusable) odak aktarır.
 */
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
