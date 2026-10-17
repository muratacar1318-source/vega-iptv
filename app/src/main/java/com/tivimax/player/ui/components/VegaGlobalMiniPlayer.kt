package com.tivimax.player.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tivimax.player.theme.OpusCyan
import com.tivimax.player.theme.TiviAccentLive
import com.tivimax.player.ui.MainViewModel

@OptIn(UnstableApi::class)
@Composable
fun VegaGlobalMiniPlayer(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    isWideScreen: Boolean = false
) {
    val isMiniPlayerActive by viewModel.isMiniPlayerActive.collectAsState()
    val activeItem by viewModel.activeMiniPlayerItem.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val currentPosition by viewModel.playerManager.currentPosition.collectAsState()
    val duration by viewModel.playerManager.duration.collectAsState()

    AnimatedVisibility(
        visible = isMiniPlayerActive && activeItem != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val item = activeItem ?: return@AnimatedVisibility

        val badgeColor = when (item.badge) {
            "CANLI" -> TiviAccentLive
            "FİLM" -> OpusCyan
            "DİZİ" -> Color(0xFF8B5CF6)
            else -> OpusCyan
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .then(if (isWideScreen) Modifier.widthIn(max = 500.dp) else Modifier)
                .aspectRatio(16f / 9f)
                .background(Color.Black)
                .clickable { viewModel.expandMiniPlayerToFullscreen() }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = viewModel.playerManager.player ?: viewModel.playerManager.initializePlayer(item.isLive)
                    }
                },
                update = { playerView ->
                    val p = viewModel.playerManager.player ?: viewModel.playerManager.initializePlayer(item.isLive)
                    if (playerView.player != p) {
                        playerView.player = p
                    }
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
                modifier = Modifier.fillMaxSize()
            )

            // Üst Bilgi Rozeti ve Kontroller (TiviMate stili)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.badge,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                viewModel.playerManager.pause()
                            } else {
                                viewModel.playerManager.play()
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.expandMiniPlayerToFullscreen() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Tam Ekran",
                            tint = OpusCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.closeMiniPlayer() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // VOD / Dizi İlerleme Çubuğu (Altta İnce Çizgi)
            if (!item.isLive && duration > 0L) {
                val progress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = OpusCyan,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}
