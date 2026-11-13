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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.extractQualityBadges
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import kotlinx.coroutines.delay

@Composable
fun VodDetailScreen(
    movie: VodStream,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    val vodDetail by viewModel.currentVodDetail.collectAsState()
    val favoriteVodIds by viewModel.favoriteVodIds.collectAsState()
    val isFavorite = favoriteVodIds.contains(movie.streamId.toString())

    var watchProgress by remember { mutableStateOf<WatchProgress?>(null) }
    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(movie.streamId) {
        watchProgress = viewModel.preferences.getWatchProgress(movie.streamId)
    }

    // VOD Akıllı Ön Isıtma (1.500 ms sonra ilk URL'yi ısıt, çıkınca iptal et)
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

    // Detay ekranı açıldığı anda otomatik olarak "Filmi İzle" / "Devam Et" butonuna focus ver
    LaunchedEffect(Unit) {
        delay(180)
        try {
            playButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val info = vodDetail?.info
    val movieTitle = info?.name ?: movie.name
    val backdropUrl = info?.movieImage ?: info?.coverBig ?: movie.streamIcon
    val posterUrl = info?.coverBig ?: info?.movieImage ?: movie.streamIcon

    val qualityBadges = remember(movie.name, info?.name) {
        val detected = extractQualityBadges(movie.name + " " + (info?.name ?: ""))
        if (detected.isEmpty()) listOf("FHD") else detected
    }

    val durationText = remember(info?.duration, info?.durationMinutes, info?.durationSecs) {
        formatVodDuration(info?.duration, info?.durationMinutes, info?.durationSecs)
    }

    val genreList = remember(info?.genre) {
        info?.genre?.split(',', '/', '•')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    val castList = remember(info?.cast, info?.actors) {
        val raw = info?.cast?.takeIf { it.isNotBlank() } ?: info?.actors
        raw?.split(',', ';')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    val trailerUrl = info?.youtubeTrailer

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
                placeholderIcon = Icons.Default.Movie,
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

            // Sinematik Karartma Maskeleri (OLED Siyaha Yumuşak Geçiş)
            if (isWideScreen) {
                // Yatay / TV: Soldan sağa derin karartma (sol %38 tam siyah, orta transparan, sağ hafif vignette)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.0f to VegaColors.Background,
                                    0.36f to VegaColors.Background,
                                    0.55f to VegaColors.Background.copy(alpha = 0.90f),
                                    0.75f to VegaColors.Background.copy(alpha = 0.50f),
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
            // YATAY / TV EKRANI: 2 Sütunlu Sinematik Vitrin
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 90.dp, end = 36.dp, top = 28.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SOL SÜTUN: 2:3 Sinematik Afiş
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .aspectRatio(0.68f)
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
                            model = posterUrl,
                            contentDescription = movieTitle,
                            contentScale = ContentScale.Crop,
                            placeholderIcon = Icons.Default.Movie,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Kaldığın Yer İlerleme Şeridi (Afiş Altı)
                    if (watchProgress != null && watchProgress!!.durationMs > 0 && watchProgress!!.positionMs > 10_000L) {
                        val progressFraction = (watchProgress!!.positionMs.toFloat() / watchProgress!!.durationMs.toFloat()).coerceIn(0f, 1f)
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .width(180.dp)
                                .height(4.dp)
                                .clip(VegaShapes.Pill)
                                .background(Color.White.copy(alpha = 0.15f))
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

                // SAĞ SÜTUN: Başlık, VIP Rozetler, Butonlar, Özet ve Oyuncular
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // İçerik Türü Etiketi (Kompakt Üst Bilgi)
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
                                text = "FİLM",
                                color = VegaColors.Accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        if (!info?.originalName.isNullOrBlank() && info?.originalName != movieTitle) {
                            Text(
                                text = info?.originalName.orEmpty(),
                                color = VegaColors.TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Film Başlığı
                    Text(
                        text = movieTitle,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VegaColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 30.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // VIP Rozetler Şeridi (IMDb, Kalite, Süre, Yıl, Yaş)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val rating = info?.rating ?: movie.rating
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

                        // Kalite Rozetleri (4K / FHD / HEVC)
                        qualityBadges.forEach { badge ->
                            QualityBadge(tag = badge)
                        }

                        // Yayın Yılı
                        val releaseYear = info?.releaseDate?.take(4)
                        if (!releaseYear.isNullOrEmpty()) {
                            Surface(
                                shape = VegaShapes.Small,
                                color = VegaColors.SurfaceVariant,
                                border = BorderStroke(1.dp, VegaColors.Border)
                            ) {
                                Text(
                                    text = releaseYear,
                                    color = VegaColors.TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Süre
                        if (!durationText.isNullOrEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = VegaColors.TextMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = durationText,
                                    color = VegaColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Yaş Sınırı
                        if (!info?.age.isNullOrBlank()) {
                            Surface(
                                shape = VegaShapes.Small,
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = "${info?.age}+",
                                    color = VegaColors.TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Tür Hapları (Genre Pills)
                    if (genreList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            genreList.take(4).forEach { genre ->
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
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Aksiyon Butonları (TV D-Pad Odaklanabilir)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasResume = watchProgress != null && watchProgress!!.positionMs > 10_000L

                        if (hasResume) {
                            val resumeTimeStr = formatDuration(watchProgress!!.positionMs)
                            Button(
                                onClick = { viewModel.playVod(movie, resume = true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VegaColors.Accent,
                                    contentColor = VegaColors.OnAccent
                                ),
                                shape = VegaShapes.Medium,
                                modifier = Modifier
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
                                    text = "Devam et ($resumeTimeStr)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.playVod(movie, resume = false) },
                                shape = VegaShapes.Medium,
                                border = BorderStroke(1.dp, VegaColors.Border),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = VegaColors.SurfaceVariant
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .dpadFocusable(
                                        focusedBorderColor = VegaColors.Accent,
                                        shape = VegaShapes.Medium
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = null,
                                    tint = VegaColors.TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Baştan izle",
                                    color = VegaColors.TextPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Button(
                                onClick = { viewModel.playVod(movie, resume = false) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VegaColors.Accent,
                                    contentColor = VegaColors.OnAccent
                                ),
                                shape = VegaShapes.Medium,
                                modifier = Modifier
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
                                    text = "Filmi izle",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Listeme Ekle / Favori Butonu
                        OutlinedButton(
                            onClick = { viewModel.toggleFavoriteVod(movie.streamId.toString()) },
                            shape = VegaShapes.Medium,
                            border = BorderStroke(
                                1.dp,
                                if (isFavorite) VegaColors.Gold.copy(alpha = 0.5f) else VegaColors.Border
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isFavorite) VegaColors.Gold.copy(alpha = 0.12f) else VegaColors.SurfaceVariant
                            ),
                            modifier = Modifier
                                .height(48.dp)
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
                                fontSize = 13.sp,
                                fontWeight = if (isFavorite) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Fragman Butonu (Mevcutsa)
                        if (!trailerUrl.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { openTrailer(context, trailerUrl) },
                                shape = VegaShapes.Medium,
                                border = BorderStroke(1.dp, VegaColors.Border),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = VegaColors.SurfaceVariant
                                ),
                                modifier = Modifier
                                    .height(48.dp)
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
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Kaydırılabilir Özet, Yönetmen ve Oyuncu Kadrosu
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val plot = info?.plot ?: info?.description
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
                                    text = "Konu Özeti",
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

                        // Yönetmen
                        info?.director?.takeIf { it.isNotBlank() }?.let { directorName ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MovieFilter,
                                    contentDescription = null,
                                    tint = VegaColors.TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Yönetmen: ",
                                    color = VegaColors.TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = directorName,
                                    color = VegaColors.TextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Oyuncu Kadrosu Çipleri
                        if (castList.isNotEmpty()) {
                            Text(
                                text = "Oyuncular",
                                color = VegaColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(end = 16.dp)
                            ) {
                                items(castList.take(12)) { actor ->
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
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = VegaColors.TextMuted,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = actor,
                                                color = VegaColors.TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ==========================================
            // DİKEY MOD (Telefon / Tablet Dikey)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(210.dp))

                // Üst Rozet & Başlık
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
                            text = "FİLM",
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
                    text = movieTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = VegaColors.TextPrimary,
                    lineHeight = 28.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Meta Bilgiler
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val rating = info?.rating ?: movie.rating
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

                    if (!durationText.isNullOrEmpty()) {
                        Text(
                            text = durationText,
                            color = VegaColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    val releaseYear = info?.releaseDate?.take(4)
                    if (!releaseYear.isNullOrEmpty()) {
                        Text(
                            text = releaseYear,
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

                Spacer(modifier = Modifier.height(18.dp))

                // Dikey Aksiyon Butonları
                val hasResume = watchProgress != null && watchProgress!!.positionMs > 10_000L
                if (hasResume) {
                    val resumeTimeStr = formatDuration(watchProgress!!.positionMs)
                    Button(
                        onClick = { viewModel.playVod(movie, resume = true) },
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
                            text = "Devam et ($resumeTimeStr)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { viewModel.playVod(movie, resume = false) },
                        shape = VegaShapes.Medium,
                        border = BorderStroke(1.dp, VegaColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = VegaColors.SurfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .dpadFocusable(
                                focusedBorderColor = VegaColors.Accent,
                                shape = VegaShapes.Medium
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                            tint = VegaColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Baştan izle",
                            color = VegaColors.TextPrimary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.playVod(movie, resume = false) },
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
                            text = "Filmi izle",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.toggleFavoriteVod(movie.streamId.toString()) },
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
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                val plot = info?.plot ?: info?.description
                if (!plot.isNullOrEmpty()) {
                    Text(
                        text = "Konu Özeti",
                        color = VegaColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = plot,
                        color = VegaColors.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                info?.director?.takeIf { it.isNotBlank() }?.let { dir ->
                    Text(
                        text = "Yönetmen: $dir",
                        color = VegaColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (castList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Oyuncular",
                        color = VegaColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(castList.take(10)) { actor ->
                            Surface(
                                shape = VegaShapes.Pill,
                                color = VegaColors.SurfaceVariant,
                                border = BorderStroke(1.dp, VegaColors.Border)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = VegaColors.TextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = actor,
                                        color = VegaColors.TextPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))
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
        return if (h > 0) "${h} sa ${m} dk" else "${m} dk"
    }
    if (!minutesStr.isNullOrBlank()) {
        val mins = minutesStr.toIntOrNull()
        if (mins != null && mins > 0) {
            val h = mins / 60
            val m = mins % 60
            return if (h > 0) "${h} sa ${m} dk" else "${m} dk"
        }
    }
    if (!durationStr.isNullOrBlank()) {
        val parts = durationStr.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: 0
            val m = parts[1].toIntOrNull() ?: 0
            if (h > 0) return "${h} sa ${m} dk"
            if (m > 0) return "${m} dk"
        }
        return durationStr
    }
    return null
}