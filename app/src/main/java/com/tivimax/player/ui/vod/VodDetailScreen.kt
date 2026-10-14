package com.tivimax.player.ui.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.data.preferences.WatchProgress
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel

@Composable
fun VodDetailScreen(
    movie: VodStream,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val vodDetail by viewModel.currentVodDetail.collectAsState()
    val favoriteVodIds by viewModel.favoriteVodIds.collectAsState()
    val isFavorite = favoriteVodIds.contains(movie.streamId.toString())

    var watchProgress by remember { mutableStateOf<WatchProgress?>(null) }

    LaunchedEffect(movie.streamId) {
        watchProgress = viewModel.preferences.getWatchProgress(movie.streamId)
    }

    val info = vodDetail?.info

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TiviBackground)
    ) {
        // Arka Plan Afiş Resmi (Backdrop - Netflix Ambient Blur)
        com.tivimax.player.ui.components.AsyncShimmerImage(
            model = info?.movieImage ?: movie.streamIcon,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholderIcon = Icons.Default.Movie,
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .then(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Modifier.blur(20.dp)
                    } else {
                        Modifier
                    }
                )
        )

        // Karartma Geçişi (Gradient - Netflix Sinematik Fade to OLED Black)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            TiviBackground.copy(alpha = 0.4f),
                            TiviBackground.copy(alpha = 0.85f),
                            TiviBackground
                        )
                    )
                )
        )

        // Ana İçerik Kaydırma Alanı
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
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TiviTextPrimary,
                lineHeight = 32.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Meta Bilgiler (Yıl, Süre, Puan)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // IMDb / Puan Rozeti
                val rating = info?.rating ?: movie.rating
                if (!rating.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TiviGold.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = TiviGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "IMDb $rating",
                                color = TiviGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Süre
                val duration = info?.duration ?: info?.durationMinutes?.let { "$it dk" }
                if (!duration.isNullOrEmpty()) {
                    Text(text = duration, color = TiviTextSecondary, fontSize = 13.sp)
                }

                // Yayın Yılı
                val year = info?.releaseDate?.take(4)
                if (!year.isNullOrEmpty()) {
                    Text(text = year, color = TiviTextSecondary, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Aksiyon Butonları (Oynat / Kaldığı Yerden Devam Et / Favori)
            if (watchProgress != null && watchProgress!!.positionMs > 10_000L) {
                // Kaldığı Yerden Devam Et Butonu
                val resumeTimeStr = formatDuration(watchProgress!!.positionMs)
                Button(
                    onClick = { viewModel.playVod(movie, resume = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = TiviPrimary, contentColor = TiviBackground),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$resumeTimeStr Kaldığın Yerden Devam Et",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Baştan Başlat Butonu
                OutlinedButton(
                    onClick = { viewModel.playVod(movie, resume = false) },
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(imageVector = Icons.Default.Replay, contentDescription = null, tint = TiviTextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Baştan İzle", color = TiviTextPrimary, fontSize = 14.sp)
                }
            } else {
                // Normal Filmi Başlat Butonu
                Button(
                    onClick = { viewModel.playVod(movie, resume = false) },
                    colors = ButtonDefaults.buttonColors(containerColor = TiviPrimary, contentColor = TiviBackground),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Filmi İzle",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Favorilere Ekle / Çıkar
            OutlinedButton(
                onClick = { viewModel.toggleFavoriteVod(movie.streamId.toString()) },
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TiviCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (isFavorite) TiviGold else TiviTextSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isFavorite) "Favorilerimden Kaldır" else "Listeme Ekle",
                    color = TiviTextPrimary,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Film Özeti (Synopsis)
            val plot = info?.plot ?: info?.description
            if (!plot.isNullOrEmpty()) {
                Text(
                    text = "Özet",
                    color = TiviTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = plot,
                    color = TiviTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Yönetmen & Oyuncular
            info?.director?.let { dir ->
                if (dir.isNotBlank()) {
                    Text(text = "Yönetmen: $dir", color = TiviTextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            val cast = info?.cast ?: info?.actors
            if (!cast.isNullOrEmpty()) {
                Text(text = "Oyuncular: $cast", color = TiviTextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
            }

            info?.genre?.let { genre ->
                if (genre.isNotBlank()) {
                    Text(text = "Tür: $genre", color = TiviPrimary, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        // Geri Butonu (En Üst Katman - zIndex 10f ile her zaman dokunulabilir)
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.65f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .size(44.dp)
                .zIndex(10f)
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
