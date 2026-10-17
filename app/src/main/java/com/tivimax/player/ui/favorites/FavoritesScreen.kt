package com.tivimax.player.ui.favorites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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

@Composable
fun FavoritesScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tümü", "Canlı TV", "Filmler & Diziler")

    val favChannels by viewModel.favoriteLiveStreams.collectAsState()
    val favMovies by viewModel.favoriteVodStreams.collectAsState()
    val favSeries by viewModel.favoriteSeriesStreams.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()

    val totalFavorites = favChannels.size + favMovies.size + favSeries.size

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val gridColumns = if (isWideScreen) (configuration.screenWidthDp / 140).coerceIn(5, 7) else 3

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ÜST KAPSÜL FİLTRE SEKMELERİ (SEGMENTED CONTROL)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = TiviSurface,
                border = BorderStroke(1.dp, TiviCardBorder),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) OpusCyan else Color.Transparent,
                            modifier = Modifier
                                .height(36.dp)
                                .dpadFocusable(
                                    shape = RoundedCornerShape(50),
                                    focusedBorderColor = Color.White,
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
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else Color.White
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
                        tint = TiviTextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Henüz favori içerik eklemediniz",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Beğendiğiniz canlı kanal, film veya dizileri yıldız ikonasını kullanarak favorilerinize ekleyebilirsiniz.",
                        color = TiviTextSecondary,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // CANLI TV FAVORİLERİ
                if (selectedTab == 0 || selectedTab == 1) {
                    items(favChannels, key = { "fav_live_${it.streamId}" }) { channel ->
                        MediaPosterCard(
                            imageUrl = channel.streamIcon,
                            title = channel.name,
                            subtitle = "Canlı TV",
                            rating = null,
                            onClick = { viewModel.playLiveChannel(channel) }
                        )
                    }
                }

                // FİLM FAVORİLERİ
                if (selectedTab == 0 || selectedTab == 2) {
                    items(favMovies, key = { "fav_vod_${it.streamId}" }) { movie ->
                        val rating = movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
                        MediaPosterCard(
                            imageUrl = movie.streamIcon,
                            title = movie.name,
                            subtitle = "Film",
                            rating = rating,
                            onClick = { viewModel.loadVodDetail(movie) }
                        )
                    }
                }

                // DİZİ FAVORİLERİ
                if (selectedTab == 0 || selectedTab == 2) {
                    items(favSeries, key = { "fav_series_${it.seriesId}" }) { series ->
                        val rating = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
                        val releaseYear = series.releaseDate?.take(4)?.ifBlank { "2024" } ?: "2024"
                        MediaPosterCard(
                            imageUrl = series.cover,
                            title = series.name,
                            subtitle = "$releaseYear • Dizi",
                            rating = rating,
                            onClick = { viewModel.loadSeriesDetail(series) }
                        )
                    }
                }
            }
        }
    }
}
