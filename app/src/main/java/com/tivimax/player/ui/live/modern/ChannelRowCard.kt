package com.tivimax.player.ui.live.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.extractQualityBadges
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.delay

/**
 * StreamVault uyumlu Kompakt Canlı TV Kanal Satır Kartı (~76dp).
 * - Derin gece laciverti kart (VegaColors.SurfaceElevated), ince çerçeve (VegaColors.Border),
 *   kare logo kutusu (VegaColors.SurfaceAccent) ve parlak mavi ilerleme çubuğu.
 * - Çift odak ve iç içe tıklama çakışması yoktur.
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
    onHideChannel: (() -> Unit)? = null,
    nowEpochSec: Long = System.currentTimeMillis() / 1000L
) {
    val isTv = LocalIsTvDevice.current
    var showContextMenu by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(channel.streamId) {
        delay(150L)
        onLoadEpg()
    }

    val programTitle = remember(epg, channel.currentProgram) {
        epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı yayın"
    }

    val progress = remember(epg, channel.programProgress, nowEpochSec) {
        if (epg != null && epg.startTimestamp > 0 && epg.stopTimestamp > epg.startTimestamp) {
            when {
                nowEpochSec in epg.startTimestamp..epg.stopTimestamp ->
                    ((nowEpochSec - epg.startTimestamp).toFloat() / (epg.stopTimestamp - epg.startTimestamp).toFloat())
                        .coerceIn(0.05f, 0.98f)
                nowEpochSec > epg.stopTimestamp -> 1.0f
                else -> 0.0f
            }
        } else if (channel.programProgress > 0f) {
            channel.programProgress
        } else {
            null
        }
    }

    val timeAndRemaining = remember(epg, channel.programStart, channel.programEnd, nowEpochSec) {
        val formattedTime = epg?.getFormattedTime()
            ?: (if (channel.programStart != null) "${channel.programStart} - ${channel.programEnd}" else "")

        val remainingMinutes = if (epg?.stopTimestamp != null && epg.stopTimestamp > 0) {
            ((epg.stopTimestamp - nowEpochSec) / 60).coerceAtLeast(0)
        } else null

        if (formattedTime.isNotBlank() && remainingMinutes != null && remainingMinutes > 0) {
            "$formattedTime • kalan $remainingMinutes dk"
        } else if (formattedTime.isNotBlank()) {
            formattedTime
        } else {
            "Canlı yayın"
        }
    }

    val qualityBadges = remember(channel.name) {
        extractQualityBadges(channel.name)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .then(
                    if (isTv) {
                        Modifier.dpadFocusable(
                            focusedBorderColor = VegaColors.FocusBorder,
                            shape = VegaShapes.Medium,
                            scale = 1.02f,
                            onClick = onChannelClick,
                            onFocusStateChange = { isFocused = it }
                        )
                    } else {
                        Modifier
                    }
                ),
            shape = VegaShapes.Medium,
            color = when {
                isFocused -> VegaColors.SurfaceVariant
                isPlaying -> VegaColors.Accent.copy(alpha = 0.14f)
                else -> VegaColors.SurfaceElevated
            },
            border = if (isFocused) null else BorderStroke(
                width = if (isPlaying) 1.5.dp else 1.dp,
                color = if (isPlaying) VegaColors.Accent else VegaColors.Border
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ana Kanal Alanı (mobilde dokunmatik + uzun basma, TV'de dış kart tek hedef)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (!isTv) {
                                    Modifier.combinedClickable(
                                        onClick = onChannelClick,
                                        onLongClick = { showContextMenu = true }
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Kare Logo Kutusu (StreamVault tarzı)
                        Box(
                            modifier = Modifier.size(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(VegaShapes.Small)
                                    .background(VegaColors.SurfaceAccent)
                                    .padding(5.dp),
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

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = (-2).dp, y = (-2).dp)
                                    .size(8.dp)
                                    .background(VegaColors.Live, CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

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
                                    color = if (isPlaying) VegaColors.Accent else VegaColors.TextPrimary,
                                    fontSize = VegaText.Body,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                if (isPlaying) {
                                    AnimatedEqualizer(
                                        modifier = Modifier.padding(start = 2.dp),
                                        barColor = VegaColors.Accent
                                    )
                                }

                                qualityBadges.take(1).forEach { badge ->
                                    QualityBadge(badge)
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = programTitle,
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = timeAndRemaining,
                                color = VegaColors.TextMuted,
                                fontSize = VegaText.Caption,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Sağ Favori Yıldızı (Mobilde kardeş dokunma alanı, TV'de salt gösterge)
                    if (isTv) {
                        if (isFavorite) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = VegaColors.Gold,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(18.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onFavoriteToggle,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (isFavorite) "Favorilerden çıkar" else "Favorilere ekle",
                                tint = if (isFavorite) VegaColors.Gold else VegaColors.TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Alt Kenar İlerleme Çubuğu
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = VegaColors.Accent,
                        trackColor = VegaColors.SurfaceAccent
                    )
                }
            }
        }

        // Hızlı Bağlam Menüsü
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier.background(VegaColors.SurfaceElevated)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (isFavorite) "Favorilerden çıkar" else "Favorilere ekle",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.Secondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.StarBorder else Icons.Default.Star,
                        contentDescription = null,
                        tint = if (isFavorite) VegaColors.TextSecondary else VegaColors.Gold
                    )
                },
                onClick = {
                    showContextMenu = false
                    onFavoriteToggle()
                }
            )

            if (onShowEpgDetail != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Program rehberi",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Secondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarViewWeek,
                            contentDescription = null,
                            tint = VegaColors.Accent
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
                    text = {
                        Text(
                            text = "Kanalı gizle",
                            color = VegaColors.Danger,
                            fontSize = VegaText.Secondary
                        )
                    },
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
