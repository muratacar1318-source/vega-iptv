package com.tivimax.player.ui.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Surface
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
import com.tivimax.player.ui.design.VegaSizes
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
        // SEKME DÜĞMELERİ (Tek odak + tıklama hedefi)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            availableTabs.forEach { tab ->
                val isSelected = (selectedTab == tab)
                var isFocused by remember { mutableStateOf(false) }

                val bgColor = when {
                    isFocused -> VegaColors.Accent
                    isSelected -> VegaColors.Accent.copy(alpha = 0.18f)
                    else -> VegaColors.SurfaceVariant
                }
                val textColor = when {
                    isFocused -> VegaColors.OnAccent
                    isSelected -> VegaColors.Accent
                    else -> VegaColors.TextSecondary
                }

                Surface(
                    shape = VegaShapes.Small,
                    color = bgColor,
                    border = BorderStroke(
                        1.dp,
                        when {
                            isFocused -> Color.White
                            isSelected -> VegaColors.Accent
                            else -> VegaColors.Border
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .then(if (isSelected) Modifier.focusRequester(firstFocus) else Modifier)
                        .dpadFocusable(
                            shape = VegaShapes.Small,
                            focusedBorderColor = Color.White,
                            scale = 1.03f,
                            onClick = { selectedTab = tab },
                            onFocusStateChange = { isFocused = it }
                        )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.title,
                            fontSize = VegaText.Secondary,
                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                            color = textColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
 * StreamVault `VodSelectionRow` uyarlaması: tek odak + tıklama hedefli seçim satırı.
 * İçinde ikinci bir odaklanabilir çocuk (RadioButton onClick vb.) BULUNMAZ.
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

    val containerColor = when {
        isFocused -> VegaColors.Accent
        selected -> VegaColors.Accent.copy(alpha = 0.18f)
        else -> VegaColors.SurfaceVariant
    }
    val contentColor = when {
        isFocused -> VegaColors.OnAccent
        selected -> VegaColors.Accent
        else -> VegaColors.TextPrimary
    }

    Surface(
        shape = VegaShapes.Medium,
        color = containerColor,
        border = BorderStroke(
            1.dp,
            when {
                isFocused -> Color.White
                selected -> VegaColors.Accent
                else -> VegaColors.Border
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .dpadFocusable(
                shape = VegaShapes.Medium,
                focusedBorderColor = Color.White,
                scale = 1.02f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) VegaColors.OnAccent else if (selected) VegaColors.Accent else VegaColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = contentColor,
                fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Medium,
                fontSize = VegaText.Body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Seçili",
                    tint = if (isFocused) VegaColors.OnAccent else VegaColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            audioTracks.forEach { track ->
                SettingsSelectionRow(
                    title = track.name,
                    icon = Icons.Default.Audiotrack,
                    selected = track.isSelected,
                    onClick = { onSelectAudioTrack(track) }
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------
// 2. ALTYAZI SEKMESİ İÇERİĞİ (Tek dikey kaydırma, sıfır iç içe scroll tuzağı)
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
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
            SettingsSelectionRow(
                title = track.name,
                icon = Icons.Default.ClosedCaption,
                selected = track.isSelected,
                onClick = { onSelectSubtitleTrack(track) }
            )
        }

        HorizontalDivider(
            color = VegaColors.Border,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        // GECİKME SATIRI (StreamVault SubtitleSettingStepper uyarlaması)
        Surface(
            shape = VegaShapes.Medium,
            color = VegaColors.SurfaceVariant,
            border = BorderStroke(1.dp, VegaColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Gecikme",
                        fontSize = VegaText.Secondary,
                        fontWeight = FontWeight.SemiBold,
                        color = if (anySubSelected) VegaColors.TextPrimary else VegaColors.TextMuted
                    )
                    val delaySec = subtitleDelayMs / 1000f
                    Text(
                        text = String.format(Locale.getDefault(), "+%.1f sn", delaySec),
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Bold,
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
        }

        // BOYUT SATIRI: Boyut [K] [O] [B] [ÇB]
        Surface(
            shape = VegaShapes.Medium,
            color = VegaColors.SurfaceVariant,
            border = BorderStroke(1.dp, VegaColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Boyut",
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.SemiBold,
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

                        Surface(
                            shape = VegaShapes.Small,
                            color = when {
                                isSizeFocused -> VegaColors.Accent
                                isSelected -> VegaColors.Accent.copy(alpha = 0.18f)
                                else -> VegaColors.SurfaceElevated
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isSizeFocused -> Color.White
                                    isSelected -> VegaColors.Accent
                                    else -> VegaColors.Border
                                }
                            ),
                            modifier = Modifier
                                .size(40.dp)
                                .dpadFocusable(
                                    shape = VegaShapes.Small,
                                    focusedBorderColor = Color.White,
                                    scale = 1.05f,
                                    onClick = { onSubtitleSizeChange(sizeVal) },
                                    onFocusStateChange = { isSizeFocused = it }
                                )
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            }
        }

        // RENK SATIRI (Sıfır sabit Color(0x...) — VegaColors ve Color.White tokenları)
        Surface(
            shape = VegaShapes.Medium,
            color = VegaColors.SurfaceVariant,
            border = BorderStroke(1.dp, VegaColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Renk",
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.SemiBold,
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

                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) VegaColors.Accent.copy(alpha = 0.16f) else Color.Transparent,
                            border = BorderStroke(
                                width = if (isColorFocused || isSelected) 2.dp else 1.dp,
                                color = if (isColorFocused) VegaColors.FocusBorder else if (isSelected) VegaColors.Accent else VegaColors.Border
                            ),
                            modifier = Modifier
                                .size(40.dp)
                                .dpadFocusable(
                                    shape = CircleShape,
                                    focusedBorderColor = VegaColors.FocusBorder,
                                    scale = 1.06f,
                                    onClick = { onSubtitleColorChange(colorKey) },
                                    onFocusStateChange = { isColorFocused = it }
                                )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(colorVal)
                                )
                            }
                        }
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

    Surface(
        shape = VegaShapes.Small,
        color = when {
            !enabled -> VegaColors.SurfaceElevated.copy(alpha = 0.4f)
            isFocused -> VegaColors.Accent
            else -> VegaColors.SurfaceElevated
        },
        border = BorderStroke(
            1.dp,
            when {
                !enabled -> VegaColors.Border.copy(alpha = 0.4f)
                isFocused -> Color.White
                else -> VegaColors.Border
            }
        ),
        modifier = Modifier
            .size(40.dp)
            .then(
                if (enabled) {
                    Modifier.dpadFocusable(
                        shape = VegaShapes.Small,
                        focusedBorderColor = Color.White,
                        scale = 1.05f,
                        onClick = onClick,
                        onFocusStateChange = { isFocused = it }
                    )
                } else Modifier
            )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = when {
                    !enabled -> VegaColors.TextMuted.copy(alpha = 0.5f)
                    isFocused -> VegaColors.OnAccent
                    accentTint -> VegaColors.Accent
                    else -> VegaColors.TextPrimary
                },
                modifier = Modifier.size(18.dp)
            )
        }
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
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        modes.forEach { (mode, label) ->
            SettingsSelectionRow(
                title = label,
                icon = Icons.Default.AspectRatio,
                selected = (aspectRatio == mode),
                onClick = { onAspectRatioChange(mode) }
            )
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
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        speeds.forEach { spd ->
            val label = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x"
            SettingsSelectionRow(
                title = label,
                icon = Icons.Default.Speed,
                selected = (playbackSpeed == spd),
                onClick = { onPlaybackSpeedChange(spd) }
            )
        }
    }
}

// ----------------------------------------------------------------------------
// YAYIN BİLGİSİ ALT ALANI (Tek odaklı genişletilebilir kart)
// ----------------------------------------------------------------------------
@Composable
internal fun DiagnosticsFooter(diagnostics: StreamDiagnostics) {
    var isExpanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val resBadge = diagnostics.resolutionBadge.ifBlank { diagnostics.resolution }.trim()
    val headerParts = listOf(resBadge, diagnostics.bitrate.trim()).filter { it.isNotBlank() }
    val headerText = if (headerParts.isNotEmpty()) {
        headerParts.joinToString(" • ")
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
    val audioBufferText = listOf(audioPart, bufferPart).filter { it.isNotBlank() }.joinToString(" | ")

    Surface(
        shape = VegaShapes.Medium,
        color = if (isFocused) VegaColors.Accent.copy(alpha = 0.22f) else VegaColors.SurfaceVariant,
        border = BorderStroke(1.dp, if (isFocused) VegaColors.Accent else VegaColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .dpadFocusable(
                shape = VegaShapes.Medium,
                focusedBorderColor = VegaColors.Accent,
                scale = 1.02f,
                onClick = { isExpanded = !isExpanded },
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isFocused) VegaColors.Accent else VegaColors.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Yayın bilgisi",
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.SemiBold,
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
                        color = VegaColors.TextPrimary
                    )
                }
                if (audioBufferText.isNotBlank()) {
                    Text(
                        text = audioBufferText,
                        fontSize = VegaText.Caption,
                        color = VegaColors.TextPrimary
                    )
                }
            }
        }
    }
}
