package com.tivimax.player.ui.favorites

import androidx.activity.compose.BackHandler
import com.tivimax.player.util.LocalIsTvDevice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.MediaPosterCard
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes

@Composable
fun FavoritesScreen(viewModel: MainViewModel) {
    // G-034: Seçili sekme ve ızgara konumu bellekte saklanır; detaydan dönünce aynı yerden başlar
    val positionStore = viewModel.catalogPositions
    var selectedTab by remember { mutableIntStateOf(positionStore.get("fav:sekme")?.firstIndex ?: 0) }
    val tabs = listOf("Tümü", "Canlı TV", "Filmler & Diziler")

    val isTvDevice = LocalIsTvDevice.current
    BackHandler(enabled = !isTvDevice && selectedTab != 0) {
        selectedTab = 0
    }

    val favChannels by viewModel.favoriteLiveStreams.collectAsState()
    val favMovies by viewModel.favoriteVodStreams.collectAsState()
    val favSeries by viewModel.favoriteSeriesStreams.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()

    val totalFavorites = favChannels.size + favMovies.size + favSeries.size

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val gridColumns = if (isWideScreen) (configuration.screenWidthDp / 140).coerceIn(5, 7) else 3

    val positionKey = "fav:$selectedTab"
    val savedPosition = remember(positionKey) { positionStore.get(positionKey) }
    val gridState = androidx.compose.runtime.saveable.rememberSaveable(
        positionKey,
        saver = androidx.compose.foundation.lazy.grid.LazyGridState.Saver
    ) {
        androidx.compose.foundation.lazy.grid.LazyGridState(savedPosition?.firstIndex ?: 0, savedPosition?.offset ?: 0)
    }
    DisposableEffect(positionKey) {
        onDispose {
            positionStore.save(positionKey, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset, 0)
            positionStore.save("fav:sekme", selectedTab, 0, 0)
        }
    }
    // G-034: TV'de dönüşte son açılan afişe odak ver (G-033/G-042/G-045 odak koduna dokunmadan)
    val restoreFocusKey = remember(positionKey) { if (isTvDevice) positionStore.lastOpenedItemKey?.takeIf { it.startsWith("fav_") } else null }
    val restoreFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(restoreFocusKey) {
        if (restoreFocusKey == null) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        try {
            restoreFocusRequester.requestFocus()
        } catch (_: Exception) {
            withFrameNanos { }
            try { restoreFocusRequester.requestFocus() } catch (_: Exception) { /* sessiz: öğe henüz yerleşmedi, odak vazgeçildi */ }
        }
        positionStore.lastOpenedItemKey = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VegaColors.Background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ÜST KAPSÜL FİLTRE SEKMELERİ (SEGMENTED CONTROL)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = VegaShapes.Pill,
                color = VegaColors.Surface,
                border = BorderStroke(1.dp, VegaColors.Border),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Surface(
                            shape = VegaShapes.Pill,
                            color = if (isSelected) VegaColors.Accent else Color.Transparent,
                            modifier = Modifier
                                .height(36.dp)
                                .dpadFocusable(
                                    shape = VegaShapes.Pill,
                                    focusedBorderColor = if (isSelected) Color.White else VegaColors.Accent,
                                    onClick = { selectedTab = index }
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) VegaColors.OnAccent else VegaColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // İÇERİK VEYA BOŞ DURUM (EMPTY STATE)
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = VegaColors.TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Henüz favori içerik eklemedin",
                        color = VegaColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Beğendiğin canlı kanal, film veya dizileri yıldız simgesini kullanarak favorilerine ekleyebilirsin.",
                        color = VegaColors.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // CANLI TV FAVORİLERİ
                if (selectedTab == 0 || selectedTab == 1) {
                    items(favChannels, key = { "fav_live_${it.streamId}" }) { channel ->
                        val itemKey = "fav_live_${channel.streamId}"
                        FocusRestoreSlot(itemKey, restoreFocusKey, restoreFocusRequester) {
                            MediaPosterCard(
                                imageUrl = channel.streamIcon,
                                title = channel.name,
                                subtitle = "Canlı TV",
                                rating = null,
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
                    items(favMovies, key = { "fav_vod_${it.streamId}" }) { movie ->
                        val rating = movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
                        val itemKey = "fav_vod_${movie.streamId}"
                        FocusRestoreSlot(itemKey, restoreFocusKey, restoreFocusRequester) {
                            MediaPosterCard(
                                imageUrl = movie.streamIcon,
                                title = movie.name,
                                subtitle = "Film",
                                rating = rating,
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
                    items(favSeries, key = { "fav_series_${it.seriesId}" }) { series ->
                        val rating = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
                        val releaseYear = series.releaseDate?.take(4)?.ifBlank { "2024" } ?: "2024"
                        val itemKey = "fav_series_${series.seriesId}"
                        FocusRestoreSlot(itemKey, restoreFocusKey, restoreFocusRequester) {
                            MediaPosterCard(
                                imageUrl = series.cover,
                                title = series.name,
                                subtitle = "$releaseYear • Dizi",
                                rating = rating,
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

/**
 * G-034: Dönüşte odak verilecek kartı (yalnızca eşleşen kartı) odak istemcisiyle sarar; diğer kartlar aynen çizilir.
 * İstemci, kartın içindeki ilk odaklanabilir öğeye (dpadFocusable) odak aktarır.
 */
@Composable
private fun FocusRestoreSlot(
    itemKey: String,
    restoreFocusKey: String?,
    requester: androidx.compose.ui.focus.FocusRequester,
    content: @Composable () -> Unit
) {
    if (restoreFocusKey != null && itemKey == restoreFocusKey) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.focusRequester(requester)) { content() }
    } else {
        content()
    }
}
