package com.tivimax.player.ui.live.shelf

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.unit.sp
import com.tivimax.player.R
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.theme.*
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import kotlinx.coroutines.delay

/**
 * 16:9 oranında yatay raf kartı (~160x90dp).
 * Netflix tarzı yatay kaydırılan raflarda canlı TV kanallarını listeler.
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
    var showContextMenu by remember { mutableStateOf(false) }

    // EPG verisini gecikmeli yükle
    LaunchedEffect(channel.streamId) {
        delay(200L)
        onLoadEpg()
    }

    // Dakikada bir EPG ilerlemesini güncelle
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

    Box(
        modifier = modifier
            .width(165.dp)
            .height(96.dp)
            .dpadFocusable(
                focusedBorderColor = OpusCyan,
                shape = RoundedCornerShape(12.dp),
                scale = 1.05f,
                onClick = onChannelClick
            )
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onChannelClick,
                onLongClick = { showContextMenu = true }
            )
    ) {
        // Kart Arka Planı ve Çerçevesi
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isPlaying) OpusCyan.copy(alpha = 0.14f) else TiviCard
            ),
            border = BorderStroke(
                width = if (isPlaying) 1.5.dp else 1.dp,
                color = if (isPlaying) OpusCyan else TiviCardBorder.copy(alpha = 0.6f)
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Kanal Logosu (Orta Üstte veya Sol Ortada)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.size(46.dp)
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
                }

                // Alt Kısım Karartma Gradyanı (Metinlerin Okunurluğu İçin)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.98f)
                                )
                            )
                        )
                )

                // Sol Üst: Kırmızı CANLI Rozeti
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
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
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Oynatılıyor Rozeti (Eğer bu kanal şu an açıksa)
                if (isPlaying) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp)
                            .background(OpusCyan.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.live_shelf_playing),
                            color = OpusCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Sağ Üst: Favori Yıldızı (Dokunma alanı optimize)
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
                        tint = if (isFavorite) TiviGold else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Alt Kısım Metinleri (Kanal Adı 1 Satır + Program Adı 1 Satır)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = channel.name,
                        color = if (isPlaying) OpusCyan else Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = programTitle,
                        color = TiviTextSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // En Altta İnce EPG İlerleme Çubuğu (2.5dp) - yalnızca EPG varsa
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = OpusCyan,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
            }
        }

        // Uzun Basma Hızlı Menüsü (Context Menu)
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
            DropdownMenuItem(
                text = { Text("Tam Ekran Oynat", color = Color.White, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = OpusCyan
                    )
                },
                onClick = {
                    showContextMenu = false
                    onChannelClick()
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
