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
import com.tivimax.player.ui.components.QualityBadge
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.extractQualityBadges
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.delay

/**
 * TiviMate / Infuse esintili sakin, hızlı tam genişlikli canlı kanal satırı.
 * Ağır kutulu kartlar yerine 0.5dp ince ayraçlarla ayrılır; aktif kanalda
 * yumuşak tonal zemin ve 3dp sol vurgu çubuğu bulunur.
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
        val raw = epg?.getDecodedTitle() ?: channel.currentProgram
        raw?.takeIf { it.isNotBlank() && it != "Canlı Yayın" && it != "Canlı yayın" }
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

    val timeMeta = remember(epg, channel.programStart, channel.programEnd, nowEpochSec) {
        val formattedTime = epg?.getFormattedTime()
            ?.takeIf { it.isNotBlank() && it != "Canlı Yayın" && it != "Canlı yayın" }
            ?: (if (!channel.programStart.isNullOrBlank() && !channel.programEnd.isNullOrBlank()) {
                "${channel.programStart} - ${channel.programEnd}"
            } else "")

        val remainingMinutes = if (epg?.stopTimestamp != null && epg.stopTimestamp > 0) {
            ((epg.stopTimestamp - nowEpochSec) / 60).coerceAtLeast(0)
        } else null

        when {
            formattedTime.isNotBlank() && remainingMinutes != null && remainingMinutes > 0 ->
                "$formattedTime · $remainingMinutes dk"
            formattedTime.isNotBlank() -> formattedTime
            else -> null
        }
    }

    val qualityBadges = remember(channel.name) {
        extractQualityBadges(channel.name)
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
                        .height(44.dp)
                        .background(
                            color = VegaColors.Accent,
                            shape = RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 66.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ana kanal alanı (mobilde dokunmatik + uzun basma, TV'de dış satır tek hedef)
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
                            .size(46.dp)
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

                            qualityBadges.take(1).forEach { badge ->
                                QualityBadge(badge)
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = programTitle ?: "Canlı yayın",
                                color = if (programTitle != null) VegaColors.TextSecondary else VegaColors.TextMuted,
                                fontSize = VegaText.Secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (timeMeta != null) {
                                Text(
                                    text = timeMeta,
                                    color = VegaColors.TextMuted,
                                    fontSize = VegaText.Caption,
                                    maxLines = 1,
                                    softWrap = false
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
                    }
                }

                // Favori yıldızı (mobilde kardeş dokunma alanı, TV'de salt gösterge)
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
                            contentDescription = if (isFavorite) "Favorilerden çıkar" else "Favorilere ekle",
                            tint = if (isFavorite) VegaColors.Gold else VegaColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Hızlı bağlam menüsü
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

        HorizontalDivider(
            thickness = 0.5.dp,
            color = VegaColors.Border.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 74.dp)
        )
    }
}
