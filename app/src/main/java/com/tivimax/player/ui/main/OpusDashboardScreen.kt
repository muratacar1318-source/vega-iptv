@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tivimax.player.ui.main

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
import com.tivimax.player.ui.design.VegaText
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

    val mobileImageUrl: String?
        get() = when (this) {
            is Movie -> movie.streamIcon
            is Series -> series.cover?.takeIf { it.isNotBlank() } ?: series.backdropPath?.firstOrNull { it.isNotBlank() }
            is Progress -> movie.streamIcon
            is EpisodeProgress -> episode.info?.movieImage?.takeIf { it.isNotBlank() } ?: backdropUrl?.takeIf { it.isNotBlank() }
            is SeriesProgress -> series.cover?.takeIf { it.isNotBlank() } ?: series.backdropPath?.firstOrNull { it.isNotBlank() }
        }

    val year: String?
        get() = when (this) {
            is Movie -> extractReleaseYear(null, movie.name)
            is Series -> extractReleaseYear(series.releaseDate, series.name)
            is Progress -> extractReleaseYear(null, movie.name)
            is EpisodeProgress -> extractReleaseYear(null, seriesTitle)
            is SeriesProgress -> extractReleaseYear(series.releaseDate, series.name)
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
            is Movie -> "Film"
            is Series -> "Dizi"
            is Progress, is EpisodeProgress, is SeriesProgress -> "Kaldığın Yerden"
        }

    val categorySubtitle: String?
        get() = when (this) {
            is Movie -> null
            is Series -> series.genre?.takeIf { it.isNotBlank() }?.substringBefore(",")?.trim()
            is Progress -> formatRemainingDuration(progress)
            is EpisodeProgress -> formatRemainingDuration(progress)
            is SeriesProgress -> formatRemainingDuration(progress)
        }

    val description: String?
        get() = when (this) {
            is Movie -> null
            is Series -> series.plot?.takeIf { it.isNotBlank() }
            is Progress -> null
            is EpisodeProgress -> episode.info?.plot?.takeIf { it.isNotBlank() }
                ?: episode.title.takeIf { it.isNotBlank() }
            is SeriesProgress -> series.plot?.takeIf { it.isNotBlank() }
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
            else -> "İzle"
        }
}

private val YEAR_REGEX = Regex("\\b(19\\d{2}|20\\d{2})\\b")

private fun extractReleaseYear(releaseDate: String?, fallbackName: String): String? {
    val fromDate = releaseDate?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
    if (fromDate != null) return fromDate
    return YEAR_REGEX.find(fallbackName)?.value
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
    return if (num == num.toInt().toFloat()) "${num.toInt()}" else String.format(java.util.Locale.US, "%.1f", num)
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

    var pendingSpotlight by remember { mutableStateOf<SpotlightContent?>(null) }
    var displayedSpotlight by remember { mutableStateOf<SpotlightContent?>(null) }

    LaunchedEffect(pendingSpotlight) {
        val target = pendingSpotlight ?: return@LaunchedEffect
        delay(120L)
        displayedSpotlight = target
    }

    val activeSpotlight = displayedSpotlight ?: initialSpotlight

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
    val shelfSpacing = if (isTvDevice) 24.dp else 20.dp
    val rowItemSpacing = if (isTvDevice) 14.dp else 12.dp
    val horizontalPadding = if (isTvDevice) 8.dp else 16.dp

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = if (isTvDevice) 10.dp else 12.dp,
            bottom = if (isTvDevice) 28.dp else 24.dp
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
                                        pendingSpotlight = when (item) {
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
                                        if (focused) pendingSpotlight = SpotlightContent.Movie(movie)
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
                                        if (focused) pendingSpotlight = SpotlightContent.Series(series)
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
                                        if (focused) pendingSpotlight = SpotlightContent.Movie(movie)
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
                                        if (focused) pendingSpotlight = SpotlightContent.Series(series)
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
 * Editorial raf başlığı: temiz tipografi, sade sayaç ve "Tümü ›" eylemi.
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
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (count != null && count > 0) {
                    Text(
                        text = count.toString(),
                        color = VegaColors.TextMuted,
                        fontSize = VegaText.Secondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = VegaColors.TextMuted,
                    fontSize = VegaText.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onSeeAllClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            var isFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .clip(VegaShapes.Small)
                    .background(if (isFocused) VegaColors.Accent.copy(alpha = 0.16f) else Color.Transparent)
                    .dpadFocusable(
                        shape = VegaShapes.Small,
                        focusedBorderColor = VegaColors.FocusBorder,
                        scale = 1.03f,
                        onClick = onSeeAllClick,
                        onFocusStateChange = { isFocused = it }
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Tümü",
                        color = if (isFocused) VegaColors.TextPrimary else VegaColors.Accent,
                        fontSize = VegaText.Secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = if (isFocused) VegaColors.TextPrimary else VegaColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Vitrin paneli:
 * - Mobil (!isTv): Apple TV+ / Infuse tarzı uzun sinematik Hero kartı (360.dp, 18.dp köşe,
 *   tam boy afiş, yumuşak alt gradyan, nokta ayrımlı üst veri ve yan yana İzle / Detay butonları).
 * - TV (isTv): Crossfade olmadan 60fps keskin geçişli yatay sinematik vitrin.
 */
@Composable
private fun StreamVaultHeroPanel(
    spotlight: SpotlightContent,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit
) {
    val isTvDevice = LocalIsTvDevice.current

    if (!isTvDevice) {
        val mobileShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(mobileShape)
                .background(VegaColors.SurfaceElevated)
        ) {
            AsyncShimmerImage(
                model = spotlight.mobileImageUrl ?: spotlight.imageUrl,
                contentDescription = spotlight.title,
                contentScale = ContentScale.Crop,
                placeholderIcon = Icons.Default.Movie,
                modifier = Modifier.fillMaxSize()
            )

            // Alt kısımda okunaklılık sağlayan pürüzsüz dikey karartma
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.36f to Color.Black.copy(alpha = 0.08f),
                                0.66f to Color.Black.copy(alpha = 0.72f),
                                0.88f to Color.Black.copy(alpha = 0.92f),
                                1.0f to Color.Black.copy(alpha = 0.96f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (spotlight.progressFraction != null) {
                    Text(
                        text = spotlight.eyebrow,
                        color = VegaColors.Accent,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                Text(
                    text = spotlight.title,
                    color = Color.White,
                    fontSize = VegaText.ScreenTitle,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 27.sp
                )

                // Nokta ayrımlı öz üst veri (ör. 2024 · ★ 8.4 · Aksiyon)
                SpotlightMetadataLine(
                    year = spotlight.year,
                    rating = spotlight.rating,
                    subtitle = spotlight.categorySubtitle ?: spotlight.eyebrow.takeIf { spotlight.progressFraction == null }
                )

                spotlight.progressFraction?.let { frac ->
                    LinearProgressIndicator(
                        progress = { frac },
                        color = VegaColors.Accent,
                        trackColor = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(VegaShapes.Pill)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Başparmak dostu yan yana tam genişlikli İzle ve Detay butonları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.Accent,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = VegaShapes.Medium,
                                scale = 1.02f,
                                onClick = onPlayClick
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = VegaColors.OnAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = spotlight.primaryActionLabel,
                                color = VegaColors.OnAccent,
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.OverlayPill,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .dpadFocusable(
                                focusedBorderColor = VegaColors.FocusBorder,
                                shape = VegaShapes.Medium,
                                scale = 1.02f,
                                onClick = onDetailClick
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Detay",
                                color = Color.White,
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        return
    }

    // TV Yatay Vitrin (Crossfade yok -> hızlı D-pad kaydırmada 60fps akıcılık)
    Card(
        shape = VegaShapes.Hero,
        colors = CardDefaults.cardColors(containerColor = VegaColors.SurfaceElevated),
        border = BorderStroke(0.5.dp, VegaColors.Border.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(288.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncShimmerImage(
                model = spotlight.imageUrl,
                contentDescription = spotlight.title,
                contentScale = ContentScale.Crop,
                placeholderIcon = Icons.Default.Movie,
                modifier = Modifier.fillMaxSize()
            )

            // Yatay ve dikey derin gece karartması
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
                                VegaColors.Background.copy(alpha = 0.52f),
                                VegaColors.Background.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpotlightMetadataLine(
                        eyebrow = spotlight.eyebrow,
                        year = spotlight.year,
                        rating = spotlight.rating,
                        subtitle = spotlight.categorySubtitle
                    )

                    Text(
                        text = spotlight.title,
                        color = VegaColors.TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 31.sp,
                        modifier = Modifier.fillMaxWidth(0.68f)
                    )

                    spotlight.description?.let { desc ->
                        Text(
                            text = desc,
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Secondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth(0.62f)
                        )
                    }

                    spotlight.progressFraction?.let { frac ->
                        LinearProgressIndicator(
                            progress = { frac },
                            color = VegaColors.Accent,
                            trackColor = VegaColors.SurfaceAccent,
                            modifier = Modifier
                                .fillMaxWidth(0.35f)
                                .height(3.dp)
                                .clip(VegaShapes.Pill)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.Accent,
                        modifier = Modifier
                            .height(46.dp)
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
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.SurfaceAccent,
                        modifier = Modifier
                            .height(46.dp)
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
                                text = "Detay",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Secondary,
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

@Composable
private fun SpotlightMetadataLine(
    eyebrow: String? = null,
    year: String? = null,
    rating: String? = null,
    subtitle: String? = null
) {
    val hasParts = !eyebrow.isNullOrBlank() || !year.isNullOrBlank() || !rating.isNullOrBlank() || !subtitle.isNullOrBlank()
    if (!hasParts) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        var needsDot = false

        if (!eyebrow.isNullOrBlank()) {
            Text(
                text = eyebrow,
                color = VegaColors.Accent,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            needsDot = true
        }

        if (!year.isNullOrBlank()) {
            if (needsDot) MetadataDot()
            Text(
                text = year,
                color = VegaColors.TextSecondary,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            needsDot = true
        }

        if (!rating.isNullOrBlank()) {
            if (needsDot) MetadataDot()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = VegaColors.Gold,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = rating,
                    color = VegaColors.Gold,
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            needsDot = true
        }

        if (!subtitle.isNullOrBlank()) {
            if (needsDot) MetadataDot()
            Text(
                text = subtitle,
                color = VegaColors.TextSecondary,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetadataDot() {
    Text(
        text = "·",
        color = VegaColors.TextMuted,
        fontSize = VegaText.Secondary,
        fontWeight = FontWeight.Bold
    )
}

/**
 * KANAL KARTI (Logo + Canlı göstergesi + Program + İlerleme çubuğu)
 */
@Composable
private fun StreamVaultChannelCard(
    channel: LiveStream,
    programTitle: String?,
    progress: Float?,
    isFavorite: Boolean,
    onClick: () -> Unit
) {
    val isTv = LocalIsTvDevice.current
    val cardShape = VegaShapes.Medium
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated
        ),
        border = if (isFocused) {
            BorderStroke(2.dp, VegaColors.FocusBorder)
        } else {
            BorderStroke(0.5.dp, VegaColors.Border.copy(alpha = 0.55f))
        },
        modifier = Modifier
            .width(if (isTv) 212.dp else 188.dp)
            .height(if (isTv) 120.dp else 112.dp)
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
                            if (isFocused) VegaColors.SurfaceVariant.copy(alpha = 0.85f) else VegaColors.SurfaceElevated,
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
                    .size(42.dp)
                    .clip(VegaShapes.Small)
                    .background(VegaColors.SurfaceAccent)
                    .padding(5.dp)
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(VegaColors.Live)
                )
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
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = programTitle?.takeIf { it.isNotBlank() } ?: "Canlı",
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Caption,
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

    val cardShape = VegaShapes.Medium
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = VegaColors.SurfaceElevated),
        border = if (isFocused) {
            BorderStroke(2.dp, VegaColors.FocusBorder)
        } else {
            BorderStroke(0.5.dp, VegaColors.Border.copy(alpha = 0.55f))
        },
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
                                Color.Black.copy(alpha = 0.48f),
                                Color.Black.copy(alpha = 0.90f)
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
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = remainingText,
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Caption,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = VegaColors.Accent,
                    trackColor = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(VegaShapes.Pill)
                )
            }
        }
    }
}
