package com.tivimax.player.ui.live.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tivimax.player.R
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.tv.StreamVaultProgressBar
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.delay

/**
 * Raf Düzeni Öne Çıkan / Hero Kartı (StreamVault görsel dili).
 * Çift odak ve iç içe tıklama çakışması olmadan TV ve mobil için temiz etkileşim sunar.
 */
@Composable
fun HeroCard(
    channel: LiveStream,
    epg: EpgProgram? = null,
    isFavorite: Boolean,
    onPlay: (LiveStream) -> Unit,
    onFavoriteToggle: (LiveStream) -> Unit,
    onLoadEpg: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTv = LocalIsTvDevice.current

    LaunchedEffect(channel.streamId) {
        onLoadEpg()
    }

    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            tick++
        }
    }

    val programTitle = remember(epg, tick) {
        epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı yayın"
    }

    val progress = remember(epg, tick) {
        epg?.getProgressFraction() ?: (if (channel.programProgress > 0f) channel.programProgress else null)
    }

    val timeText = remember(epg, tick) {
        val formattedTime = epg?.getFormattedTime()
            ?: (if (channel.programStart != null) "${channel.programStart} - ${channel.programEnd}" else "")

        val remainingMinutes = if (epg?.stopTimestamp != null && epg.stopTimestamp > 0) {
            val nowSec = System.currentTimeMillis() / 1000L
            ((epg.stopTimestamp - nowSec) / 60).coerceAtLeast(0)
        } else null

        if (formattedTime.isNotBlank() && remainingMinutes != null && remainingMinutes > 0) {
            "$formattedTime • kalan $remainingMinutes dk"
        } else if (formattedTime.isNotBlank()) {
            formattedTime
        } else {
            "Canlı yayın"
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(216.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isTv) {
                        Modifier
                            .clip(VegaShapes.Large)
                            .clickable { onPlay(channel) }
                    } else {
                        Modifier
                    }
                ),
            shape = VegaShapes.Large,
            color = VegaColors.SurfaceElevated,
            border = BorderStroke(1.dp, VegaColors.Border)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Arka Plan: Yumuşak Bulanık Görsel
                if (!channel.streamIcon.isNullOrBlank()) {
                    AsyncShimmerImage(
                        model = channel.streamIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(32.dp),
                        placeholderIcon = Icons.Default.Tv
                    )
                }

                // Katmanlı Gece Gradyanı
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.52f),
                                    Color.Black.copy(alpha = 0.80f),
                                    Color.Black.copy(alpha = 0.94f)
                                )
                            )
                        )
                )

                // İçerik Alanı
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Üst Satır: Canlı ve Öne Çıkan Rozetleri
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(VegaShapes.Small)
                                .background(VegaColors.Live.copy(alpha = 0.92f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Text(
                                text = stringResource(R.string.live_shelf_live_badge),
                                color = Color.White,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = VegaShapes.Small,
                            color = VegaColors.Accent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "Öne çıkan kanal",
                                color = VegaColors.Accent,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Orta Kısım: StreamVault Kare Logo Kutusu + Kanal ve Program Bilgisi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(VegaShapes.Medium)
                                .background(VegaColors.SurfaceAccent)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncShimmerImage(
                                model = channel.streamIcon,
                                contentDescription = channel.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                                placeholderIcon = Icons.Default.Tv
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channel.name,
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.SectionTitle,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = programTitle,
                                color = VegaColors.Accent,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = timeText,
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Alt Kısım: İlerleme Çubuğu ve "İzle" Aksiyonu
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (progress != null) {
                            StreamVaultProgressBar(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isTv) "Hemen izlemek için OK'a bas" else "Hemen izlemek için dokun",
                                color = VegaColors.TextMuted,
                                fontSize = VegaText.Caption
                            )

                            Surface(
                                shape = VegaShapes.Medium,
                                color = VegaColors.Accent,
                                modifier = Modifier
                                    .height(38.dp)
                                    .then(
                                        if (isTv) {
                                            Modifier.dpadFocusable(
                                                focusedBorderColor = Color.White,
                                                shape = VegaShapes.Medium,
                                                scale = 1.04f,
                                                onClick = { onPlay(channel) }
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = VegaColors.OnAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.live_shelf_watch),
                                        color = VegaColors.OnAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = VegaText.Secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sağ Üst: Kardeş Favori Butonu (İç içe tıklama/odak çakışması yok)
        var isFavFocused by remember { mutableStateOf(false) }
        Surface(
            shape = VegaShapes.Pill,
            color = if (isFavFocused) VegaColors.SurfaceVariant else VegaColors.OverlayPill,
            border = if (isFavFocused) null else BorderStroke(1.dp, VegaColors.Border),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(36.dp)
                .dpadFocusable(
                    focusedBorderColor = VegaColors.FocusBorder,
                    shape = VegaShapes.Pill,
                    scale = 1.06f,
                    onClick = { onFavoriteToggle(channel) },
                    onFocusStateChange = { isFavFocused = it }
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isFavorite) "Favorilerden çıkar" else "Favorilere ekle",
                    tint = if (isFavorite) VegaColors.Gold else VegaColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
