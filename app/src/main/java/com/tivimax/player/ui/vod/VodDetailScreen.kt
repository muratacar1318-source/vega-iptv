package com.tivimax.player.ui.vod

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.extractQualityBadges
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.vegaBackdrop
import kotlinx.coroutines.delay

/**
 * STREAMVAULT FİLM DETAY EKRANI (MovieInfo.png + CatalogDetailVisuals.kt görsel dili)
 * - Tam genişlikte net sinematik arka plan görseli (üstte temiz, aşağıda derin gece lacivertine geçiş)
 * - Sol üst "Kütüphaneye dön" hapı
 * - Solda 2:3 afiş, sağda [Filmler] + [★ 8.2/10] rozetleri, büyük başlık, noktalı meta şerit,
 *   tek odak/tıklama garantili StreamVault aksiyon butonları ve özet
 * - Altta yarı saydam yuvarlatılmış StreamVault bilgi kartı (Yönetmen, Tür, Süre, Oyuncular)
 */
@Composable
fun VodDetailScreen(
    movie: VodStream,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    val vodDetail by viewModel.currentVodDetail.collectAsState()
    val favoriteVodIds by viewModel.favoriteVodIds.collectAsState()
    val isFavorite = favoriteVodIds.contains(movie.streamId.toString())

    var watchProgress by remember(movie.streamId) { mutableStateOf<WatchProgress?>(null) }
    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(movie.streamId) {
        watchProgress = viewModel.preferences.getWatchProgress(movie.streamId)
    }

    // Akıllı ön ısıtma
    DisposableEffect(movie.streamId) {
        if (movie.streamId !in listOf(101, 102, 103, 201)) {
            val urls = viewModel.repository.buildVodStreamUrls(movie.streamId, movie.containerExtension)
            val firstUrl = urls.firstOrNull()
            if (!firstUrl.isNullOrBlank()) {
                viewModel.vodPrewarmer.schedulePrewarm(firstUrl, movie.streamId)
            }
        }
        onDispose {
            viewModel.vodPrewarmer.cancelAll("ekrandan çıkıldı")
        }
    }

    val info = vodDetail?.info
    val movieTitle = info?.name?.takeIf { it.isNotBlank() } ?: movie.name
    val backdropUrl = info?.movieImage ?: info?.coverBig ?: movie.streamIcon
    val posterUrl = info?.coverBig ?: info?.movieImage ?: movie.streamIcon

    val qualityBadges = remember(movie.name, info?.name) {
        extractQualityBadges(movie.name + " " + (info?.name ?: ""))
    }
    val durationText = remember(info?.duration, info?.durationMinutes, info?.durationSecs) {
        formatVodDuration(info?.duration, info?.durationMinutes, info?.durationSecs)
    }
    val releaseDate = info?.releaseDate?.takeIf { it.isNotBlank() }
    val genreText = info?.genre?.takeIf { it.isNotBlank() }
    val directorText = info?.director?.takeIf { it.isNotBlank() }
    val countryText = info?.country?.takeIf { it.isNotBlank() }
    val castList = remember(info?.cast, info?.actors) {
        val raw = info?.cast?.takeIf { it.isNotBlank() } ?: info?.actors
        raw?.split(',', ';')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }
    val ratingText = remember(info?.rating, movie.rating, movie.rating5Based) {
        val raw = info?.rating ?: movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
        val num = raw?.trim()?.substringBefore("/")?.replace(',', '.')?.toFloatOrNull()
        if (num != null && num > 0f) {
            val f = if (num == num.toInt().toFloat()) "${num.toInt()}" else String.format(java.util.Locale.US, "%.1f", num)
            if (num <= 5f) "$f/5" else "$f/10"
        } else null
    }
    val trailerUrl = info?.youtubeTrailer
    val hasResume = watchProgress != null && watchProgress!!.positionMs > 10_000L

    // 1-Tık Kumanda Garantisi: Ekran açılır açılmaz (ve izleme durumu yüklenince) Oynat / Devam et butonuna güvenli odak
    LaunchedEffect(movie.streamId, hasResume) {
        repeat(3) {
            withFrameNanos { }
            delay(80)
            if (runCatching { playButtonFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    val hasFactInfo = listOf(directorText, releaseDate, durationText, genreText, countryText).any { !it.isNullOrBlank() } || castList.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .vegaBackdrop()
    ) {
        // 1. SİNEMATİK ARKA PLAN GÖRSELİ (MovieInfo.png — üstte net görsel, aşağıda derin gece laciverti)
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
                placeholderIcon = Icons.Default.Movie,
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
                                0.0f to VegaColors.Background.copy(alpha = 0.62f),
                                0.45f to Color.Transparent,
                                1.0f to VegaColors.Background.copy(alpha = 0.25f)
                            )
                        )
                    )
            )
        }

        // 2. KAYDIRILABİLİR İÇERİK
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (isWideScreen) 52.dp else 16.dp,
                    vertical = if (isWideScreen) 28.dp else 14.dp
                ),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // SOL ÜST: StreamVault "Kütüphaneye dön" Hap Butonu (MovieInfo.png)
            StreamVaultBackButton(onClick = onBack)

            if (isWideScreen) {
                // YATAY / TV DÜZENİ (StreamVault MovieInfo.png ile birebir 2 sütun, alt hizalı)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // SOL: 2:3 Afiş Kartı
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(236.dp)
                                .aspectRatio(2f / 3f)
                                .clip(VegaShapes.Large)
                                .background(VegaColors.SurfaceElevated)
                                .border(1.dp, VegaColors.Border, VegaShapes.Large)
                        ) {
                            AsyncShimmerImage(
                                model = posterUrl,
                                contentDescription = movieTitle,
                                contentScale = ContentScale.Crop,
                                placeholderIcon = Icons.Default.Movie,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (hasResume && watchProgress!!.durationMs > 0L) {
                            val frac = (watchProgress!!.positionMs.toFloat() / watchProgress!!.durationMs.toFloat()).coerceIn(0f, 1f)
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { frac },
                                color = VegaColors.Accent,
                                trackColor = VegaColors.SurfaceAccent,
                                modifier = Modifier
                                    .width(216.dp)
                                    .height(4.dp)
                                    .clip(VegaShapes.Pill)
                            )
                        }
                    }

                    // SAĞ: Rozetler, Başlık, Meta, StreamVault Butonları ve Özet
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Rozetler Satırı: [Filmler]  [★ 8.2/10]  [4K]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = VegaShapes.Small,
                                color = VegaColors.SurfaceAccent.copy(alpha = 0.88f),
                                border = BorderStroke(1.dp, VegaColors.Border)
                            ) {
                                Text(
                                    text = "Filmler",
                                    color = VegaColors.TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
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
                                        Text(text = ratingText, color = VegaColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            qualityBadges.forEach { badge -> QualityBadge(tag = badge) }
                        }

                        // Büyük Film Başlığı
                        Text(
                            text = movieTitle,
                            color = VegaColors.TextPrimary,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            lineHeight = 36.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Noktalı Meta Bilgi Şeridi (2026 • 01:21:42 • Bilim Kurgu)
                        val metaValues = listOfNotNull(releaseDate, durationText, genreText)
                        if (metaValues.isNotEmpty()) {
                            StreamVaultMetaStrip(values = metaValues)
                        }

                        // StreamVault Aksiyon Butonları
                        VodActionRow(
                            hasResume = hasResume,
                            resumeTimeStr = if (hasResume) formatDuration(watchProgress!!.positionMs) else null,
                            isFavorite = isFavorite,
                            trailerUrl = trailerUrl,
                            playButtonFocusRequester = playButtonFocusRequester,
                            onPlayResume = { viewModel.playVod(movie, resume = true) },
                            onPlayStart = { viewModel.playVod(movie, resume = false) },
                            onToggleFavorite = { viewModel.toggleFavoriteVod(movie.streamId.toString()) },
                            onOpenTrailer = { openTrailer(context, trailerUrl) }
                        )

                        // Konu Özeti
                        val plot = info?.plot ?: info?.description
                        if (!plot.isNullOrBlank()) {
                            Text(
                                text = plot,
                                color = VegaColors.TextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // StreamVault Yarı Saydam Bilgi Kartı (DetailInfoPanel — Yönetmen, Türler, Oyuncular)
                if (hasFactInfo) {
                    StreamVaultDetailInfoPanel(
                        modifier = Modifier.fillMaxWidth(),
                        spacing = 12.dp
                    ) {
                        StreamVaultDetailHeading(title = "Film bilgileri")
                        Spacer(modifier = Modifier.height(2.dp))
                        directorText?.let { StreamVaultFactRow(label = "Yönetmen", value = it) }
                        releaseDate?.let { StreamVaultFactRow(label = "Yayın tarihi", value = it) }
                        durationText?.let { StreamVaultFactRow(label = "Süre", value = it) }
                        genreText?.let { StreamVaultFactRow(label = "Tür", value = it) }
                        countryText?.let { StreamVaultFactRow(label = "Ülke", value = it) }
                        if (castList.isNotEmpty()) {
                            StreamVaultFactRow(
                                label = "Oyuncular",
                                value = castList.take(10).joinToString(", ")
                            )
                        }
                    }
                }
            } else {
                // DİKEY TELEFON DÜZENİ (StreamVault kompakt)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .width(126.dp)
                            .aspectRatio(2f / 3f)
                            .clip(VegaShapes.Medium)
                            .background(VegaColors.SurfaceElevated)
                            .border(1.dp, VegaColors.Border, VegaShapes.Medium)
                    ) {
                        AsyncShimmerImage(
                            model = posterUrl,
                            contentDescription = movieTitle,
                            contentScale = ContentScale.Crop,
                            placeholderIcon = Icons.Default.Movie,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = VegaShapes.Small,
                                color = VegaColors.SurfaceAccent,
                                border = BorderStroke(1.dp, VegaColors.Border)
                            ) {
                                Text("Filmler", color = VegaColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                            if (ratingText != null) {
                                Surface(
                                    shape = VegaShapes.Small,
                                    color = VegaColors.Gold.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, VegaColors.Gold.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = VegaColors.Gold, modifier = Modifier.size(11.dp))
                                        Text(ratingText, color = VegaColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Text(
                            text = movieTitle,
                            color = VegaColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 24.sp
                        )

                        val metaValues = listOfNotNull(releaseDate, durationText)
                        if (metaValues.isNotEmpty()) {
                            StreamVaultMetaStrip(values = metaValues)
                        }
                    }
                }

                VodActionRow(
                    hasResume = hasResume,
                    resumeTimeStr = if (hasResume) formatDuration(watchProgress!!.positionMs) else null,
                    isFavorite = isFavorite,
                    trailerUrl = trailerUrl,
                    playButtonFocusRequester = playButtonFocusRequester,
                    onPlayResume = { viewModel.playVod(movie, resume = true) },
                    onPlayStart = { viewModel.playVod(movie, resume = false) },
                    onToggleFavorite = { viewModel.toggleFavoriteVod(movie.streamId.toString()) },
                    onOpenTrailer = { openTrailer(context, trailerUrl) }
                )

                val plot = info?.plot ?: info?.description
                if (!plot.isNullOrBlank()) {
                    StreamVaultDetailHeading(title = "Özet")
                    Text(
                        text = plot,
                        color = VegaColors.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                if (hasFactInfo) {
                    StreamVaultDetailInfoPanel(
                        modifier = Modifier.fillMaxWidth(),
                        spacing = 10.dp
                    ) {
                        StreamVaultDetailHeading(title = "Film bilgileri")
                        directorText?.let { StreamVaultFactRow(label = "Yönetmen", value = it) }
                        releaseDate?.let { StreamVaultFactRow(label = "Yayın tarihi", value = it) }
                        durationText?.let { StreamVaultFactRow(label = "Süre", value = it) }
                        genreText?.let { StreamVaultFactRow(label = "Tür", value = it) }
                        countryText?.let { StreamVaultFactRow(label = "Ülke", value = it) }
                        if (castList.isNotEmpty()) {
                            StreamVaultFactRow(
                                label = "Oyuncular",
                                value = castList.take(10).joinToString(", ")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun StreamVaultBackButton(
    onClick: () -> Unit,
    label: String = "Kütüphaneye dön",
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = if (isFocused) VegaColors.TextPrimary else VegaColors.Surface.copy(alpha = 0.78f)
    val contentColor = if (isFocused) VegaColors.Background else VegaColors.TextPrimary

    Surface(
        shape = VegaShapes.Medium,
        color = bgColor,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) VegaColors.FocusBorder else VegaColors.Border
        ),
        modifier = modifier
            .height(42.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Medium,
                scale = 1.04f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun VodActionRow(
    hasResume: Boolean,
    resumeTimeStr: String?,
    isFavorite: Boolean,
    trailerUrl: String?,
    playButtonFocusRequester: FocusRequester,
    onPlayResume: () -> Unit,
    onPlayStart: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenTrailer: () -> Unit
) {
    val actionShape = RoundedCornerShape(11.dp)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        // Sabit key ("act_primary") sayesinde watchProgress gelince odak düğümü silinmez / kaybolmaz
        item(key = "act_primary") {
            val primaryLabel = if (hasResume) {
                if (!resumeTimeStr.isNullOrBlank()) "Devam et ($resumeTimeStr)" else "Devam et"
            } else {
                "Oynat"
            }
            val primaryAction = if (hasResume) onPlayResume else onPlayStart
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
                        onClick = primaryAction
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(20.dp))
                    Text(
                        text = primaryLabel,
                        color = VegaColors.OnAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (hasResume) {
            item(key = "act_restart") {
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
                            onClick = onPlayStart,
                            onFocusStateChange = { isFocused = it }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, tint = VegaColors.TextPrimary, modifier = Modifier.size(18.dp))
                        Text("Baştan izle", color = VegaColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (!trailerUrl.isNullOrBlank()) {
            item(key = "act_trailer") {
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
                            onClick = onOpenTrailer,
                            onFocusStateChange = { isFocused = it }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Fragman", color = VegaColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // StreamVault Yuvarlak Kalp / Favori Düğmesi (MovieInfo.png)
        item(key = "act_fav") {
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
                        onClick = onToggleFavorite,
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
}

/**
 * StreamVault CatalogDetailVisuals.kt — DetailInfoPanel uyarlaması.
 * Yarı saydam yuvarlatılmış bilgi kartı (Yönetmen, Türler, Oyuncular).
 */
@Composable
internal fun StreamVaultDetailInfoPanel(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(VegaShapes.Hero)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        VegaColors.SurfaceElevated.copy(alpha = 0.94f),
                        VegaColors.Surface.copy(alpha = 0.88f)
                    )
                )
            )
            .border(1.dp, VegaColors.Border, VegaShapes.Hero)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

@Composable
internal fun StreamVaultFactRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = VegaColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(112.dp)
        )
        Text(
            text = value,
            color = VegaColors.TextPrimary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun StreamVaultDetailHeading(
    title: String,
    subtitle: String? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(if (subtitle == null) 22.dp else 36.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(VegaColors.Accent)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = VegaColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = VegaColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
internal fun StreamVaultMetaStrip(values: List<String>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEachIndexed { idx, value ->
            Text(
                text = value,
                color = VegaColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (idx < values.lastIndex) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(VegaColors.TextMuted)
                )
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

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun formatVodDuration(durationStr: String?, minutesStr: String?, durationSecs: Long?): String? {
    if (durationSecs != null && durationSecs > 0) {
        val h = durationSecs / 3600
        val m = (durationSecs % 3600) / 60
        return if (h > 0) "$h sa $m dk" else "$m dk"
    }
    if (!minutesStr.isNullOrBlank()) {
        val mins = minutesStr.toIntOrNull()
        if (mins != null && mins > 0) {
            val h = mins / 60
            val m = mins % 60
            return if (h > 0) "$h sa $m dk" else "$m dk"
        }
    }
    if (!durationStr.isNullOrBlank()) {
        val parts = durationStr.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: 0
            val m = parts[1].toIntOrNull() ?: 0
            if (h > 0) return "$h sa $m dk"
            if (m > 0) return "$m dk"
        }
        return durationStr
    }
    return null
}
