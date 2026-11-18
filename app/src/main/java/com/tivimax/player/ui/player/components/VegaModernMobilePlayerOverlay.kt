package com.tivimax.player.ui.player.components

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaSizes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.delay

// ============================================================================
// VEGA SİNEMA OYNATICI KATMANI (MİNİMAL VİDEO ÜSTÜ HUD + TV KUMANDA UYUMLU)
// ============================================================================

val NeonPurple: Color get() = VegaColors.Accent
val NeonPink: Color get() = VegaColors.Accent
val FrostedGlassBackground: Color get() = VegaColors.OverlayPill
val FrostedGlassBorder: Color get() = VegaColors.Border

/**
 * Dikey ve yatay yerleşimleri destekleyen tam oynatıcı ekranı kabı.
 */
@Composable
fun VegaModernMobilePlayerScreen(
    title: String = "Vega IPTV",
    subtitle: String = "",
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentTimeMs: Long,
    totalDurationMs: Long,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onNextEpisode: () -> Unit,
    onBackClick: () -> Unit,
    onCastClick: () -> Unit,
    onEpisodesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    videoSurface: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isLocked by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VegaColors.Background)
    ) {
        if (isLandscape) {
            Box(modifier = Modifier.fillMaxSize()) {
                videoSurface()

                if (!isLocked) {
                    VegaPlayerControlsOverlay(
                        title = title,
                        subtitle = subtitle,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        isLocked = false,
                        currentTimeMs = currentTimeMs,
                        totalDurationMs = totalDurationMs,
                        onPlayPauseToggle = onPlayPauseToggle,
                        onSeek = onSeek,
                        onSkipBackward = onSkipBackward,
                        onSkipForward = onSkipForward,
                        onNextEpisode = onNextEpisode,
                        onBackClick = onBackClick,
                        onCastClick = onCastClick,
                        onLockToggle = { isLocked = true },
                        onEpisodesClick = onEpisodesClick,
                        onSettingsClick = onSettingsClick
                    )
                } else {
                    UnlockOverlayPill(
                        label = "Ekran kilidini aç",
                        onUnlock = { isLocked = false }
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    videoSurface()

                    if (!isLocked) {
                        VegaPlayerControlsOverlay(
                            title = title,
                            subtitle = subtitle,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            isLocked = false,
                            currentTimeMs = currentTimeMs,
                            totalDurationMs = totalDurationMs,
                            onPlayPauseToggle = onPlayPauseToggle,
                            onSeek = onSeek,
                            onSkipBackward = onSkipBackward,
                            onSkipForward = onSkipForward,
                            onNextEpisode = onNextEpisode,
                            onBackClick = onBackClick,
                            onCastClick = onCastClick,
                            onLockToggle = { isLocked = true },
                            onEpisodesClick = onEpisodesClick,
                            onSettingsClick = onSettingsClick
                        )
                    } else {
                        UnlockOverlayPill(
                            label = "Kilidi aç",
                            onUnlock = { isLocked = false }
                        )
                    }
                }

                VegaPlayerPortraitMenuArea(
                    title = title,
                    onNextEpisode = onNextEpisode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun UnlockOverlayPill(
    label: String,
    onUnlock: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = VegaShapes.Pill,
            color = if (isFocused) VegaColors.Accent else Color.Black.copy(alpha = 0.65f),
            border = if (isFocused) null else BorderStroke(0.75.dp, Color.White.copy(alpha = 0.22f)),
            modifier = Modifier
                .focusRequester(focusRequester)
                .dpadFocusable(
                    shape = VegaShapes.Pill,
                    focusedBorderColor = Color.White,
                    scale = 1.04f,
                    onClick = onUnlock,
                    onFocusStateChange = { isFocused = it }
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = label,
                    tint = if (isFocused) VegaColors.OnAccent else Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    color = if (isFocused) VegaColors.OnAccent else Color.White,
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Minimal sinema oynatıcı katmanı (Infuse / Apple TV+ yaklaşımı):
 *
 * - Üst ve alt yumuşak gradyan karartma, video ortası tamamen açık.
 * - Üst şerit: yuvarlak çerçevesiz geri düğmesi, tek satır başlık (+ canlı noktası / bölüm bilgisi), sağda kompakt çerçevesiz ikonlar.
 * - Orta ulaşım kontrolleri: -10 sn, büyük Oynat/Duraklat (64dp), +10 sn (parıltı/kutu karmaşası yok).
 * - Alt şerit: ince sarma çubuğu, geçen süre & kalan süre (-42 dk), sade ikincil aksiyon satırı.
 */
@Composable
fun VegaPlayerControlsOverlay(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isLocked: Boolean,
    currentTimeMs: Long,
    totalDurationMs: Long,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onNextEpisode: () -> Unit,
    onBackClick: () -> Unit,
    onCastClick: () -> Unit,
    onLockToggle: () -> Unit,
    onEpisodesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
    qualityBadge: String? = null,
    clockText: String = "",
    aspectRatioLabel: String = "Orijinal",
    playbackSpeedLabel: String = "1.0x",
    audioLabel: String = "Varsayılan",
    subtitleLabel: String = "Kapalı",
    hasPreviousEpisode: Boolean = false,
    hasNextEpisode: Boolean = false,
    onPreviousEpisode: (() -> Unit)? = null,
    onAudioClick: (() -> Unit)? = null,
    onSubtitleClick: (() -> Unit)? = null,
    onAspectRatioClick: (() -> Unit)? = null,
    onSpeedClick: (() -> Unit)? = null,
    playButtonFocusRequester: FocusRequester? = null
) {
    val isTv = LocalIsTvDevice.current
    val internalPlayFocus = remember { FocusRequester() }
    val playFocus = playButtonFocusRequester ?: internalPlayFocus
    val timelineFocus = remember { FocusRequester() }
    val actionsFocus = remember { FocusRequester() }

    // TV'de katman açıldığında odak doğrudan Oynat/Duraklat düğmesine gelsin
    LaunchedEffect(isTv) {
        if (isTv) {
            repeat(4) {
                delay(50)
                if (runCatching { playFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ÜST YUMUŞAK GRADYAN KARARTMA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.32f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        // ALT YUMUŞAK GRADYAN KARARTMA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.40f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.38f),
                            Color.Black.copy(alpha = 0.78f)
                        )
                    )
                )
        )

        // ====================================================================
        // ÜST ŞERİT (Minimal geri düğmesi + tek satır başlık + çerçevesiz ikonlar)
        // ====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                OverlayCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    onClick = onBackClick
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = VegaText.SectionTitle,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (isLive) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(VegaColors.Live)
                                )
                                Text(
                                    text = "Canlı",
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (!qualityBadge.isNullOrBlank()) {
                            Text(
                                text = qualityBadge,
                                color = Color.White.copy(alpha = 0.68f),
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = VegaText.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Sağ: Saat ve Kompakt Çerçevesiz İkonlar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (clockText.isNotBlank()) {
                    Text(
                        text = clockText,
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = VegaText.Body,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }

                if (!isTv) {
                    OverlayCircleButton(
                        icon = Icons.Default.Cast,
                        contentDescription = "Yansıt",
                        onClick = onCastClick
                    )
                }

                OverlayCircleButton(
                    icon = Icons.Default.AspectRatio,
                    contentDescription = "Görüntü oranı",
                    onClick = onAspectRatioClick ?: onSettingsClick
                )

                OverlayCircleButton(
                    icon = Icons.Default.Subtitles,
                    contentDescription = "Ses ve altyazı",
                    onClick = onAudioClick ?: onSubtitleClick ?: onSettingsClick
                )

                OverlayCircleButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Ayarlar",
                    onClick = onSettingsClick
                )
            }
        }

        // ====================================================================
        // ORTA ULAŞIM KONTROLLERİ (-10 / Oynat-Duraklat / +10)
        // ====================================================================
        Box(modifier = Modifier.align(Alignment.Center)) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(46.dp),
                    strokeWidth = 2.5.dp,
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.16f)
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isLive && hasPreviousEpisode && onPreviousEpisode != null) {
                        OverlayTransportButton(
                            icon = Icons.Default.SkipPrevious,
                            contentDescription = "Önceki bölüm",
                            onClick = onPreviousEpisode,
                            sizeType = TransportSize.COMPACT,
                            modifier = Modifier.focusProperties {
                                down = if (totalDurationMs > 0L) timelineFocus else actionsFocus
                            }
                        )
                    }

                    if (!isLive) {
                        OverlayTransportButton(
                            icon = Icons.Default.Replay10,
                            contentDescription = "10 sn geri",
                            onClick = onSkipBackward,
                            sizeType = TransportSize.SECONDARY,
                            modifier = Modifier.focusProperties {
                                down = if (totalDurationMs > 0L) timelineFocus else actionsFocus
                            }
                        )
                    }

                    OverlayTransportButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                        sizeType = TransportSize.PRIMARY,
                        onClick = onPlayPauseToggle,
                        modifier = Modifier
                            .focusRequester(playFocus)
                            .focusProperties {
                                down = if (!isLive && totalDurationMs > 0L) timelineFocus else actionsFocus
                            }
                    )

                    if (!isLive) {
                        OverlayTransportButton(
                            icon = Icons.Default.Forward10,
                            contentDescription = "10 sn ileri",
                            onClick = onSkipForward,
                            sizeType = TransportSize.SECONDARY,
                            modifier = Modifier.focusProperties {
                                down = if (totalDurationMs > 0L) timelineFocus else actionsFocus
                            }
                        )
                    }

                    if (!isLive && hasNextEpisode) {
                        OverlayTransportButton(
                            icon = Icons.Default.SkipNext,
                            contentDescription = "Sonraki bölüm",
                            onClick = onNextEpisode,
                            sizeType = TransportSize.COMPACT,
                            modifier = Modifier.focusProperties {
                                down = if (totalDurationMs > 0L) timelineFocus else actionsFocus
                            }
                        )
                    }
                }
            }
        }

        // ====================================================================
        // ALT ŞERİT (İnce zaman çubuğu + geçen/kalan süre + sade ikincil aksiyonlar)
        // ====================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. İNCE ZAMAN ÇUBUĞU (Sadece Film/Dizi)
            if (!isLive && totalDurationMs > 0L) {
                var isTimelineFocused by remember { mutableStateOf(false) }
                val progress = (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                val remainingMs = (totalDurationMs - currentTimeMs).coerceAtLeast(0L)

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = progress,
                        onValueChange = { newPct ->
                            onSeek((newPct * totalDurationMs).toLong())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .focusRequester(timelineFocus)
                            .focusProperties {
                                up = playFocus
                                down = actionsFocus
                            }
                            .onFocusChanged { isTimelineFocused = it.isFocused }
                            .onPreviewKeyEvent { ev ->
                                if (ev.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                                when (ev.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        onSkipBackward()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        onSkipForward()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                        onPlayPauseToggle()
                                        true
                                    }
                                    else -> false
                                }
                            },
                        colors = SliderDefaults.colors(
                            thumbColor = if (isTimelineFocused) VegaColors.Accent else Color.White,
                            activeTrackColor = VegaColors.Accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(currentTimeMs),
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatRemainingCompact(remainingMs),
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 2. SADE İKİNCİL AKSİYON SATIRI (Çerçevesiz minimal düğmeler)
            val upTarget = if (!isLive && totalDurationMs > 0L) timelineFocus else playFocus
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OverlayInlineAction(
                    icon = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    label = if (isLocked) "Kilitli" else "Kilit",
                    isActive = isLocked,
                    onClick = onLockToggle,
                    modifier = Modifier
                        .focusRequester(actionsFocus)
                        .focusProperties { up = upTarget }
                )

                if (!isLive) {
                    OverlayInlineAction(
                        icon = Icons.AutoMirrored.Filled.List,
                        label = "Bölümler",
                        onClick = onEpisodesClick,
                        modifier = Modifier.focusProperties { up = upTarget }
                    )
                    OverlayInlineAction(
                        icon = Icons.Default.Speed,
                        label = playbackSpeedLabel,
                        isActive = playbackSpeedLabel != "1.0x",
                        onClick = onSpeedClick ?: onSettingsClick,
                        modifier = Modifier.focusProperties { up = upTarget }
                    )
                }

                OverlayInlineAction(
                    icon = Icons.Default.Audiotrack,
                    label = audioLabel.takeIf { it.isNotBlank() && it != "Varsayılan" } ?: "Ses",
                    onClick = onAudioClick ?: onSettingsClick,
                    modifier = Modifier.focusProperties { up = upTarget }
                )

                OverlayInlineAction(
                    icon = Icons.Default.ClosedCaption,
                    label = subtitleLabel.takeIf { it.isNotBlank() && it != "Kapalı" } ?: "Altyazı",
                    isActive = subtitleLabel.isNotBlank() && subtitleLabel != "Kapalı",
                    onClick = onSubtitleClick ?: onSettingsClick,
                    modifier = Modifier.focusProperties { up = upTarget }
                )

                OverlayInlineAction(
                    icon = Icons.Default.AspectRatio,
                    label = aspectRatioLabel,
                    onClick = onAspectRatioClick ?: onSettingsClick,
                    modifier = Modifier.focusProperties { up = upTarget }
                )

                OverlayInlineAction(
                    icon = Icons.Default.Tune,
                    label = qualityBadge?.takeIf { it.isNotBlank() } ?: "Kalite",
                    onClick = onSettingsClick,
                    modifier = Modifier.focusProperties { up = upTarget }
                )
            }
        }
    }
}

private enum class TransportSize { PRIMARY, SECONDARY, COMPACT }

@Composable
private fun OverlayCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(VegaSizes.IconButton)
            .clip(VegaShapes.Pill)
            .background(
                when {
                    isFocused -> VegaColors.Accent
                    isHighlighted -> VegaColors.Accent.copy(alpha = 0.24f)
                    else -> Color.Black.copy(alpha = 0.26f)
                }
            )
            .dpadFocusable(
                shape = VegaShapes.Pill,
                focusedBorderColor = Color.White,
                scale = 1.06f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) VegaColors.OnAccent else Color.White,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun OverlayTransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    sizeType: TransportSize,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val sizeDp = when (sizeType) {
        TransportSize.PRIMARY -> 64.dp
        TransportSize.SECONDARY -> 56.dp
        TransportSize.COMPACT -> 48.dp
    }
    val iconDp = when (sizeType) {
        TransportSize.PRIMARY -> 38.dp
        TransportSize.SECONDARY -> 30.dp
        TransportSize.COMPACT -> 24.dp
    }

    val containerColor = when {
        isFocused -> VegaColors.Accent
        sizeType == TransportSize.PRIMARY -> Color.Black.copy(alpha = 0.44f)
        else -> Color.Black.copy(alpha = 0.24f)
    }
    val tintColor = if (isFocused) VegaColors.OnAccent else Color.White

    Surface(
        shape = VegaShapes.Pill,
        color = containerColor,
        border = if (!isFocused && sizeType == TransportSize.PRIMARY) {
            BorderStroke(0.75.dp, Color.White.copy(alpha = 0.20f))
        } else null,
        modifier = modifier
            .size(sizeDp)
            .dpadFocusable(
                shape = VegaShapes.Pill,
                focusedBorderColor = Color.White,
                scale = if (sizeType == TransportSize.PRIMARY) 1.06f else 1.05f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tintColor,
                modifier = Modifier.size(iconDp)
            )
        }
    }
}

@Composable
private fun OverlayInlineAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    val containerColor = when {
        isFocused -> VegaColors.Accent
        isActive -> VegaColors.Accent.copy(alpha = 0.20f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val contentColor = when {
        isFocused -> VegaColors.OnAccent
        isActive -> VegaColors.Accent
        else -> Color.White.copy(alpha = 0.90f)
    }

    Row(
        modifier = modifier
            .height(36.dp)
            .clip(VegaShapes.Pill)
            .background(containerColor)
            .dpadFocusable(
                shape = VegaShapes.Pill,
                focusedBorderColor = Color.White,
                scale = 1.04f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = VegaText.Caption,
            fontWeight = if (isFocused || isActive) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Dikey mod alt bilgi alanı (Bölümler, Detay ve konu, Ses ve altyazı).
 */
@Composable
fun VegaPlayerPortraitMenuArea(
    title: String,
    onNextEpisode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Bölümler", "Detay ve konu", "Ses ve altyazı")

    Column(
        modifier = modifier
            .background(VegaColors.Background)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = title.substringBefore("-").trim(),
            color = VegaColors.TextPrimary,
            fontSize = VegaText.SectionTitle,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { index, tabName ->
                val isSelected = selectedTab == index
                var isFocused by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .clip(VegaShapes.Pill)
                        .background(
                            when {
                                isFocused -> VegaColors.Accent
                                isSelected -> VegaColors.Accent.copy(alpha = 0.18f)
                                else -> Color.White.copy(alpha = 0.06f)
                            }
                        )
                        .dpadFocusable(
                            shape = VegaShapes.Pill,
                            focusedBorderColor = Color.White,
                            scale = 1.03f,
                            onClick = { selectedTab = index },
                            onFocusStateChange = { isFocused = it }
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = tabName,
                        color = when {
                            isFocused -> VegaColors.OnAccent
                            isSelected -> VegaColors.Accent
                            else -> VegaColors.TextSecondary
                        },
                        fontSize = VegaText.Secondary,
                        fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(thickness = 0.5.dp, color = VegaColors.Border.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(10.dp))

        when (selectedTab) {
            0 -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        var isNextFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(VegaShapes.Small)
                                .background(
                                    if (isNextFocused) VegaColors.Accent else Color.Transparent
                                )
                                .dpadFocusable(
                                    shape = VegaShapes.Small,
                                    focusedBorderColor = Color.White,
                                    scale = 1.02f,
                                    onClick = onNextEpisode,
                                    onFocusStateChange = { isNextFocused = it }
                                )
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = null,
                                tint = if (isNextFocused) VegaColors.OnAccent else VegaColors.Accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Sonraki bölüme geç",
                                color = if (isNextFocused) VegaColors.OnAccent else VegaColors.TextPrimary,
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            1 -> {
                Text(
                    text = title,
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Secondary,
                    lineHeight = 20.sp
                )
            }
            2 -> {
                Text(
                    text = "Ses ve altyazı ayarlarını oynatıcı çubuğundaki Ayarlar menüsünden değiştirebilirsin.",
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Secondary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun formatRemainingCompact(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
    val totalMinutes = (totalSeconds + 59L) / 60L
    return when {
        totalSeconds < 60L -> "-$totalSeconds sn"
        totalMinutes >= 60L -> {
            val hours = totalMinutes / 60L
            val mins = totalMinutes % 60L
            if (mins > 0L) "-$hours sa $mins dk" else "-$hours sa"
        }
        else -> "-$totalMinutes dk"
    }
}
