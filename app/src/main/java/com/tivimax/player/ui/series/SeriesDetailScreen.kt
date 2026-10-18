package com.tivimax.player.ui.series

import android.content.res.Configuration
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.tivimax.player.data.model.SeriesEpisode
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
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

    var selectedSeason by remember(seasonKeys) {
        mutableStateOf(seasonKeys.firstOrNull() ?: "1")
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
        // Arka Plan Afiş Resmi (Backdrop - Netflix Ambient Blur)
        AsyncShimmerImage(
            model = info?.cover ?: series.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholderIcon = Icons.Default.VideoLibrary,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (isWideScreen) 1f else 0.55f)
                .then(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Modifier.blur(22.dp)
                    } else {
                        Modifier
                    }
                )
        )

        // Gradient Karartma (Netflix Sinematik Fade to OLED Black)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isWideScreen) {
                        Brush.horizontalGradient(
                            colors = listOf(
                                VegaColors.Background.copy(alpha = 0.95f),
                                VegaColors.Background.copy(alpha = 0.88f),
                                VegaColors.Background.copy(alpha = 0.70f),
                                VegaColors.Background.copy(alpha = 0.90f)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                VegaColors.Background.copy(alpha = 0.4f),
                                VegaColors.Background.copy(alpha = 0.85f),
                                VegaColors.Background
                            )
                        )
                    }
                )
        )

        // Geri Butonu (D-Pad Odaklanabilir)
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .size(44.dp)
                .zIndex(10f)
                .dpadFocusable(
                    focusedBorderColor = VegaColors.Accent,
                    shape = CircleShape,
                    scale = 1.08f,
                    onClick = onBack
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // ==========================================
        // İÇERİK DÜZENİ (YATAY/TV: 2 SÜTUN, DİKEY: TEK SÜTUN)
        // ==========================================
        if (isWideScreen) {
            // YATAY MOD / ANDROID TV: 2 SÜTUNLU SİNEMATİK DÜZEN
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 76.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // SOL SÜTUN (Afiş, Butonlar & Özet)
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(VegaShapes.Large)
                            .background(VegaColors.Card)
                            .border(1.dp, VegaColors.Border, VegaShapes.Large)
                    ) {
                        AsyncShimmerImage(
                            model = info?.cover ?: series.cover,
                            contentDescription = info?.name ?: series.name,
                            contentScale = ContentScale.Crop,
                            placeholderIcon = Icons.Default.VideoLibrary,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Oynat Butonu
                    if (episodesForSeason.isNotEmpty()) {
                        val nextEp = episodesForSeason.first()
                        val nextEpProgress = watchProgressMap[nextEp.id]
                        val hasNextProgress = nextEpProgress != null && nextEpProgress.positionMs > 10_000L && nextEpProgress.durationMs > 0
                        Button(
                            onClick = { viewModel.playSeriesEpisode(nextEp, series.name, resume = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                            shape = VegaShapes.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .focusRequester(playButtonFocusRequester)
                                .dpadFocusable(
                                    focusedBorderColor = Color.White,
                                    shape = VegaShapes.Medium,
                                    onClick = { viewModel.playSeriesEpisode(nextEp, series.name, resume = true) }
                                )
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasNextProgress) {
                                    val mins = (nextEpProgress.positionMs / 60000)
                                    val secs = ((nextEpProgress.positionMs % 60000) / 1000)
                                    "Devam et (${String.format("%02d:%02d", mins, secs)})"
                                } else {
                                    "Sezon $selectedSeason, ${nextEp.episodeNum}. bölümü başlat"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Favori Butonu
                    OutlinedButton(
                        onClick = { viewModel.toggleFavoriteSeries(series.seriesId.toString()) },
                        shape = VegaShapes.Medium,
                        border = BorderStroke(1.dp, VegaColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .dpadFocusable(
                                focusedBorderColor = VegaColors.Accent,
                                shape = VegaShapes.Medium,
                                onClick = { viewModel.toggleFavoriteSeries(series.seriesId.toString()) }
                            )
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (isFavorite) VegaColors.Gold else VegaColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFavorite) "Listemden kaldır" else "Listeme ekle",
                            color = VegaColors.TextPrimary,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Kaydırılabilir Konu Özeti
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val plot = info?.plot ?: series.plot
                        if (!plot.isNullOrEmpty()) {
                            Text(text = "Dizi Konusu", color = VegaColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = plot, color = VegaColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val cast = info?.cast
                        if (!cast.isNullOrEmpty()) {
                            Text(text = "Oyuncular: $cast", color = VegaColors.TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                // SAĞ SÜTUN (Başlık, Rozetler, Sezon Sekmeleri & Bölüm Listesi)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Dizi Başlığı
                    Text(
                        text = info?.name ?: series.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VegaColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Meta Rozetler
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val rating = info?.rating ?: series.rating
                        if (!rating.isNullOrEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(VegaShapes.Small)
                                    .background(VegaColors.Gold.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "IMDb $rating", color = VegaColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        info?.genre?.let { genre ->
                            if (genre.isNotBlank()) {
                                Text(text = genre, color = VegaColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        if (seasonKeys.isNotEmpty()) {
                            Text(text = "• ${seasonKeys.size} Sezon", color = VegaColors.TextSecondary, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sezon Seçici Sekmeler (D-Pad Odaklanabilir)
                    if (seasonKeys.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(seasonKeys) { seasonNum ->
                                val isSelected = seasonNum == selectedSeason
                                Surface(
                                    shape = VegaShapes.Medium,
                                    color = if (isSelected) VegaColors.Accent.copy(alpha = 0.2f) else VegaColors.Card,
                                    border = BorderStroke(1.dp, if (isSelected) VegaColors.Accent else VegaColors.Border),
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
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
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
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(episodesForSeason, key = { it.id }) { episode ->
                                EpisodeCard(
                                    episode = episode,
                                    watchProgress = watchProgressMap[episode.id],
                                    onEpisodeClick = { viewModel.playSeriesEpisode(episode, series.name, resume = true) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // DİKEY MOD (Telefon / Tablet Dikey)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(180.dp))

                    Text(
                        text = info?.name ?: series.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VegaColors.TextPrimary,
                        lineHeight = 28.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val rating = info?.rating ?: series.rating
                        if (!rating.isNullOrEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(VegaShapes.Small)
                                    .background(VegaColors.Gold.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "IMDb $rating", color = VegaColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        info?.genre?.let { genre ->
                            if (genre.isNotBlank()) {
                                Text(text = genre, color = VegaColors.Accent, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val plot = info?.plot ?: series.plot
                    if (!plot.isNullOrEmpty()) {
                        Text(text = plot, color = VegaColors.TextSecondary, fontSize = 14.sp, lineHeight = 22.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 1. Bölümü Oynat Butonu
                    if (episodesForSeason.isNotEmpty()) {
                        val nextEp = episodesForSeason.first()
                        val nextEpProgress = watchProgressMap[nextEp.id]
                        val hasNextProgress = nextEpProgress != null && nextEpProgress.positionMs > 10_000L && nextEpProgress.durationMs > 0
                        Button(
                            onClick = { viewModel.playSeriesEpisode(nextEp, series.name, resume = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                            shape = VegaShapes.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .focusRequester(playButtonFocusRequester)
                                .dpadFocusable(
                                    focusedBorderColor = Color.White,
                                    shape = VegaShapes.Medium,
                                    onClick = { viewModel.playSeriesEpisode(nextEp, series.name, resume = true) }
                                )
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasNextProgress) {
                                    val mins = (nextEpProgress.positionMs / 60000)
                                    val secs = ((nextEpProgress.positionMs % 60000) / 1000)
                                    "Devam et (${String.format("%02d:%02d", mins, secs)})"
                                } else {
                                    "Sezon $selectedSeason, ${nextEp.episodeNum}. bölümü başlat"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    OutlinedButton(
                        onClick = { viewModel.toggleFavoriteSeries(series.seriesId.toString()) },
                        shape = VegaShapes.Medium,
                        border = BorderStroke(1.dp, VegaColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .dpadFocusable(
                                focusedBorderColor = VegaColors.Accent,
                                shape = VegaShapes.Medium,
                                onClick = { viewModel.toggleFavoriteSeries(series.seriesId.toString()) }
                            )
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (isFavorite) VegaColors.Gold else VegaColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isFavorite) "Listemden kaldır" else "Listeme ekle",
                            color = VegaColors.TextPrimary,
                            fontSize = 13.sp
                        )
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
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(seasonKeys) { seasonNum ->
                                val isSelected = seasonNum == selectedSeason
                                Surface(
                                    shape = VegaShapes.Medium,
                                    color = if (isSelected) VegaColors.Accent.copy(alpha = 0.2f) else VegaColors.Card,
                                    border = BorderStroke(1.dp, if (isSelected) VegaColors.Accent else VegaColors.Border),
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

@Composable
fun EpisodeCard(
    episode: SeriesEpisode,
    watchProgress: WatchProgress? = null,
    onEpisodeClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
        shape = VegaShapes.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                onClick = onEpisodeClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bölüm Görseli / İkonu
            Box(
                modifier = Modifier
                    .size(width = 84.dp, height = 54.dp)
                    .clip(VegaShapes.Small)
                    .background(VegaColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                if (!episode.info?.movieImage.isNullOrEmpty()) {
                    AsyncImage(
                        model = episode.info?.movieImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Oynat",
                    tint = VegaColors.Accent,
                    modifier = Modifier.size(26.dp)
                )

                // Kaldığı Yer İlerleme Çizgisi
                if (watchProgress != null && watchProgress.durationMs > 0 && watchProgress.positionMs > 10_000L) {
                    val progressFraction = (watchProgress.positionMs.toFloat() / watchProgress.durationMs.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(VegaColors.Gold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${episode.episodeNum}. Bölüm: ${episode.title.ifEmpty { "Bölüm ${episode.episodeNum}" }}",
                    color = VegaColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    episode.info?.duration?.let { dur ->
                        Text(text = dur, color = VegaColors.TextSecondary, fontSize = 11.sp)
                    }
                    if (watchProgress != null && watchProgress.durationMs > 0 && watchProgress.positionMs > 10_000L) {
                        if (episode.info?.duration != null) {
                            Text(text = " • ", color = VegaColors.TextSecondary, fontSize = 11.sp)
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
            }
        }
    }
}
