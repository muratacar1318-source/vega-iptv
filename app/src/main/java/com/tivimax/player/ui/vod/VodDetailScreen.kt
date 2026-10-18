package com.tivimax.player.ui.vod

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VegaColors.Background)
    ) {
        // Arka Plan Afiş Resmi (Backdrop - Ambient Blur)
        AsyncShimmerImage(
            model = info?.movieImage ?: movie.streamIcon,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholderIcon = Icons.Default.Movie,
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

        // Karartma Geçişi (Gradient - Sinematik Fade to OLED Black)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isWideScreen) {
                        Brush.horizontalGradient(
                            colors = listOf(
                                VegaColors.Background.copy(alpha = 0.95f),
                                VegaColors.Background.copy(alpha = 0.85f),
                                VegaColors.Background.copy(alpha = 0.65f),
                                VegaColors.Background.copy(alpha = 0.85f)
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
            // YATAY / TV EKRANI: 2 Sütunlu Sinematik Düzen (Ekrana sığan, taşmayan)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 76.dp, end = 28.dp, top = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SOL SÜTUN: Film Afişi (Poster)
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.88f)
                        .aspectRatio(0.68f)
                        .clip(VegaShapes.Large)
                        .background(VegaColors.Card)
                        .border(1.dp, VegaColors.Border, VegaShapes.Large)
                ) {
                    AsyncShimmerImage(
                        model = info?.movieImage ?: movie.streamIcon,
                        contentDescription = info?.name ?: movie.name,
                        contentScale = ContentScale.Crop,
                        placeholderIcon = Icons.Default.Movie,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // SAĞ SÜTUN: Başlık, IMDb, Butonlar ve Kaydırılabilir Özet
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Film Başlığı
                    Text(
                        text = info?.name ?: movie.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VegaColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 28.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Meta Bilgiler (IMDb, Süre, Yıl, Tür)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val rating = info?.rating ?: movie.rating
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

                        val duration = info?.duration ?: info?.durationMinutes?.let { "$it dk" }
                        if (!duration.isNullOrEmpty()) {
                            Text(text = duration, color = VegaColors.TextSecondary, fontSize = 13.sp)
                        }

                        val year = info?.releaseDate?.take(4)
                        if (!year.isNullOrEmpty()) {
                            Text(text = year, color = VegaColors.TextSecondary, fontSize = 13.sp)
                        }

                        info?.genre?.let { genre ->
                            if (genre.isNotBlank()) {
                                Text(text = "• $genre", color = VegaColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Yan Yana Odaklanabilir Aksiyon Butonları
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasResume = watchProgress != null && watchProgress!!.positionMs > 10_000L

                        if (hasResume) {
                            val resumeTimeStr = formatDuration(watchProgress!!.positionMs)
                            Button(
                                onClick = { viewModel.playVod(movie, resume = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                                shape = VegaShapes.Medium,
                                modifier = Modifier
                                    .height(46.dp)
                                    .focusRequester(playButtonFocusRequester)
                                    .dpadFocusable(
                                        focusedBorderColor = Color.White,
                                        shape = VegaShapes.Medium,
                                        onClick = { viewModel.playVod(movie, resume = true) }
                                    )
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "$resumeTimeStr Devam et", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.playVod(movie, resume = false) },
                                shape = VegaShapes.Medium,
                                border = BorderStroke(1.dp, VegaColors.Border),
                                modifier = Modifier
                                    .height(46.dp)
                                    .dpadFocusable(
                                        focusedBorderColor = VegaColors.Accent,
                                        shape = VegaShapes.Medium,
                                        onClick = { viewModel.playVod(movie, resume = false) }
                                    )
                            ) {
                                Icon(imageVector = Icons.Default.Replay, contentDescription = null, tint = VegaColors.TextPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Baştan izle", color = VegaColors.TextPrimary, fontSize = 13.sp)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.playVod(movie, resume = false) },
                                colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                                shape = VegaShapes.Medium,
                                modifier = Modifier
                                    .height(46.dp)
                                    .focusRequester(playButtonFocusRequester)
                                    .dpadFocusable(
                                        focusedBorderColor = Color.White,
                                        shape = VegaShapes.Medium,
                                        onClick = { viewModel.playVod(movie, resume = false) }
                                    )
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Filmi izle", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Listeme Ekle / Favori Butonu
                        OutlinedButton(
                            onClick = { viewModel.toggleFavoriteVod(movie.streamId.toString()) },
                            shape = VegaShapes.Medium,
                            border = BorderStroke(1.dp, VegaColors.Border),
                            modifier = Modifier
                                .height(46.dp)
                                .dpadFocusable(
                                    focusedBorderColor = VegaColors.Accent,
                                    shape = VegaShapes.Medium,
                                    onClick = { viewModel.toggleFavoriteVod(movie.streamId.toString()) }
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
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Kaydırılabilir Özet ve Künye
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val plot = info?.plot ?: info?.description
                        if (!plot.isNullOrEmpty()) {
                            Text(text = "Özet", color = VegaColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = plot, color = VegaColors.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        info?.director?.let { dir ->
                            if (dir.isNotBlank()) {
                                Text(text = "Yönetmen: $dir", color = VegaColors.TextSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }

                        val cast = info?.cast ?: info?.actors
                        if (!cast.isNullOrEmpty()) {
                            Text(text = "Oyuncular: $cast", color = VegaColors.TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            // DİKEY MOD (Telefon / Tablet Dikey)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(200.dp))

                // Film Başlığı
                Text(
                    text = info?.name ?: movie.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = VegaColors.TextPrimary,
                    lineHeight = 28.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Meta Bilgiler
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val rating = info?.rating ?: movie.rating
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

                    val duration = info?.duration ?: info?.durationMinutes?.let { "$it dk" }
                    if (!duration.isNullOrEmpty()) {
                        Text(text = duration, color = VegaColors.TextSecondary, fontSize = 13.sp)
                    }

                    val year = info?.releaseDate?.take(4)
                    if (!year.isNullOrEmpty()) {
                        Text(text = year, color = VegaColors.TextSecondary, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Aksiyon Butonları
                if (watchProgress != null && watchProgress!!.positionMs > 10_000L) {
                    val resumeTimeStr = formatDuration(watchProgress!!.positionMs)
                    Button(
                        onClick = { viewModel.playVod(movie, resume = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                        shape = VegaShapes.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .focusRequester(playButtonFocusRequester)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = VegaShapes.Medium,
                                onClick = { viewModel.playVod(movie, resume = true) }
                            )
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "$resumeTimeStr Devam et", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { viewModel.playVod(movie, resume = false) },
                        shape = VegaShapes.Medium,
                        border = BorderStroke(1.dp, VegaColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .dpadFocusable(
                                focusedBorderColor = VegaColors.Accent,
                                shape = VegaShapes.Medium,
                                onClick = { viewModel.playVod(movie, resume = false) }
                            )
                    ) {
                        Icon(imageVector = Icons.Default.Replay, contentDescription = null, tint = VegaColors.TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Baştan izle", color = VegaColors.TextPrimary, fontSize = 14.sp)
                    }
                } else {
                    Button(
                        onClick = { viewModel.playVod(movie, resume = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                        shape = VegaShapes.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .focusRequester(playButtonFocusRequester)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = VegaShapes.Medium,
                                onClick = { viewModel.playVod(movie, resume = false) }
                            )
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Filmi izle", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { viewModel.toggleFavoriteVod(movie.streamId.toString()) },
                    shape = VegaShapes.Medium,
                    border = BorderStroke(1.dp, VegaColors.Border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .dpadFocusable(
                            focusedBorderColor = VegaColors.Accent,
                            shape = VegaShapes.Medium,
                            onClick = { viewModel.toggleFavoriteVod(movie.streamId.toString()) }
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
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                val plot = info?.plot ?: info?.description
                if (!plot.isNullOrEmpty()) {
                    Text(text = "Özet", color = VegaColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = plot, color = VegaColors.TextSecondary, fontSize = 14.sp, lineHeight = 22.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                info?.director?.let { dir ->
                    if (dir.isNotBlank()) {
                        Text(text = "Yönetmen: $dir", color = VegaColors.TextSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                val cast = info?.cast ?: info?.actors
                if (!cast.isNullOrEmpty()) {
                    Text(text = "Oyuncular: $cast", color = VegaColors.TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                info?.genre?.let { genre ->
                    if (genre.isNotBlank()) {
                        Text(text = "Tür: $genre", color = VegaColors.Accent, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
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
