package com.tivimax.player.ui.player.components

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.delay

// ============================================================================
// VEGA OYNATICI KATMANI (STREAMVAULT BUZLU CAM + ANDROID TV KUMANDA UYUMLU)
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
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = VegaShapes.Pill,
            color = if (isFocused) VegaColors.Accent else VegaColors.OverlayPanel,
            border = BorderStroke(1.dp, if (isFocused) Color.White else VegaColors.Accent),
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
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = label,
                    tint = if (isFocused) VegaColors.OnAccent else VegaColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    color = if (isFocused) VegaColors.OnAccent else VegaColors.TextPrimary,
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * StreamVault `VodPlayerControlsOverlay` + `ChannelView.png` uyumlu cam kontrol katmanı.
 *
 * - Üst şerit: Yuvarlatılmış geri düğmesi (`OverlayPill` + `Border`), başlık + bölüm/kalite rozeti, sağda saat / hızlı haplar.
 * - Alt cam kontrol paneli (`OverlayPanel`, `RoundedCornerShape(18.dp)`, `1.dp` `Border`):
 *   - Parlak vurgulu zaman çubuğu (D-pad sol/sağ ile ±10 sn sarma, geçen süre solda, toplam/kalan süre sağda)
 *   - Büyük ulaşım düğmeleri (`Önceki`, `-10 sn`, otomatik odaklı `Oynat/Duraklat`, `+10 sn`, `Sonraki`)
 *   - StreamVault iki satırlı hızlı aksiyon karoları (`Ses`, `Altyazı`, `Oran`, `Hız`, `Bölümler`, `Ayarlar`).
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
    val tilesFocus = remember { FocusRequester() }

    // TV'de katman açıldığında odak doğrudan Oynat/Duraklat düğmesine gelsin
    LaunchedEffect(isTv) {
        if (isTv) {
            repeat(4) {
                delay(50)
                if (runCatching { playFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.72f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.86f)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ====================================================================
        // ÜST ŞERİT (ChannelView.png / StreamVault)
        // ====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
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

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isLive) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .clip(VegaShapes.Small)
                                    .background(VegaColors.OverlayPill)
                                    .border(1.dp, VegaColors.Border, VegaShapes.Small)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(VegaColors.Live)
                                )
                                Text(
                                    text = "Canlı",
                                    color = Color.White,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = VegaText.SectionTitle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (!qualityBadge.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = VegaColors.OverlayPill,
                                border = BorderStroke(1.dp, VegaColors.Border)
                            ) {
                                Text(
                                    text = qualityBadge,
                                    color = VegaColors.TextSecondary,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Sağ: Saat ve Hızlı Haplar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (clockText.isNotBlank()) {
                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.OverlayPanel,
                        border = BorderStroke(1.dp, VegaColors.Border)
                    ) {
                        Text(
                            text = clockText,
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Body,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                if (!isTv) {
                    OverlayCircleButton(
                        icon = Icons.Default.Cast,
                        contentDescription = "Yansıt",
                        onClick = onCastClick
                    )
                }

                OverlayCircleButton(
                    icon = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Ekran kilidi",
                    isHighlighted = isLocked,
                    onClick = onLockToggle
                )
            }
        }

        // YÜKLENİYOR GÖSTERGESİ
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp),
                strokeWidth = 3.dp,
                color = VegaColors.Accent,
                trackColor = Color.White.copy(alpha = 0.14f)
            )
        }

        // ====================================================================
        // ALT BUZLU GECE CAM KONTROL PANELİ (ChannelView.png + VodPlayerControlsOverlay)
        // ====================================================================
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = VegaColors.OverlayPanel,
            border = BorderStroke(1.dp, VegaColors.Border),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. İLERLEME / SARMA ÇUBUĞU (Sadece Film/Dizi)
                if (!isLive && totalDurationMs > 0L) {
                    var isTimelineFocused by remember { mutableStateOf(false) }
                    val progress = (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    val remainingMs = (totalDurationMs - currentTimeMs).coerceAtLeast(0L)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDuration(currentTimeMs),
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${formatDuration(totalDurationMs)}  (-${formatDuration(remainingMs)})",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Slider(
                            value = progress,
                            onValueChange = { newPct ->
                                onSeek((newPct * totalDurationMs).toLong())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .focusRequester(timelineFocus)
                                .focusProperties { down = playFocus }
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
                                thumbColor = if (isTimelineFocused) Color.White else VegaColors.Accent,
                                activeTrackColor = VegaColors.Accent,
                                inactiveTrackColor = VegaColors.SurfaceAccent
                            )
                        )
                    }
                }

                // 2. BÜYÜK ULAŞIM DÜĞMELERİ (-10 sn, Oynat/Duraklat, +10 sn, Önceki/Sonraki Bölüm)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isLive && hasPreviousEpisode && onPreviousEpisode != null) {
                            OverlayTransportButton(
                                icon = Icons.Default.SkipPrevious,
                                contentDescription = "Önceki bölüm",
                                onClick = onPreviousEpisode,
                                modifier = Modifier.focusProperties {
                                    if (totalDurationMs > 0L) up = timelineFocus
                                    down = tilesFocus
                                }
                            )
                        }

                        if (!isLive) {
                            OverlayTransportButton(
                                icon = Icons.Default.Replay10,
                                contentDescription = "10 sn geri",
                                onClick = onSkipBackward,
                                modifier = Modifier.focusProperties {
                                    if (totalDurationMs > 0L) up = timelineFocus
                                    down = tilesFocus
                                }
                            )
                        }

                        OverlayTransportButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                            isPrimary = true,
                            onClick = onPlayPauseToggle,
                            modifier = Modifier
                                .focusRequester(playFocus)
                                .focusProperties {
                                    if (!isLive && totalDurationMs > 0L) up = timelineFocus
                                    down = tilesFocus
                                }
                        )

                        if (!isLive) {
                            OverlayTransportButton(
                                icon = Icons.Default.Forward10,
                                contentDescription = "10 sn ileri",
                                onClick = onSkipForward,
                                modifier = Modifier.focusProperties {
                                    if (totalDurationMs > 0L) up = timelineFocus
                                    down = tilesFocus
                                }
                            )
                        }

                        if (!isLive && hasNextEpisode) {
                            OverlayTransportButton(
                                icon = Icons.Default.SkipNext,
                                contentDescription = "Sonraki bölüm",
                                onClick = onNextEpisode,
                                modifier = Modifier.focusProperties {
                                    if (totalDurationMs > 0L) up = timelineFocus
                                    down = tilesFocus
                                }
                            )
                        }
                    }
                }

                // 3. STREAMVAULT İKİ SATIRLI HIZLI AKSİYON KAROLARI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OverlayActionTile(
                        eyebrow = "Ses",
                        value = audioLabel,
                        onClick = onAudioClick ?: onSettingsClick,
                        modifier = Modifier
                            .focusRequester(tilesFocus)
                            .focusProperties { up = playFocus }
                    )
                    OverlayActionTile(
                        eyebrow = "Altyazı",
                        value = subtitleLabel,
                        onClick = onSubtitleClick ?: onSettingsClick,
                        modifier = Modifier.focusProperties { up = playFocus }
                    )
                    OverlayActionTile(
                        eyebrow = "Oran",
                        value = aspectRatioLabel,
                        onClick = onAspectRatioClick ?: onSettingsClick,
                        modifier = Modifier.focusProperties { up = playFocus }
                    )
                    if (!isLive) {
                        OverlayActionTile(
                            eyebrow = "Hız",
                            value = playbackSpeedLabel,
                            onClick = onSpeedClick ?: onSettingsClick,
                            modifier = Modifier.focusProperties { up = playFocus }
                        )
                        OverlayActionTile(
                            eyebrow = "Bölümler",
                            value = "Liste",
                            onClick = onEpisodesClick,
                            modifier = Modifier.focusProperties { up = playFocus }
                        )
                    }
                    OverlayActionTile(
                        eyebrow = "Ayarlar",
                        value = "Tümü",
                        onClick = onSettingsClick,
                        modifier = Modifier.focusProperties { up = playFocus }
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Pill,
        color = when {
            isFocused -> VegaColors.Accent
            isHighlighted -> VegaColors.Accent.copy(alpha = 0.24f)
            else -> VegaColors.OverlayPill
        },
        border = BorderStroke(
            1.dp,
            if (isFocused) Color.White else if (isHighlighted) VegaColors.Accent else VegaColors.Border
        ),
        modifier = modifier
            .size(40.dp)
            .dpadFocusable(
                shape = VegaShapes.Pill,
                focusedBorderColor = Color.White,
                scale = 1.05f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isFocused) VegaColors.OnAccent else Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun OverlayTransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val sizeDp = if (isPrimary) 54.dp else 44.dp
    val iconDp = if (isPrimary) 30.dp else 22.dp

    val containerColor = when {
        isFocused -> VegaColors.Accent
        isPrimary -> VegaColors.Accent.copy(alpha = 0.26f)
        else -> VegaColors.SurfaceElevated.copy(alpha = 0.92f)
    }
    val tintColor = when {
        isFocused -> VegaColors.OnAccent
        else -> Color.White
    }

    Surface(
        shape = VegaShapes.Pill,
        color = containerColor,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = when {
                isFocused -> Color.White
                isPrimary -> VegaColors.Accent
                else -> VegaColors.Border
            }
        ),
        modifier = modifier
            .size(sizeDp)
            .dpadFocusable(
                shape = VegaShapes.Pill,
                focusedBorderColor = Color.White,
                scale = if (isPrimary) 1.06f else 1.05f,
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
private fun OverlayActionTile(
    eyebrow: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val tileShape = RoundedCornerShape(11.dp)

    val containerColor = when {
        isFocused -> VegaColors.Accent
        isActive -> VegaColors.Accent.copy(alpha = 0.22f)
        else -> VegaColors.SurfaceElevated.copy(alpha = 0.92f)
    }
    val eyebrowColor = when {
        isFocused -> VegaColors.OnAccent.copy(alpha = 0.8f)
        isActive -> VegaColors.Gold
        else -> VegaColors.Accent
    }
    val valueColor = when {
        isFocused -> VegaColors.OnAccent
        else -> VegaColors.TextPrimary
    }

    Surface(
        shape = tileShape,
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (isFocused) Color.White else VegaColors.Border
        ),
        modifier = modifier
            .widthIn(min = 96.dp)
            .height(50.dp)
            .dpadFocusable(
                focusedBorderColor = Color.White,
                shape = tileShape,
                scale = 1.04f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = eyebrow,
                color = eyebrowColor,
                fontSize = VegaText.Caption,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Dikey mod alt bilgi alanı (Bölümler, Detaylar, Ses ve altyazı).
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
            .padding(12.dp)
    ) {
        Text(
            text = title.substringBefore("-").trim(),
            color = VegaColors.TextPrimary,
            fontSize = VegaText.BodyProminent,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { index, tabName ->
                val isSelected = selectedTab == index
                var isFocused by remember { mutableStateOf(false) }

                Surface(
                    shape = VegaShapes.Small,
                    color = when {
                        isFocused -> VegaColors.Accent
                        isSelected -> VegaColors.Accent.copy(alpha = 0.18f)
                        else -> VegaColors.SurfaceVariant
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isFocused) Color.White else if (isSelected) VegaColors.Accent else VegaColors.Border
                    ),
                    modifier = Modifier.dpadFocusable(
                        shape = VegaShapes.Small,
                        focusedBorderColor = Color.White,
                        scale = 1.03f,
                        onClick = { selectedTab = index },
                        onFocusStateChange = { isFocused = it }
                    )
                ) {
                    Text(
                        text = tabName,
                        color = when {
                            isFocused -> VegaColors.OnAccent
                            isSelected -> VegaColors.Accent
                            else -> VegaColors.TextSecondary
                        },
                        fontSize = VegaText.Secondary,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (selectedTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        var isNextFocused by remember { mutableStateOf(false) }
                        Surface(
                            shape = VegaShapes.Medium,
                            color = if (isNextFocused) VegaColors.Accent else VegaColors.SurfaceVariant,
                            border = BorderStroke(1.dp, if (isNextFocused) Color.White else VegaColors.Border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .dpadFocusable(
                                    shape = VegaShapes.Medium,
                                    focusedBorderColor = Color.White,
                                    scale = 1.02f,
                                    onClick = onNextEpisode,
                                    onFocusStateChange = { isNextFocused = it }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
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
