package com.tivimax.player.ui.player

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.tivimax.player.R
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.TiviPlayerManager
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun DualViewSplitScreen(
    playerManager: TiviPlayerManager,
    isPortrait: Boolean,
    primaryTitle: String,
    secondaryTitle: String,
    isPrimaryAudioActive: Boolean,
    isScreensSwapped: Boolean,
    isPrimaryBuffering: Boolean,
    isSecondaryBuffering: Boolean,
    aspectRatio: AspectRatioMode,
    onSwap: () -> Unit,
    onClose: () -> Unit,
    onChangeSecondary: () -> Unit
) {
    val firstIsPrimary = !isScreensSwapped

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isPortrait) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Birinci Pencere (Üst)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    key(firstIsPrimary) {
                        DualViewSingleWindow(
                            playerManager = playerManager,
                            isPrimary = firstIsPrimary,
                            title = if (firstIsPrimary) primaryTitle else secondaryTitle,
                            hasAudio = if (firstIsPrimary) isPrimaryAudioActive else !isPrimaryAudioActive,
                            isBuffering = if (firstIsPrimary) isPrimaryBuffering else isSecondaryBuffering,
                            aspectRatio = aspectRatio,
                            onFocusAudio = {
                                playerManager.switchDualViewAudioFocus(primaryActive = firstIsPrimary)
                            }
                        )
                    }
                }

                // 1dp ince TiviMate bölme çizgisi
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(VegaColors.Border.copy(alpha = 0.7f))
                )

                // İkinci Pencere (Alt)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    key(!firstIsPrimary) {
                        DualViewSingleWindow(
                            playerManager = playerManager,
                            isPrimary = !firstIsPrimary,
                            title = if (!firstIsPrimary) primaryTitle else secondaryTitle,
                            hasAudio = if (!firstIsPrimary) isPrimaryAudioActive else !isPrimaryAudioActive,
                            isBuffering = if (!firstIsPrimary) isPrimaryBuffering else isSecondaryBuffering,
                            aspectRatio = aspectRatio,
                            onFocusAudio = {
                                playerManager.switchDualViewAudioFocus(primaryActive = !firstIsPrimary)
                            }
                        )
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                // Birinci Pencere (Sol)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    key(firstIsPrimary) {
                        DualViewSingleWindow(
                            playerManager = playerManager,
                            isPrimary = firstIsPrimary,
                            title = if (firstIsPrimary) primaryTitle else secondaryTitle,
                            hasAudio = if (firstIsPrimary) isPrimaryAudioActive else !isPrimaryAudioActive,
                            isBuffering = if (firstIsPrimary) isPrimaryBuffering else isSecondaryBuffering,
                            aspectRatio = aspectRatio,
                            onFocusAudio = {
                                playerManager.switchDualViewAudioFocus(primaryActive = firstIsPrimary)
                            }
                        )
                    }
                }

                // 1dp ince TiviMate bölme çizgisi
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(VegaColors.Border.copy(alpha = 0.7f))
                )

                // İkinci Pencere (Sağ)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    key(!firstIsPrimary) {
                        DualViewSingleWindow(
                            playerManager = playerManager,
                            isPrimary = !firstIsPrimary,
                            title = if (!firstIsPrimary) primaryTitle else secondaryTitle,
                            hasAudio = if (!firstIsPrimary) isPrimaryAudioActive else !isPrimaryAudioActive,
                            isBuffering = if (!firstIsPrimary) isPrimaryBuffering else isSecondaryBuffering,
                            aspectRatio = aspectRatio,
                            onFocusAudio = {
                                playerManager.switchDualViewAudioFocus(primaryActive = !firstIsPrimary)
                            }
                        )
                    }
                }
            }
        }

        // Üst Orta Minimal Çift Ekran Kontrol Şeridi (Sakin TiviMate yayın çubuğu)
        Surface(
            shape = VegaShapes.Small,
            color = VegaColors.OverlayPanel.copy(alpha = 0.88f),
            border = BorderStroke(0.5.dp, VegaColors.Border.copy(alpha = 0.65f)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .displayCutoutPadding()
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(VegaColors.Live)
                )

                Text(
                    text = "Çift ekran",
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.SemiBold
                )

                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(14.dp)
                        .background(VegaColors.Border.copy(alpha = 0.55f))
                )

                DualViewBarIconButton(
                    icon = Icons.Default.SwapHoriz,
                    contentDescription = "Ekranları değiştir",
                    tint = Color.White,
                    onClick = onSwap
                )

                DualViewBarIconButton(
                    icon = Icons.Default.Edit,
                    contentDescription = "2. kanalı değiştir",
                    tint = Color.White,
                    onClick = onChangeSecondary
                )

                DualViewBarIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Tek ekrana dön",
                    tint = VegaColors.Danger,
                    focusedContainerColor = VegaColors.Danger,
                    focusedTint = Color.White,
                    onClick = onClose
                )
            }
        }
    }
}

@Composable
private fun DualViewBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    focusedContainerColor: Color = VegaColors.Accent,
    focusedTint: Color = VegaColors.OnAccent
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (isFocused) focusedContainerColor else Color.Transparent)
            .dpadFocusable(
                shape = CircleShape,
                drawFocusBorder = false,
                scale = 1.06f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) focusedTint else tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun DualViewSingleWindow(
    playerManager: TiviPlayerManager,
    isPrimary: Boolean,
    title: String,
    hasAudio: Boolean,
    isBuffering: Boolean,
    aspectRatio: AspectRatioMode,
    onFocusAudio: () -> Unit
) {
    val playerInstanceVersion by playerManager.playerInstanceVersion.collectAsState()
    var localPlayerView by remember { mutableStateOf<PlayerView?>(null) }
    var isWindowFocused by remember { mutableStateOf(false) }

    LaunchedEffect(localPlayerView, isPrimary, playerInstanceVersion) {
        val view = localPlayerView ?: return@LaunchedEffect
        if (isPrimary) {
            playerManager.player ?: playerManager.initializePlayer(isLive = true)
            playerManager.attachView(view, isPrimary = true, tag = "DualViewSingleWindow-primary")
        } else {
            val targetPlayer = playerManager.secondaryExoPlayer
            if (view.player != targetPlayer) {
                view.player = targetPlayer
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .dpadFocusable(
                shape = RectangleShape,
                drawFocusBorder = false,
                scale = 1.0f,
                onClick = onFocusAudio,
                onFocusStateChange = { isWindowFocused = it }
            )
            .then(
                when {
                    isWindowFocused -> Modifier.border(2.dp, VegaColors.Accent)
                    hasAudio -> Modifier.border(1.5.dp, VegaColors.Accent.copy(alpha = 0.85f))
                    else -> Modifier
                }
            )
    ) {
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx).inflate(R.layout.player_texture_view, null, false) as PlayerView
                view.apply {
                    useController = false
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    if (isPrimary) {
                        playerManager.player ?: playerManager.initializePlayer(isLive = true)
                        playerManager.attachView(this, isPrimary = true, tag = "DualViewSingleWindow-primary")
                    } else {
                        player = playerManager.secondaryExoPlayer
                    }
                    resizeMode = mapDualViewResizeMode(aspectRatio)
                }
                localPlayerView = view
                view
            },
            update = { playerView ->
                if (!isPrimary) {
                    val targetPlayer = playerManager.secondaryExoPlayer
                    if (playerView.player != targetPlayer) {
                        playerView.player = targetPlayer
                    }
                }
                val targetResize = mapDualViewResizeMode(aspectRatio)
                if (playerView.resizeMode != targetResize) {
                    playerView.resizeMode = targetResize
                }
            },
            onRelease = { playerView ->
                if (localPlayerView === playerView) {
                    localPlayerView = null
                }
                if (isPrimary) {
                    playerManager.detachView(playerView, tag = "DualViewSingleWindow-primary")
                } else {
                    playerView.player = null
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Yükleniyor göstergesi
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = VegaColors.Accent,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Minimal Köşe Aktif Ses ve Kanal Göstergesi (Tek sade koyu hap: hoparlör ikonu + kanal adı)
        Surface(
            shape = VegaShapes.Small,
            color = VegaColors.OverlayPanel.copy(alpha = 0.78f),
            border = BorderStroke(
                width = 0.5.dp,
                color = if (hasAudio) VegaColors.Accent.copy(alpha = 0.65f) else VegaColors.Border.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (hasAudio) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (hasAudio) "Ses açık" else "Sessiz",
                    tint = if (hasAudio) VegaColors.Accent else VegaColors.TextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = title,
                    color = if (hasAudio) Color.White else VegaColors.TextSecondary,
                    fontSize = VegaText.Caption,
                    fontWeight = if (hasAudio) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun mapDualViewResizeMode(aspectRatio: AspectRatioMode): Int = when (aspectRatio) {
    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

@Composable
fun DualViewChannelPicker(
    visible: Boolean,
    isPortrait: Boolean,
    liveStreams: List<LiveStream>,
    currentStreamId: Int,
    onChannelSelected: (LiveStream) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredChannels = remember(liveStreams, searchQuery, currentStreamId) {
        if (searchQuery.isBlank()) {
            liveStreams.filter { it.streamId != currentStreamId }
        } else {
            liveStreams.filter {
                it.streamId != currentStreamId && it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val closeFocusRequester = remember { FocusRequester() }

    if (visible) {
        BackHandler(enabled = true) {
            onDismiss()
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            repeat(4) {
                delay(60)
                val ok = runCatching { closeFocusRequester.requestFocus() }.isSuccess
                if (ok) return@LaunchedEffect
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Arka karartma (dokunmada kapatır; D-pad odak tuzağı oluşturmaz)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VegaColors.Scrim)
                    .pointerInput(onDismiss) {
                        detectTapGestures { onDismiss() }
                    }
            )

            // Sağ kenara yaslı tam yükseklik TiviMate kanal seçici çekmecesi
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(if (isPortrait) 300.dp else 364.dp)
                    .background(VegaColors.OverlayPanel)
                    .pointerInput(Unit) {
                        detectTapGestures { /* Çekmece içi dokunmayı yut */ }
                    }
            ) {
                // Sol 0.5dp ince ayırıcı çizgi
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(VegaColors.Border.copy(alpha = 0.55f))
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .displayCutoutPadding()
                ) {
                    // Başlık satırı
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "İkinci kanalı seç",
                                color = Color.White,
                                fontSize = VegaText.SectionTitle,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Çift ekran iki eşzamanlı bağlantı kullanır",
                                color = VegaColors.TextMuted,
                                fontSize = VegaText.Caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        var isCloseFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isCloseFocused) VegaColors.Accent else Color.White.copy(alpha = 0.08f))
                                .focusRequester(closeFocusRequester)
                                .dpadFocusable(
                                    shape = CircleShape,
                                    drawFocusBorder = false,
                                    scale = 1.06f,
                                    onClick = onDismiss,
                                    onFocusStateChange = { isCloseFocused = it }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = if (isCloseFocused) VegaColors.OnAccent else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = VegaColors.Border.copy(alpha = 0.45f)
                    )

                    // Sade Arama Alanı
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        OutlinedTextField(
                            keyboardOptions = tvKeyboardOptions(),
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .tvOpenKeyboardOnOk()
                                .fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = "Kanal ara...",
                                    color = VegaColors.TextMuted,
                                    fontSize = VegaText.Secondary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = VegaColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    DualViewBarIconButton(
                                        icon = Icons.Default.Clear,
                                        contentDescription = "Temizle",
                                        tint = VegaColors.TextSecondary,
                                        onClick = { searchQuery = "" }
                                    )
                                }
                            },
                            singleLine = true,
                            shape = VegaShapes.Small,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VegaColors.Accent,
                                unfocusedBorderColor = VegaColors.Border.copy(alpha = 0.6f),
                                focusedContainerColor = VegaColors.SurfaceVariant,
                                unfocusedContainerColor = VegaColors.SurfaceVariant,
                                focusedTextColor = VegaColors.TextPrimary,
                                unfocusedTextColor = VegaColors.TextPrimary
                            )
                        )
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = VegaColors.Border.copy(alpha = 0.45f)
                    )

                    // İnce 0.5dp çizgiyle ayrılmış TiviMate Kanal Satırları
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        itemsIndexed(filteredChannels, key = { idx, ch -> "${ch.streamId}_$idx" }) { _, channel ->
                            var isItemFocused by remember { mutableStateOf(false) }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .background(if (isItemFocused) VegaColors.Accent else Color.Transparent)
                                        .dpadFocusable(
                                            shape = RectangleShape,
                                            drawFocusBorder = false,
                                            scale = 1.0f,
                                            onClick = { onChannelSelected(channel) },
                                            onFocusStateChange = { isItemFocused = it }
                                        )
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!channel.streamIcon.isNullOrBlank()) {
                                        AsyncImage(
                                            model = channel.streamIcon,
                                            contentDescription = channel.name,
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = channel.name,
                                            color = if (isItemFocused) VegaColors.OnAccent else VegaColors.TextPrimary,
                                            fontSize = VegaText.Body,
                                            fontWeight = if (isItemFocused) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!channel.currentProgram.isNullOrBlank()) {
                                            Text(
                                                text = channel.currentProgram,
                                                color = if (isItemFocused) VegaColors.OnAccent.copy(alpha = 0.78f) else VegaColors.TextMuted,
                                                fontSize = VegaText.Caption,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = VegaColors.Border.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
