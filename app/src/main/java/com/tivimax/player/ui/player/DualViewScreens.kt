package com.tivimax.player.ui.player

import com.tivimax.player.MainActivity
import android.app.RemoteAction
import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import java.util.Locale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.TiviPlayerManager
import com.tivimax.player.player.TrackInfo
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaSizes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.VegaChoiceChip
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.CategoryManagementDialog
import com.tivimax.player.ui.player.components.NumericKeypadDialog
import com.tivimax.player.ui.player.components.TiviBottomInfoCard
import com.tivimax.player.ui.player.components.TiviNumericZapperBox
import com.tivimax.player.ui.player.components.TiviOverlayGuide
import com.tivimax.player.ui.player.components.TiviRecentChannelsBar
import com.tivimax.player.ui.player.components.PlayerSettingsPanel
import com.tivimax.player.ui.player.components.SettingsTab
import com.tivimax.player.ui.player.components.PlayerPillButton
import com.tivimax.player.ui.player.components.BouncyIconButton
import com.tivimax.player.ui.player.components.LocalPlayerInteraction
import com.tivimax.player.ui.player.components.LivePlayerActionBar
import com.tivimax.player.ui.player.components.LiveMorePanel
import com.tivimax.player.ui.player.components.PlayerSidePanel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.view.KeyEvent

import com.tivimax.player.util.DeviceMode
import com.tivimax.player.util.LocalDeviceMode
import com.tivimax.player.util.LocalIsTvDevice
import com.tivimax.player.util.LocalOrientationManager

@OptIn(UnstableApi::class)
@Composable
fun DualViewSplitScreen(
    playerManager: com.tivimax.player.player.TiviPlayerManager,
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (isPortrait) {
            Column(modifier = Modifier.fillMaxSize()) {
                // First Screen (Top)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
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

                // Split divider line
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(VegaColors.Accent.copy(alpha = 0.6f))
                )

                // Second Screen (Bottom)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
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
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                // First Screen (Left)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
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

                // Split divider line
                Spacer(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(VegaColors.Accent.copy(alpha = 0.6f))
                )

                // Second Screen (Right)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
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

        // Floating Dual-View Action Pill (Top Center)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.4f)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Live Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(VegaColors.Live)
                )

                Text(
                    text = "ÇİFT EKRAN",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Swap Button
                IconButton(
                    onClick = onSwap,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Ekranları değiştir",
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Change 2nd channel Button
                IconButton(
                    onClick = onChangeSecondary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "2. kanalı değiştir",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tek ekrana dön",
                        tint = VegaColors.Danger,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun DualViewSingleWindow(
    playerManager: com.tivimax.player.player.TiviPlayerManager,
    isPrimary: Boolean,
    title: String,
    hasAudio: Boolean,
    isBuffering: Boolean,
    aspectRatio: AspectRatioMode,
    onFocusAudio: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onFocusAudio() }
            .border(
                width = if (hasAudio) 2.dp else 1.dp,
                color = if (hasAudio) VegaColors.Accent else VegaColors.Border
            )
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    if (isPrimary) {
                        playerManager.initializePlayer()
                        playerManager.attachView(this, isPrimary = true, tag = "DualViewSingleWindow-primary")
                    } else {
                        player = playerManager.secondaryExoPlayer
                    }
                    resizeMode = when (aspectRatio) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            },
            update = { playerView ->
                if (isPrimary) {
                    playerManager.initializePlayer()
                    playerManager.attachView(playerView, isPrimary = true, tag = "DualViewSingleWindow-primary")
                } else {
                    val targetPlayer = playerManager.secondaryExoPlayer
                    if (playerView.player != targetPlayer) {
                        playerView.player = targetPlayer
                    }
                }
                playerView.resizeMode = when (aspectRatio) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            onRelease = { playerView ->
                if (isPrimary) {
                    playerManager.detachView(playerView, tag = "DualViewSingleWindow-primary")
                } else {
                    playerView.player = null
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Indicator
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = VegaColors.Accent,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Overlay Badge: Title & Audio Status
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (hasAudio) VegaColors.Accent else Color.Black.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (hasAudio) VegaColors.Accent else Color.White.copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasAudio) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = if (hasAudio) Color.Black else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (hasAudio) "SES AÇIK" else "SESSİZ",
                        color = if (hasAudio) Color.Black else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
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
    val filteredChannels = remember(liveStreams, searchQuery) {
        if (searchQuery.isBlank()) {
            liveStreams.filter { it.streamId != currentStreamId }
        } else {
            liveStreams.filter {
                it.streamId != currentStreamId && it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    PlayerSidePanel(
        visible = visible,
        title = "İkinci kanalı seç",
        onClose = onDismiss,
        isPortrait = isPortrait
    ) {
        Text(
            text = "Not: Çift ekran aynı anda 2 bağlantı kullanır; sağlayıcınızın çoklu bağlantıyı desteklemesi gerekir.",
            color = VegaColors.TextMuted,
            fontSize = VegaText.Secondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Arama Çubuğu
        OutlinedTextField(
            keyboardOptions = tvKeyboardOptions(),
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.tvOpenKeyboardOnOk().fillMaxWidth(),
            placeholder = { Text("Kanal ara...", color = VegaColors.TextMuted, fontSize = VegaText.Secondary) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = VegaColors.Accent)
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Temizle", tint = VegaColors.TextPrimary)
                    }
                }
            },
            singleLine = true,
            shape = VegaShapes.Medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VegaColors.Accent,
                unfocusedBorderColor = VegaColors.Border,
                focusedContainerColor = VegaColors.SurfaceVariant,
                unfocusedContainerColor = VegaColors.SurfaceVariant,
                focusedTextColor = VegaColors.TextPrimary,
                unfocusedTextColor = VegaColors.TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Kanal Listesi
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredChannels, key = { it.streamId }) { channel ->
                var isItemFocused by remember { mutableStateOf(false) }

                Surface(
                    onClick = { onChannelSelected(channel) },
                    shape = VegaShapes.Small,
                    color = if (isItemFocused) VegaColors.Accent.copy(alpha = 0.25f) else VegaColors.SurfaceVariant,
                    border = BorderStroke(
                        if (isItemFocused) 2.dp else 1.dp,
                        if (isItemFocused) VegaColors.Accent else VegaColors.Border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .onFocusChanged { isItemFocused = it.isFocused }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!channel.streamIcon.isNullOrBlank()) {
                            AsyncImage(
                                model = channel.streamIcon,
                                contentDescription = channel.name,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channel.name,
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!channel.currentProgram.isNullOrBlank()) {
                                Text(
                                    text = "Canlı: ${channel.currentProgram}",
                                    color = VegaColors.TextSecondary,
                                    fontSize = VegaText.Caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
