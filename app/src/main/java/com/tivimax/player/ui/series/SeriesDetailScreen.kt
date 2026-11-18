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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.tivimax.player.ui.vod.StreamVaultDetailHeading
import com.tivimax.player.ui.vod.StreamVaultMetaStrip
import kotlinx.coroutines.delay

/**
 * STREAMVAULT DİZİ DETAY VE BÖLÜM LİSTESİ (SeriesEpisodes.png ile birebir görsel dil)
 * - Tam ekran sinematik arka plan + "Kütüphaneye dön" hapı
 * - Afiş + başlık + noktalı meta şerit + Devam et / Oynat + Fragman + Yuvarlak Kalp Favori
 * - Dikey mavi vurgu çubuklu "Sezonlar" ve "Bölümler (N)" başlıkları
 * - Geniş yuvarlatılmış 16:9 görselli StreamVault bölüm satır kartları
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

    // Dizi Bölümü Akıllı Ön Isıtma
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

    LaunchedEffect(Unit) {
        delay(180)
        try {
            playButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
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
    val trailerUrl = series.youtubeTrailer
    val plot = info?.plot?.takeIf { it.isNotBlank() } ?: series.plot?.takeIf { it.isNotBlank() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .vegaBackdrop()
    ) {
        // 1. SİNEMATİK ARKA PLAN
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isWideScreen) 440.dp else 300.dp)
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
                                0.0f to VegaColors.Background.copy(alpha = 0.35f),
                                0.50f to VegaColors.Background.copy(alpha = 0.72f),
                                0.88f to VegaColors.Background.copy(alpha = 0.95f),
                                1.0f to VegaColors.Background
                            )
                        )
                    )
            )
        }

        // 2. AKICI LAZYCOLUMN (Hero + Sezonlar + Geniş StreamVault Bölüm Satırları)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                horizontal = if (isWideScreen) 48.dp else 16.dp,
                vertical = if (isWideScreen) 24.dp else 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // SOL ÜST: Kütüphaneye dön hapı
            item(key = "series_back") {
                Surface(
                    shape = VegaShapes.Medium,
                    color = VegaColors.TextPrimary,
                    modifier = Modifier
                        .height(40.dp)
                        .dpadFocusable(
                            focusedBorderColor = VegaColors.FocusBorder,
                            shape = VegaShapes.Medium,
                            scale = 1.04f,
                            onClick = onBack
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = VegaColors.Background,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Kütüphaneye dön",
                            color = VegaColors.Background,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // HERO BÖLÜMÜ
            item(key = "series_hero") {
                val primaryEp = resumeEpisode ?: episodesForSeason.firstOrNull()
                val primaryProg = primaryEp?.let { watchProgressMap[it.id] }
                val hasPrimaryProg = primaryProg != null && primaryProg.positionMs > 10_000L && primaryProg.durationMs > 0L

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isWideScreen) 28.dp else 14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .width(if (isWideScreen) 196.dp else 118.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(VegaColors.SurfaceElevated)
                            .border(1.dp, VegaColors.Border, RoundedCornerShape(14.dp))
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = VegaColors.SurfaceAccent.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, VegaColors.Border)
                            ) {
                                Text("Diziler", color = VegaColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                            if (ratingText != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
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
                            fontSize = if (isWideScreen) 26.sp else 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.4).sp,
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

                        // Aksiyon Butonları
                        val actionShape = RoundedCornerShape(11.dp)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (primaryEp != null) {
                                item(key = "ser_play") {
                                    Surface(
                                        shape = actionShape,
                                        color = VegaColors.Accent,
                                        modifier = Modifier
                                            .height(44.dp)
                                            .focusRequester(playButtonFocusRequester)
                                            .dpadFocusable(
                                                focusedBorderColor = Color.White,
                                                shape = actionShape,
                                                scale = 1.04f,
                                                onClick = { viewModel.playSeriesEpisode(primaryEp, series.name, resume = true) }
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 18.dp),
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
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            if (!trailerUrl.isNullOrBlank()) {
                                item(key = "ser_trailer") {
                                    Surface(
                                        shape = actionShape,
                                        color = VegaColors.SurfaceAccent,
                                        border = BorderStroke(1.dp, VegaColors.Border),
                                        modifier = Modifier
                                            .height(44.dp)
                                            .dpadFocusable(
                                                focusedBorderColor = VegaColors.FocusBorder,
                                                shape = actionShape,
                                                scale = 1.04f,
                                                onClick = { openTrailer(context, trailerUrl) }
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Fragman", color = VegaColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            item(key = "ser_fav") {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isFavorite) VegaColors.Live.copy(alpha = 0.20f) else VegaColors.SurfaceAccent,
                                    border = BorderStroke(1.dp, if (isFavorite) VegaColors.Live.copy(alpha = 0.55f) else VegaColors.Border),
                                    modifier = Modifier
                                        .size(44.dp)
                                        .dpadFocusable(
                                            focusedBorderColor = VegaColors.FocusBorder,
                                            shape = CircleShape,
                                            scale = 1.06f,
                                            onClick = { viewModel.toggleFavoriteSeries(series.seriesId.toString()) }
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
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
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
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            // SEZONLAR BÖLÜMÜ (StreamVault SeriesEpisodes.png)
            if (seasonKeys.isNotEmpty()) {
                item(key = "seasons_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StreamVaultDetailHeading(title = "Sezonlar")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRestorer()
                        ) {
                            items(seasonKeys, key = { "season_$it" }) { seasonNum ->
                                val isSelected = seasonNum == selectedSeason
                                val seasonShape = RoundedCornerShape(10.dp)
                                Surface(
                                    shape = seasonShape,
                                    color = if (isSelected) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.SurfaceElevated,
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) VegaColors.Accent else VegaColors.Border
                                    ),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .dpadFocusable(
                                            focusedBorderColor = VegaColors.FocusBorder,
                                            shape = seasonShape,
                                            scale = 1.04f,
                                            onClick = { selectedSeason = seasonNum }
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 18.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Sezon $seasonNum",
                                            color = if (isSelected) VegaColors.TextPrimary else VegaColors.TextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // BÖLÜMLER BAŞLIĞI (StreamVault SeriesEpisodes.png)
            item(key = "episodes_heading") {
                StreamVaultDetailHeading(
                    title = "Bölümler (${episodesForSeason.size})",
                    subtitle = "Sezon $selectedSeason"
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
                        isWideScreen = isWideScreen,
                        onClick = { viewModel.playSeriesEpisode(episode, series.name, resume = true) }
                    )
                }
            }

            item(key = "series_bottom_space") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * STREAMVAULT BÖLÜM SATIR KARTI (SeriesEpisodes.png ile birebir tasarım)
 */
@Composable
private fun StreamVaultEpisodeRowCard(
    episode: SeriesEpisode,
    fallbackCover: String?,
    watchProgress: WatchProgress?,
    isWideScreen: Boolean,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(14.dp)
    val thumbUrl = episode.info?.movieImage?.takeIf { it.isNotBlank() } ?: fallbackCover
    val hasProgress = watchProgress != null && watchProgress.durationMs > 0L && watchProgress.positionMs > 10_000L
    val progressFraction = if (hasProgress) {
        (watchProgress!!.positionMs.toFloat() / watchProgress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        shape = cardShape,
        color = VegaColors.SurfaceElevated.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = cardShape,
                scale = 1.02f,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isWideScreen) 14.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isWideScreen) 18.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SOL: 16:9 Bölüm Önizleme Görseli + "Bölüm X" Rozeti + İlerleme Çubuğu
            Box(
                modifier = Modifier
                    .width(if (isWideScreen) 168.dp else 120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VegaColors.SurfaceAccent)
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
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Bölüm ${episode.episodeNum}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (hasProgress) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = VegaColors.Accent,
                        trackColor = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }

            // SAĞ: S01B01 Başlığı + Mavi Süre + 2 Satır Açıklama
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val epCode = "S${episode.season.toString().padStart(2, '0')}B${episode.episodeNum.toString().padStart(2, '0')}"
                val cleanTitle = episode.title.trim().takeIf { it.isNotBlank() && !it.equals(epCode, ignoreCase = true) }
                Text(
                    text = if (cleanTitle != null) "$epCode  •  $cleanTitle" else epCode,
                    color = VegaColors.TextPrimary,
                    fontSize = if (isWideScreen) 15.sp else 13.sp,
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
                    if (hasProgress) {
                        val mins = watchProgress!!.positionMs / 60000L
                        val secs = (watchProgress.positionMs % 60000L) / 1000L
                        Text(
                            text = "Kaldığın yer: ${String.format("%02d:%02d", mins, secs)}",
                            color = VegaColors.Gold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                episode.info?.plot?.takeIf { it.isNotBlank() }?.let { epPlot ->
                    Text(
                        text = epPlot,
                        color = VegaColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
