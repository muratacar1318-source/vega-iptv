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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
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
import kotlinx.coroutines.delay

@Composable
fun SeriesDetailScreen(
    series: SeriesStream,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    val seriesInfoResponse by viewModel.currentSeriesInfo.collectAsState()
    val episodesMap = seriesInfoResponse?.episodes ?: emptyMap()
    val watchProgressMap by viewModel.watchProgressMap.collectAsState()

    val favoriteSeriesIds by viewModel.favoriteSeriesIds.collectAsState()
    val isFavorite = favoriteSeriesIds.contains(series.seriesId.toString())

    val playButtonFocusRequester = remember { FocusRequester() }

    // Mevcut Sezonlar
    val seasonKeys = remember(episodesMap) {
        episodesMap.keys.sortedBy { it.toIntOrNull() ?: 1 }
    }

    // Toplam Bölüm Sayısı
    val totalEpisodeCount = remember(episodesMap) {
        episodesMap.values.sumOf { it.size }
    }

    // G-047: "Oynat" kaldığın bölümü açsın — en son izlenen bölüm; o bölüm bitmişse sıradaki bölüm
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

    // G-047: Oynatıcıdan dönünce aynı sezon açık kalsın (son seçilen sezon hatırlanır;
    // hiç seçilmediyse kaldığın bölümün sezonu açılır)
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

    // Dizi Bölümü Ön Isıtma (Seçili sezonun 1. bölümü, sezon değişince veya çıkınca iptal et)
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

    val info = seriesInfoResponse?.info
    val seriesTitle = info?.name ?: series.name
    val backdropUrl = series.backdropPath?.firstOrNull()
        ?: info?.backdropPath?.firstOrNull()
        ?: info?.cover
        ?: series.cover
    val coverUrl = info?.cover ?: series.cover

    val qualityBadges = remember(series.name, info?.name) {
        val detected = extractQualityBadges(series.name + " " + (info?.name ?: ""))
        if (detected.isEmpty()) listOf("FHD") else detected
    }

    val genreList = remember(info?.genre, series.genre) {
        val raw = info?.genre?.takeIf { it.isNotBlank() } ?: series.genre
        raw?.split(',', '/', '•')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    val castList = remember(info?.cast, series.cast) {
        val raw = info?.cast?.takeIf { it.isNotBlank() } ?: series.cast
        raw?.split(',', ';')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    val trailerUrl = series.youtubeTrailer

    // Detay ekranı açıldığı anda otomatik olarak "1. Bölümü Oynat / Devam Et" butonuna focus ver
    LaunchedEffect(Unit) {
        delay(180)
        try {
            playButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VegaColors.Background)
    ) {
        // ==========================================
        // 1. SİNEMATİK ARKA PLAN (VIP BACKDROP & GRADIENT BLEED)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (isWideScreen) 1f else 0.58f)
                .align(Alignment.TopEnd)
        ) {
            AsyncShimmerImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholderIcon = Icons.Default.VideoLibrary,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            Modifier.blur(14.dp)
                        } else {
                            Modifier
                        }
                    )
            )

            // Sinematik Karartma Maskeleri
            if (isWideScreen) {
                // Yatay / TV: Soldan sağa derin karartma (sol sütun tamamen siyah, sağda görsel görünür)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.0f to VegaColors.Background,
                                    0.40f to VegaColors.Background,
                                    0.60f to VegaColors.Background.copy(alpha = 0.90f),
                                    0.80f to VegaColors.Background.copy(alpha = 0.50f),
                                    1.0f to VegaColors.Background.copy(alpha = 0.70f)
                                )
                            )
                        )
                )
                // Alttan yukarı dikey geçiş
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.65f to Color.Transparent,
                                    1.0f to VegaColors.Background
                                )
                            )
                        )
                )
            } else {
                // Dikey Mod: Üstten alta derin karartma
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.35f to VegaColors.Background.copy(alpha = 0.40f),
                                    0.75f to VegaColors.Background.copy(alpha = 0.92f),
                                    1.0f to VegaColors.Background
                                )
                            )
                        )
                )
            }
        }

        // ==========================================
        // 2. GERİ DÖNÜŞ BUTONU
        // ==========================================
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = VegaColors.OverlayPill,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = if (isWideScreen) 28.dp else 16.dp, top = 16.dp)
                .size(44.dp)
                .zIndex(10f)
                .dpadFocusable(
                    focusedBorderColor = VegaColors.Accent,
                    shape = CircleShape,
                    scale = 1.08f
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ==========================================
        // 3. İÇERİK DÜZENİ (YATAY/TV: 2 SÜTUN, DİKEY: AKICI SAYFA)
        // ==========================================
        if (isWideScreen) {
            // YATAY MOD / ANDROID TV: 2 SÜTUNLU SİNEMATİK DÜZEN
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 90.dp, end = 32.dp, top = 28.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp)
            ) {
                // SOL SÜTUN (Afiş, Butonlar, Konu Özeti & Oyuncular)
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Dizi Afiş Kartı (16:9 Sinematik Kart)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                            .clip(VegaShapes.Large)
                            .background(VegaColors.Card)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)
                                    )
                                ),
                                VegaShapes.Large
                            )
                    ) {
                        AsyncShimmerImage(
                            model = coverUrl,
                            contentDescription = seriesTitle,
                            contentScale = ContentScale.Crop,
                            placeholderIcon = Icons.Default.VideoLibrary,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Oynat / Kaldığın Yerden Devam Et Butonu
                    if (episodesForSeason.isNotEmpty()) {
                        val nextEp = resumeEpisode ?: episodesForSeason.first() // G-047
                        val nextEpProgress = watchProgressMap[nextEp.id]
                        val hasNextProgress = nextEpProgress != null && nextEpProgress.positionMs > 10_000L && nextEpProgress.durationMs > 0

                        Button(
                            onClick = { viewModel.playSeriesEpisode(nextEp, series.name, resume = true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VegaColors.Accent,
                                contentColor = VegaColors.OnAccent
                            ),
                            shape = VegaShapes.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .focusRequester(playButtonFocusRequester)
                                .dpadFocusable(
                                    focusedBorderColor = Color.White,
                                    shape = VegaShapes.Medium
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = VegaColors.OnAccent
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasNextProgress) {
                                    val mins = (nextEpProgress.positionMs / 60000)
                                    val secs = ((nextEpProgress.positionMs % 60000) / 1000)
                                    "Devam et (S${nextEp.season}:B${nextEp.episodeNum} • ${String.format("%02d:%02d", mins, secs)})"
                                } else {
                                    "Sezon ${nextEp.season}, ${nextEp.episodeNum}. bölümü başlat"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Favori ve Fragman Butonları Satırı
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.toggleFavoriteSeries(series.seriesId.toString()) },
                            shape = VegaShapes.Medium,
                            border = BorderStroke(
                                1.dp,
                                if (isFavorite) VegaColors.Gold.copy(alpha = 0.5f) else VegaColors.Border
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isFavorite) VegaColors.Gold.copy(alpha = 0.12f) else VegaColors.SurfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .dpadFocusable(
                                    focusedBorderColor = VegaColors.Accent,
                                    shape = VegaShapes.Medium
                                )
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                tint = if (isFavorite) VegaColors.Gold else VegaColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isFavorite) "Listede" else "Listeme ekle",
                                color = if (isFavorite) VegaColors.Gold else VegaColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isFavorite) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        if (!trailerUrl.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { openTrailer(context, trailerUrl) },
                                shape = VegaShapes.Medium,
                                border = BorderStroke(1.dp, VegaColors.Border),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = VegaColors.SurfaceVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .dpadFocusable(
                                        focusedBorderColor = VegaColors.Accent,
                                        shape = VegaShapes.Medium
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleOutline,
                                    contentDescription = null,
                                    tint = VegaColors.TextSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Fragman",
                                    color = VegaColors.TextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Kaydırılabilir Konu Özeti & Oyuncular
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val plot = info?.plot ?: series.plot
                        if (!plot.isNullOrEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(13.dp)
                                        .clip(VegaShapes.Pill)
                                        .background(VegaColors.Accent)
                                )
                                Text(
                                    text = "Dizi Konusu",
                                    color = VegaColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = plot,
                                color = VegaColors.TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        info?.director?.takeIf { it.isNotBlank() }?.let { directorName ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MovieFilter,
                                    contentDescription = null,
                                    tint = VegaColors.TextMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Yönetmen: $directorName",
                                    color = VegaColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (castList.isNotEmpty()) {
                            Text(
                                text = "Oyuncular",
                                color = VegaColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(castList.take(8)) { actor ->
                                    Surface(
                                        shape = VegaShapes.Pill,
                                        color = VegaColors.SurfaceVariant,
                                        border = BorderStroke(1.dp, VegaColors.Border),
                                        modifier = Modifier.dpadFocusable(
                                            focusedBorderColor = VegaColors.Accent,
                                            shape = VegaShapes.Pill
                                        )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = VegaColors.TextMuted,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = actor,
                                                color = VegaColors.TextPrimary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SAĞ SÜTUN (Başlık, Rozetler, Sezon Sekmeleri & 16:9 Bölüm Kartları Listesi)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Dizi Başlığı
                    Text(
                        text = seriesTitle,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VegaColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Meta Rozetler Şeridi
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = VegaShapes.Small,
                            color = VegaColors.Accent.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "DİZİ",
                                color = VegaColors.Accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        val rating = info?.rating ?: series.rating
                        if (!rating.isNullOrEmpty()) {
                            Surface(
                                shape = VegaShapes.Small,
                                color = VegaColors.Gold.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, VegaColors.Gold.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = VegaColors.Gold,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "IMDb $rating",
                                        color = VegaColors.Gold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        qualityBadges.forEach { badge ->
                            QualityBadge(tag = badge)
                        }

                        if (seasonKeys.isNotEmpty()) {
                            Surface(
                                shape = VegaShapes.Small,
                                color = VegaColors.SurfaceVariant,
                                border = BorderStroke(1.dp, VegaColors.Border)
                            ) {
                                Text(
                                    text = if (totalEpisodeCount > 0) "${seasonKeys.size} Sezon • $totalEpisodeCount Bölüm" else "${seasonKeys.size} Sezon",
                                    color = VegaColors.TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        genreList.take(2).forEach { genre ->
                            Surface(
                                shape = VegaShapes.Pill,
                                color = VegaColors.SurfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, VegaColors.Border)
                            ) {
                                Text(
                                    text = genre,
                                    color = VegaColors.TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sezon Seçici Sekmeler (D-Pad Odaklanabilir & focusRestorer)
                    if (seasonKeys.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRestorer() // G-047
                        ) {
                            items(seasonKeys) { seasonNum ->
                                val isSelected = seasonNum == selectedSeason
                                Surface(
                                    shape = VegaShapes.Medium,
                                    color = if (isSelected) VegaColors.Accent.copy(alpha = 0.20f) else VegaColors.Card,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) VegaColors.Accent else VegaColors.Border
                                    ),
                                    modifier = Modifier.dpadFocusable(
                                        focusedBorderColor = VegaColors.Accent,
                                        shape = VegaShapes.Medium,
                                        onClick = { selectedSeason = seasonNum }
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(VegaColors.Accent)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = "Sezon $seasonNum",
                                            color = if (isSelected) VegaColors.Accent else VegaColors.TextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bölüm Listesi
                    if (episodesForSeason.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Bölümler yükleniyor...",
                                color = VegaColors.TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .focusRestorer(), // G-047
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(episodesForSeason, key = { it.id }) { episode ->
                                EpisodeCard(
                                    episode = episode,
                                    fallbackImage = backdropUrl,
                                    watchProgress = watchProgressMap[episode.id],
                                    onEpisodeClick = { viewModel.playSeriesEpisode(episode, series.name, resume = true) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ==========================================
            // DİKEY MOD (Telefon / Tablet Dikey)
            // ==========================================
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(190.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = VegaShapes.Small,
                            color = VegaColors.Accent.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "DİZİ",
                                color = VegaColors.Accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        qualityBadges.firstOrNull()?.let { badge ->
                            QualityBadge(tag = badge)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = seriesTitle,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VegaColors.TextPrimary,
                        lineHeight = 28.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val rating = info?.rating ?: series.rating
                        if (!rating.isNullOrEmpty()) {
                            Surface(
                                shape = VegaShapes.Small,
                                color = VegaColors.Gold.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, VegaColors.Gold.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = VegaColors.Gold,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "IMDb $rating",
                                        color = VegaColors.Gold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (seasonKeys.isNotEmpty()) {
                            Text(
                                text = "${seasonKeys.size} Sezon",
                                color = VegaColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (genreList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            genreList.take(3).forEach { genre ->
                                Surface(
                                    shape = VegaShapes.Pill,
                                    color = VegaColors.SurfaceVariant,
                                    border = BorderStroke(1.dp, VegaColors.Border)
                                ) {
                                    Text(
                                        text = genre,
                                        color = VegaColors.TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val plot = info?.plot ?: series.plot
                    if (!plot.isNullOrEmpty()) {
                        Text(
                            text = plot,
                            color = VegaColors.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 1. Bölümü Oynat Butonu
                    if (episodesForSeason.isNotEmpty()) {
                        val nextEp = resumeEpisode ?: episodesForSeason.first() // G-047
                        val nextEpProgress = watchProgressMap[nextEp.id]
                        val hasNextProgress = nextEpProgress != null && nextEpProgress.positionMs > 10_000L && nextEpProgress.durationMs > 0
                        Button(
                            onClick = { viewModel.playSeriesEpisode(nextEp, series.name, resume = true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VegaColors.Accent,
                                contentColor = VegaColors.OnAccent
                            ),
                            shape = VegaShapes.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .focusRequester(playButtonFocusRequester)
                                .dpadFocusable(
                                    focusedBorderColor = Color.White,
                                    shape = VegaShapes.Medium
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = VegaColors.OnAccent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasNextProgress) {
                                    val mins = (nextEpProgress.positionMs / 60000)
                                    val secs = ((nextEpProgress.positionMs % 60000) / 1000)
                                    "Devam et (S${nextEp.season}:B${nextEp.episodeNum} • ${String.format("%02d:%02d", mins, secs)})"
                                } else {
                                    "Sezon ${nextEp.season}, ${nextEp.episodeNum}. bölümü başlat"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.toggleFavoriteSeries(series.seriesId.toString()) },
                            shape = VegaShapes.Medium,
                            border = BorderStroke(
                                1.dp,
                                if (isFavorite) VegaColors.Gold.copy(alpha = 0.5f) else VegaColors.Border
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isFavorite) VegaColors.Gold.copy(alpha = 0.12f) else VegaColors.SurfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .dpadFocusable(
                                    focusedBorderColor = VegaColors.Accent,
                                    shape = VegaShapes.Medium
                                )
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                tint = if (isFavorite) VegaColors.Gold else VegaColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isFavorite) "Listede" else "Listeme ekle",
                                color = if (isFavorite) VegaColors.Gold else VegaColors.TextPrimary,
                                fontSize = 13.sp
                            )
                        }

                        if (!trailerUrl.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { openTrailer(context, trailerUrl) },
                                shape = VegaShapes.Medium,
                                border = BorderStroke(1.dp, VegaColors.Border),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = VegaColors.SurfaceVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .dpadFocusable(
                                        focusedBorderColor = VegaColors.Accent,
                                        shape = VegaShapes.Medium
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleOutline,
                                    contentDescription = null,
                                    tint = VegaColors.TextSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Fragman",
                                    color = VegaColors.TextPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Bölümler",
                        color = VegaColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (seasonKeys.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRestorer() // G-047
                        ) {
                            items(seasonKeys) { seasonNum ->
                                val isSelected = seasonNum == selectedSeason
                                Surface(
                                    shape = VegaShapes.Medium,
                                    color = if (isSelected) VegaColors.Accent.copy(alpha = 0.20f) else VegaColors.Card,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) VegaColors.Accent else VegaColors.Border
                                    ),
                                    modifier = Modifier.dpadFocusable(
                                        focusedBorderColor = VegaColors.Accent,
                                        shape = VegaShapes.Medium,
                                        onClick = { selectedSeason = seasonNum }
                                    )
                                ) {
                                    Text(
                                        text = "Sezon $seasonNum",
                                        color = if (isSelected) VegaColors.Accent else VegaColors.TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (episodesForSeason.isEmpty()) {
                    item {
                        Text(
                            text = "Bölümler yükleniyor...",
                            color = VegaColors.TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(episodesForSeason, key = { it.id }) { episode ->
                        EpisodeCard(
                            episode = episode,
                            fallbackImage = backdropUrl,
                            watchProgress = watchProgressMap[episode.id],
                            onEpisodeClick = { viewModel.playSeriesEpisode(episode, series.name, resume = true) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

/**
 * 16:9 Sinematik Bölüm Kartı
 */
@Composable
fun EpisodeCard(
    episode: SeriesEpisode,
    fallbackImage: String? = null,
    watchProgress: WatchProgress? = null,
    onEpisodeClick: () -> Unit
) {
    Surface(
        onClick = onEpisodeClick,
        shape = VegaShapes.Medium,
        color = VegaColors.Card,
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                scale = 1.02f
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 16:9 Bölüm Görseli / Önizlemesi
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 68.dp)
                    .clip(VegaShapes.Small)
                    .background(VegaColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = episode.info?.movieImage?.takeIf { it.isNotBlank() } ?: fallbackImage
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Karartma Maskesi
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )

                // Ortalanmış Oynatma İkonu
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.60f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Oynat",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Sol Üst: Bölüm Numarası Rozeti
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(VegaShapes.Small)
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "B.${episode.episodeNum}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Kaldığı Yer İlerleme Çizgisi (Thumbnail Altı)
                if (watchProgress != null && watchProgress.durationMs > 0 && watchProgress.positionMs > 10_000L) {
                    val progressFraction = (watchProgress.positionMs.toFloat() / watchProgress.durationMs.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(VegaColors.Accent)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Bölüm Bilgileri
            Column(modifier = Modifier.weight(1f)) {
                val epTitle = episode.title.ifEmpty { "Bölüm ${episode.episodeNum}" }
                Text(
                    text = "${episode.episodeNum}. $epTitle",
                    color = VegaColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    episode.info?.duration?.let { dur ->
                        Text(
                            text = dur,
                            color = VegaColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (watchProgress != null && watchProgress.durationMs > 0 && watchProgress.positionMs > 10_000L) {
                        if (episode.info?.duration != null) {
                            Text(text = " • ", color = VegaColors.TextMuted, fontSize = 11.sp)
                        }
                        val mins = (watchProgress.positionMs / 60000)
                        val secs = ((watchProgress.positionMs % 60000) / 1000)
                        Text(
                            text = "Kaldığın yer: ${String.format("%02d:%02d", mins, secs)}",
                            color = VegaColors.Gold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Bölüm Konusu Özeti (Varsa 2 satır)
                episode.info?.plot?.takeIf { it.isNotBlank() }?.let { epPlot ->
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = epPlot,
                        color = VegaColors.TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
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