package com.tivimax.player.ui.vod

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaSizes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.vegaBackdrop
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.delay

@Composable
fun VodDetailScreen(
    movie: VodStream,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv = LocalIsTvDevice.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val isMobilePortrait = !isTv && !isWideScreen

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
    val releaseYear = remember(releaseDate) {
        releaseDate?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: releaseDate
    }
    val genreText = info?.genre?.takeIf { it.isNotBlank() }
    val directorText = info?.director?.takeIf { it.isNotBlank() }
    val countryText = info?.country?.takeIf { it.isNotBlank() }
    val castList = remember(info?.cast, info?.actors) {
        val raw = info?.cast?.takeIf { it.isNotBlank() } ?: info?.actors
        raw?.split(',', ';')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }
    val ratingShort = remember(info?.rating, movie.rating, movie.rating5Based) {
        val raw = info?.rating ?: movie.rating ?: movie.rating5Based?.let { String.format(java.util.Locale.US, "%.1f", it * 2) }
        val num = raw?.trim()?.substringBefore("/")?.replace(',', '.')?.toFloatOrNull()
        if (num != null && num > 0f) {
            if (num == num.toInt().toFloat()) "${num.toInt()}" else String.format(java.util.Locale.US, "%.1f", num)
        } else null
    }
    val trailerUrl = info?.youtubeTrailer
    val plot = info?.plot?.takeIf { it.isNotBlank() } ?: info?.description?.takeIf { it.isNotBlank() }
    val hasResume = watchProgress != null && !watchProgress!!.isWatched && watchProgress!!.positionMs > 10_000L
    val resumeFraction = remember(watchProgress, hasResume) {
        val wp = watchProgress
        if (hasResume && wp != null && wp.durationMs > 0L) {
            (wp.positionMs.toFloat() / wp.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    // 1-Tık Kumanda Garantisi: TV / geniş ekranda kullanıcı odağı başka öğeye taşımadıysa Oynat / Devam et butonuna güvenli odak
    var userMovedFocus by remember(movie.streamId) { mutableStateOf(false) }
    var initialFocusApplied by remember(movie.streamId) { mutableStateOf(false) }
    LaunchedEffect(movie.streamId, hasResume, isMobilePortrait) {
        if (isMobilePortrait || userMovedFocus || initialFocusApplied) return@LaunchedEffect
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

    val factRows = remember(directorText, releaseDate, durationText, genreText, countryText, castList) {
        buildList {
            directorText?.let { add("Yönetmen" to it) }
            if (castList.isNotEmpty()) add("Oyuncular" to castList.take(10).joinToString(", "))
            genreText?.let { add("Tür" to it) }
            releaseDate?.let { add("Yayın tarihi" to it) }
            durationText?.let { add("Süre" to it) }
            countryText?.let { add("Ülke" to it) }
        }
    }

    if (isMobilePortrait) {
        // MOBİL SİNEMA DETAYI (Apple TV / Infuse tarzı tam genişlik üst görsel + temiz başparmak aksiyonları + çerçevesiz tipografi)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VegaColors.Background)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 44.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. TAM GENİŞLİK SİNEMA GÖRSELİ + BAŞLIK & NOKTALI META
                item(key = "vod_hero_mobile") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(312.dp)
                    ) {
                        AsyncShimmerImage(
                            model = backdropUrl,
                            contentDescription = movieTitle,
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
                                text = movieTitle,
                                color = VegaColors.TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.4).sp,
                                lineHeight = 29.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            DetailDotMetadataRow(
                                items = listOfNotNull(releaseYear, durationText),
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

                // 2. BİRİNCİL TAM GENİŞLİK CTA + İKİNCİL AKSİYON SATIRI
                item(key = "vod_actions_mobile") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (hasResume && resumeFraction > 0.01f) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                LinearProgressIndicator(
                                    progress = { resumeFraction },
                                    color = VegaColors.Accent,
                                    trackColor = VegaColors.SurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(VegaShapes.Pill)
                                )
                                Text(
                                    text = "Kaldığın yer: ${formatDuration(watchProgress!!.positionMs)}",
                                    color = VegaColors.TextMuted,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Tam genişlik 48.dp birincil buton ("İzle" / "Devam et")
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
                                    onClick = { viewModel.playVod(movie, resume = hasResume) }
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
                                    text = if (hasResume) "Devam et" else "İzle",
                                    color = VegaColors.OnAccent,
                                    fontSize = VegaText.BodyProminent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // İkincil aksiyon satırı: Baştan izle, Favori, Fragman
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasResume) {
                                MobileSecondaryActionButton(
                                    label = "Baştan izle",
                                    icon = Icons.Default.Replay,
                                    onClick = { viewModel.playVod(movie, resume = false) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            MobileSecondaryActionButton(
                                label = if (isFavorite) "Favoride" else "Favori",
                                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                isHighlighted = isFavorite,
                                highlightColor = VegaColors.Gold,
                                onClick = { viewModel.toggleFavoriteVod(movie.streamId.toString()) },
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

                // 3. ÖZET METNİ (Kutusuz, rahat okunan 21.sp satır yüksekliği)
                if (!plot.isNullOrBlank()) {
                    item(key = "vod_plot_mobile") {
                        Text(
                            text = plot,
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Body,
                            lineHeight = 21.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }
                }

                // 4. İNCE ÇİZGİLİ META SATIRLARI (Kutusuz — Yönetmen, Oyuncular, Tür...)
                if (factRows.isNotEmpty()) {
                    item(key = "vod_facts_mobile") {
                        DetailHairlineMetaList(
                            rows = factRows,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            // YÜZEN YARI SAYDAM YUVARLAK GERİ DÜĞMESİ (Sol üst, statusBarsPadding)
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

            // 2. KAYDIRILABİLİR EDİTORYAL İÇERİK
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(horizontal = 52.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                item(key = "vod_back") {
                    StreamVaultBackButton(
                        onClick = onBack,
                        onFocusStateChange = { if (it) userMovedFocus = true }
                    )
                }

                item(key = "vod_hero_wide") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(34.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // SOL: Çerçevesiz temiz 2:3 afiş
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .width(220.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(VegaShapes.Large)
                                    .background(VegaColors.SurfaceElevated)
                            ) {
                                AsyncShimmerImage(
                                    model = posterUrl,
                                    contentDescription = movieTitle,
                                    contentScale = ContentScale.Crop,
                                    placeholderIcon = Icons.Default.Movie,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (hasResume && resumeFraction > 0.01f) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { resumeFraction },
                                    color = VegaColors.Accent,
                                    trackColor = VegaColors.SurfaceVariant,
                                    modifier = Modifier
                                        .width(204.dp)
                                        .height(3.5.dp)
                                        .clip(VegaShapes.Pill)
                                )
                            }
                        }

                        // SAĞ: Sol hizalı editoryal başlık, noktalı meta, tek tık aksiyon satırı ve özet
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = movieTitle,
                                color = VegaColors.TextPrimary,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                                lineHeight = 38.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            DetailDotMetadataRow(
                                items = listOfNotNull(releaseYear, durationText, genreText),
                                rating = ratingShort,
                                qualityBadges = qualityBadges
                            )

                            VodActionRow(
                                hasResume = hasResume,
                                resumeTimeStr = if (hasResume) formatDuration(watchProgress!!.positionMs) else null,
                                isFavorite = isFavorite,
                                trailerUrl = trailerUrl,
                                playButtonFocusRequester = playButtonFocusRequester,
                                onPlayResume = { viewModel.playVod(movie, resume = true) },
                                onPlayStart = { viewModel.playVod(movie, resume = false) },
                                onToggleFavorite = { viewModel.toggleFavoriteVod(movie.streamId.toString()) },
                                onOpenTrailer = { openTrailer(context, trailerUrl) },
                                onOtherActionFocused = { userMovedFocus = true }
                            )

                            if (!plot.isNullOrBlank()) {
                                Text(
                                    text = plot,
                                    color = VegaColors.TextSecondary,
                                    fontSize = VegaText.Body,
                                    lineHeight = 22.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (factRows.isNotEmpty()) {
                    item(key = "vod_facts_wide") {
                        StreamVaultDetailInfoPanel(
                            modifier = Modifier.fillMaxWidth(),
                            spacing = 12.dp
                        ) {
                            StreamVaultDetailHeading(title = "Film bilgileri")
                            Spacer(modifier = Modifier.height(2.dp))
                            factRows.forEach { (label, value) ->
                                StreamVaultFactRow(label = label, value = value)
                            }
                        }
                    }
                }

                item(key = "vod_bottom_space") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
internal fun DetailMobileBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = VegaColors.OverlayPill,
        modifier = modifier
            .size(VegaSizes.IconButton)
            .dpadFocusable(
                shape = CircleShape,
                focusedBorderColor = VegaColors.FocusBorder,
                scale = 1.05f,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Geri",
                tint = Color.White,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
internal fun MobileSecondaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    highlightColor: Color = VegaColors.Accent
) {
    val bgColor = if (isHighlighted) highlightColor.copy(alpha = 0.16f) else VegaColors.SurfaceVariant.copy(alpha = 0.78f)
    val contentColor = if (isHighlighted) highlightColor else VegaColors.TextPrimary

    Surface(
        shape = VegaShapes.Medium,
        color = bgColor,
        modifier = modifier
            .height(VegaSizes.ButtonHeight)
            .dpadFocusable(
                shape = VegaShapes.Medium,
                focusedBorderColor = VegaColors.FocusBorder,
                scale = 1.02f,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun DetailDotMetadataRow(
    items: List<String>,
    rating: String? = null,
    qualityBadges: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    if (items.isEmpty() && rating == null && qualityBadges.isEmpty()) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { idx, value ->
            Text(
                text = value,
                color = VegaColors.TextSecondary,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (idx < items.lastIndex || rating != null) {
                Text(
                    text = "·",
                    color = VegaColors.TextMuted,
                    fontSize = VegaText.Body,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (rating != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = VegaColors.Gold,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = rating,
                    color = VegaColors.Gold,
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        qualityBadges.take(2).forEach { badge ->
            QualityBadge(tag = badge)
        }
    }
}

@Composable
internal fun DetailHairlineMetaList(
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        rows.forEachIndexed { idx, (label, value) ->
            if (idx > 0) {
                HorizontalDivider(
                    color = VegaColors.Border.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = label,
                    color = VegaColors.TextMuted,
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(96.dp)
                )
                Text(
                    text = value,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.Secondary,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun StreamVaultBackButton(
    onClick: () -> Unit,
    label: String = "Kütüphaneye dön",
    modifier: Modifier = Modifier,
    onFocusStateChange: ((Boolean) -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = if (isFocused) VegaColors.TextPrimary else VegaColors.OverlayPill
    val contentColor = if (isFocused) VegaColors.Background else VegaColors.TextPrimary

    Surface(
        shape = VegaShapes.Pill,
        color = bgColor,
        modifier = modifier
            .height(42.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Pill,
                scale = 1.04f,
                onClick = onClick,
                onFocusStateChange = {
                    isFocused = it
                    onFocusStateChange?.invoke(it)
                }
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
                fontSize = VegaText.Secondary,
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
    onOpenTrailer: () -> Unit,
    onOtherActionFocused: () -> Unit = {}
) {
    val actionShape = VegaShapes.Medium
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
                "İzle"
            }
            val primaryAction = if (hasResume) onPlayResume else onPlayStart
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
                        onClick = primaryAction
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(21.dp))
                    Text(
                        text = primaryLabel,
                        color = VegaColors.OnAccent,
                        fontSize = VegaText.Body,
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
                    color = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated.copy(alpha = 0.78f),
                    modifier = Modifier
                        .height(VegaSizes.ButtonHeightTv)
                        .dpadFocusable(
                            focusedBorderColor = VegaColors.FocusBorder,
                            shape = actionShape,
                            scale = 1.04f,
                            onClick = onPlayStart,
                            onFocusStateChange = {
                                isFocused = it
                                if (it) onOtherActionFocused()
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, tint = VegaColors.TextPrimary, modifier = Modifier.size(18.dp))
                        Text("Baştan izle", color = VegaColors.TextPrimary, fontSize = VegaText.Secondary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (!trailerUrl.isNullOrBlank()) {
            item(key = "act_trailer") {
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
                            onClick = onOpenTrailer,
                            onFocusStateChange = {
                                isFocused = it
                                if (it) onOtherActionFocused()
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

        item(key = "act_fav") {
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
                        onClick = onToggleFavorite,
                        onFocusStateChange = {
                            isFocused = it
                            if (it) onOtherActionFocused()
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
}

/**
 * Yumuşak tonal bilgi paneli (Yönetmen, Tür, Oyuncular).
 * TV'de odaklanınca net vurgu çerçevesi alır; odaklanmadığında çerçevesiz dingin yüzeydir.
 */
@Composable
internal fun StreamVaultDetailInfoPanel(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isTv = LocalIsTvDevice.current
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(VegaShapes.Large)
            .then(
                if (isTv) {
                    Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                } else {
                    Modifier
                }
            )
            .background(
                if (isFocused) VegaColors.SurfaceVariant.copy(alpha = 0.85f)
                else VegaColors.SurfaceElevated.copy(alpha = 0.56f)
            )
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, VegaColors.FocusBorder, VegaShapes.Large)
                } else {
                    Modifier
                }
            )
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
            fontSize = VegaText.Secondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(112.dp)
        )
        Text(
            text = value,
            color = VegaColors.TextPrimary,
            fontSize = VegaText.Secondary,
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
                .width(3.dp)
                .height(if (subtitle == null) 20.dp else 32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(VegaColors.Accent)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = VegaColors.TextPrimary,
                fontSize = VegaText.SectionTitle,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Caption
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
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (idx < values.lastIndex) {
                Text(
                    text = "·",
                    color = VegaColors.TextMuted,
                    fontSize = VegaText.Body,
                    fontWeight = FontWeight.Bold
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
