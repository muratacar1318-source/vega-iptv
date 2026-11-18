package com.tivimax.player.ui.live.modern

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.delay

/**
 * TiviMate / Infuse esintili tam genişlikli detaylı canlı kanal satırı.
 * Şimdiki program, 2dp ilerleme çubuğu ve sıradaki program bilgisini 0.5dp ince ayraçlarla sunar.
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

    val rawTitle = remember(epg, channel.currentProgram) {
        val t = epg?.getDecodedTitle() ?: channel.currentProgram
        if (!t.isNullOrBlank() && t != "Canlı Yayın" && t != "Canlı yayın") t else null
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

    val currentTimeSpan = remember(epg, channel.programStart, channel.programEnd) {
        epg?.getFormattedTime()
            ?.takeIf { it.isNotBlank() && it != "Canlı Yayın" && it != "Canlı yayın" }
            ?: (if (!channel.programStart.isNullOrBlank() && !channel.programEnd.isNullOrBlank()) {
                "${channel.programStart} - ${channel.programEnd}"
            } else "")
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
            if (title.isNotBlank() && title != "Canlı Yayın" && title != "Canlı yayın") {
                if (timeStr.isNotBlank()) "Sonra · $timeStr $title" else "Sonra · $title"
            } else null
        } else if (!channel.nextProgram.isNullOrBlank()) {
            "Sonra · ${channel.nextProgram}"
        } else null
    }

    val rowBackground = when {
        isFocused -> VegaColors.SurfaceVariant
        isPlaying -> VegaColors.Accent.copy(alpha = 0.10f)
        else -> Color.Transparent
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBackground)
                .then(
                    if (isTv) {
                        Modifier.dpadFocusable(
                            focusedBorderColor = VegaColors.FocusBorder,
                            shape = VegaShapes.Small,
                            scale = 1.015f,
                            onClick = onChannelClick,
                            onFocusStateChange = { isFocused = it }
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            // Aktif kanal sol vurgu çubuğu (3.dp)
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(3.dp)
                        .height(54.dp)
                        .background(
                            color = VegaColors.Accent,
                            shape = RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
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
                        .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Temiz kare logo kutusu
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(VegaShapes.Small)
                            .background(VegaColors.SurfaceElevated)
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

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (channel.num > 0) {
                                Text(
                                    text = "${channel.num}",
                                    color = VegaColors.TextMuted,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = channel.name,
                                color = if (isPlaying) VegaColors.Accent else VegaColors.TextPrimary,
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isPlaying) {
                                AnimatedEqualizer(
                                    barColor = VegaColors.Accent,
                                    maxHeight = 11.dp,
                                    barWidth = 2.dp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = rawTitle ?: "Program bilgisi yok",
                                color = if (rawTitle != null) VegaColors.TextSecondary else VegaColors.TextMuted,
                                fontSize = VegaText.Secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (currentTimeSpan.isNotBlank() && rawTitle != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentTimeSpan,
                                    color = VegaColors.TextMuted,
                                    fontSize = VegaText.Caption,
                                    maxLines = 1
                                )
                            }
                        }

                        if (progress != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(VegaShapes.Pill),
                                color = VegaColors.Accent,
                                trackColor = VegaColors.SurfaceVariant
                            )
                        }

                        if (nextProgramText != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = nextProgramText,
                                color = VegaColors.TextMuted,
                                fontSize = VegaText.Caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Favori yıldızı (mobilde kardeş buton, TV'de salt gösterge)
                if (isTv) {
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = VegaColors.Gold,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favori",
                            tint = if (isFavorite) VegaColors.Gold else VegaColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Hızlı menü
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

        HorizontalDivider(
            thickness = 0.5.dp,
            color = VegaColors.Border.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 78.dp)
        )
    }
}
