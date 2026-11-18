@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tivimax.player.ui.series

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.SeriesEpisode
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.extractQualityBadges
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.vegaBackdrop
import com.tivimax.player.ui.vod.StreamVaultBackButton
import com.tivimax.player.ui.vod.StreamVaultDetailHeading
import com.tivimax.player.ui.vod.StreamVaultDetailInfoPanel
import com.tivimax.player.ui.vod.StreamVaultFactRow
import com.tivimax.player.ui.vod.StreamVaultMetaStrip
import kotlinx.coroutines.delay

/**
 * STREAMVAULT DİZİ DETAY VE BÖLÜM LİSTESİ (SeriesEpisodes.png ile birebir görsel dil)
 * - Tam genişlikte net sinematik arka plan (üstte temiz, aşağıda derin gece laciverti)
 * - Sol üst "Kütüphaneye dön" hapı + afiş + başlık + noktalı meta şerit + Devam et/Oynat + Fragman + Kalp Favori
 * - Yarı saydam yuvarlatılmış bilgi kartı (Yönetmen, Türler, Oyuncular)
 * - Dikey mavi vurgu çubuklu "Sezonlar" ve "Bölümler (N)" başlıkları + kaldığın bölüm vurgusu
 * - Geniş 16:9 görselli, odaklandığında parlak çerçeve + yüzey vurgusu + sağda oynat ikonu beliren bölüm kartları
 */
@Composable
fun SeriesDetailScreen(
    series: SeriesStream,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    val seriesInfoResponse by viewModel.currentSeriesInfo.collectAsState()
    val episodesMap = seriesInfoResponse?.episodes ?: emptyMap()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()
    val favoriteSeriesIds by viewModel.favoriteSeriesIds.collectAsState()
    val isFavorite = favoriteSeriesIds.contains(series.seriesId.toString())

    val playButtonFocusRequester = remember { FocusRequester() }

    val seasonKeys = remember(episodesMap) {
        episodesMap.keys.sortedBy { it.toIntOrNull() ?: 1 }
    }
    val totalEpisodeCount = remember(episodesMap) {
        episodesMap.values.sumOf { it.size }
    }

    val resumeEpisode = remember(episodesMap, seasonKeys, watchProgressMap) {
        val all = seasonKeys.flatMap { episodesMap[it] ?: emptyList() }
        val last = all
            .filter { watchProgressMap[it.id] != null }
            .maxByOrNull { watchProgressMap[it.id]?.timestamp ?: 0L }
        when {
            last == null -> null
            watchProgressMap[last.id]?.isWatched == true -> all.getOrNull(all.indexOf(last) + 1) ?: last
            else -> last
        }
    }

    var selectedSeason by remember(seasonKeys) {
        mutableStateOf(
            viewModel.lastSelectedSeasonBySeries[series.seriesId]?.takeIf { it in seasonKeys }
                ?: resumeEpisode?.season?.toString()?.takeIf { it in seasonKeys }
                ?: seasonKeys.firstOrNull() ?: "1"
        )
    }
    LaunchedEffect(selectedSeason, seasonKeys) {
        if (selectedSeason in seasonKeys) {
            viewModel.lastSelectedSeasonBySeries[series.seriesId] = selectedSeason
        }
    }

    val episodesForSeason = remember(episodesMap, selectedSeason) {
        episodesMap[selectedSeason] ?: emptyList()
    }
    val primaryEp = resumeEpisode ?: episodesForSeason.firstOrNull()

    // Dizi bölümü akıllı ön ısıtma
    val nextEpForPrewarm = episodesForSeason.firstOrNull()
    DisposableEffect(series.seriesId, selectedSeason, nextEpForPrewarm?.id) {
        if (nextEpForPrewarm != null && series.seriesId !in listOf(101, 102, 103, 201)) {
            val urls = viewModel.repository.buildSeriesStreamUrls(nextEpForPrewarm.id, nextEpForPrewarm.containerExtension)
            val firstUrl = urls.firstOrNull()
            if (!firstUrl.isNullOrBlank()) {
                viewModel.vodPrewarmer.schedulePrewarm(firstUrl, nextEpForPrewarm.id.toIntOrNull())
            }
        }
        onDispose {
            viewModel.vodPrewarmer.cancelAll("ekrandan çıkıldı / sezon değişti")
        }
    }

    // 1-Tık Kumanda Garantisi: Bölümler yüklendiği anda Oynat / Devam et butonuna güvenli odak ver
    var initialFocusApplied by remember(series.seriesId) { mutableStateOf(false) }
    LaunchedEffect(series.seriesId, primaryEp?.id) {
        if (primaryEp != null && !initialFocusApplied) {
            repeat(3) {
                withFrameNanos { }
                delay(80)
                if (runCatching { playButtonFocusRequester.requestFocus() }.isSuccess) {
                    initialFocusApplied = true
                    return@LaunchedEffect
                }
            }
        }
    }

    val info = seriesInfoResponse?.info
    val seriesTitle = info?.name?.takeIf { it.isNotBlank() } ?: series.name
    val backdropUrl = series.backdropPath?.firstOrNull()
        ?: info?.backdropPath?.firstOrNull()
        ?: info?.cover
        ?: series.cover
    val coverUrl = info?.cover ?: series.cover
    val qualityBadges = remember(series.name, info?.name) {
        extractQualityBadges(series.name + " " + (info?.name ?: ""))
    }
    val ratingText = remember(info?.rating, series.rating, series.rating5Based) {
        val raw = info?.rating ?: series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
        val num = raw?.trim()?.substringBefore("/")?.replace(',', '.')?.toFloatOrNull()
        if (num != null && num > 0f) {
            val f = if (num == num.toInt().toFloat()) "${num.toInt()}" else String.format(java.util.Locale.US, "%.1f", num)
            if (num <= 5f) "$f/5" else "$f/10"
        } else null
    }
    val genreText = info?.genre?.takeIf { it.isNotBlank() } ?: series.genre?.takeIf { it.isNotBlank() }
    val releaseDate = info?.releaseDate?.takeIf { it.isNotBlank() } ?: series.releaseDate?.takeIf { it.isNotBlank() }
    val directorText = info?.director?.takeIf { it.isNotBlank() } ?: series.director?.takeIf { it.isNotBlank() }
    val castText = info?.cast?.takeIf { it.isNotBlank() } ?: series.cast?.takeIf { it.isNotBlank() }
    val trailerUrl = series.youtubeTrailer
    val plot = info?.plot?.takeIf { it.isNotBlank() } ?: series.plot?.takeIf { it.isNotBlank() }
    val hasFactInfo = listOf(directorText, genreText, releaseDate, castText).any { !it.isNullOrBlank() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .vegaBackdrop()
    ) {
        // 1. SİNEMATİK ARKA PLAN (SeriesEpisodes.png — üstte net, aşağıya derin gece laciverti)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isWideScreen) 460.dp else 300.dp)
                .align(Alignment.TopCenter)
        ) {
            AsyncShimmerImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholderIcon = Icons.Default.VideoLibrary,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.36f to VegaColors.Background.copy(alpha = 0.28f),
                                0.72f to VegaColors.Background.copy(alpha = 0.86f),
                                1.0f to VegaColors.Background
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to VegaColors.Background.copy(alpha = 0.60f),
                                0.45f to Color.Transparent,
                                1.0f to VegaColors.Background.copy(alpha = 0.25f)
                            )
                        )
                    )
            )
        }

        // 2. AKICI LAZYCOLUMN (Hero + Bilgi Kartı + Sezonlar + Geniş StreamVault Bölüm Satırları)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                horizontal = if (isWideScreen) 52.dp else 16.dp,
                vertical = if (isWideScreen) 28.dp else 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // SOL ÜST: Kütüphaneye dön hapı
            item(key = "series_back") {
                StreamVaultBackButton(onClick = onBack)
            }

            // HERO BÖLÜMÜ
            item(key = "series_hero") {
                val primaryProg = primaryEp?.let { watchProgressMap[it.id] }
                val hasPrimaryProg = primaryProg != null && primaryProg.positionMs > 10_000L && primaryProg.durationMs > 0L

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isWideScreen) 30.dp else 14.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .width(if (isWideScreen) 216.dp else 120.dp)
                            .aspectRatio(2f / 3f)
                            .clip(VegaShapes.Large)
                            .background(VegaColors.SurfaceElevated)
                            .border(1.dp, VegaColors.Border, VegaShapes.Large)
                    ) {
                        AsyncShimmerImage(
                            model = coverUrl,
                            contentDescription = seriesTitle,
                            contentScale = ContentScale.Crop,
                            placeholderIcon = Icons.Default.VideoLibrary,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = VegaShapes.Small,
                                color = VegaColors.SurfaceAccent.copy(alpha = 0.88f),
                                border = BorderStroke(1.dp, VegaColors.Border)
                            ) {
                                Text("Diziler", color = VegaColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                            if (ratingText != null) {
                                Surface(
                                    shape = VegaShapes.Small,
                                    color = VegaColors.Gold.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, VegaColors.Gold.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(13.dp))
                                        Text(ratingText, color = VegaColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            qualityBadges.forEach { QualityBadge(tag = it) }
                        }

                        Text(
                            text = seriesTitle,
                            color = VegaColors.TextPrimary,
                            fontSize = if (isWideScreen) 28.sp else 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            lineHeight = if (isWideScreen) 34.sp else 24.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        val metaList = buildList {
                            releaseDate?.let { add(it) }
                            if (seasonKeys.isNotEmpty()) {
                                add(if (totalEpisodeCount > 0) "${seasonKeys.size} Sezon • $totalEpisodeCount Bölüm" else "${seasonKeys.size} Sezon")
                            }
                            genreText?.let { add(it) }
                        }
                        if (metaList.isNotEmpty()) {
                            StreamVaultMetaStrip(values = metaList)
                        }

                        // Aksiyon Butonları (tek odak + tek tıklama)
                        val actionShape = RoundedCornerShape(11.dp)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            if (primaryEp != null) {
                                item(key = "ser_primary") {
                                    Surface(
                                        shape = actionShape,
                                        color = VegaColors.Accent,
                                        modifier = Modifier
                                            .height(46.dp)
                                            .focusRequester(playButtonFocusRequester)
                                            .dpadFocusable(
                                                focusedBorderColor = Color.White,
                                                shape = actionShape,
                                                scale = 1.045f,
                                                onClick = { viewModel.playSeriesEpisode(primaryEp, series.name, resume = true) }
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 20.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(20.dp))
                                            Text(
                                                text = if (hasPrimaryProg) {
                                                    "Devam et (S${primaryEp.season}:B${primaryEp.episodeNum})"
                                                } else {
                                                    "Oynat (S${primaryEp.season}:B${primaryEp.episodeNum})"
                                                },
                                                color = VegaColors.OnAccent,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            if (!trailerUrl.isNullOrBlank()) {
                                item(key = "ser_trailer") {
                                    var isFocused by remember { mutableStateOf(false) }
                                    Surface(
                                        shape = actionShape,
                                        color = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceAccent,
                                        border = BorderStroke(
                                            width = if (isFocused) 2.dp else 1.dp,
                                            color = if (isFocused) VegaColors.FocusBorder else VegaColors.Border
                                        ),
                                        modifier = Modifier
                                            .height(46.dp)
                                            .dpadFocusable(
                                                focusedBorderColor = VegaColors.FocusBorder,
                                                shape = actionShape,
                                                scale = 1.04f,
                                                onClick = { openTrailer(context, trailerUrl) },
                                                onFocusStateChange = { isFocused = it }
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 18.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Fragman", color = VegaColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            item(key = "ser_fav") {
                                var isFocused by remember { mutableStateOf(false) }
                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        isFavorite -> VegaColors.Live.copy(alpha = 0.22f)
                                        isFocused -> VegaColors.SurfaceVariant
                                        else -> VegaColors.SurfaceAccent
                                    },
                                    border = BorderStroke(
                                        width = if (isFocused) 2.dp else 1.dp,
                                        color = when {
                                            isFocused -> VegaColors.FocusBorder
                                            isFavorite -> VegaColors.Live.copy(alpha = 0.55f)
                                            else -> VegaColors.Border
                                        }
                                    ),
                                    modifier = Modifier
                                        .size(46.dp)
                                        .dpadFocusable(
                                            focusedBorderColor = VegaColors.FocusBorder,
                                            shape = CircleShape,
                                            scale = 1.06f,
                                            onClick = { viewModel.toggleFavoriteSeries(series.seriesId.toString()) },
                                            onFocusStateChange = { isFocused = it }
                                        )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = if (isFavorite) "Favorilerden çıkar" else "Favorilere ekle",
                                            tint = if (isFavorite) VegaColors.Live else VegaColors.TextPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (plot != null && isWideScreen) {
                            Text(
                                text = plot,
                                color = VegaColors.TextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (plot != null && !isWideScreen) {
                item(key = "series_plot_mobile") {
                    Text(
                        text = plot,
                        color = VegaColors.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            // StreamVault Yarı Saydam Bilgi Kartı (Yönetmen, Türler, Oyuncular)
            if (hasFactInfo) {
                item(key = "series_facts_panel") {
                    StreamVaultDetailInfoPanel(
                        modifier = Modifier.fillMaxWidth(),
                        spacing = 10.dp
                    ) {
                        StreamVaultDetailHeading(title = "Dizi bilgileri")
                        directorText?.let { StreamVaultFactRow(label = "Yönetmen", value = it) }
                        releaseDate?.let { StreamVaultFactRow(label = "Yayın tarihi", value = it) }
                        genreText?.let { StreamVaultFactRow(label = "Tür", value = it) }
                        castText?.let { StreamVaultFactRow(label = "Oyuncular", value = it) }
                    }
                }
            }

            // SEZONLAR BÖLÜMÜ (StreamVault SeriesEpisodes.png)
            if (seasonKeys.isNotEmpty()) {
                item(key = "seasons_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StreamVaultDetailHeading(title = "Sezonlar")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRestorer()
                        ) {
                            items(seasonKeys, key = { "season_$it" }) { seasonNum ->
                                val isSelected = seasonNum == selectedSeason
                                val seasonEpCount = episodesMap[seasonNum]?.size ?: 0
                                val seasonShape = RoundedCornerShape(11.dp)
                                var isFocused by remember { mutableStateOf(false) }

                                Surface(
                                    shape = seasonShape,
                                    color = when {
                                        isFocused -> VegaColors.SurfaceVariant
                                        isSelected -> VegaColors.Accent.copy(alpha = 0.20f)
                                        else -> VegaColors.SurfaceElevated
                                    },
                                    border = BorderStroke(
                                        width = if (isFocused) 2.5.dp else if (isSelected) 1.5.dp else 1.dp,
                                        color = when {
                                            isFocused -> VegaColors.FocusBorder
                                            isSelected -> VegaColors.Accent
                                            else -> VegaColors.Border
                                        }
                                    ),
                                    modifier = Modifier
                                        .height(42.dp)
                                        .dpadFocusable(
                                            focusedBorderColor = VegaColors.FocusBorder,
                                            shape = seasonShape,
                                            scale = 1.045f,
                                            onClick = { selectedSeason = seasonNum },
                                            onFocusStateChange = { isFocused = it }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Sezon $seasonNum",
                                            color = if (isSelected || isFocused) VegaColors.TextPrimary else VegaColors.TextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                                        )
                                        if (seasonEpCount > 0) {
                                            Text(
                                                text = "($seasonEpCount)",
                                                color = if (isSelected) VegaColors.Accent else VegaColors.TextMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // BÖLÜMLER BAŞLIĞI (StreamVault SeriesEpisodes.png — sezon değişiminde anlık bölüm sayacı)
            item(key = "episodes_heading") {
                val resumeInSeason = resumeEpisode?.takeIf { it.season.toString() == selectedSeason }
                val sub = if (resumeInSeason != null) {
                    "Sezon $selectedSeason • Kaldığın bölüm: Bölüm ${resumeInSeason.episodeNum}"
                } else {
                    "Sezon $selectedSeason"
                }
                StreamVaultDetailHeading(
                    title = "Bölümler (${episodesForSeason.size})",
                    subtitle = sub
                )
            }

            if (seriesInfoResponse == null) {
                item(key = "episodes_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = VegaColors.Accent, strokeWidth = 2.5.dp)
                    }
                }
            } else if (episodesForSeason.isEmpty()) {
                item(key = "episodes_empty") {
                    Text(
                        text = "Bu sezon için bölüm bulunamadı.",
                        color = VegaColors.TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(episodesForSeason, key = { "ep_${it.id}_${it.episodeNum}" }) { episode ->
                    StreamVaultEpisodeRowCard(
                        episode = episode,
                        fallbackCover = coverUrl,
                        watchProgress = watchProgressMap[episode.id],
                        isResumeTarget = resumeEpisode?.id == episode.id,
                        isWideScreen = isWideScreen,
                        onClick = { viewModel.playSeriesEpisode(episode, series.name, resume = true) }
                    )
                }
            }

            item(key = "series_bottom_space") {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

/**
 * STREAMVAULT BÖLÜM SATIR KARTI (SeriesEpisodes.png ile birebir tasarım)
 * - Odaklandığında parlak 2.5.dp çerçeve + VegaColors.SurfaceVariant yüzey vurgusu + sağda oynat ikonu
 * - Kaldığın bölüm vurgusu ("Devam et" rozeti + mavi ilerleme çubuğu)
 */
@Composable
private fun StreamVaultEpisodeRowCard(
    episode: SeriesEpisode,
    fallbackCover: String?,
    watchProgress: WatchProgress?,
    isResumeTarget: Boolean,
    isWideScreen: Boolean,
    onClick: () -> Unit
) {
    val cardShape = VegaShapes.Large
    val thumbUrl = episode.info?.movieImage?.takeIf { it.isNotBlank() } ?: fallbackCover
    val isWatched = watchProgress?.isWatched == true
    val hasProgress = watchProgress != null && watchProgress.durationMs > 0L && watchProgress.positionMs > 10_000L && !isWatched
    val progressFraction = if (hasProgress) {
        (watchProgress!!.positionMs.toFloat() / watchProgress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else if (isWatched) {
        1f
    } else 0f

    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = cardShape,
        color = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated.copy(alpha = 0.94f),
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else if (isResumeTarget) 1.5.dp else 1.dp,
            color = when {
                isFocused -> VegaColors.FocusBorder
                isResumeTarget -> VegaColors.Accent.copy(alpha = 0.65f)
                else -> VegaColors.Border
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = cardShape,
                scale = 1.02f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isFocused) {
                        Brush.horizontalGradient(
                            listOf(
                                VegaColors.SurfaceVariant,
                                VegaColors.SurfaceElevated.copy(alpha = 0.96f)
                            )
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                VegaColors.SurfaceElevated.copy(alpha = 0.94f),
                                VegaColors.Surface.copy(alpha = 0.88f)
                            )
                        )
                    }
                )
                .padding(if (isWideScreen) 14.dp else 11.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isWideScreen) 18.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SOL: 16:9 Bölüm Önizleme Görseli + "Bölüm X" Rozeti + İlerleme Çubuğu
            Box(
                modifier = Modifier
                    .width(if (isWideScreen) 176.dp else 124.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VegaColors.SurfaceAccent)
                    .border(1.dp, VegaColors.Border, RoundedCornerShape(10.dp))
            ) {
                AsyncShimmerImage(
                    model = thumbUrl,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    placeholderIcon = Icons.Default.PlayArrow,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Bölüm ${episode.episodeNum}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (progressFraction > 0.01f) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = if (isWatched) VegaColors.Success else VegaColors.Accent,
                        trackColor = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.5.dp)
                    )
                }
            }

            // ORTA: S01B01 Başlığı + Mavi Süre + Devam et rozeti + 2 Satır Açıklama
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val epCode = "S${episode.season.toString().padStart(2, '0')}B${episode.episodeNum.toString().padStart(2, '0')}"
                val cleanTitle = episode.title.trim().takeIf { it.isNotBlank() && !it.equals(epCode, ignoreCase = true) }
                Text(
                    text = if (cleanTitle != null) "$epCode  •  $cleanTitle" else epCode,
                    color = VegaColors.TextPrimary,
                    fontSize = if (isWideScreen) 15.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    episode.info?.duration?.takeIf { it.isNotBlank() }?.let { dur ->
                        Text(
                            text = dur,
                            color = VegaColors.Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (isResumeTarget || hasProgress) {
                        Surface(
                            shape = VegaShapes.Small,
                            color = VegaColors.Accent.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.42f))
                        ) {
                            Text(
                                text = "Devam et",
                                color = VegaColors.Accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (hasProgress) {
                        val mins = watchProgress!!.positionMs / 60000L
                        val secs = (watchProgress.positionMs % 60000L) / 1000L
                        Text(
                            text = "Kaldığın yer: ${String.format("%02d:%02d", mins, secs)}",
                            color = VegaColors.Gold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (isWatched) {
                        Surface(
                            shape = VegaShapes.Small,
                            color = VegaColors.Success.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, VegaColors.Success.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "İzlendi",
                                color = VegaColors.Success,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                episode.info?.plot?.takeIf { it.isNotBlank() }?.let { epPlot ->
                    Text(
                        text = epPlot,
                        color = VegaColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // SAĞ: Odaklandığında (veya aktif bölümde) belirgin Oynat İkonu
            if (isFocused || isResumeTarget) {
                Box(
                    modifier = Modifier
                        .size(if (isWideScreen) 44.dp else 38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFocused) VegaColors.Accent else VegaColors.Accent.copy(alpha = 0.22f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Oynat",
                        tint = if (isFocused) VegaColors.OnAccent else VegaColors.Accent,
                        modifier = Modifier.size(if (isWideScreen) 24.dp else 20.dp)
                    )
                }
            }
        }
    }
}

private fun openTrailer(context: android.content.Context, trailer: String?) {
    if (trailer.isNullOrBlank()) return
    try {
        val url = if (trailer.startsWith("http://") || trailer.startsWith("https://")) {
            trailer
        } else {
            "https://www.youtube.com/watch?v=$trailer"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {}
}
