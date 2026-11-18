@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tivimax.player.ui.series

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.SeriesEpisode
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.extractQualityBadges
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.design.VegaButton
import com.tivimax.player.ui.design.VegaButtonVariant
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaSizes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.vegaBackdrop
import com.tivimax.player.ui.vod.DetailDotMetadataRow
import com.tivimax.player.ui.vod.DetailHairlineMetaList
import com.tivimax.player.ui.vod.DetailMobileBackButton
import com.tivimax.player.ui.vod.MobileSecondaryActionButton
import com.tivimax.player.ui.vod.StreamVaultBackButton
import com.tivimax.player.ui.vod.StreamVaultDetailHeading
import com.tivimax.player.ui.vod.StreamVaultDetailInfoPanel
import com.tivimax.player.ui.vod.StreamVaultFactRow
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.delay

@Composable
fun SeriesDetailScreen(
    series: SeriesStream,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv = LocalIsTvDevice.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val isMobilePortrait = !isTv && !isWideScreen

    val seriesInfoResponse by viewModel.currentSeriesInfo.collectAsState()
    val isDetailLoading by viewModel.seriesDetailLoading.collectAsState()
    val detailError by viewModel.seriesDetailError.collectAsState()
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
    var visibleEpisodeLimit by remember(series.seriesId, selectedSeason) { mutableIntStateOf(60) }
    val pagedEpisodes = remember(episodesForSeason, visibleEpisodeLimit) {
        episodesForSeason.take(visibleEpisodeLimit)
    }
    val primaryEp = resumeEpisode ?: episodesForSeason.firstOrNull()
    val primaryProg = primaryEp?.let { watchProgressMap[it.id] }
    val hasPrimaryProg = primaryProg != null &&
        !primaryProg.isWatched &&
        primaryProg.positionMs > 10_000L &&
        primaryProg.durationMs > 0L

    // Dizi bölümü akıllı ön ısıtma (primaryEp hedeflenir)
    DisposableEffect(series.seriesId, primaryEp?.id) {
        if (primaryEp != null && series.seriesId !in listOf(101, 102, 103, 201)) {
            val urls = viewModel.repository.buildSeriesStreamUrls(primaryEp.id, primaryEp.containerExtension)
            val firstUrl = urls.firstOrNull()
            if (!firstUrl.isNullOrBlank()) {
                viewModel.vodPrewarmer.schedulePrewarm(firstUrl, primaryEp.id.toIntOrNull())
            }
        }
        onDispose {
            viewModel.vodPrewarmer.cancelAll("ekrandan çıkıldı / bölüm değişti")
        }
    }

    // 1-Tık Kumanda Garantisi: TV / geniş ekranda kullanıcı odağı başka öğeye taşımadıysa Oynat / Devam et butonuna güvenli odak ver
    var userMovedFocus by remember(series.seriesId) { mutableStateOf(false) }
    var initialFocusApplied by remember(series.seriesId) { mutableStateOf(false) }
    LaunchedEffect(series.seriesId, primaryEp?.id, isMobilePortrait) {
        if (isMobilePortrait) return@LaunchedEffect
        if (primaryEp != null && !initialFocusApplied && !userMovedFocus) {
            repeat(3) {
                withFrameNanos { }
                delay(80)
                if (userMovedFocus) return@LaunchedEffect
                if (playButtonFocusRequester.requestFocusSafely()) {
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
    val ratingShort = remember(info?.rating, series.rating, series.rating5Based) {
        val raw = info?.rating ?: series.rating ?: series.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
        val num = raw?.trim()?.substringBefore("/")?.replace(',', '.')?.toFloatOrNull()
        if (num != null && num > 0f) {
            if (num == num.toInt().toFloat()) "${num.toInt()}" else String.format(java.util.Locale.US, "%.1f", num)
        } else null
    }
    val genreText = info?.genre?.takeIf { it.isNotBlank() } ?: series.genre?.takeIf { it.isNotBlank() }
    val releaseDate = info?.releaseDate?.takeIf { it.isNotBlank() } ?: series.releaseDate?.takeIf { it.isNotBlank() }
    val releaseYear = remember(releaseDate) {
        releaseDate?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: releaseDate
    }
    val directorText = info?.director?.takeIf { it.isNotBlank() } ?: series.director?.takeIf { it.isNotBlank() }
    val castText = info?.cast?.takeIf { it.isNotBlank() } ?: series.cast?.takeIf { it.isNotBlank() }
    val trailerUrl = series.youtubeTrailer
    val plot = info?.plot?.takeIf { it.isNotBlank() } ?: series.plot?.takeIf { it.isNotBlank() }

    val factRows = remember(directorText, castText, genreText, releaseDate) {
        buildList {
            directorText?.let { add("Yönetmen" to it) }
            castText?.let { add("Oyuncular" to it) }
            genreText?.let { add("Tür" to it) }
            releaseDate?.let { add("Yayın tarihi" to it) }
        }
    }

    if (isMobilePortrait) {
        // MOBİL DİZİ DETAYI (Tam genişlik sinema görseli + başparmak aksiyonları + minimal sezon sekmeleri + ince çizgili bölüm satırları)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VegaColors.Background)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                // 1. TAM GENİŞLİK SİNEMA GÖRSELİ + BAŞLIK & META
                item(key = "series_hero_mobile") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(308.dp)
                    ) {
                        AsyncShimmerImage(
                            model = backdropUrl,
                            contentDescription = seriesTitle,
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
                                            0.0f to Color.Black.copy(alpha = 0.28f),
                                            0.32f to Color.Transparent,
                                            0.64f to VegaColors.Background.copy(alpha = 0.72f),
                                            0.90f to VegaColors.Background.copy(alpha = 0.96f),
                                            1.0f to VegaColors.Background
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = seriesTitle,
                                color = VegaColors.TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.4).sp,
                                lineHeight = 29.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            val metaItems = buildList {
                                releaseYear?.let { add(it) }
                                if (seasonKeys.isNotEmpty()) {
                                    add("${seasonKeys.size} Sezon")
                                }
                                if (totalEpisodeCount > 0) {
                                    add("$totalEpisodeCount Bölüm")
                                }
                            }
                            DetailDotMetadataRow(
                                items = metaItems,
                                rating = ratingShort,
                                qualityBadges = qualityBadges
                            )

                            if (!genreText.isNullOrBlank()) {
                                Text(
                                    text = genreText,
                                    color = VegaColors.TextMuted,
                                    fontSize = VegaText.Secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 2. BİRİNCİL CTA + İKİNCİL SATIR
                item(key = "series_actions_mobile") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (primaryEp != null) {
                            val ctaLabel = if (hasPrimaryProg) {
                                "Devam et · S${primaryEp.season} B${primaryEp.episodeNum}"
                            } else {
                                "İzle · S${primaryEp.season} B${primaryEp.episodeNum}"
                            }
                            Surface(
                                shape = VegaShapes.Medium,
                                color = VegaColors.Accent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(VegaSizes.ButtonHeightTv)
                                    .dpadFocusable(
                                        focusedBorderColor = Color.White,
                                        shape = VegaShapes.Medium,
                                        scale = 1.02f,
                                        onClick = { viewModel.playSeriesEpisode(primaryEp, series.name, resume = hasPrimaryProg) }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = VegaColors.OnAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = ctaLabel,
                                        color = VegaColors.OnAccent,
                                        fontSize = VegaText.BodyProminent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MobileSecondaryActionButton(
                                label = if (isFavorite) "Favoride" else "Favori",
                                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                isHighlighted = isFavorite,
                                highlightColor = VegaColors.Gold,
                                onClick = { viewModel.toggleFavoriteSeries(series.seriesId.toString()) },
                                modifier = Modifier.weight(1f)
                            )
                            if (!trailerUrl.isNullOrBlank()) {
                                MobileSecondaryActionButton(
                                    label = "Fragman",
                                    icon = Icons.Default.PlayCircleOutline,
                                    onClick = { openTrailer(context, trailerUrl) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 3. ÖZET (Kutusuz, 21.sp satır yüksekliği)
                if (!plot.isNullOrBlank()) {
                    item(key = "series_plot_mobile") {
                        Text(
                            text = plot,
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Body,
                            lineHeight = 21.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 14.dp)
                        )
                    }
                }

                // 4. MİNİMAL YATAY SEZON SEKMELERİ
                if (seasonKeys.isNotEmpty()) {
                    item(key = "seasons_tabs_mobile") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(seasonKeys, key = { "mob_season_$it" }) { seasonNum ->
                                    val isSelected = seasonNum == selectedSeason
                                    val seasonEpCount = episodesMap[seasonNum]?.size ?: 0
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = VegaSizes.MinTouch)
                                            .clip(VegaShapes.Small)
                                            .dpadFocusable(
                                                shape = VegaShapes.Small,
                                                focusedBorderColor = VegaColors.FocusBorder,
                                                scale = 1.02f,
                                                onClick = { selectedSeason = seasonNum }
                                            )
                                            .padding(horizontal = 4.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Sezon $seasonNum",
                                                color = if (isSelected) VegaColors.TextPrimary else VegaColors.TextMuted,
                                                fontSize = VegaText.BodyProminent,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            if (seasonEpCount > 0) {
                                                Text(
                                                    text = "$seasonEpCount",
                                                    color = if (isSelected) VegaColors.Accent else VegaColors.TextMuted.copy(alpha = 0.7f),
                                                    fontSize = VegaText.Caption,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(if (isSelected) 36.dp else 0.dp)
                                                .height(2.5.dp)
                                                .clip(VegaShapes.Pill)
                                                .background(if (isSelected) VegaColors.Accent else Color.Transparent)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = VegaColors.Border.copy(alpha = 0.35f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }

                // 5. BÖLÜM LİSTESİ / YÜKLEME / HATA DURUMLARI
                if (seriesInfoResponse == null && detailError != null) {
                    item(key = "episodes_error_mobile") {
                        SeriesDetailStatusBlock(
                            title = "Dizi bilgileri yüklenemedi",
                            subtitle = "Bağlantını kontrol edip tekrar dene.",
                            isError = true,
                            onRetry = { viewModel.loadSeriesDetail(series) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                        )
                    }
                } else if (seriesInfoResponse == null && isDetailLoading) {
                    item(key = "episodes_loading_mobile") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = VegaColors.Accent, strokeWidth = 2.5.dp)
                        }
                    }
                } else if (seriesInfoResponse == null) {
                    item(key = "episodes_unavailable_mobile") {
                        SeriesDetailStatusBlock(
                            title = "Bölüm listesi alınamadı",
                            subtitle = null,
                            isError = false,
                            onRetry = { viewModel.loadSeriesDetail(series) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                        )
                    }
                } else if (episodesForSeason.isEmpty()) {
                    item(key = "episodes_empty_mobile") {
                        Text(
                            text = "Bu sezon için bölüm bulunamadı.",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Secondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                        )
                    }
                } else {
                    itemsIndexed(pagedEpisodes, key = { idx, it -> "mob_ep_${it.id}_${it.episodeNum}_$idx" }) { idx, episode ->
                        val epProgress = watchProgressMap[episode.id]
                        val canResumeEpisode = epProgress != null && !epProgress.isWatched && epProgress.positionMs > 10_000L
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (idx > 0) {
                                HorizontalDivider(
                                    color = VegaColors.Border.copy(alpha = 0.3f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            MobileEpisodeRow(
                                episode = episode,
                                fallbackCover = coverUrl,
                                watchProgress = epProgress,
                                isResumeTarget = resumeEpisode?.id == episode.id,
                                onClick = { viewModel.playSeriesEpisode(episode, series.name, resume = canResumeEpisode) }
                            )
                        }
                    }

                    if (episodesForSeason.size > visibleEpisodeLimit) {
                        val remainingCount = episodesForSeason.size - visibleEpisodeLimit
                        item(key = "episodes_load_more_mobile_$visibleEpisodeLimit") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                VegaButton(
                                    text = "Daha fazla bölüm göster ($remainingCount)",
                                    variant = VegaButtonVariant.SECONDARY,
                                    icon = Icons.Default.ExpandMore,
                                    onClick = { visibleEpisodeLimit += 60 }
                                )
                            }
                        }
                    }
                }

                // 6. İNCE ÇİZGİLİ DİZİ BİLGİLERİ (Kutusuz)
                if (factRows.isNotEmpty()) {
                    item(key = "series_facts_mobile") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Dizi bilgileri",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.SectionTitle,
                                fontWeight = FontWeight.Bold
                            )
                            DetailHairlineMetaList(rows = factRows)
                        }
                    }
                }
            }

            // YÜZEN YARI SAYDAM YUVARLAK GERİ DÜĞMESİ
            DetailMobileBackButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 14.dp, top = 10.dp)
            )
        }
    } else {
        // TV / GENİŞ EKRAN SİNEMA DÜZENİ (Infuse / Apple TV+ dingin editoryal kompozisyon)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .vegaBackdrop()
        ) {
            // 1. TAM GENİŞLİK SİNEMATİK ARKA PLAN
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
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
                                    0.38f to VegaColors.Background.copy(alpha = 0.28f),
                                    0.74f to VegaColors.Background.copy(alpha = 0.88f),
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
                                    0.0f to VegaColors.Background.copy(alpha = 0.72f),
                                    0.48f to VegaColors.Background.copy(alpha = 0.14f),
                                    1.0f to VegaColors.Background.copy(alpha = 0.22f)
                                )
                            )
                        )
                )
            }

            // 2. AKICI LAZYCOLUMN
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(horizontal = 52.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                item(key = "series_back") {
                    StreamVaultBackButton(
                        onClick = onBack,
                        onFocusStateChange = { if (it) userMovedFocus = true }
                    )
                }

                // HERO BÖLÜMÜ
                item(key = "series_hero") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .width(214.dp)
                                .aspectRatio(2f / 3f)
                                .clip(VegaShapes.Large)
                                .background(VegaColors.SurfaceElevated)
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
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = seriesTitle,
                                color = VegaColors.TextPrimary,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                                lineHeight = 36.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            val metaList = buildList {
                                releaseYear?.let { add(it) }
                                if (seasonKeys.isNotEmpty()) {
                                    add(
                                        if (totalEpisodeCount > 0) "${seasonKeys.size} Sezon · $totalEpisodeCount Bölüm"
                                        else "${seasonKeys.size} Sezon"
                                    )
                                }
                                genreText?.let { add(it) }
                            }
                            DetailDotMetadataRow(
                                items = metaList,
                                rating = ratingShort,
                                qualityBadges = qualityBadges
                            )

                            // Aksiyon Butonları (tek odak + tek tıklama)
                            val actionShape = VegaShapes.Medium
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
                                                .height(VegaSizes.ButtonHeightTv)
                                                .focusRequester(playButtonFocusRequester)
                                                .dpadFocusable(
                                                    focusedBorderColor = Color.White,
                                                    shape = actionShape,
                                                    scale = 1.045f,
                                                    onClick = { viewModel.playSeriesEpisode(primaryEp, series.name, resume = hasPrimaryProg) }
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 22.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(21.dp))
                                                Text(
                                                    text = if (hasPrimaryProg) {
                                                        "Devam et (S${primaryEp.season}:B${primaryEp.episodeNum})"
                                                    } else {
                                                        "İzle (S${primaryEp.season}:B${primaryEp.episodeNum})"
                                                    },
                                                    color = VegaColors.OnAccent,
                                                    fontSize = VegaText.Body,
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
                                            color = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated.copy(alpha = 0.78f),
                                            modifier = Modifier
                                                .height(VegaSizes.ButtonHeightTv)
                                                .dpadFocusable(
                                                    focusedBorderColor = VegaColors.FocusBorder,
                                                    shape = actionShape,
                                                    scale = 1.04f,
                                                    onClick = { openTrailer(context, trailerUrl) },
                                                    onFocusStateChange = {
                                                        isFocused = it
                                                        if (it) userMovedFocus = true
                                                    }
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 18.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = VegaColors.TextPrimary, modifier = Modifier.size(18.dp))
                                                Text("Fragman", color = VegaColors.TextPrimary, fontSize = VegaText.Secondary, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }

                                item(key = "ser_fav") {
                                    var isFocused by remember { mutableStateOf(false) }
                                    Surface(
                                        shape = CircleShape,
                                        color = when {
                                            isFavorite -> VegaColors.Gold.copy(alpha = 0.18f)
                                            isFocused -> VegaColors.SurfaceVariant
                                            else -> VegaColors.SurfaceElevated.copy(alpha = 0.78f)
                                        },
                                        modifier = Modifier
                                            .size(VegaSizes.ButtonHeightTv)
                                            .dpadFocusable(
                                                focusedBorderColor = VegaColors.FocusBorder,
                                                shape = CircleShape,
                                                scale = 1.06f,
                                                onClick = { viewModel.toggleFavoriteSeries(series.seriesId.toString()) },
                                                onFocusStateChange = {
                                                    isFocused = it
                                                    if (it) userMovedFocus = true
                                                }
                                            )
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = if (isFavorite) "Favorilerden çıkar" else "Favorilere ekle",
                                                tint = if (isFavorite) VegaColors.Gold else VegaColors.TextPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (plot != null) {
                                Text(
                                    text = plot,
                                    color = VegaColors.TextSecondary,
                                    fontSize = VegaText.Body,
                                    lineHeight = 22.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Yumuşak tonal bilgi paneli
                if (factRows.isNotEmpty()) {
                    item(key = "series_facts_panel") {
                        StreamVaultDetailInfoPanel(
                            modifier = Modifier.fillMaxWidth(),
                            spacing = 10.dp
                        ) {
                            StreamVaultDetailHeading(title = "Dizi bilgileri")
                            factRows.forEach { (label, value) ->
                                StreamVaultFactRow(label = label, value = value)
                            }
                        }
                    }
                }

                // SEZONLAR BÖLÜMÜ (Çerçevesiz tonal sekmeler)
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
                                    var isFocused by remember { mutableStateOf(false) }

                                    Surface(
                                        shape = VegaShapes.Medium,
                                        color = when {
                                            isFocused -> VegaColors.SurfaceVariant
                                            isSelected -> VegaColors.Accent.copy(alpha = 0.18f)
                                            else -> VegaColors.SurfaceElevated.copy(alpha = 0.65f)
                                        },
                                        modifier = Modifier
                                            .height(42.dp)
                                            .dpadFocusable(
                                                focusedBorderColor = VegaColors.FocusBorder,
                                                shape = VegaShapes.Medium,
                                                scale = 1.045f,
                                                onClick = { selectedSeason = seasonNum },
                                                onFocusStateChange = {
                                                    isFocused = it
                                                    if (it) userMovedFocus = true
                                                }
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
                                                fontSize = VegaText.Secondary,
                                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                                            )
                                            if (seasonEpCount > 0) {
                                                Text(
                                                    text = "$seasonEpCount",
                                                    color = if (isSelected) VegaColors.Accent else VegaColors.TextMuted,
                                                    fontSize = VegaText.Caption,
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

                // BÖLÜMLER BAŞLIĞI
                item(key = "episodes_heading") {
                    val resumeInSeason = resumeEpisode?.takeIf { it.season.toString() == selectedSeason }
                    val sub = if (resumeInSeason != null) {
                        "Sezon $selectedSeason · Kaldığın bölüm: Bölüm ${resumeInSeason.episodeNum}"
                    } else {
                        "Sezon $selectedSeason"
                    }
                    StreamVaultDetailHeading(
                        title = "Bölümler (${episodesForSeason.size})",
                        subtitle = sub
                    )
                }

                if (seriesInfoResponse == null && detailError != null) {
                    item(key = "episodes_error") {
                        SeriesDetailStatusBlock(
                            title = "Dizi bilgileri yüklenemedi",
                            subtitle = "Bağlantını kontrol edip tekrar dene.",
                            isError = true,
                            onRetry = { viewModel.loadSeriesDetail(series) }
                        )
                    }
                } else if (seriesInfoResponse == null && isDetailLoading) {
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
                } else if (seriesInfoResponse == null) {
                    item(key = "episodes_unavailable") {
                        SeriesDetailStatusBlock(
                            title = "Bölüm listesi alınamadı",
                            subtitle = null,
                            isError = false,
                            onRetry = { viewModel.loadSeriesDetail(series) }
                        )
                    }
                } else if (episodesForSeason.isEmpty()) {
                    item(key = "episodes_empty") {
                        Text(
                            text = "Bu sezon için bölüm bulunamadı.",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Secondary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    itemsIndexed(pagedEpisodes, key = { idx, it -> "ep_${it.id}_${it.episodeNum}_$idx" }) { _, episode ->
                        val epProgress = watchProgressMap[episode.id]
                        val canResumeEpisode = epProgress != null && !epProgress.isWatched && epProgress.positionMs > 10_000L
                        TvEpisodeRowCard(
                            episode = episode,
                            fallbackCover = coverUrl,
                            watchProgress = epProgress,
                            isResumeTarget = resumeEpisode?.id == episode.id,
                            onClick = { viewModel.playSeriesEpisode(episode, series.name, resume = canResumeEpisode) }
                        )
                    }

                    if (episodesForSeason.size > visibleEpisodeLimit) {
                        val remainingCount = episodesForSeason.size - visibleEpisodeLimit
                        item(key = "episodes_load_more_$visibleEpisodeLimit") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                VegaButton(
                                    text = "Daha fazla bölüm göster ($remainingCount)",
                                    variant = VegaButtonVariant.SECONDARY,
                                    icon = Icons.Default.ExpandMore,
                                    onClick = { visibleEpisodeLimit += 60 }
                                )
                            }
                        }
                    }
                }

                item(key = "series_bottom_space") {
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun SeriesDetailStatusBlock(
    title: String,
    subtitle: String?,
    isError: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(VegaShapes.Large)
            .background(VegaColors.SurfaceElevated.copy(alpha = 0.6f))
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isError) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = VegaColors.Danger,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = title,
            color = VegaColors.TextPrimary,
            fontSize = VegaText.SectionTitle,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = VegaColors.TextSecondary,
                fontSize = VegaText.Secondary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        VegaButton(
            text = "Tekrar dene",
            variant = VegaButtonVariant.PRIMARY,
            icon = Icons.Default.Refresh,
            onClick = onRetry
        )
    }
}

/**
 * Mobil için çerçevesiz / ince çizgili bölüm satırı:
 * - Kompakt 16:9 görsel + hafif yarı saydam oynat rozeti + altta ince ilerleme çizgisi
 * - B1 · Bölüm Adı, süre, durum ve kısa özet
 */
@Composable
private fun MobileEpisodeRow(
    episode: SeriesEpisode,
    fallbackCover: String?,
    watchProgress: WatchProgress?,
    isResumeTarget: Boolean,
    onClick: () -> Unit
) {
    val thumbUrl = episode.info?.movieImage?.takeIf { it.isNotBlank() } ?: fallbackCover
    val isWatched = watchProgress?.isWatched == true
    val hasProgress = watchProgress != null && watchProgress.durationMs > 0L && watchProgress.positionMs > 10_000L && !isWatched
    val progressFraction = if (hasProgress) {
        (watchProgress!!.positionMs.toFloat() / watchProgress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else if (isWatched) {
        1f
    } else 0f

    val epCode = "B${episode.episodeNum}"
    val rawTitle = episode.title.trim()
    val fullCode = "S${episode.season.toString().padStart(2, '0')}B${episode.episodeNum.toString().padStart(2, '0')}"
    val cleanTitle = rawTitle.takeIf {
        it.isNotBlank() && !it.equals(epCode, ignoreCase = true) && !it.equals(fullCode, ignoreCase = true)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Small,
                scale = 1.01f,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 16:9 küçük önizleme görseli + yarı saydam oynat simgesi + alt ilerleme çizgisi
        Box(
            modifier = Modifier
                .width(126.dp)
                .aspectRatio(16f / 9f)
                .clip(VegaShapes.Small)
                .background(VegaColors.SurfaceElevated)
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
                    .size(28.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.56f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (progressFraction > 0.01f) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = if (isWatched) VegaColors.Success else VegaColors.Accent,
                    trackColor = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.5.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (cleanTitle != null) "$epCode · $cleanTitle" else "Bölüm ${episode.episodeNum}",
                color = VegaColors.TextPrimary,
                fontSize = VegaText.Body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val duration = episode.info?.duration?.takeIf { it.isNotBlank() }
            val statusText = when {
                hasProgress -> {
                    val mins = watchProgress!!.positionMs / 60000L
                    val secs = (watchProgress.positionMs % 60000L) / 1000L
                    "Devam et · ${String.format("%02d:%02d", mins, secs)}"
                }
                isWatched -> "İzlendi"
                isResumeTarget -> "Sıradaki"
                else -> null
            }
            val statusColor = when {
                isWatched -> VegaColors.Success
                hasProgress || isResumeTarget -> VegaColors.Accent
                else -> VegaColors.TextMuted
            }

            if (duration != null || statusText != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (duration != null) {
                        Text(
                            text = duration,
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (duration != null && statusText != null) {
                        Text(text = "·", color = VegaColors.TextMuted, fontSize = VegaText.Caption)
                    }
                    if (statusText != null) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            episode.info?.plot?.takeIf { it.isNotBlank() }?.let { epPlot ->
                Text(
                    text = epPlot,
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Secondary,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * TV / Geniş Ekran için dingin tonal bölüm satırı:
 * - Odaklanmadığında çerçevesiz yumuşak tonal yüzey; odaklandığında parlak FocusBorder ve sağda oynat ikonu
 */
@Composable
private fun TvEpisodeRowCard(
    episode: SeriesEpisode,
    fallbackCover: String?,
    watchProgress: WatchProgress?,
    isResumeTarget: Boolean,
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
        color = when {
            isFocused -> VegaColors.SurfaceVariant
            isResumeTarget -> VegaColors.Accent.copy(alpha = 0.09f)
            else -> VegaColors.SurfaceElevated.copy(alpha = 0.52f)
        },
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
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(172.dp)
                    .aspectRatio(16f / 9f)
                    .clip(VegaShapes.Medium)
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
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Bölüm ${episode.episodeNum}",
                        color = Color.White,
                        fontSize = VegaText.Caption,
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
                            .height(3.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val epCode = "S${episode.season.toString().padStart(2, '0')}B${episode.episodeNum.toString().padStart(2, '0')}"
                val cleanTitle = episode.title.trim().takeIf { it.isNotBlank() && !it.equals(epCode, ignoreCase = true) }
                Text(
                    text = if (cleanTitle != null) "$epCode · $cleanTitle" else epCode,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.BodyProminent,
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
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (hasProgress) {
                        val mins = watchProgress!!.positionMs / 60000L
                        val secs = (watchProgress.positionMs % 60000L) / 1000L
                        Text(
                            text = "Devam et · ${String.format("%02d:%02d", mins, secs)}",
                            color = VegaColors.Accent,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isWatched) {
                        Text(
                            text = "İzlendi",
                            color = VegaColors.Success,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (isResumeTarget) {
                        Text(
                            text = "Sıradaki",
                            color = VegaColors.Accent,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                episode.info?.plot?.takeIf { it.isNotBlank() }?.let { epPlot ->
                    Text(
                        text = epPlot,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Secondary,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isFocused || isResumeTarget) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFocused) VegaColors.Accent else VegaColors.Accent.copy(alpha = 0.20f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Oynat",
                        tint = if (isFocused) VegaColors.OnAccent else VegaColors.Accent,
                        modifier = Modifier.size(22.dp)
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
