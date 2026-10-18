package com.tivimax.player.ui.live.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import com.tivimax.player.R
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.theme.*
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import kotlinx.coroutines.delay

/**
 * Raf Düzeni (Shelf Layout) Öne Çıkan / Hero Kartı (~210-220dp).
 * Son izlenen veya ilk favori kanalı büyük görsel ve zengin EPG detaylarıyla sunar.
 * Tıklama veya "İzle" butonu doğrudan tam ekran oynatıcıyı açar.
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
    LaunchedEffect(channel.streamId) {
        onLoadEpg()
    }

    // Dakikada bir süreyi ve ilerlemeyi tazele
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            tick++
        }
    }

    val programTitle = remember(epg, tick) {
        epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı Yayın"
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
            "Canlı Yayın"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(215.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .dpadFocusable(
                focusedBorderColor = OpusCyan,
                shape = RoundedCornerShape(16.dp),
                scale = 1.02f,
                onClick = { onPlay(channel) }
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable { onPlay(channel) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TiviCard),
        border = BorderStroke(1.dp, OpusCyan.copy(alpha = 0.35f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Arka Plan: Yumuşak Bulanık Logo / Görsel Arka Plan
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

            // Katmanlı Koyu Gradyan (Metinlerin ve Kontrollerin Kusursuz Okunması İçin)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.82f),
                                Color.Black.copy(alpha = 0.95f)
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
                // Üst Satır: CANLI Rozeti & Öne Çıkan Başlığı & Favori Butonu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Kırmızı Canlı Hapı
                        Row(
                            modifier = Modifier
                                .background(VegaColors.Live.copy(alpha = 0.9f), VegaShapes.Small)
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
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        // Öne Çıkan Etiketi
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = OpusCyan.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, OpusCyan.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "ÖNE ÇIKAN KANAL",
                                color = OpusCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Favori Butonu
                    IconButton(
                        onClick = { onFavoriteToggle(channel) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorilere Ekle",
                            tint = if (isFavorite) TiviGold else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Orta Kısım: Kanal Logosu + Kanal Adı + Program Detayları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.size(56.dp)
                    ) {
                        AsyncShimmerImage(
                            model = channel.streamIcon,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            placeholderIcon = Icons.Default.Tv
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = programTitle,
                            color = OpusCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = timeText,
                            color = TiviTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Alt Kısım: İlerleme Çubuğu ve Büyük "İzle" Butonu
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = OpusCyan,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Hemen İzlemek İçin Dokunun",
                            color = TiviTextMuted,
                            fontSize = 11.sp
                        )

                        Button(
                            onClick = { onPlay(channel) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OpusCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .dpadFocusable(
                                    focusedBorderColor = Color.White,
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = { onPlay(channel) }
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.live_shelf_watch),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
