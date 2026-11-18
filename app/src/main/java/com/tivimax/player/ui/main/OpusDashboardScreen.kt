@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tivimax.player.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.focus.focusRestorer
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

    val key: String
        get() = when (this) {
            is Movie -> "vod_${movie.streamId}"
            is Series -> "ser_${series.seriesId}"
            is Progress -> "prog_${movie.streamId}"
        }

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
            is Movie -> formatSpotlightRating(movie.rating, movie.rating5Based)
            is Series -> formatSpotlightRating(series.rating, series.rating5Based)
            is Progress -> formatSpotlightRating(movie.rating, movie.rating5Based)
        }

    val eyebrow: String
        get() = when (this) {
            is Movie -> "Filmler"
            is Series -> "Diziler"
            is Progress -> "Kaldığın Yerden"
        }

    val categorySubtitle: String
        get() = when (this) {
            is Movie -> "Sinema filmi"
            is Series -> series.genre?.takeIf { it.isNotBlank() } ?: "Dizi"
            is Progress -> {
                if (progress.durationMs > progress.positionMs) {
                    val mins = ((progress.durationMs - progress.positionMs) / 60000L).toInt()
                    if (mins >= 60) "${mins / 60} sa ${mins % 60} dk kaldı" else "$mins dk kaldı"
                } else {
                    "İzlemeye devam et"
                }
            }
        }

    val description: String
        get() = when (this) {
            is Movie -> "Yüksek kaliteli sinema keyfi. Hemen izlemeye başla veya ayrıntıları incele."
            is Series -> series.plot?.takeIf { it.isNotBlank() } ?: "Tüm sezon ve bölümleriyle keyifle izle."
            is Progress -> "Kaldığın dakikadan kesintisiz devam et."
        }

    val progressFraction: Float?
        get() = when (this) {
            is Progress -> if (progress.durationMs > 0L) {
                (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
            } else null
            else -> null
        }

    val primaryActionLabel: String
        get() = when (this) {
            is Progress -> "Devam et"
            else -> "Oynat"
        }
}

private fun formatSpotlightRating(raw: String?, rating5Based: Double?): String? {
    val source = raw?.trim()?.takeIf { it.isNotBlank() }
        ?: rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
        ?: return null
    val num = source.substringBefore("/").replace(',', '.').toFloatOrNull() ?: return null
    if (num <= 0f) return null
    val formatted = if (num == num.toInt().toFloat()) "${num.toInt()}" else String.format(java.util.Locale.US, "%.1f", num)
    return if (num <= 5f) "$formatted/5" else "$formatted/10"
}

/**
 * VEGA ANA SAYFA (STREAMVAULT RAF VE CANLI VİTRİN DÜZENİ)
 * Derin gece arka planı üzerinde kumanda odağına göre yumuşak Crossfade ile güncellenen vitrin kartı,
 * yatay StreamVault rafları (Kaldığın Yerden, Favori Kanallar, Son İzlenen Kanallar, Son Eklenen Filmler, Popüler Diziler).
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

    // G-034: Ana sayfa dikey kaydırma konumu korunur
    val positionStore = viewModel.catalogPositions
    val savedScroll = remember { positionStore.get("home:scroll")?.offset ?: 0 }
    val pageScrollState = rememberScrollState(initial = savedScroll)
    DisposableEffect(Unit) {
        onDispose {
            positionStore.save("home:scroll", 0, pageScrollState.value, 0)
        }
    }

    val posterWidth = if (isTvDevice) 160.dp else 132.dp
    val shelfSpacing = if (isTvDevice) 24.dp else 18.dp
    val rowItemSpacing = if (isTvDevice) 14.dp else 12.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(pageScrollState)
            .padding(horizontal = if (isTvDevice) 22.dp else 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(shelfSpacing)
    ) {
        // 1. STREAMVAULT CANLI VİTRİN PANELİ (Alt raflardaki odakla Crossfade senkronlu)
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
                StreamVaultSectionHeader(
                    title = "Kaldığın Yerden Devam Et",
                    count = continueWatching.size
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(rowItemSpacing),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth().focusRestorer()
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
                    count = favoriteChannels.size,
                    onSeeAllClick = {
                        viewModel.selectedLiveCategory.value = "favorites"
                        viewModel.setSelectedHomeTab(HomeTab.LIVE_TV)
                    }
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(rowItemSpacing),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth().focusRestorer()
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
                    count = recentChannels.size,
                    onSeeAllClick = { viewModel.setSelectedHomeTab(HomeTab.LIVE_TV) }
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(rowItemSpacing),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth().focusRestorer()
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
                    count = vodStreams.size,
                    onSeeAllClick = { viewModel.setSelectedHomeTab(HomeTab.MOVIES) }
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(rowItemSpacing),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth().focusRestorer()
                ) {
                    items(newlyAddedVodList, key = { "new_vod_${it.streamId}" }) { movie ->
                        val prog = watchProgressMap[movie.streamId.toString()]
                        val progFraction = if (prog != null && prog.durationMs > 0L) {
                            (prog.positionMs.toFloat() / prog.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        Box(modifier = Modifier.width(posterWidth)) {
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
                    count = seriesStreams.size,
                    onSeeAllClick = { viewModel.setSelectedHomeTab(HomeTab.SERIES) }
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(rowItemSpacing),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth().focusRestorer()
                ) {
                    items(newlyAddedSeriesList, key = { "new_series_${it.seriesId}" }) { series ->
                        Box(modifier = Modifier.width(posterWidth)) {
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

        Spacer(modifier = Modifier.height(if (isTvDevice) 28.dp else 72.dp))
    }
}

/**
 * STREAMVAULT RAF BAŞLIĞI + ÖĞE SAYACI + "Tümünü gör" HAPI (Movies.png ile birebir)
 */
@Composable
fun StreamVaultSectionHeader(
    title: String,
    subtitle: String? = null,
    count: Int? = null,
    onSeeAllClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(if (subtitle.isNullOrBlank()) 20.dp else 30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VegaColors.Accent)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        color = VegaColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (count != null && count > 0) {
                        Surface(
                            shape = VegaShapes.Pill,
                            color = VegaColors.SurfaceVariant,
                            border = BorderStroke(1.dp, VegaColors.Border)
                        ) {
                            Text(
                                text = count.toString(),
                                color = VegaColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
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
        }

        if (onSeeAllClick != null) {
            Spacer(modifier = Modifier.width(10.dp))
            var isFocused by remember { mutableStateOf(false) }
            Surface(
                shape = VegaShapes.Pill,
                color = if (isFocused) VegaColors.Accent.copy(alpha = 0.28f) else VegaColors.Accent.copy(alpha = 0.14f),
                border = BorderStroke(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    color = if (isFocused) VegaColors.FocusBorder else VegaColors.Accent.copy(alpha = 0.30f)
                ),
                modifier = Modifier.dpadFocusable(
                    shape = VegaShapes.Pill,
                    focusedBorderColor = VegaColors.FocusBorder,
                    scale = 1.045f,
                    onClick = onSeeAllClick,
                    onFocusStateChange = { isFocused = it }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Tümünü gör",
                        color = if (isFocused) VegaColors.TextPrimary else VegaColors.Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = if (isFocused) VegaColors.TextPrimary else VegaColors.Accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * STREAMVAULT CANLI VİTRİN PANELİ (Crossfade ile dinamik afiş ve bilgi geçişi)
 */
@Composable
private fun StreamVaultHeroPanel(
    spotlight: SpotlightContent,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit
) {
    val isTvDevice = LocalIsTvDevice.current
    val heroHeight = if (isTvDevice) 288.dp else 236.dp

    Card(
        shape = VegaShapes.Hero,
        colors = CardDefaults.cardColors(containerColor = VegaColors.SurfaceElevated),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Yumuşak Crossfade ile vitrin görseli geçişi
            Crossfade(
                targetState = spotlight.imageUrl to spotlight.title,
                animationSpec = tween(durationMillis = 280),
                label = "heroBackdropCrossfade"
            ) { (imgUrl, imgTitle) ->
                AsyncShimmerImage(
                    model = imgUrl,
                    contentDescription = imgTitle,
                    contentScale = ContentScale.Crop,
                    placeholderIcon = Icons.Default.Movie,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // StreamVault derin gece soldan sağa gradyanı
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to VegaColors.Background.copy(alpha = 0.96f),
                                0.42f to VegaColors.Surface.copy(alpha = 0.84f),
                                0.72f to VegaColors.Surface.copy(alpha = 0.28f),
                                1.0f to Color.Transparent
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
                                VegaColors.Background.copy(alpha = 0.55f),
                                VegaColors.Background.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isTvDevice) 28.dp else 18.dp, vertical = if (isTvDevice) 22.dp else 16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Yumuşak Crossfade ile vitrin bilgileri geçişi (aksiyon butonları sabit kalır -> odak kopmaz)
                Crossfade(
                    targetState = spotlight,
                    animationSpec = tween(durationMillis = 220),
                    label = "heroMetaCrossfade"
                ) { active ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = VegaShapes.Small,
                                color = VegaColors.Accent.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = active.eyebrow,
                                    color = VegaColors.Accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }

                            active.rating?.let { ratingLabel ->
                                Surface(
                                    shape = VegaShapes.Small,
                                    color = VegaColors.Gold.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, VegaColors.Gold.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(12.dp))
                                        Text(text = ratingLabel, color = VegaColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Text(
                                text = active.categorySubtitle,
                                color = VegaColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = active.title,
                            color = VegaColors.TextPrimary,
                            fontSize = if (isTvDevice) 26.sp else 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.4).sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = if (isTvDevice) 31.sp else 24.sp,
                            modifier = Modifier.fillMaxWidth(if (isTvDevice) 0.68f else 0.96f)
                        )

                        Text(
                            text = active.description,
                            color = VegaColors.TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth(if (isTvDevice) 0.62f else 0.94f)
                        )

                        active.progressFraction?.let { frac ->
                            LinearProgressIndicator(
                                progress = { frac },
                                color = VegaColors.Accent,
                                trackColor = VegaColors.SurfaceAccent,
                                modifier = Modifier
                                    .fillMaxWidth(if (isTvDevice) 0.35f else 0.65f)
                                    .height(3.dp)
                                    .clip(VegaShapes.Pill)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // StreamVault Aksiyon Butonları (tek odak + tek tıklama)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.Accent,
                        modifier = Modifier
                            .height(if (isTvDevice) 46.dp else 42.dp)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = VegaShapes.Medium,
                                scale = 1.045f,
                                onClick = onPlayClick
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 22.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = VegaColors.OnAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = spotlight.primaryActionLabel,
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
                            .height(if (isTvDevice) 46.dp else 42.dp)
                            .dpadFocusable(
                                focusedBorderColor = VegaColors.FocusBorder,
                                shape = VegaShapes.Medium,
                                scale = 1.045f,
                                onClick = onDetailClick
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = VegaColors.TextPrimary,
                                modifier = Modifier.size(18.dp)
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
 * STREAMVAULT KANAL KARTI (212x120dp — Logo + Canlı rozeti + Program + İlerleme çubuğu)
 */
@Composable
private fun StreamVaultChannelCard(
    channel: LiveStream,
    programTitle: String?,
    progress: Float?,
    isFavorite: Boolean,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(11.dp)
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated
        ),
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) VegaColors.FocusBorder else VegaColors.Border
        ),
        modifier = Modifier
            .width(212.dp)
            .height(120.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = cardShape,
                scale = 1.045f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (isFocused) VegaColors.SurfaceVariant.copy(alpha = 0.85f) else VegaColors.SurfaceVariant.copy(alpha = 0.65f),
                            VegaColors.Surface
                        )
                    )
                )
        ) {
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

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

    val cardShape = RoundedCornerShape(11.dp)
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = VegaColors.SurfaceElevated),
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) VegaColors.FocusBorder else VegaColors.Border
        ),
        modifier = Modifier
            .width(224.dp)
            .height(126.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = cardShape,
                scale = 1.045f,
                onClick = onClick,
                onFocusStateChange = { focused ->
                    isFocused = focused
                    onFocusChange(focused)
                }
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
                    .size(if (isFocused) 40.dp else 36.dp)
                    .clip(CircleShape)
                    .background(VegaColors.Accent.copy(alpha = if (isFocused) 1f else 0.90f)),
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = remainingText,
                    color = VegaColors.TextSecondary,
                    fontSize = 11.sp,
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
