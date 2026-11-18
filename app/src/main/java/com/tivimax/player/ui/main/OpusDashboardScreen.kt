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
import com.tivimax.player.ui.components.MediaPosterCard
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.util.LocalIsTvDevice

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

    val eyebrow: String
        get() = when (this) {
            is Movie -> "Filmler"
            is Series -> "Diziler"
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
            is Series -> if (!series.plot.isNullOrBlank()) series.plot!! else "Tüm sezon ve bölümleriyle keyifle izle."
            is Progress -> "Kaldığın dakikadan kesintisiz devam et."
        }
}

/**
 * VEGA ANA SAYFA (STREAMVAULT RAF VE VİTRİN DÜZENİ)
 * Derin gece arka planı üzerinde vitrin kartı, yatay StreamVault rafları
 * (Kaldığın Yerden, Favori Kanallar, Son İzlenen Kanallar, Son Eklenen Filmler, Popüler Diziler).
 */
@Composable
fun OpusDashboardScreen(viewModel: MainViewModel) {
    val isTvDevice = LocalIsTvDevice.current
    val continueWatching by viewModel.continueWatchingMovies.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val favoriteLiveIds by viewModel.favoriteLiveIds.collectAsState()
    val allLiveStreams by viewModel.allLiveStreams.collectAsState()
    val vodStreams by viewModel.vodStreams.collectAsState()
    val seriesStreams by viewModel.seriesStreams.collectAsState()
    val favoriteVodIds by viewModel.favoriteVodIds.collectAsState()
    val favoriteSeriesIds by viewModel.favoriteSeriesIds.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()
    val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()

    val favoriteChannels = remember(allLiveStreams, favoriteLiveIds) {
        if (favoriteLiveIds.isEmpty()) emptyList()
        else allLiveStreams.filter { favoriteLiveIds.contains(it.streamId.toString()) }.take(15)
    }

    val newlyAddedVodList = remember(vodStreams) {
        vodStreams
            .filter { !it.added.isNullOrBlank() }
            .sortedByDescending { it.added?.toLongOrNull() ?: 0L }
            .distinctBy { it.name.trim().lowercase() }
            .take(18)
            .ifEmpty { vodStreams.take(14) }
    }

    val newlyAddedSeriesList = remember(seriesStreams) {
        seriesStreams
            .filter { !it.added.isNullOrBlank() || !it.lastModified.isNullOrBlank() }
            .sortedByDescending { (it.lastModified?.toLongOrNull() ?: it.added?.toLongOrNull() ?: 0L) }
            .distinctBy { it.name.trim().lowercase() }
            .take(18)
            .ifEmpty { seriesStreams.take(14) }
    }

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

    var currentSpotlight by remember { mutableStateOf<SpotlightContent?>(null) }
    val activeSpotlight = currentSpotlight ?: initialSpotlight

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTvDevice) 18.dp else 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. STREAMVAULT HERO VİTRİN PANELİ
        activeSpotlight?.let { spotlight ->
            StreamVaultHeroPanel(
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

        // 2. KALDIĞIN YERDEN DEVAM ET RAFI
        if (continueWatching.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StreamVaultSectionHeader(title = "Kaldığın Yerden Devam Et")
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
        }

        // 3. FAVORİ KANALLAR RAFI (StreamVault DashboardChannelCard)
        if (favoriteChannels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StreamVaultSectionHeader(
                    title = "Favori Kanallar",
                    onSeeAllClick = {
                        viewModel.selectedLiveCategory.value = "favorites"
                        viewModel.setSelectedHomeTab(HomeTab.LIVE_TV)
                    }
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(favoriteChannels, key = { "fav_ch_${it.streamId}" }) { channel ->
                        val epg = epgSummaryMap[channel.streamId]
                        StreamVaultChannelCard(
                            channel = channel,
                            programTitle = epg?.getDecodedTitle() ?: channel.currentProgram,
                            progress = epg?.getProgressFraction(),
                            isFavorite = true,
                            onClick = { viewModel.playLiveChannel(channel) }
                        )
                    }
                }
            }
        }

        // 4. SON İZLENEN CANLI KANALLAR RAFI
        if (recentChannels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StreamVaultSectionHeader(
                    title = "Son İzlenen Canlı Kanallar",
                    onSeeAllClick = { viewModel.setSelectedHomeTab(HomeTab.LIVE_TV) }
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(recentChannels.take(14), key = { "recent_${it.streamId}" }) { channel ->
                        val epg = epgSummaryMap[channel.streamId]
                        StreamVaultChannelCard(
                            channel = channel,
                            programTitle = epg?.getDecodedTitle() ?: channel.currentProgram,
                            progress = epg?.getProgressFraction(),
                            isFavorite = favoriteLiveIds.contains(channel.streamId.toString()),
                            onClick = { viewModel.playLiveChannel(channel) }
                        )
                    }
                }
            }
        }

        // 5. SON EKLENEN FİLMLER RAFI (StreamVault Movies.png poster şeridi)
        if (newlyAddedVodList.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StreamVaultSectionHeader(
                    title = "Son Eklenen Filmler",
                    onSeeAllClick = { viewModel.setSelectedHomeTab(HomeTab.MOVIES) }
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(newlyAddedVodList, key = { "new_vod_${it.streamId}" }) { movie ->
                        val prog = watchProgressMap[movie.streamId.toString()]
                        val progFraction = if (prog != null && prog.durationMs > 0L) {
                            (prog.positionMs.toFloat() / prog.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        Box(modifier = Modifier.width(if (isTvDevice) 144.dp else 130.dp)) {
                            MediaPosterCard(
                                imageUrl = movie.streamIcon,
                                title = movie.name,
                                subtitle = "",
                                rating = movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) },
                                watchProgress = progFraction,
                                isFavorite = favoriteVodIds.contains(movie.streamId.toString()),
                                onFocusChange = { focused ->
                                    if (focused) currentSpotlight = SpotlightContent.Movie(movie)
                                },
                                onClick = { viewModel.loadVodDetail(movie) }
                            )
                        }
                    }
                }
            }
        }

        // 6. POPÜLER VE YENİ DİZİLER RAFI
        if (newlyAddedSeriesList.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StreamVaultSectionHeader(
                    title = "Popüler ve Yeni Diziler",
                    onSeeAllClick = { viewModel.setSelectedHomeTab(HomeTab.SERIES) }
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(newlyAddedSeriesList, key = { "new_series_${it.seriesId}" }) { series ->
                        Box(modifier = Modifier.width(if (isTvDevice) 144.dp else 130.dp)) {
                            MediaPosterCard(
                                imageUrl = series.cover,
                                title = series.name,
                                subtitle = "",
                                rating = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) },
                                isFavorite = favoriteSeriesIds.contains(series.seriesId.toString()),
                                onFocusChange = { focused ->
                                    if (focused) currentSpotlight = SpotlightContent.Series(series)
                                },
                                onClick = { viewModel.loadSeriesDetail(series) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isTvDevice) 24.dp else 72.dp))
    }
}

/**
 * STREAMVAULT RAF BAŞLIĞI + "Tümünü gör" HAPI (Movies.png ile birebir)
 */
@Composable
fun StreamVaultSectionHeader(
    title: String,
    subtitle: String? = null,
    onSeeAllClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = VegaColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = VegaColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onSeeAllClick != null) {
            Surface(
                shape = VegaShapes.Pill,
                color = VegaColors.Accent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.28f)),
                modifier = Modifier.dpadFocusable(
                    shape = VegaShapes.Pill,
                    focusedBorderColor = VegaColors.FocusBorder,
                    scale = 1.04f,
                    onClick = onSeeAllClick
                )
            ) {
                Text(
                    text = "Tümünü gör",
                    color = VegaColors.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * STREAMVAULT HERO VİTRİN PANELİ (browse_hero_panel.png + MovieInfo.png görsel dili)
 */
@Composable
private fun StreamVaultHeroPanel(
    spotlight: SpotlightContent,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit
) {
    val isTvDevice = LocalIsTvDevice.current
    val heroHeight = if (isTvDevice) 270.dp else 230.dp

    Card(
        shape = VegaShapes.Hero,
        colors = CardDefaults.cardColors(containerColor = VegaColors.SurfaceElevated),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncShimmerImage(
                model = spotlight.imageUrl,
                contentDescription = spotlight.title,
                contentScale = ContentScale.Crop,
                placeholderIcon = Icons.Default.Movie,
                modifier = Modifier.fillMaxSize()
            )

            // StreamVault derin gece soldan sağa gradyanı
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to VegaColors.Background.copy(alpha = 0.96f),
                                0.45f to VegaColors.Surface.copy(alpha = 0.84f),
                                0.78f to VegaColors.Surface.copy(alpha = 0.32f),
                                1.0f to VegaColors.Background.copy(alpha = 0.25f)
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
                                Color.Transparent,
                                VegaColors.Background.copy(alpha = 0.65f),
                                VegaColors.Background.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isTvDevice) 26.dp else 18.dp, vertical = if (isTvDevice) 22.dp else 16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = spotlight.eyebrow,
                    color = VegaColors.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = spotlight.title,
                    color = VegaColors.TextPrimary,
                    fontSize = if (isTvDevice) 26.sp else 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (isTvDevice) 30.sp else 24.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Noktalı Meta Bilgi Şeridi
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rating = spotlight.rating
                    if (!rating.isNullOrBlank() && rating != "0" && rating != "0.0") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(13.dp))
                            Text(text = rating, color = VegaColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(VegaColors.TextMuted))
                    }
                    Text(
                        text = spotlight.categorySubtitle,
                        color = VegaColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = spotlight.description,
                    color = VegaColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth(if (isTvDevice) 0.65f else 0.95f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // StreamVault Aksiyon Butonları
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.Accent,
                        modifier = Modifier
                            .height(if (isTvDevice) 44.dp else 40.dp)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = VegaShapes.Medium,
                                scale = 1.04f,
                                onClick = onPlayClick
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = VegaColors.OnAccent,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(
                                text = "Oynat",
                                color = VegaColors.OnAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.SurfaceAccent,
                        border = BorderStroke(1.dp, VegaColors.Border),
                        modifier = Modifier
                            .height(if (isTvDevice) 44.dp else 40.dp)
                            .dpadFocusable(
                                focusedBorderColor = VegaColors.FocusBorder,
                                shape = VegaShapes.Medium,
                                scale = 1.04f,
                                onClick = onDetailClick
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = VegaColors.TextPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "Detaylar",
                                color = VegaColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * STREAMVAULT KANAL KARTI (210x118dp — Logo + Canlı rozeti + EPG + İlerleme çubuğu)
 */
@Composable
private fun StreamVaultChannelCard(
    channel: LiveStream,
    programTitle: String?,
    progress: Float?,
    isFavorite: Boolean,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(10.dp)
    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = VegaColors.SurfaceElevated),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .width(206.dp)
            .height(116.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = cardShape,
                scale = 1.035f,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(VegaColors.SurfaceVariant.copy(alpha = 0.65f), VegaColors.Surface)
                    )
                )
        ) {
            // Kanal Logosu (merkez üstte)
            AsyncShimmerImage(
                model = channel.streamIcon,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                placeholderIcon = Icons.Default.Tv,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(44.dp)
                    .clip(VegaShapes.Small)
                    .background(VegaColors.SurfaceAccent)
                    .padding(5.dp)
            )

            // Sağ Üst: Canlı ve Favori Rozeti
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = VegaColors.Gold,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(VegaColors.Live.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Canlı",
                        color = VegaColors.Live,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Alt: Kanal Adı + Program + İlerleme Çubuğu
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = channel.name,
                    color = VegaColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = programTitle?.takeIf { it.isNotBlank() } ?: "Canlı yayın",
                    color = VegaColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (progress != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        color = VegaColors.Accent,
                        trackColor = VegaColors.SurfaceAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(VegaShapes.Pill)
                    )
                }
            }
        }
    }
}

/**
 * KALDIĞIN YERDEN DEVAM ET KARTI (16:9)
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

    val cardShape = RoundedCornerShape(10.dp)
    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = VegaColors.SurfaceElevated),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .width(214.dp)
            .height(120.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = cardShape,
                scale = 1.035f,
                onClick = onClick,
                onFocusStateChange = onFocusChange
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncShimmerImage(
                model = movie.streamIcon,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                placeholderIcon = Icons.Default.Movie,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(VegaColors.Accent.copy(alpha = 0.90f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = VegaColors.OnAccent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = movie.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = remainingText,
                    color = VegaColors.TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = VegaColors.Accent,
                    trackColor = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(VegaShapes.Pill)
                )
            }
        }
    }
}
