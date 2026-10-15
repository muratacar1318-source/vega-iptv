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
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable

@Composable
fun OpusDashboardScreen(viewModel: MainViewModel) {
    val userInfo by viewModel.userInfo.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val liveCategories by viewModel.liveCategories.collectAsState()
    val vodStreams by viewModel.vodStreams.collectAsState()
    val seriesCategories by viewModel.seriesCategories.collectAsState()
    val favoriteLiveIds by viewModel.favoriteLiveIds.collectAsState()

    // Sadece Film ve Dizi (VOD) içerikleri için kaldığı yerden devam et listesi
    val activeVodProgressList = remember(watchProgressMap) {
        watchProgressMap.values
            .filter { it.positionMs > 10_000L && !it.isWatched }
            .sortedByDescending { it.timestamp }
            .take(10)
    }

    // En Son Eklenen İçerikler (En yeni tarihe göre sıralama)
    val newlyAddedVodList = remember(vodStreams) {
        vodStreams
            .filter { !it.added.isNullOrBlank() }
            .sortedByDescending { it.added?.toLongOrNull() ?: 0L }
            .take(12)
            .ifEmpty { vodStreams.take(10) }
    }

    val totalLiveCount = remember(liveCategories) { liveCategories.sumOf { it.itemCount } }
    val totalVodCount = remember(vodStreams) { vodStreams.size }
    val totalSeriesCount = remember(seriesCategories) { seriesCategories.sumOf { it.itemCount } }
    val totalFavCount = remember(favoriteLiveIds) { favoriteLiveIds.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. KULLANICI & HESAP BİLGİ KARTI (HERO BANNER)
        // ==========================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TiviCard),
            border = BorderStroke(1.dp, TiviCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                OpusCyan.copy(alpha = 0.12f),
                                TiviPrimary.copy(alpha = 0.18f),
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
                                .background(OpusCyan.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = userInfo?.username ?: "Hoş Geldiniz",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(TiviGreen)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Abonelik Aktif",
                                    color = TiviGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TiviSurfaceVariant,
                        border = BorderStroke(1.dp, TiviCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${userInfo?.activeCons ?: "1"}/${userInfo?.maxConnections ?: "1"} Bağlantı",
                                color = Color.White,
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
            text = "KATEGORİLER",
            color = TiviTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OpusTileCard(
                title = "CANLI TV",
                subtitle = if (totalLiveCount > 0) "$totalLiveCount Kanal" else "Canlı Yayınlar",
                icon = Icons.Default.LiveTv,
                accentColor = OpusCyan,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSelectedHomeTab(HomeTab.LIVE_TV) }
            )

            OpusTileCard(
                title = "FİLMLER",
                subtitle = if (totalVodCount > 0) "$totalVodCount Film" else "Seç İzle",
                icon = Icons.Default.Movie,
                accentColor = TiviPrimary,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSelectedHomeTab(HomeTab.MOVIES) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OpusTileCard(
                title = "DİZİLER",
                subtitle = if (totalSeriesCount > 0) "$totalSeriesCount Dizi" else "Tüm Sezonlar",
                icon = Icons.Default.VideoLibrary,
                accentColor = Color(0xFFA855F7),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSelectedHomeTab(HomeTab.SERIES) }
            )

            OpusTileCard(
                title = "FAVORİLER",
                subtitle = if (totalFavCount > 0) "$totalFavCount Kayıtlı" else "Favori Listem",
                icon = Icons.Default.Star,
                accentColor = TiviGold,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSelectedHomeTab(HomeTab.FAVORITES) }
            )

            OpusTileCard(
                title = "AYARLAR",
                subtitle = "Sistem & Tercihler",
                icon = Icons.Default.Settings,
                accentColor = TiviTextSecondary,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setSelectedHomeTab(HomeTab.SETTINGS) }
            )
        }

        // ==========================================
        // 3. KALDIĞIN YERDEN DEVAM ET (SADECE FİLM / DİZİ VOD)
        // ==========================================
        if (activeVodProgressList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Kaldığın Yerden Devam Et (Film & Dizi)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(activeVodProgressList, key = { "progress_${it.streamId}" }) { progress ->
                    val movie = remember(vodStreams, progress.streamId) {
                        vodStreams.firstOrNull { it.streamId == progress.streamId }
                    }

                    if (movie != null) {
                        DashboardProgressCard(
                            movie = movie,
                            progress = progress,
                            onClick = { viewModel.playVod(movie, resume = true) }
                        )
                    }
                }
            }
        }

        // ==========================================
        // 4. SON EKLENEN FİLMLER & İÇERİKLER (YENİ)
        // ==========================================
        if (newlyAddedVodList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Whatshot, contentDescription = null, tint = TiviAccentLive, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Son Eklenen İçerikler",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
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
        // 5. SON İZLENEN CANLI KANALLAR
        // ==========================================
        if (recentChannels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = TiviGold, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Son İzlenen Canlı Kanallar",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TiviCard),
        border = BorderStroke(1.dp, TiviCardBorder),
        modifier = modifier
            .height(84.dp)
            .dpadFocusable(
                focusedBorderColor = accentColor,
                shape = RoundedCornerShape(14.dp),
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TiviTextMuted, modifier = Modifier.size(16.dp))
                }

                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        color = TiviTextSecondary,
                        fontSize = 10.sp,
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TiviCardDark),
        border = BorderStroke(1.dp, TiviCardBorder),
        modifier = Modifier
            .width(130.dp)
            .height(175.dp)
            .dpadFocusable(
                focusedBorderColor = OpusCyan,
                shape = RoundedCornerShape(12.dp),
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
                        .clip(RoundedCornerShape(4.dp))
                        .background(OpusCyan)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("KALAN", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                }

                Column {
                    Text(
                        text = movie.name,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = OpusCyan,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun NewlyAddedVodCard(
    movie: VodStream,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TiviCardDark),
        border = BorderStroke(1.dp, TiviCardBorder),
        modifier = Modifier
            .width(120.dp)
            .height(165.dp)
            .dpadFocusable(
                focusedBorderColor = OpusCyan,
                shape = RoundedCornerShape(12.dp),
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TiviAccentLive)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("YENİ", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    val rating = movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
                    if (!rating.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = TiviGold, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(rating, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = movie.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DashboardRecentChannelCard(
    channel: LiveStream,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TiviCardDark),
        border = BorderStroke(1.dp, TiviCardBorder),
        modifier = Modifier
            .width(110.dp)
            .height(72.dp)
            .dpadFocusable(
                focusedBorderColor = TiviGold,
                shape = RoundedCornerShape(12.dp),
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
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
