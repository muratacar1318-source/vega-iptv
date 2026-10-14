package com.tivimax.player.ui.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.tivimax.player.data.model.SeriesEpisode
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel

@Composable
fun SeriesDetailScreen(
    series: SeriesStream,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val seriesInfoResponse by viewModel.currentSeriesInfo.collectAsState()
    val episodesMap = seriesInfoResponse?.episodes ?: emptyMap()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()

    // Mevcut Sezonlar
    val seasonKeys = remember(episodesMap) {
        episodesMap.keys.sortedBy { it.toIntOrNull() ?: 1 }
    }

    var selectedSeason by remember(seasonKeys) {
        mutableStateOf(seasonKeys.firstOrNull() ?: "1")
    }

    val episodesForSeason = remember(episodesMap, selectedSeason) {
        episodesMap[selectedSeason] ?: emptyList()
    }

    val info = seriesInfoResponse?.info

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
        // Arka Plan Afiş Resmi (Backdrop - Netflix Ambient Blur)
        com.tivimax.player.ui.components.AsyncShimmerImage(
            model = info?.cover ?: series.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholderIcon = Icons.Default.VideoLibrary,
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .then(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Modifier.blur(20.dp)
                    } else {
                        Modifier
                    }
                )
        )

        // Gradient Karartma (Netflix Sinematik Fade to OLED Black)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            TiviBackground.copy(alpha = 0.4f),
                            TiviBackground.copy(alpha = 0.85f),
                            TiviBackground
                        )
                    )
                )
        )

        // Ana İçerik
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(180.dp))

                // Dizi Başlığı
                Text(
                    text = info?.name ?: series.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TiviTextPrimary,
                    lineHeight = 32.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Meta Bilgiler (Puan, Tür)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val rating = info?.rating ?: series.rating
                    if (!rating.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TiviGold.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = TiviGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "IMDb $rating",
                                    color = TiviGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    info?.genre?.let { genre ->
                        if (genre.isNotBlank()) {
                            Text(text = genre, color = TiviPrimary, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Konu Özeti
                val plot = info?.plot ?: series.plot
                if (!plot.isNullOrEmpty()) {
                    Text(
                        text = plot,
                        color = TiviTextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sezon Seçici Çipler
                Text(
                    text = "Bölümler",
                    color = TiviTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (seasonKeys.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(seasonKeys) { seasonNum ->
                            val isSelected = seasonNum == selectedSeason
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedSeason = seasonNum },
                                label = { Text("Sezon $seasonNum") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TiviSecondary,
                                    selectedLabelColor = Color.White,
                                    containerColor = TiviCard,
                                    labelColor = TiviTextSecondary
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                if (episodesForSeason.isNotEmpty()) {
                    val nextEp = episodesForSeason.first()
                    val nextEpProgress = watchProgressMap[nextEp.id]
                    val hasNextProgress = nextEpProgress != null && nextEpProgress.positionMs > 10_000L && nextEpProgress.durationMs > 0
                    Button(
                        onClick = { viewModel.playSeriesEpisode(nextEp, series.name, resume = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = TiviSecondary, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasNextProgress) {
                                val mins = (nextEpProgress.positionMs / 60000)
                                val secs = ((nextEpProgress.positionMs % 60000) / 1000)
                                "Kaldığın Yerden Devam Et (${String.format("%02d:%02d", mins, secs)})"
                            } else {
                                "Sezon $selectedSeason, ${nextEp.episodeNum}. Bölümü Başlat"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                val favoriteSeriesIds by viewModel.favoriteSeriesIds.collectAsState()
                val isFavorite = favoriteSeriesIds.contains(series.seriesId.toString())

                OutlinedButton(
                    onClick = { viewModel.toggleFavoriteSeries(series.seriesId.toString()) },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (isFavorite) TiviGold else TiviTextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFavorite) "Diziyi Favorilerimden Çıkar" else "Diziyi Favorilerime Ekle",
                        color = TiviTextPrimary,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Bölüm Listesi
            if (episodesForSeason.isEmpty()) {
                item {
                    Text(
                        text = "Bölümler yükleniyor...",
                        color = TiviTextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(episodesForSeason) { episode ->
                    EpisodeCard(
                        episode = episode,
                        watchProgress = watchProgressMap[episode.id],
                        onEpisodeClick = { viewModel.playSeriesEpisode(episode, series.name, resume = true) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        // Geri Butonu (En Üst Katman - zIndex 10f ile her zaman dokunulabilir)
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.65f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .size(44.dp)
                .zIndex(10f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun EpisodeCard(
    episode: SeriesEpisode,
    watchProgress: WatchProgress? = null,
    onEpisodeClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TiviCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEpisodeClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bölüm Görseli / İkonu
            Box(
                modifier = Modifier
                    .size(width = 88.dp, height = 58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F141F)),
                contentAlignment = Alignment.Center
            ) {
                if (!episode.info?.movieImage.isNullOrEmpty()) {
                    AsyncImage(
                        model = episode.info?.movieImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Oynat",
                    tint = TiviPrimary,
                    modifier = Modifier.size(26.dp)
                )

                // Kaldığı Yer İlerleme Çizgisi
                if (watchProgress != null && watchProgress.durationMs > 0 && watchProgress.positionMs > 10_000L) {
                    val progressFraction = (watchProgress.positionMs.toFloat() / watchProgress.durationMs.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(TiviGold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${episode.episodeNum}. Bölüm: ${episode.title.ifEmpty { "Bölüm ${episode.episodeNum}" }}",
                    color = TiviTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    episode.info?.duration?.let { dur ->
                        Text(text = dur, color = TiviTextSecondary, fontSize = 12.sp)
                    }
                    if (watchProgress != null && watchProgress.durationMs > 0 && watchProgress.positionMs > 10_000L) {
                        if (episode.info?.duration != null) {
                            Text(text = " • ", color = TiviTextSecondary, fontSize = 12.sp)
                        }
                        val mins = (watchProgress.positionMs / 60000)
                        val secs = ((watchProgress.positionMs % 60000) / 1000)
                        Text(
                            text = "Kaldığın Yer: ${String.format("%02d:%02d", mins, secs)}",
                            color = TiviGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
