package com.tivimax.player.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.live.EpgChannelCard
import com.tivimax.player.ui.series.SeriesPosterCard
import com.tivimax.player.ui.vod.MoviePosterCard

@Composable
fun FavoritesScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Canlı Kanallar", "Filmler", "Diziler")

    val favoriteLiveIds by viewModel.favoriteLiveIds.collectAsState()
    val favoriteVodIds by viewModel.favoriteVodIds.collectAsState()
    val favoriteSeriesIds by viewModel.favoriteSeriesIds.collectAsState()

    val allChannels by viewModel.liveStreams.collectAsState()
    val allMovies by viewModel.vodStreams.collectAsState()
    val allSeries by viewModel.seriesStreams.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()

    val favChannels = remember(favoriteLiveIds, allChannels) {
        allChannels.filter { favoriteLiveIds.contains(it.streamId.toString()) }.distinctBy { it.streamId }
    }

    val favMovies = remember(favoriteVodIds, allMovies) {
        allMovies.filter { favoriteVodIds.contains(it.streamId.toString()) }.distinctBy { it.streamId }
    }

    val favSeries = remember(favoriteSeriesIds, allSeries) {
        allSeries.filter { favoriteSeriesIds.contains(it.seriesId.toString()) }.distinctBy { it.seriesId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = TiviSurface,
            contentColor = TiviPrimary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            color = if (selectedTab == index) TiviPrimary else TiviTextSecondary
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                // CANLI KANALLAR FAVORİLERİ
                if (favChannels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Henüz favori canlı kanal eklemediniz.\nKanalların yanındaki yıldız simgesine tıklayarak ekleyebilirsiniz.",
                            color = TiviTextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(favChannels, key = { "${it.streamId}_${it.num}" }) { channel ->
                            EpgChannelCard(
                                channel = channel,
                                isFavorite = true,
                                isPlaying = false,
                                onChannelClick = { viewModel.playLiveChannel(channel) },
                                onPlayFullscreen = { viewModel.playLiveChannel(channel) },
                                onFavoriteToggle = { viewModel.toggleFavoriteLive(channel.streamId.toString()) }
                            )
                        }
                    }
                }
            }
            1 -> {
                // FİLMLER FAVORİLERİ
                if (favMovies.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Henüz favori film eklemediniz.\nFilm detay sayfasından 'Listeme Ekle' ile ekleyebilirsiniz.",
                            color = TiviTextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(favMovies, key = { "${it.streamId}_${it.num}" }) { movie ->
                            MoviePosterCard(
                                movie = movie,
                                watchProgress = watchProgressMap[movie.streamId.toString()],
                                onClick = { viewModel.loadVodDetail(movie) }
                            )
                        }
                    }
                }
            }
            2 -> {
                // DİZİLER FAVORİLERİ
                if (favSeries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Henüz favori dizi eklemediniz.\nDizi detay sayfasından 'Diziyi Favorilerime Ekle' ile ekleyebilirsiniz.",
                            color = TiviTextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(favSeries, key = { "${it.seriesId}_${it.num}" }) { series ->
                            SeriesPosterCard(
                                series = series,
                                onClick = { viewModel.loadSeriesDetail(series) }
                            )
                        }
                    }
                }
            }
        }
    }
}
