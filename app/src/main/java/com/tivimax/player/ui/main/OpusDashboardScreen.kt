package com.tivimax.player.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable

@Composable
fun OpusDashboardScreen(viewModel: MainViewModel) {
    val userInfo by viewModel.userInfo.collectAsState()
    val continueWatching by viewModel.continueWatchingMovies.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val liveCategories by viewModel.liveCategories.collectAsState()
    val vodStreams by viewModel.vodStreams.collectAsState()
    val seriesStreams by viewModel.seriesStreams.collectAsState()
    val seriesCategories by viewModel.seriesCategories.collectAsState()
    val favoriteLiveIds by viewModel.favoriteLiveIds.collectAsState()

    // En Son Eklenen Filmler
    val newlyAddedVodList = remember(vodStreams) {
        vodStreams
            .filter { !it.added.isNullOrBlank() }
            .sortedByDescending { it.added?.toLongOrNull() ?: 0L }
            .distinctBy { (it.name ?: "").trim().lowercase() }
            .take(12)
            .ifEmpty { vodStreams.take(10) }
    }

    // En Son Eklenen Diziler
    val newlyAddedSeriesList = remember(seriesStreams) {
        seriesStreams
            .filter { !it.added.isNullOrBlank() || !it.lastModified.isNullOrBlank() }
            .sortedByDescending { (it.lastModified?.toLongOrNull() ?: it.added?.toLongOrNull() ?: 0L) }
            .distinctBy { (it.name ?: "").trim().lowercase() }
            .take(12)
            .ifEmpty { seriesStreams.take(10) }
    }

    val totalLiveCount = remember(liveCategories) { liveCategories.sumOf { it.itemCount } }
    val totalVodCount = remember(vodStreams) { vodStreams.size }
    val totalSeriesCount = remember(seriesCategories) { seriesCategories.sumOf { it.itemCount } }
    val totalFavCount = remember(favoriteLiveIds) { favoriteLiveIds.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VegaColors.Background)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. KULLANICI & HESAP BİLGİ KARTI (HERO BANNER)
        // ==========================================
        Card(
            shape = VegaShapes.Large,
            colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
            border = BorderStroke(1.dp, VegaColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                VegaColors.Accent.copy(alpha = 0.12f),
                                VegaColors.Accent.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(VegaColors.Accent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = userInfo?.username ?: "Hoş geldin",
                                color = VegaColors.TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(VegaColors.Success)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Abonelik Aktif",
                                    color = VegaColors.Success,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.SurfaceVariant,
                        border = BorderStroke(1.dp, VegaColors.Border)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${userInfo?.activeCons ?: "1"}/${userInfo?.maxConnections ?: "1"} Bağlantı",
                                color = VegaColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. OPUS STYLE ANA KATEGORİ IZGARASI (GRID)
        // ==========================================
        Text(
            text = "Kategoriler",
            color = VegaColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OpusTileCard(
                title = "Canlı TV",
                subtitle = if (totalLiveCount > 0) "$totalLiveCount Kanal" else "Canlı Yayınlar",
                icon = Icons.Default.LiveTv,
                accentColor = VegaColors.Accent,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSelectedHomeTab(HomeTab.LIVE_TV) }
            )

            OpusTileCard(
                title = "Filmler",
                subtitle = if (totalVodCount > 0) "$totalVodCount Film" else "Seç İzle",
                icon = Icons.Default.Movie,
                accentColor = VegaColors.Accent,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSelectedHomeTab(HomeTab.MOVIES) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OpusTileCard(
                title = "Diziler",
                subtitle = if (totalSeriesCount > 0) "$totalSeriesCount Dizi" else "Tüm Sezonlar",
                icon = Icons.Default.VideoLibrary,
                accentColor = VegaColors.Accent,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSelectedHomeTab(HomeTab.SERIES) }
            )

            OpusTileCard(
                title = "Favoriler",
                subtitle = if (totalFavCount > 0) "$totalFavCount Kayıtlı" else "Favori listem",
                icon = Icons.Default.Star,
                accentColor = VegaColors.Gold,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSelectedHomeTab(HomeTab.FAVORITES) }
            )

            OpusTileCard(
                title = "Ayarlar",
                subtitle = "Sistem ve tercihler",
                icon = Icons.Default.Settings,
                accentColor = VegaColors.TextSecondary,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSelectedHomeTab(HomeTab.SETTINGS) }
            )
        }

        // ==========================================
        // 3. KALDIĞIN YERDEN DEVAM ET (SADECE FİLM / DİZİ VOD)
        // ==========================================
        if (continueWatching.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Kaldığın Yerden Devam Et",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = VegaColors.TextPrimary
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(continueWatching, key = { "progress_${it.first.streamId}" }) { (progress, movie) ->
                    DashboardProgressCard(
                        movie = movie,
                        progress = progress,
                        onClick = { viewModel.playVod(movie, resume = true) }
                    )
                }
            }
        }

        // ==========================================
        // 4. SON EKLENEN FİLMLER
        // ==========================================
        if (newlyAddedVodList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Whatshot, contentDescription = null, tint = VegaColors.Live, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Son Eklenen Filmler",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = VegaColors.TextPrimary
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(newlyAddedVodList, key = { "new_vod_${it.streamId}" }) { movie ->
                    NewlyAddedVodCard(
                        movie = movie,
                        onClick = { viewModel.loadVodDetail(movie) }
                    )
                }
            }
        }

        // ==========================================
        // 5. SON EKLENEN DİZİLER
        // ==========================================
        if (newlyAddedSeriesList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Son Eklenen Diziler",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = VegaColors.TextPrimary
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(newlyAddedSeriesList, key = { "new_series_${it.seriesId}" }) { series ->
                    NewlyAddedSeriesCard(
                        series = series,
                        onClick = { viewModel.loadSeriesDetail(series) }
                    )
                }
            }
        }

        // ==========================================
        // 6. SON İZLENEN CANLI KANALLAR
        // ==========================================
        if (recentChannels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Son İzlenen Canlı Kanallar",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = VegaColors.TextPrimary
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(recentChannels.take(10), key = { "recent_${it.streamId}" }) { channel ->
                    DashboardRecentChannelCard(
                        channel = channel,
                        onClick = { viewModel.playLiveChannel(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OpusTileCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = VegaShapes.Medium,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = modifier
            .height(84.dp)
            .dpadFocusable(
                focusedBorderColor = accentColor,
                shape = VegaShapes.Medium,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.15f), Color.Transparent),
                        radius = 200f
                    )
                )
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(VegaShapes.Small)
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = VegaColors.TextMuted, modifier = Modifier.size(16.dp))
                }

                Column {
                    Text(
                        text = title,
                        color = VegaColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        color = VegaColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardProgressCard(
    movie: VodStream,
    progress: WatchProgress,
    onClick: () -> Unit
) {
    val progressFraction = if (progress.durationMs > 0) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = VegaShapes.Medium,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .width(130.dp)
            .height(175.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                onClick = onClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncShimmerImage(
                model = movie.streamIcon,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .clip(VegaShapes.Small)
                        .background(VegaColors.Accent)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("Kalan", color = VegaColors.OnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Column {
                    Text(
                        text = movie.name,
                        color = VegaColors.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = VegaColors.Accent,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(VegaShapes.Small)
                    )
                }
            }
        }

        // Alt navigasyon barı arkasında içerik kalmaması için alt boşluk
        Spacer(modifier = Modifier.height(90.dp))
    }
}

@Composable
private fun NewlyAddedVodCard(
    movie: VodStream,
    onClick: () -> Unit
) {
    Card(
        shape = VegaShapes.Medium,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .width(120.dp)
            .height(165.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                onClick = onClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncShimmerImage(
                model = movie.streamIcon,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Kompakt YENİ Rozeti - Sol Üst Köşeye Sıfır
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(bottomEnd = 8.dp))
                    .background(VegaColors.Live)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Yeni",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // Puan Rozeti - Sağ Üst
            val rating = movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
            if (!rating.isNullOrBlank()) {
                Surface(
                    shape = VegaShapes.Small,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(rating, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Film İsmi - Alt
            Text(
                text = movie.name,
                color = VegaColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun NewlyAddedSeriesCard(
    series: SeriesStream,
    onClick: () -> Unit
) {
    Card(
        shape = VegaShapes.Medium,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .width(120.dp)
            .height(165.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                onClick = onClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncShimmerImage(
                model = series.cover,
                contentDescription = series.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // DİZİ Rozeti - Sol Üst Köşeye Sıfır
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(bottomEnd = 8.dp))
                    .background(VegaColors.Accent)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Dizi",
                    color = VegaColors.OnAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // Puan Rozeti - Sağ Üst
            val rating = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
            if (!rating.isNullOrBlank()) {
                Surface(
                    shape = VegaShapes.Small,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(rating, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Dizi İsmi - Alt
            Text(
                text = series.name,
                color = VegaColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun DashboardRecentChannelCard(
    channel: LiveStream,
    onClick: () -> Unit
) {
    Card(
        shape = VegaShapes.Medium,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .width(110.dp)
            .height(72.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncShimmerImage(
                model = channel.streamIcon,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(28.dp)
                    .clip(VegaShapes.Small)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = channel.name,
                color = VegaColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
