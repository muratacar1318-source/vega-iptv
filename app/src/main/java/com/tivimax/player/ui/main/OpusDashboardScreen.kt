package com.tivimax.player.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.util.LocalIsTvDevice

/**
 * Öne Çıkan Vitrin (Hero Spotlight) İçerik Modeli
 */
private sealed class SpotlightContent {
    data class Movie(val movie: VodStream) : SpotlightContent()
    data class Series(val series: SeriesStream) : SpotlightContent()
    data class Progress(val progress: WatchProgress, val movie: VodStream) : SpotlightContent()

    val title: String
        get() = when (this) {
            is Movie -> movie.name
            is Series -> series.name
            is Progress -> movie.name
        }

    val imageUrl: String?
        get() = when (this) {
            is Movie -> movie.streamIcon
            is Series -> series.backdropPath?.firstOrNull() ?: series.cover
            is Progress -> movie.streamIcon
        }

    val rating: String?
        get() = when (this) {
            is Movie -> movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
            is Series -> series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
            is Progress -> movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
        }

    val badgeLabel: String
        get() = when (this) {
            is Movie -> "Öne Çıkan Film"
            is Series -> "Popüler Dizi"
            is Progress -> "Kaldığın Yerden"
        }

    val categorySubtitle: String
        get() = when (this) {
            is Movie -> "Sinema Filmi"
            is Series -> series.genre ?: "Dizi"
            is Progress -> "İzlemeye Devam Et"
        }

    val description: String
        get() = when (this) {
            is Movie -> "Yüksek çözünürlüklü sinema keyfi. Hemen izlemeye başla veya detayları incele."
            is Series -> if (!series.plot.isNullOrBlank()) series.plot!! else "Tüm sezon ve bölümleriyle keyifle izleyin."
            is Progress -> "Kaldığın dakikadan kesintisiz devam et."
        }
}

/**
 * VEGA SİNEMATİK ANA SAYFA (CINEMATIC HERO DASHBOARD)
 * Üstte büyük vitrin (Spotlight Billboard), TV kumandasıyla odaklanılan içeriğe dinamik geçiş,
 * 16:9 yatay Kaldığın Yerden Devam Et şeridi ve dikey sinematik içerik rayları.
 */
@Composable
fun OpusDashboardScreen(viewModel: MainViewModel) {
    val userInfo by viewModel.userInfo.collectAsState()
    val continueWatching by viewModel.continueWatchingMovies.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val vodStreams by viewModel.vodStreams.collectAsState()
    val seriesStreams by viewModel.seriesStreams.collectAsState()

    // En Son Eklenen Filmler
    val newlyAddedVodList = remember(vodStreams) {
        vodStreams
            .filter { !it.added.isNullOrBlank() }
            .sortedByDescending { it.added?.toLongOrNull() ?: 0L }
            .distinctBy { (it.name ?: "").trim().lowercase() }
            .take(16)
            .ifEmpty { vodStreams.take(12) }
    }

    // En Son Eklenen Diziler
    val newlyAddedSeriesList = remember(seriesStreams) {
        seriesStreams
            .filter { !it.added.isNullOrBlank() || !it.lastModified.isNullOrBlank() }
            .sortedByDescending { (it.lastModified?.toLongOrNull() ?: it.added?.toLongOrNull() ?: 0L) }
            .distinctBy { (it.name ?: "").trim().lowercase() }
            .take(16)
            .ifEmpty { seriesStreams.take(12) }
    }

    // Başlangıç Vitrin İçeriği
    val initialSpotlight = remember(continueWatching, newlyAddedVodList, newlyAddedSeriesList) {
        when {
            continueWatching.isNotEmpty() -> {
                val (prog, mov) = continueWatching.first()
                SpotlightContent.Progress(prog, mov)
            }
            newlyAddedVodList.isNotEmpty() -> SpotlightContent.Movie(newlyAddedVodList.first())
            newlyAddedSeriesList.isNotEmpty() -> SpotlightContent.Series(newlyAddedSeriesList.first())
            else -> null
        }
    }

    // Kumanda ile alt listelerde gezinirken vitrinin dinamik güncellenmesi
    var currentSpotlight by remember { mutableStateOf<SpotlightContent?>(null) }
    val activeSpotlight = currentSpotlight ?: initialSpotlight

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VegaColors.Background)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. ÜST KULLANICI & HIZLI KATEGORİ GEÇİŞ HAPLARI
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Kullanıcı ve Bağlantı Durumu
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(VegaColors.Accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = userInfo?.username ?: "Hoş geldin",
                        color = VegaColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(VegaColors.Success)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Abonelik aktif",
                            color = VegaColors.Success,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Hızlı Kategori Butonları
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickNavPill(title = "Canlı", icon = Icons.Default.LiveTv) { viewModel.setSelectedHomeTab(HomeTab.LIVE_TV) }
                QuickNavPill(title = "Filmler", icon = Icons.Default.Movie) { viewModel.setSelectedHomeTab(HomeTab.MOVIES) }
                QuickNavPill(title = "Diziler", icon = Icons.Default.VideoLibrary) { viewModel.setSelectedHomeTab(HomeTab.SERIES) }
                QuickNavPill(title = "Favoriler", icon = Icons.Default.Star) { viewModel.setSelectedHomeTab(HomeTab.FAVORITES) }
            }
        }

        // ==========================================
        // 2. SİNEMATİK BÜYÜK VİTRİN (HERO SPOTLIGHT BILLBOARD)
        // ==========================================
        activeSpotlight?.let { spotlight ->
            CinematicHeroSpotlight(
                spotlight = spotlight,
                onPlayClick = {
                    when (spotlight) {
                        is SpotlightContent.Movie -> viewModel.playVod(spotlight.movie, resume = false)
                        is SpotlightContent.Series -> viewModel.loadSeriesDetail(spotlight.series)
                        is SpotlightContent.Progress -> viewModel.playVod(spotlight.movie, resume = true)
                    }
                },
                onDetailClick = {
                    when (spotlight) {
                        is SpotlightContent.Movie -> viewModel.loadVodDetail(spotlight.movie)
                        is SpotlightContent.Series -> viewModel.loadSeriesDetail(spotlight.series)
                        is SpotlightContent.Progress -> viewModel.loadVodDetail(spotlight.movie)
                    }
                }
            )
        }

        // ==========================================
        // 3. KALDIĞIN YERDEN DEVAM ET (16:9 SİNEMATİK ŞERİT)
        // ==========================================
        if (continueWatching.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            SectionHeader(
                title = "Kaldığın Yerden Devam Et",
                icon = Icons.Default.History,
                iconColor = VegaColors.Accent
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(continueWatching, key = { "progress_${it.first.streamId}" }) { (progress, movie) ->
                    DashboardProgressCard(
                        movie = movie,
                        progress = progress,
                        onFocusChange = { focused ->
                            if (focused) currentSpotlight = SpotlightContent.Progress(progress, movie)
                        },
                        onClick = { viewModel.playVod(movie, resume = true) }
                    )
                }
            }
        }

        // ==========================================
        // 4. SON EKLENEN SİNEMA FİLMLERİ
        // ==========================================
        if (newlyAddedVodList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            SectionHeader(
                title = "Son Eklenen Sinema Filmleri",
                icon = Icons.Default.Whatshot,
                iconColor = VegaColors.Live,
                onSeeAllClick = { viewModel.setSelectedHomeTab(HomeTab.MOVIES) }
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(newlyAddedVodList, key = { "new_vod_${it.streamId}" }) { movie ->
                    NewlyAddedVodCard(
                        movie = movie,
                        onFocusChange = { focused ->
                            if (focused) currentSpotlight = SpotlightContent.Movie(movie)
                        },
                        onClick = { viewModel.loadVodDetail(movie) }
                    )
                }
            }
        }

        // ==========================================
        // 5. POPÜLER VE YENİ DİZİLER
        // ==========================================
        if (newlyAddedSeriesList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            SectionHeader(
                title = "Popüler ve Yeni Diziler",
                icon = Icons.Default.VideoLibrary,
                iconColor = VegaColors.Accent,
                onSeeAllClick = { viewModel.setSelectedHomeTab(HomeTab.SERIES) }
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(newlyAddedSeriesList, key = { "new_series_${it.seriesId}" }) { series ->
                    NewlyAddedSeriesCard(
                        series = series,
                        onFocusChange = { focused ->
                            if (focused) currentSpotlight = SpotlightContent.Series(series)
                        },
                        onClick = { viewModel.loadSeriesDetail(series) }
                    )
                }
            }
        }

        // ==========================================
        // 6. TREND CANLI TV KANALLARI
        // ==========================================
        if (recentChannels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            SectionHeader(
                title = "Son İzlenen Canlı Kanallar",
                icon = Icons.Default.Tv,
                iconColor = VegaColors.Gold,
                onSeeAllClick = { viewModel.setSelectedHomeTab(HomeTab.LIVE_TV) }
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(recentChannels.take(12), key = { "recent_${it.streamId}" }) { channel ->
                    DashboardRecentChannelCard(
                        channel = channel,
                        onClick = { viewModel.playLiveChannel(channel) }
                    )
                }
            }
        }

        // Alt navigasyon barı arkasında içerik kalmaması için alt boşluk
        Spacer(modifier = Modifier.height(80.dp))
    }
}

/**
 * ŞERİT BAŞLIK BİLEŞENİ
 */
@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    iconColor: Color = VegaColors.Accent,
    onSeeAllClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VegaColors.TextPrimary
            )
        }
        if (onSeeAllClick != null) {
            Text(
                text = "Tümünü gör >",
                color = VegaColors.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(VegaShapes.Small)
                    .clickable { onSeeAllClick() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * SİNEMATİK BÜYÜK VİTRİN BİLEŞENİ (CINEMATIC HERO SPOTLIGHT)
 */
@Composable
private fun CinematicHeroSpotlight(
    spotlight: SpotlightContent,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit
) {
    val isTvDevice = LocalIsTvDevice.current
    val heroHeight = if (isTvDevice) 340.dp else 285.dp

    Card(
        shape = VegaShapes.Large,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Arka Plan Sinematik Görsel
            AsyncShimmerImage(
                model = spotlight.imageUrl,
                contentDescription = spotlight.title,
                contentScale = ContentScale.Crop,
                placeholderIcon = Icons.Default.Movie,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Çok Katmanlı Sinematik Gradyanlar (Üstten, Alttan ve Soldan Karartma)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.92f),
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black
                            )
                        )
                    )
            )

            // 3. İçerik ve Aksiyon Alanı
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Etiket Rozeti
                Surface(
                    shape = VegaShapes.Pill,
                    color = VegaColors.Accent.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = spotlight.badgeLabel,
                        color = VegaColors.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Başlık
                Text(
                    text = spotlight.title,
                    color = VegaColors.TextPrimary,
                    fontSize = if (isTvDevice) 24.sp else 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (isTvDevice) 28.sp else 24.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Rozetler Satırı (Puan, 4K, Kategori)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!spotlight.rating.isNullOrBlank() && spotlight.rating != "0" && spotlight.rating != "0.0") {
                        Surface(
                            shape = VegaShapes.Small,
                            color = Color.Black.copy(alpha = 0.7f),
                            border = BorderStroke(0.5.dp, VegaColors.Gold.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = VegaColors.Gold,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = spotlight.rating!!,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Surface(
                        shape = VegaShapes.Small,
                        color = Color.Black.copy(alpha = 0.5f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "4K UHD",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = spotlight.categorySubtitle,
                        color = VegaColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Kısa Açıklama
                Text(
                    text = spotlight.description,
                    color = VegaColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Aksiyon Butonları
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Birincil Buton: Hemen Oynat
                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.Accent,
                        modifier = Modifier
                            .height(if (isTvDevice) 42.dp else 36.dp)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = VegaShapes.Medium,
                                scale = 1.05f,
                                onClick = onPlayClick
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = VegaColors.OnAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Hemen izle",
                                color = VegaColors.OnAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // İkincil Buton: Detaylar
                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.SurfaceVariant,
                        border = BorderStroke(1.dp, VegaColors.Border),
                        modifier = Modifier
                            .height(if (isTvDevice) 42.dp else 36.dp)
                            .dpadFocusable(
                                focusedBorderColor = VegaColors.Accent,
                                shape = VegaShapes.Medium,
                                scale = 1.05f,
                                onClick = onDetailClick
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = VegaColors.TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Detaylar",
                                color = VegaColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * HIZLI KATEGORİ GEÇİŞ HAPI
 */
@Composable
private fun QuickNavPill(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = VegaShapes.Pill,
        color = VegaColors.SurfaceVariant,
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Pill,
                scale = 1.05f,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VegaColors.Accent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = title,
                color = VegaColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * KALDIĞIN YERDEN DEVAM ET KARTI (16:9 SİNEMATİK)
 */
@Composable
private fun DashboardProgressCard(
    movie: VodStream,
    progress: WatchProgress,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val progressFraction = if (progress.durationMs > 0) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val remainingText = if (progress.durationMs > progress.positionMs) {
        val diffMs = progress.durationMs - progress.positionMs
        val mins = (diffMs / 60000L).toInt()
        if (mins > 60) "${mins / 60} sa ${mins % 60} dk kaldı" else "$mins dk kaldı"
    } else "Kaldığın yer"

    Card(
        shape = VegaShapes.Medium,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .width(220.dp)
            .height(125.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                scale = 1.05f,
                onClick = onClick,
                onFocusStateChange = onFocusChange
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncShimmerImage(
                model = movie.streamIcon,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
            )

            // Ortada Transparan Oynat İkonu
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = VegaColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Alt İçerik
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = movie.name,
                        color = VegaColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = remainingText,
                        color = VegaColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
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
}

/**
 * SON EKLENEN SİNEMA FİLMİ KARTI (2:3 DİKEY)
 */
@Composable
private fun NewlyAddedVodCard(
    movie: VodStream,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        shape = VegaShapes.Medium,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .width(130.dp)
            .height(190.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                scale = 1.05f,
                onClick = onClick,
                onFocusStateChange = onFocusChange
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
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
            )

            // "Yeni" rozeti
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
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Puan rozeti
            val rating = movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
            if (!rating.isNullOrBlank() && rating != "0" && rating != "0.0") {
                Surface(
                    shape = VegaShapes.Small,
                    color = Color.Black.copy(alpha = 0.7f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
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
                        Text(rating, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Film İsmi
            Text(
                text = movie.name,
                color = VegaColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

/**
 * POPÜLER DİZİ KARTI (2:3 DİKEY)
 */
@Composable
private fun NewlyAddedSeriesCard(
    series: SeriesStream,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        shape = VegaShapes.Medium,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .width(130.dp)
            .height(190.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                scale = 1.05f,
                onClick = onClick,
                onFocusStateChange = onFocusChange
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
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
            )

            // "Dizi" rozeti
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
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Puan rozeti
            val rating = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
            if (!rating.isNullOrBlank() && rating != "0" && rating != "0.0") {
                Surface(
                    shape = VegaShapes.Small,
                    color = Color.Black.copy(alpha = 0.7f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
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
                        Text(rating, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Dizi İsmi
            Text(
                text = series.name,
                color = VegaColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

/**
 * TREND CANLI TV KANALI KARTI (16:9 YATAY)
 */
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
            .width(150.dp)
            .height(84.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                scale = 1.05f,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(VegaColors.SurfaceVariant, VegaColors.Card)
                    )
                )
                .padding(8.dp)
        ) {
            // Canlı Sinyal Rozeti
            Row(
                modifier = Modifier.align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(VegaColors.Live)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Canlı",
                    color = VegaColors.Live,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Kanal Logosu Ortada
            AsyncShimmerImage(
                model = channel.streamIcon,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(34.dp)
                    .clip(VegaShapes.Small)
                    .align(Alignment.Center)
            )

            // Kanal İsmi Altta
            Text(
                text = channel.name,
                color = VegaColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
