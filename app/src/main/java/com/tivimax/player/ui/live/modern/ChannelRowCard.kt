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
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.extractQualityBadges
import kotlinx.coroutines.delay

/**
 * Modern Kompakt Canlı TV Kanal Satır Kartı (~74dp).
 * - Solda 54dp logo ve kırmızı canlı noktası.
 * - Ortada kanal adı, oynatılıyor ekolayzeri, program adı, saat ve kalan dakika.
 * - Altta 2.5dp ince ilerleme çubuğu.
 * - Sağda 48dp dokunma alanlı favori yıldızı.
 * - Play butonu kaldırılmıştır; kartın tamamına dokunmak hemen oynatır.
 * - Uzun basıldığında hızlı bağlam menüsü açılır.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRowCard(
    channel: LiveStream,
    epg: EpgProgram? = null,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onChannelClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onLoadEpg: () -> Unit,
    modifier: Modifier = Modifier,
    onShowEpgDetail: (() -> Unit)? = null,
    onHideChannel: (() -> Unit)? = null
) {
    var showContextMenu by remember { mutableStateOf(false) }

    // EPG verisini gecikmeli yükle
    LaunchedEffect(channel.streamId) {
        delay(150L)
        onLoadEpg()
    }

    // Dakikada bir EPG ilerlemesini güncelle (her kare çizimi önler)
    var minuteTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            minuteTick++
        }
    }

    val programTitle = remember(epg, minuteTick) {
        epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı Yayın"
    }

    val progress = remember(epg, minuteTick) {
        epg?.getProgressFraction() ?: (if (channel.programProgress > 0f) channel.programProgress else null)
    }

    val timeAndRemaining = remember(epg, minuteTick) {
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

    val qualityBadges = remember(channel.name) {
        extractQualityBadges(channel.name)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
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
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // SOL: 54dp Kanal Logosu (Zemin ve Kırmızı Canlı Noktası)
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

                        // Sol Üst: Kırmızı Canlı Noktası
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = (-2).dp, y = (-2).dp)
                                .size(8.dp)
                                .background(Color(0xFFFF1744), CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // ORTA: Kanal Adı (1 satır), Program Adı (1 satır), Saat/Kalan (1 satır)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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
                                    modifier = Modifier.padding(start = 2.dp),
                                    barColor = OpusCyan
                                )
                            }

                            qualityBadges.take(1).forEach { badge ->
                                QualityBadge(badge)
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = programTitle,
                            color = TiviTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = timeAndRemaining,
                            color = TiviTextMuted,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // SAĞ: 48dp Dokunma Alanlı Favori Yıldızı
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorite) "Favorilerden Çıkar" else "Favorilere Ekle",
                            tint = if (isFavorite) TiviGold else TiviTextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // ALT KENAR: İnce EPG İlerleme Çubuğu (2.5dp)
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = OpusCyan,
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )
                }
            }
        }

        // Hızlı Bağlam Menüsü (Context Menu)
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

            if (onShowEpgDetail != null) {
                DropdownMenuItem(
                    text = { Text("Program Rehberi (EPG)", color = Color.White, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarViewWeek,
                            contentDescription = null,
                            tint = OpusCyan
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onShowEpgDetail()
                    }
                )
            }

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
