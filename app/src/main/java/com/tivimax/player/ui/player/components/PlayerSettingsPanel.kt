package com.tivimax.player.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.StreamDiagnostics
import com.tivimax.player.player.TrackInfo
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import java.util.Locale

enum class SettingsTab(val title: String) {
    AUDIO("Ses"),
    SUBTITLE("Altyazı"),
    ASPECT_RATIO("Görüntü"),
    SPEED("Hız")
}

@Composable
fun PlayerSettingsPanel(
    visible: Boolean,
    initialTab: SettingsTab = SettingsTab.AUDIO,
    isLive: Boolean,
    isPortrait: Boolean,
    audioTracks: List<TrackInfo>,
    onSelectAudioTrack: (TrackInfo) -> Unit,
    subtitleTracks: List<TrackInfo>,
    onSelectSubtitleTrack: (TrackInfo) -> Unit,
    onDisableSubtitles: () -> Unit,
    subtitleDelayMs: Long,
    onSubtitleDelayChange: (Long) -> Unit,
    subtitleSize: String,
    onSubtitleSizeChange: (String) -> Unit,
    subtitleColor: String,
    onSubtitleColorChange: (String) -> Unit,
    aspectRatio: AspectRatioMode,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    diagnostics: StreamDiagnostics? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val firstFocus = remember { FocusRequester() }

    var selectedTab by remember(visible, initialTab) {
        mutableStateOf(if (isLive && initialTab == SettingsTab.SPEED) SettingsTab.AUDIO else initialTab)
    }

    val availableTabs = remember(isLive) {
        if (isLive) {
            listOf(SettingsTab.AUDIO, SettingsTab.SUBTITLE, SettingsTab.ASPECT_RATIO)
        } else {
            listOf(SettingsTab.AUDIO, SettingsTab.SUBTITLE, SettingsTab.ASPECT_RATIO, SettingsTab.SPEED)
        }
    }

    PlayerSidePanel(
        visible = visible,
        title = "Ayarlar",
        onClose = onClose,
        modifier = modifier,
        isPortrait = isPortrait,
        firstFocusRequester = firstFocus,
        headerIcon = Icons.Default.Tune
    ) {
        // DÜZ ALT ÇİZGİLİ TIVIMATE SEKME ŞERİDİ (Kutu / çerçeve yok)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            availableTabs.forEach { tab ->
                val isSelected = (selectedTab == tab)
                var isFocused by remember { mutableStateOf(false) }

                val bgColor = if (isFocused) VegaColors.SurfaceVariant else Color.Transparent
                val textColor = when {
                    isSelected -> VegaColors.Accent
                    isFocused -> VegaColors.TextPrimary
                    else -> VegaColors.TextSecondary
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(bgColor)
                        .then(if (isSelected) Modifier.focusRequester(firstFocus) else Modifier)
                        .dpadFocusable(
                            shape = RectangleShape,
                            drawFocusBorder = false,
                            scale = 1.0f,
                            onClick = { selectedTab = tab },
                            onFocusStateChange = { isFocused = it }
                        )
                ) {
                    Text(
                        text = tab.title,
                        fontSize = VegaText.Secondary,
                        fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    if (isSelected || isFocused) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(
                                    if (isSelected) VegaColors.Accent else VegaColors.Accent.copy(alpha = 0.55f)
                                )
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = VegaColors.Border.copy(alpha = 0.45f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // AKTİF SEKME İÇERİĞİ
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                SettingsTab.AUDIO -> AudioTabContent(
                    audioTracks = audioTracks,
                    onSelectAudioTrack = onSelectAudioTrack
                )
                SettingsTab.SUBTITLE -> SubtitleTabContent(
                    subtitleTracks = subtitleTracks,
                    onSelectSubtitleTrack = onSelectSubtitleTrack,
                    onDisableSubtitles = onDisableSubtitles,
                    subtitleDelayMs = subtitleDelayMs,
                    onSubtitleDelayChange = onSubtitleDelayChange,
                    subtitleSize = subtitleSize,
                    onSubtitleSizeChange = onSubtitleSizeChange,
                    subtitleColor = subtitleColor,
                    onSubtitleColorChange = onSubtitleColorChange
                )
                SettingsTab.ASPECT_RATIO -> AspectRatioTabContent(
                    aspectRatio = aspectRatio,
                    onAspectRatioChange = onAspectRatioChange
                )
                SettingsTab.SPEED -> SpeedTabContent(
                    playbackSpeed = playbackSpeed,
                    onPlaybackSpeedChange = onPlaybackSpeedChange
                )
            }
        }

        // YAYIN BİLGİSİ ALT ALANI
        if (diagnostics != null) {
            DiagnosticsFooter(diagnostics = diagnostics)
        }
    }
}

/**
 * TiviMate düz seçim satırı: 48dp yükseklik, sol 3dp vurgu şeridi, odaklanınca anlık `SurfaceVariant`.
 */
@Composable
private fun SettingsSelectionRow(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val rowBackground = if (isFocused) VegaColors.SurfaceVariant else Color.Transparent
    val showIndicator = selected || isFocused
    val iconTint = when {
        selected || isFocused -> VegaColors.Accent
        else -> VegaColors.TextSecondary
    }
    val textColor = when {
        selected -> VegaColors.Accent
        isFocused -> VegaColors.TextPrimary
        else -> VegaColors.TextPrimary.copy(alpha = 0.92f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(rowBackground)
            .dpadFocusable(
                shape = RectangleShape,
                drawFocusBorder = false,
                scale = 1.0f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (showIndicator) VegaColors.Accent else Color.Transparent)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(19.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            color = textColor,
            fontWeight = if (selected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = VegaText.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Seçili",
                tint = VegaColors.Accent,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsHairlineDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = VegaColors.Border.copy(alpha = 0.35f)
    )
}

// ----------------------------------------------------------------------------
// 1. SES SEKMESİ İÇERİĞİ
// ----------------------------------------------------------------------------
@Composable
private fun AudioTabContent(
    audioTracks: List<TrackInfo>,
    onSelectAudioTrack: (TrackInfo) -> Unit
) {
    if (audioTracks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Ek ses parçası bulunamadı\n(Varsayılan ses aktif)",
                color = VegaColors.TextSecondary,
                fontSize = VegaText.Secondary,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            audioTracks.forEachIndexed { index, track ->
                SettingsSelectionRow(
                    title = track.name,
                    icon = Icons.Default.Audiotrack,
                    selected = track.isSelected,
                    onClick = { onSelectAudioTrack(track) }
                )
                if (index < audioTracks.lastIndex) {
                    SettingsHairlineDivider()
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// 2. ALTYAZI SEKMESİ İÇERİĞİ (Düz satırlar + 0.5dp ince ayırıcılar)
// ----------------------------------------------------------------------------
@Composable
private fun SubtitleTabContent(
    subtitleTracks: List<TrackInfo>,
    onSelectSubtitleTrack: (TrackInfo) -> Unit,
    onDisableSubtitles: () -> Unit,
    subtitleDelayMs: Long,
    onSubtitleDelayChange: (Long) -> Unit,
    subtitleSize: String,
    onSubtitleSizeChange: (String) -> Unit,
    subtitleColor: String,
    onSubtitleColorChange: (String) -> Unit
) {
    val anySubSelected = subtitleTracks.any { it.isSelected }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Kapalı Seçeneği
        SettingsSelectionRow(
            title = "Kapalı",
            icon = Icons.Default.SubtitlesOff,
            selected = !anySubSelected,
            onClick = onDisableSubtitles
        )

        // Mevcut Altyazılar
        subtitleTracks.forEach { track ->
            SettingsHairlineDivider()
            SettingsSelectionRow(
                title = track.name,
                icon = Icons.Default.ClosedCaption,
                selected = track.isSelected,
                onClick = { onSelectSubtitleTrack(track) }
            )
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = VegaColors.Border.copy(alpha = 0.50f),
            modifier = Modifier.padding(vertical = 6.dp)
        )

        // GECİKME SATIRI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Gecikme",
                    fontSize = VegaText.Body,
                    fontWeight = FontWeight.Medium,
                    color = if (anySubSelected) VegaColors.TextPrimary else VegaColors.TextMuted
                )
                val delaySec = subtitleDelayMs / 1000f
                Text(
                    text = String.format(Locale.getDefault(), "+%.1f sn", delaySec),
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.SemiBold,
                    color = if (anySubSelected) VegaColors.Accent else VegaColors.TextMuted
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val canDec = anySubSelected && subtitleDelayMs > 0L
                val canInc = anySubSelected && subtitleDelayMs < 5000L

                StepperIconButton(
                    icon = Icons.Default.Remove,
                    contentDescription = "Gecikmeyi azalt",
                    enabled = canDec,
                    onClick = { onSubtitleDelayChange((subtitleDelayMs - 100L).coerceAtLeast(0L)) }
                )

                StepperIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Gecikmeyi artır",
                    enabled = canInc,
                    onClick = { onSubtitleDelayChange((subtitleDelayMs + 100L).coerceAtMost(5000L)) }
                )

                StepperIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Gecikmeyi sıfırla",
                    enabled = canDec,
                    accentTint = true,
                    onClick = { onSubtitleDelayChange(0L) }
                )
            }
        }

        SettingsHairlineDivider()

        // BOYUT SATIRI: Boyut [K] [O] [B] [ÇB]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Boyut",
                fontSize = VegaText.Body,
                fontWeight = FontWeight.Medium,
                color = VegaColors.TextPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val sizeOptions = listOf(
                    "14" to "K",
                    "18" to "O",
                    "22" to "B",
                    "26" to "ÇB"
                )
                val currentNorm = when (subtitleSize) {
                    "KUCUK" -> "14"
                    "ORTA" -> "18"
                    "BUYUK" -> "22"
                    "COK_BUYUK" -> "26"
                    else -> subtitleSize
                }

                sizeOptions.forEach { (sizeVal, label) ->
                    val isSelected = (currentNorm == sizeVal)
                    var isSizeFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(VegaShapes.Small)
                            .background(
                                when {
                                    isSizeFocused -> VegaColors.Accent
                                    isSelected -> VegaColors.Accent.copy(alpha = 0.22f)
                                    else -> Color.White.copy(alpha = 0.06f)
                                }
                            )
                            .dpadFocusable(
                                shape = VegaShapes.Small,
                                drawFocusBorder = false,
                                scale = 1.0f,
                                onClick = { onSubtitleSizeChange(sizeVal) },
                                onFocusStateChange = { isSizeFocused = it }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = VegaText.Secondary,
                            fontWeight = if (isSelected || isSizeFocused) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isSizeFocused -> VegaColors.OnAccent
                                isSelected -> VegaColors.Accent
                                else -> VegaColors.TextSecondary
                            }
                        )
                    }
                }
            }
        }

        SettingsHairlineDivider()

        // RENK SATIRI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Renk",
                fontSize = VegaText.Body,
                fontWeight = FontWeight.Medium,
                color = VegaColors.TextPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val colorOptions = listOf(
                    "BEYAZ" to Color.White,
                    "SARI" to VegaColors.Gold,
                    "YESIL" to VegaColors.Success,
                    "MAVI" to VegaColors.Accent
                )

                colorOptions.forEach { (colorKey, colorVal) ->
                    val isSelected = (subtitleColor == colorKey)
                    var isColorFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (isColorFocused) VegaColors.SurfaceVariant else Color.Transparent
                            )
                            .then(
                                if (isSelected || isColorFocused) {
                                    Modifier.border(
                                        width = if (isColorFocused) 2.dp else 1.5.dp,
                                        color = VegaColors.Accent,
                                        shape = CircleShape
                                    )
                                } else Modifier
                            )
                            .dpadFocusable(
                                shape = CircleShape,
                                drawFocusBorder = false,
                                scale = 1.0f,
                                onClick = { onSubtitleColorChange(colorKey) },
                                onFocusStateChange = { isColorFocused = it }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(colorVal)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    accentTint: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(VegaShapes.Small)
            .background(
                when {
                    !enabled -> Color.White.copy(alpha = 0.03f)
                    isFocused -> VegaColors.Accent
                    else -> Color.White.copy(alpha = 0.08f)
                }
            )
            .then(
                if (enabled) {
                    Modifier.dpadFocusable(
                        shape = VegaShapes.Small,
                        drawFocusBorder = false,
                        scale = 1.0f,
                        onClick = onClick,
                        onFocusStateChange = { isFocused = it }
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> VegaColors.TextMuted.copy(alpha = 0.45f)
                isFocused -> VegaColors.OnAccent
                accentTint -> VegaColors.Accent
                else -> VegaColors.TextPrimary
            },
            modifier = Modifier.size(18.dp)
        )
    }
}

// ----------------------------------------------------------------------------
// 3. GÖRÜNTÜ SEKMESİ İÇERİĞİ (En-Boy Oranı)
// ----------------------------------------------------------------------------
@Composable
private fun AspectRatioTabContent(
    aspectRatio: AspectRatioMode,
    onAspectRatioChange: (AspectRatioMode) -> Unit
) {
    val modes = listOf(
        AspectRatioMode.FIT to "Orijinal",
        AspectRatioMode.FILL to "Doldur",
        AspectRatioMode.ZOOM to "Yakınlaştır",
        AspectRatioMode.RATIO_16_9 to "16:9",
        AspectRatioMode.RATIO_4_3 to "4:3"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        modes.forEachIndexed { index, (mode, label) ->
            SettingsSelectionRow(
                title = label,
                icon = Icons.Default.AspectRatio,
                selected = (aspectRatio == mode),
                onClick = { onAspectRatioChange(mode) }
            )
            if (index < modes.lastIndex) {
                SettingsHairlineDivider()
            }
        }
    }
}

// ----------------------------------------------------------------------------
// 4. HIZ SEKMESİ İÇERİĞİ
// ----------------------------------------------------------------------------
@Composable
private fun SpeedTabContent(
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit
) {
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        speeds.forEachIndexed { index, spd ->
            val label = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x"
            SettingsSelectionRow(
                title = label,
                icon = Icons.Default.Speed,
                selected = (playbackSpeed == spd),
                onClick = { onPlaybackSpeedChange(spd) }
            )
            if (index < speeds.lastIndex) {
                SettingsHairlineDivider()
            }
        }
    }
}

// ----------------------------------------------------------------------------
// YAYIN BİLGİSİ ALT ALANI (Düz ince ayırıcılı, tek odaklı genişletilebilir satır)
// ----------------------------------------------------------------------------
@Composable
internal fun DiagnosticsFooter(diagnostics: StreamDiagnostics) {
    var isExpanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val resBadge = diagnostics.resolutionBadge.ifBlank { diagnostics.resolution }.trim()
    val headerParts = listOf(resBadge, diagnostics.bitrate.trim()).filter { it.isNotBlank() }
    val headerText = if (headerParts.isNotEmpty()) {
        headerParts.joinToString(" · ")
    } else {
        "Bilgi bekleniyor…"
    }

    val videoDetail = when {
        diagnostics.videoCodec.isNotBlank() && diagnostics.frameRate.isNotBlank() ->
            "${diagnostics.videoCodec} (${diagnostics.frameRate})"
        diagnostics.videoCodec.isNotBlank() -> diagnostics.videoCodec
        diagnostics.frameRate.isNotBlank() -> diagnostics.frameRate
        else -> ""
    }
    val videoText = if (videoDetail.isNotBlank()) "Video: $videoDetail" else ""

    val audioPart = if (diagnostics.audioCodec.isNotBlank()) "Ses: ${diagnostics.audioCodec}" else ""
    val bufferPart = if (diagnostics.bufferHealthSecs.isNotBlank()) "Önbellek: ${diagnostics.bufferHealthSecs}" else ""
    val audioBufferText = listOf(audioPart, bufferPart).filter { it.isNotBlank() }.joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = VegaColors.Border.copy(alpha = 0.45f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(if (isFocused) VegaColors.SurfaceVariant else Color.Transparent)
                .dpadFocusable(
                    shape = RectangleShape,
                    drawFocusBorder = false,
                    scale = 1.0f,
                    onClick = { isExpanded = !isExpanded },
                    onFocusStateChange = { isFocused = it }
                )
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(if (isFocused) VegaColors.Accent else Color.Transparent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isFocused) VegaColors.Accent else VegaColors.TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Yayın bilgisi",
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Medium,
                            color = if (isFocused) VegaColors.TextPrimary else VegaColors.TextSecondary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = headerText,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Medium,
                            color = VegaColors.Accent
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = VegaColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (isExpanded && (videoText.isNotBlank() || audioBufferText.isNotBlank())) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (videoText.isNotBlank()) {
                        Text(
                            text = videoText,
                            fontSize = VegaText.Caption,
                            color = VegaColors.TextSecondary
                        )
                    }
                    if (audioBufferText.isNotBlank()) {
                        Text(
                            text = audioBufferText,
                            fontSize = VegaText.Caption,
                            color = VegaColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}
