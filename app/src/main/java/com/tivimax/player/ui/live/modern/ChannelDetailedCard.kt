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

    val currentTitle = remember(epg, minuteTick) {
        epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı Yayın"
    }

    val progress = remember(epg, minuteTick) {
        epg?.getProgressFraction() ?: (if (channel.programProgress > 0f) channel.programProgress else 0.45f)
    }

    val currentTimeSpan = remember(epg, minuteTick) {
        epg?.getFormattedTime() ?: (if (channel.programStart != null) "${channel.programStart} - ${channel.programEnd}" else "")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(98.dp)
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
            modifier = Modifier.fillMaxSize(),
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
                    .fillMaxSize()
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

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-2).dp, y = (-2).dp)
                            .size(8.dp)
                            .background(Color(0xFFFF1744), CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Detaylı Bilgiler
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // 1. Satır: Kanal Adı & Equalizer
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Text(
                            text = "Şimdi: $currentTitle",
                            color = TiviTextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (currentTimeSpan.isNotBlank()) {
                            Text(
                                text = currentTimeSpan,
                                color = TiviTextMuted,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // İlerleme Çubuğu
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = OpusCyan,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 3. Satır: Sıradaki Program (varsa)
                    Text(
                        text = if (!channel.nextProgram.isNullOrBlank()) "Sıradaki: ${channel.nextProgram}" else "Program akışı güncel",
                        color = TiviTextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    text = { Text("Kanalı Gizle", color = Color(0xFFEF5350), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color(0xFFEF5350)
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
