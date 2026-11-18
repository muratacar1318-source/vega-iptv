package com.tivimax.player.ui.live.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.delay

/**
 * StreamVault uyumlu 16:9 Yatay Raf Kanal Kartı (~165x98dp).
 * Çift odak ve iç içe tıklama çakışması yoktur.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfChannelCard(
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
    val isTv = LocalIsTvDevice.current
    var showContextMenu by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(channel.streamId) {
        delay(200L)
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

    Box(
        modifier = modifier
            .width(168.dp)
            .height(98.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isTv) {
                        Modifier.dpadFocusable(
                            focusedBorderColor = VegaColors.FocusBorder,
                            shape = VegaShapes.Medium,
                            scale = 1.05f,
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
            border = if (isFocused) null else BorderStroke(
                width = if (isPlaying) 1.5.dp else 1.dp,
                color = if (isPlaying) VegaColors.Accent else VegaColors.Border
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // StreamVault kare logo kutusu
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
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
                }

                // Alt Kısım Karartma Gradyanı
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.82f),
                                    Color.Black.copy(alpha = 0.96f)
                                )
                            )
                        )
                )

                // Sol Üst: Canlı Rozeti
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(VegaShapes.Small)
                        .background(VegaColors.OverlayPill)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(VegaColors.Live, CircleShape)
                    )
                    Text(
                        text = stringResource(R.string.live_shelf_live_badge),
                        color = Color.White,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Oynatılıyor Rozeti
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp)
                            .clip(VegaShapes.Small)
                            .background(VegaColors.Accent.copy(alpha = 0.24f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.live_shelf_playing),
                            color = VegaColors.Accent,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // TV'de salt favori göstergesi
                if (isTv && isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = VegaColors.Gold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp)
                            .size(14.dp)
                    )
                }

                // Alt Kısım Metinleri
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = channel.name,
                        color = if (isPlaying) VegaColors.Accent else VegaColors.TextPrimary,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = programTitle,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // En Altta İnce Parlak Mavi İlerleme Çubuğu
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

        // Mobilde dış Box içinde kardeş favori butonu (iç içe tıklama yok)
        if (!isTv) {
            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favori",
                    tint = if (isFavorite) VegaColors.Gold else VegaColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Uzun Basma Hızlı Menüsü
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
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Tam ekran oynat",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.Secondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = VegaColors.Accent
                    )
                },
                onClick = {
                    showContextMenu = false
                    onChannelClick()
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
