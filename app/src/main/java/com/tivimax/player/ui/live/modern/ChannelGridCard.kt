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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.util.LocalIsTvDevice

/**
 * Sade 3 sütunlu ızgara kanal kartı (~112dp).
 * Çift odak ve iç içe tıklama çakışması yoktur.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelGridCard(
    channel: LiveStream,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onChannelClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onHideChannel: (() -> Unit)? = null
) {
    val isTv = LocalIsTvDevice.current
    var showContextMenu by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isTv) {
                        Modifier.dpadFocusable(
                            focusedBorderColor = VegaColors.FocusBorder,
                            shape = VegaShapes.Medium,
                            scale = 1.04f,
                            onClick = onChannelClick,
                            onFocusStateChange = { isFocused = it }
                        )
                    } else {
                        Modifier
                            .clip(VegaShapes.Medium)
                            .combinedClickable(
                                onClick = onChannelClick,
                                onLongClick = { showContextMenu = true }
                            )
                    }
                ),
            shape = VegaShapes.Medium,
            color = when {
                isFocused -> VegaColors.SurfaceVariant
                isPlaying -> VegaColors.Accent.copy(alpha = 0.14f)
                else -> VegaColors.SurfaceElevated
            },
            border = when {
                isFocused -> null
                isPlaying -> BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.7f))
                else -> BorderStroke(0.5.dp, VegaColors.Border.copy(alpha = 0.45f))
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(VegaColors.Live, CircleShape)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(6.dp))
                    }

                    if (isTv && isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = VegaColors.Gold,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(14.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isPlaying) {
                        AnimatedEqualizer(
                            modifier = Modifier.padding(end = 4.dp),
                            barColor = VegaColors.Accent,
                            maxHeight = 11.dp,
                            barWidth = 2.dp
                        )
                    }

                    Text(
                        text = channel.name,
                        color = if (isPlaying) VegaColors.Accent else VegaColors.TextPrimary,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (!isTv) {
            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favori",
                    tint = if (isFavorite) VegaColors.Gold else VegaColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

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
}
