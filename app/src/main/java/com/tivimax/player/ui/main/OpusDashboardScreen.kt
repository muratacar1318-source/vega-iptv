@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tivimax.player.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.SeriesEpisode
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
import kotlinx.coroutines.delay

private sealed class ContinueWatchingItem {
    abstract val progress: WatchProgress
    abstract val key: String
    abstract val title: String
    abstract val imageUrl: String?

    data class Movie(
        override val progress: WatchProgress,
        val movie: VodStream
    ) : ContinueWatchingItem() {
        override val key: String get() = "prog_vod_${movie.streamId}"
        override val title: String get() = movie.name
        override val imageUrl: String? get() = movie.streamIcon
    }

    data class Episode(
        override val progress: WatchProgress,
        val episode: SeriesEpisode,
        val seriesTitle: String,
        val backdropUrl: String?
    ) : ContinueWatchingItem() {
        override val key: String get() = "prog_ep_${episode.id}"
        override val title: String get() = "$seriesTitle · S${episode.season} B${episode.episodeNum}"
        override val imageUrl: String?
            get() = backdropUrl?.takeIf { it.isNotBlank() } ?: episode.info?.movieImage?.takeIf { it.isNotBlank() }
    }

    data class Series(
        override val progress: WatchProgress,
        val series: SeriesStream
    ) : ContinueWatchingItem() {
        override val key: String get() = "prog_ser_${series.seriesId}"
        override val title: String get() = series.name
        override val imageUrl: String?
            get() = series.backdropPath?.firstOrNull { it.isNotBlank() } ?: series.cover
    }
}

private sealed class SpotlightContent {
    data class Movie(val movie: VodStream) : SpotlightContent()
    data class Series(val series: SeriesStream) : SpotlightContent()
    data class Progress(val progress: WatchProgress, val movie: VodStream) : SpotlightContent()
    data class EpisodeProgress(
        val progress: WatchProgress,
        val episode: SeriesEpisode,
        val seriesTitle: String,
        val backdropUrl: String?
    ) : SpotlightContent()
    data class SeriesProgress(val progress: WatchProgress, val series: SeriesStream) : SpotlightContent()

    val key: String
        get() = when (this) {
            is Movie -> "vod_${movie.streamId}"
            is Series -> "ser_${series.seriesId}"
            is Progress -> "prog_${movie.streamId}"
            is EpisodeProgress -> "prog_ep_${episode.id}"
            is SeriesProgress -> "prog_ser_${series.seriesId}"
        }

    val title: String
        get() = when (this) {
            is Movie -> movie.name
            is Series -> series.name
            is Progress -> movie.name
            is EpisodeProgress -> "$seriesTitle · S${episode.season} B${episode.episodeNum}"
            is SeriesProgress -> series.name
        }

    val imageUrl: String?
        get() = when (this) {
            is Movie -> movie.streamIcon
            is Series -> series.backdropPath?.firstOrNull { it.isNotBlank() } ?: series.cover
            is Progress -> movie.streamIcon
            is EpisodeProgress -> backdropUrl?.takeIf { it.isNotBlank() } ?: episode.info?.movieImage?.takeIf { it.isNotBlank() }
            is SeriesProgress -> series.backdropPath?.firstOrNull { it.isNotBlank() } ?: series.cover
        }

    val rating: String?
        get() = when (this) {
            is Movie -> formatSpotlightRating(movie.rating, movie.rating5Based)
            is Series -> formatSpotlightRating(series.rating, series.rating5Based)
            is Progress -> formatSpotlightRating(movie.rating, movie.rating5Based)
            is EpisodeProgress -> formatSpotlightRating(episode.info?.rating, null)
            is SeriesProgress -> formatSpotlightRating(series.rating, series.rating5Based)
        }

    val eyebrow: String
        get() = when (this) {
            is Movie -> "Filmler"
            is Series -> "Diziler"
            is Progress, is EpisodeProgress, is SeriesProgress -> "Kaldığın Yerden"
        }

    val categorySubtitle: String
        get() = when (this) {
            is Movie -> "Sinema filmi"
            is Series -> series.genre?.takeIf { it.isNotBlank() } ?: "Dizi"
            is Progress -> formatRemainingDuration(progress)
            is EpisodeProgress -> formatRemainingDuration(progress)
            is SeriesProgress -> formatRemainingDuration(progress)
        }

    val description: String
        get() = when (this) {
            is Movie -> "Yüksek kaliteli sinema keyfi. Hemen izlemeye başla veya ayrıntıları incele."
            is Series -> series.plot?.takeIf { it.isNotBlank() } ?: "Tüm sezon ve bölümleriyle keyifle izle."
            is Progress -> "Kaldığın dakikadan kesintisiz devam et."
            is EpisodeProgress -> episode.info?.plot?.takeIf { it.isNotBlank() }
                ?: episode.title.takeIf { it.isNotBlank() }
                ?: "Kaldığın bölümden kesintisiz devam et."
            is SeriesProgress -> series.plot?.takeIf { it.isNotBlank() } ?: "Kaldığın diziden devam et."
        }

    val progressFraction: Float?
        get() = when (this) {
            is Progress -> progressFractionOf(progress)
            is EpisodeProgress -> progressFractionOf(progress)
            is SeriesProgress -> progressFractionOf(progress)
            else -> null
        }

    val primaryActionLabel: String
        get() = when (this) {
            is Progress, is EpisodeProgress, is SeriesProgress -> "Devam et"
            else -> "Oynat"
        }
}

private fun formatRemainingDuration(progress: WatchProgress): String {
    return if (progress.durationMs > progress.positionMs) {
        val mins = ((progress.durationMs - progress.positionMs) / 60000L).toInt()
        if (mins >= 60) "${mins / 60} sa ${mins % 60} dk kaldı" else "$mins dk kaldı"
    } else {
        "İzlemeye devam et"
    }
}

private fun progressFractionOf(progress: WatchProgress): Float? =
    if (progress.durationMs > 0L) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }

private fun epgProgressAt(epg: EpgProgram?, nowSec: Long): Float? {
    if (epg == null || epg.startTimestamp <= 0L || epg.stopTimestamp <= epg.startTimestamp) return null
    return when {
        nowSec in epg.startTimestamp..epg.stopTimestamp ->
            ((nowSec - epg.startTimestamp).toFloat() / (epg.stopTimestamp - epg.startTimestamp).toFloat()).coerceIn(0.05f, 0.98f)
        nowSec > epg.stopTimestamp -> 1.0f
        else -> 0.0f
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
 * yatay StreamVault rafları (Kaldığın Yerden, Favori Kanallar, Son İzlenen Kanallar, Son Eklenen Filmler,
 * Popüler Diziler, Favori Filmler, Favori Diziler).
 */
@Composable
fun OpusDashboardScreen(viewModel: MainViewModel) {
    val isTvDevice = LocalIsTvDevice.current
    val continueWatchingMovies by viewModel.continueWatchingMovies.collectAsState()
    val currentPlayingEpisode by viewModel.currentPlayingEpisode.collectAsState()
    val currentSeriesInfo by viewModel.currentSeriesInfo.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val favoriteChannelsAll by viewModel.favoriteLiveStreams.collectAsState()
    val newlyAddedVodList by viewModel.recentVodPreview.collectAsState()
    val newlyAddedSeriesList by viewModel.recentSeriesPreview.collectAsState()
    val favoriteVodList by viewModel.favoriteVodStreams.collectAsState()
    val favoriteSeriesList by viewModel.favoriteSeriesStreams.collectAsState()
    val vodCategories by viewModel.vodCategories.collectAsState()
    val seriesCategories by viewModel.seriesCategories.collectAsState()
    val favoriteLiveIds by viewModel.favoriteLiveIds.collectAsState()
    val favoriteVodIds by viewModel.favoriteVodIds.collectAsState()
    val favoriteSeriesIds by viewModel.favoriteSeriesIds.collectAsState()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()
    val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()

    // Paylaşılan 30 saniyelik saat: canlı kanal yayın ilerleme çubuklarını tek sayaçla günceller
    var clockNowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            clockNowSec = System.currentTimeMillis() / 1000L
        }
    }

    val favoriteChannels = remember(favoriteChannelsAll) { favoriteChannelsAll.take(15) }
    val recentChannelsLimited = remember(recentChannels) { recentChannels.take(14) }
    val totalVodCount = remember(vodCategories, newlyAddedVodList) {
        vodCategories.firstOrNull { it.categoryId == "all" }?.itemCount?.takeIf { it > 0 } ?: newlyAddedVodList.size
    }
    val totalSeriesCount = remember(seriesCategories, newlyAddedSeriesList) {
        seriesCategories.firstOrNull { it.categoryId == "all" }?.itemCount?.takeIf { it > 0 } ?: newlyAddedSeriesList.size
    }

    // Kaldığın Yerden Devam Et: filmler + varsa aktif dizi bölümü / kayıtlı dizi ilerlemeleri
    val continueWatchingItems = remember(
        continueWatchingMovies,
        currentPlayingEpisode,
        currentSeriesInfo,
        watchProgressMap,
        favoriteSeriesList,
        newlyAddedSeriesList
    ) {
        val merged = mutableListOf<ContinueWatchingItem>()
        val usedStreamIds = HashSet<Int>()

        continueWatchingMovies.forEach { (progress, movie) ->
            merged.add(ContinueWatchingItem.Movie(progress, movie))
            usedStreamIds.add(movie.streamId)
        }

        currentPlayingEpisode?.let { (episode, seriesTitle) ->
            val epId = episode.id.toIntOrNull()
            val prog = epId?.let { watchProgressMap[it.toString()] }
            if (prog != null && prog.positionMs > 10_000L && !prog.isWatched && usedStreamIds.add(prog.streamId)) {
                val backdrop = currentSeriesInfo?.info?.backdropPath?.firstOrNull { it.isNotBlank() }
                    ?: episode.info?.movieImage
                merged.add(ContinueWatchingItem.Episode(prog, episode, seriesTitle, backdrop))
            }
        }

        val knownSeries = (favoriteSeriesList + newlyAddedSeriesList).distinctBy { it.seriesId }
        knownSeries.forEach { series ->
            val prog = watchProgressMap[series.seriesId.toString()]
            if (prog != null && prog.positionMs > 10_000L && !prog.isWatched && usedStreamIds.add(prog.streamId)) {
                merged.add(ContinueWatchingItem.Series(prog, series))
            }
        }

        merged.sortedByDescending { it.progress.timestamp }.take(12)
    }

    // Hero vitrin başlangıcı: yatay arkaplan görselli (backdrop) içeriği önceliklendir
    val initialSpotlight = remember(continueWatchingItems, newlyAddedVodList, newlyAddedSeriesList) {
        val firstContinue = continueWatchingItems.firstOrNull()
        when (firstContinue) {
            is ContinueWatchingItem.Movie -> SpotlightContent.Progress(firstContinue.progress, firstContinue.movie)
            is ContinueWatchingItem.Episode -> SpotlightContent.EpisodeProgress(
                firstContinue.progress,
                firstContinue.episode,
                firstContinue.seriesTitle,
                firstContinue.backdropUrl
            )
            is ContinueWatchingItem.Series -> SpotlightContent.SeriesProgress(firstContinue.progress, firstContinue.series)
            null -> {
                val seriesWithBackdrop = newlyAddedSeriesList.firstOrNull { ser ->
                    ser.backdropPath?.any { it.isNotBlank() } == true
                }
                when {
                    seriesWithBackdrop != null -> SpotlightContent.Series(seriesWithBackdrop)
                    newlyAddedVodList.isNotEmpty() -> SpotlightContent.Movie(newlyAddedVodList.first())
                    newlyAddedSeriesList.isNotEmpty() -> SpotlightContent.Series(newlyAddedSeriesList.first())
                    else -> null
                }
            }
        }
    }

    var currentSpotlight by remember { mutableStateOf<SpotlightContent?>(null) }
    val activeSpotlight = currentSpotlight ?: initialSpotlight

    // G-034: Ana sayfa dikey kaydırma konumu korunur
    val positionStore = viewModel.catalogPositions
    val savedPos = remember { positionStore.get("home:scroll") }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedPos?.firstIndex ?: 0,
        initialFirstVisibleItemScrollOffset = savedPos?.offset ?: 0
    )
    DisposableEffect(Unit) {
        onDispose {
            positionStore.save("home:scroll", listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, 0)
        }
    }

    val posterWidth = if (isTvDevice) 160.dp else 132.dp
    val shelfSpacing = if (isTvDevice) 24.dp else 18.dp
    val rowItemSpacing = if (isTvDevice) 14.dp else 12.dp
    val horizontalPadding = if (isTvDevice) 8.dp else 14.dp

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = 10.dp,
            bottom = if (isTvDevice) 28.dp else 72.dp
        ),
        verticalArrangement = Arrangement.spacedBy(shelfSpacing)
    ) {
        // 1. STREAMVAULT CANLI VİTRİN PANELİ (Alt raflardaki odakla Crossfade senkronlu)
        if (activeSpotlight != null) {
            item(key = "hero", contentType = "hero") {
                StreamVaultHeroPanel(
                    spotlight = activeSpotlight,
                    onPlayClick = {
                        when (activeSpotlight) {
                            is SpotlightContent.Movie -> viewModel.playVod(activeSpotlight.movie, resume = false)
                            is SpotlightContent.Series -> viewModel.loadSeriesDetail(activeSpotlight.series)
                            is SpotlightContent.Progress -> viewModel.playVod(activeSpotlight.movie, resume = true)
                            is SpotlightContent.EpisodeProgress -> viewModel.playSeriesEpisode(
                                activeSpotlight.episode,
                                activeSpotlight.seriesTitle,
                                resume = true
                            )
                            is SpotlightContent.SeriesProgress -> viewModel.loadSeriesDetail(activeSpotlight.series)
                        }
                    },
                    onDetailClick = {
                        when (activeSpotlight) {
                            is SpotlightContent.Movie -> viewModel.loadVodDetail(activeSpotlight.movie)
                            is SpotlightContent.Series -> viewModel.loadSeriesDetail(activeSpotlight.series)
                            is SpotlightContent.Progress -> viewModel.loadVodDetail(activeSpotlight.movie)
                            is SpotlightContent.EpisodeProgress -> viewModel.playSeriesEpisode(
                                activeSpotlight.episode,
                                activeSpotlight.seriesTitle,
                                resume = true
                            )
                            is SpotlightContent.SeriesProgress -> viewModel.loadSeriesDetail(activeSpotlight.series)
                        }
                    }
                )
            }
        }

        // 2. KALDIĞIN YERDEN DEVAM ET RAFI (Filmler + Diziler)
        if (continueWatchingItems.isNotEmpty()) {
            item(key = "continue_watching", contentType = "progress_shelf") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StreamVaultSectionHeader(
                        title = "Kaldığın Yerden Devam Et",
                        count = continueWatchingItems.size
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(rowItemSpacing),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth().focusRestorer()
                    ) {
                        items(continueWatchingItems, key = { it.key }) { item ->
                            DashboardProgressCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                progress = item.progress,
                                onFocusChange = { focused ->
                                    if (focused) {
                                        currentSpotlight = when (item) {
                                            is ContinueWatchingItem.Movie -> SpotlightContent.Progress(item.progress, item.movie)
                                            is ContinueWatchingItem.Episode -> SpotlightContent.EpisodeProgress(
                                                item.progress,
                                                item.episode,
                                                item.seriesTitle,
                                                item.backdropUrl
                                            )
                                            is ContinueWatchingItem.Series -> SpotlightContent.SeriesProgress(item.progress, item.series)
                                        }
                                    }
                                },
                                onClick = {
                                    when (item) {
                                        is ContinueWatchingItem.Movie -> viewModel.playVod(item.movie, resume = true)
                                        is ContinueWatchingItem.Episode -> viewModel.playSeriesEpisode(
                                            item.episode,
                                            item.seriesTitle,
                                            resume = true
                                        )
                                        is ContinueWatchingItem.Series -> viewModel.loadSeriesDetail(item.series)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. FAVORİ KANALLAR RAFI (StreamVault DashboardChannelCard)
        if (favoriteChannels.isNotEmpty()) {
            item(key = "favorite_channels", contentType = "channel_shelf") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StreamVaultSectionHeader(
                        title = "Favori Kanallar",
                        count = favoriteChannelsAll.size,
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
                                progress = epgProgressAt(epg, clockNowSec),
                                isFavorite = true,
                                onClick = { viewModel.playLiveChannel(channel) }
                            )
                        }
                    }
                }
            }
        }

        // 4. SON İZLENEN CANLI KANALLAR RAFI
        if (recentChannelsLimited.isNotEmpty()) {
            item(key = "recent_channels", contentType = "channel_shelf") {
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
                        items(recentChannelsLimited, key = { "recent_${it.streamId}" }) { channel ->
                            val epg = epgSummaryMap[channel.streamId]
                            StreamVaultChannelCard(
                                channel = channel,
                                programTitle = epg?.getDecodedTitle() ?: channel.currentProgram,
                                progress = epgProgressAt(epg, clockNowSec),
                                isFavorite = favoriteLiveIds.contains(channel.streamId.toString()),
                                onClick = { viewModel.playLiveChannel(channel) }
                            )
                        }
                    }
                }
            }
        }

        // 5. SON EKLENEN FİLMLER RAFI
        if (newlyAddedVodList.isNotEmpty()) {
            item(key = "recent_vod", contentType = "poster_shelf") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StreamVaultSectionHeader(
                        title = "Son Eklenen Filmler",
                        count = totalVodCount,
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
        }

        // 6. POPÜLER VE YENİ DİZİLER RAFI
        if (newlyAddedSeriesList.isNotEmpty()) {
            item(key = "recent_series", contentType = "poster_shelf") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StreamVaultSectionHeader(
                        title = "Popüler ve Yeni Diziler",
                        count = totalSeriesCount,
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
        }

        // 7. FAVORİ FİLMLER RAFI
        if (favoriteVodList.isNotEmpty()) {
            item(key = "favorite_vod", contentType = "poster_shelf") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StreamVaultSectionHeader(
                        title = "Favori Filmler",
                        count = favoriteVodList.size,
                        onSeeAllClick = {
                            viewModel.catalogPositions.save("fav:sekme", 2, 0, 0)
                            viewModel.setSelectedHomeTab(HomeTab.FAVORITES)
                        }
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(rowItemSpacing),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth().focusRestorer()
                    ) {
                        items(favoriteVodList.take(18), key = { "fav_vod_${it.streamId}" }) { movie ->
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
                                    isFavorite = true,
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
        }

        // 8. FAVORİ DİZİLER RAFI
        if (favoriteSeriesList.isNotEmpty()) {
            item(key = "favorite_series", contentType = "poster_shelf") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StreamVaultSectionHeader(
                        title = "Favori Diziler",
                        count = favoriteSeriesList.size,
                        onSeeAllClick = {
                            viewModel.catalogPositions.save("fav:sekme", 2, 0, 0)
                            viewModel.setSelectedHomeTab(HomeTab.FAVORITES)
                        }
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(rowItemSpacing),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth().focusRestorer()
                    ) {
                        items(favoriteSeriesList.take(18), key = { "fav_series_${it.seriesId}" }) { series ->
                            Box(modifier = Modifier.width(posterWidth)) {
                                MediaPosterCard(
                                    imageUrl = series.cover,
                                    title = series.name,
                                    subtitle = "",
                                    rating = series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) },
                                    isFavorite = true,
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
        }
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
                    onFocusStateChange = { isFocused = it },
                    drawFocusBorder = false
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
                onFocusStateChange = { isFocused = it },
                drawFocusBorder = false
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
    title: String,
    imageUrl: String?,
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
                },
                drawFocusBorder = false
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncShimmerImage(
                model = imageUrl,
                contentDescription = title,
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
                    text = title,
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
