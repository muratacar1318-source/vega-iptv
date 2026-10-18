package com.tivimax.player.ui.live.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.theme.*
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import kotlinx.coroutines.delay

/**
 * Detaylı Kanal Satır Kartı (~96dp).
 * Şimdiki program (EPG), ilerleme çubuğu ve sıradaki program bilgisini birlikte gösterir.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelDetailedCard(
    channel: LiveStream,
    epg: EpgProgram? = null,
    nextEpg: EpgProgram? = null,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onChannelClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onLoadEpg: () -> Unit,
    modifier: Modifier = Modifier,
    onHideChannel: (() -> Unit)? = null
) {
    var showContextMenu by remember { mutableStateOf(false) }

    LaunchedEffect(channel.streamId) {
        delay(150L)
        onLoadEpg()
    }

    var minuteTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            minuteTick++
        }
    }

    val rawTitle = remember(epg, minuteTick) {
        val t = epg?.getDecodedTitle() ?: channel.currentProgram
        if (!t.isNullOrBlank() && t != "Canlı Yayın") t else null
    }

    val progress = remember(epg, minuteTick) {
        epg?.getProgressFraction() ?: if (channel.programProgress > 0f) channel.programProgress else null
    }

    val currentTimeSpan = remember(epg, minuteTick) {
        epg?.getFormattedTime() ?: (if (channel.programStart != null) "${channel.programStart} - ${channel.programEnd}" else "")
    }

    val nextProgramText = remember(nextEpg, channel.nextProgram) {
        if (nextEpg != null) {
            val timeStr = if (nextEpg.startTimestamp > 0) {
                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                sdf.format(java.util.Date(nextEpg.startTimestamp * 1000L))
            } else if (!nextEpg.start.isNullOrBlank() && nextEpg.start.length >= 16) {
                nextEpg.start.substring(11, 16)
            } else ""

            val title = nextEpg.getDecodedTitle()
            if (title.isNotBlank() && title != "Canlı Yayın") {
                if (timeStr.isNotBlank()) "Sonra: $timeStr $title" else "Sonra: $title"
            } else null
        } else if (!channel.nextProgram.isNullOrBlank()) {
            "Sonra: ${channel.nextProgram}"
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .dpadFocusable(
                focusedBorderColor = OpusCyan,
                shape = RoundedCornerShape(12.dp),
                scale = 1.02f,
                onClick = onChannelClick
            )
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onChannelClick,
                onLongClick = { showContextMenu = true }
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isPlaying) OpusCyan.copy(alpha = 0.12f) else TiviCard
            ),
            border = BorderStroke(
                width = if (isPlaying) 1.5.dp else 1.dp,
                color = if (isPlaying) OpusCyan else TiviCardBorder
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo ve Canlı Noktası
                Box(
                    modifier = Modifier.size(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AsyncShimmerImage(
                            model = channel.streamIcon,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            placeholderIcon = Icons.Default.Tv
                        )
                    }

                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = (-2).dp, y = (-2).dp)
                                .size(8.dp)
                                .background(VegaColors.Live, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Detaylı Bilgiler
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // 1. Satır: Kanal No & Kanal Adı & Equalizer
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (channel.num > 0) {
                            Text(
                                text = "${channel.num}",
                                color = TiviTextMuted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        Text(
                            text = channel.name,
                            color = if (isPlaying) OpusCyan else Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isPlaying) {
                            AnimatedEqualizer(
                                modifier = Modifier.padding(start = 4.dp),
                                barColor = OpusCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // 2. Satır: Şimdi Oynayan Program + Saat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (rawTitle != null) {
                            Text(
                                text = "Şimdi: $rawTitle",
                                color = TiviTextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Text(
                                text = "Program bilgisi yok",
                                color = TiviTextMuted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (currentTimeSpan.isNotBlank() && rawTitle != null) {
                            Text(
                                text = currentTimeSpan,
                                color = TiviTextMuted,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // İlerleme Çubuğu (varsa)
                    if (progress != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(1.dp)),
                            color = OpusCyan,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }

                    // 3. Satır: Sıradaki Program (varsa)
                    if (nextProgramText != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = nextProgramText,
                            color = TiviTextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Favori Yıldızı
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(44.dp)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) TiviGold else TiviTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Hızlı Menü
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier.background(TiviSurfaceVariant)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (isFavorite) "Favorilerden Çıkar" else "Favorilere Ekle",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.StarBorder else Icons.Default.Star,
                        contentDescription = null,
                        tint = if (isFavorite) TiviTextSecondary else TiviGold
                    )
                },
                onClick = {
                    showContextMenu = false
                    onFavoriteToggle()
                }
            )
            if (onHideChannel != null) {
                DropdownMenuItem(
                    text = { Text("Kanalı gizle", color = VegaColors.Danger, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = VegaColors.Danger
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onHideChannel()
                    }
                )
            }
        }
    }
}
