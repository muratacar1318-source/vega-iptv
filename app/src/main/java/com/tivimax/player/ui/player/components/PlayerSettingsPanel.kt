package com.tivimax.player.ui.player.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.StreamDiagnostics
import com.tivimax.player.player.TrackInfo
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

    BackHandler(enabled = visible) {
        onClose()
    }

    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            delay(100)
            runCatching { firstFocus.requestFocus() }
        }
    }

    var selectedTab by remember(visible, initialTab) {
        mutableStateOf(if (isLive && (initialTab == SettingsTab.ASPECT_RATIO || initialTab == SettingsTab.SPEED)) SettingsTab.AUDIO else initialTab)
    }

    val availableTabs = remember(isLive) {
        if (isLive) {
            listOf(SettingsTab.AUDIO, SettingsTab.SUBTITLE)
        } else {
            listOf(SettingsTab.AUDIO, SettingsTab.SUBTITLE, SettingsTab.ASPECT_RATIO, SettingsTab.SPEED)
        }
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .displayCutoutPadding()
    ) {
        // Karartma / Scrim (Dış alana dokununca kapat)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onClose()
                }
        )

        // Panel Gövdesi
        AnimatedVisibility(
            visible = visible,
            enter = if (isPortrait) {
                slideInVertically(initialOffsetY = { it }) + fadeIn()
            } else {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            },
            exit = if (isPortrait) {
                slideOutVertically(targetOffsetY = { it }) + fadeOut()
            } else {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            },
            modifier = Modifier.align(if (isPortrait) Alignment.BottomCenter else Alignment.CenterEnd)
        ) {
            Surface(
                modifier = if (isPortrait) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeightDp * 0.55f)
                } else {
                    Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* Dokunmayı panel içinde yut */ },
                shape = if (isPortrait) {
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                } else {
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                },
                color = VegaColors.OverlayPanel,
                border = BorderStroke(1.dp, VegaColors.Border),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // ÜST BAR: Başlık ve Kapat Butonu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ayarlar",
                            fontSize = VegaText.SectionTitle,
                            fontWeight = FontWeight.Bold,
                            color = VegaColors.TextPrimary
                        )
                        var isCloseFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(VegaSizes.IconButton)
                                .onFocusChanged { isCloseFocused = it.isFocused }
                                .then(
                                    if (isCloseFocused) {
                                        Modifier.border(2.dp, VegaColors.Accent, CircleShape)
                                    } else Modifier
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = if (isCloseFocused) VegaColors.Accent else VegaColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // SEKME BUTONLARI (Tabs)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableTabs.forEach { tab ->
                            val isSelected = (selectedTab == tab)
                            var isFocused by remember { mutableStateOf(false) }
                            Surface(
                                onClick = { selectedTab = tab },
                                shape = VegaShapes.Small,
                                color = if (isFocused) VegaColors.Accent.copy(alpha = 0.25f)
                                else if (isSelected) VegaColors.Accent.copy(alpha = 0.18f)
                                else VegaColors.SurfaceVariant,
                                border = if (isFocused) BorderStroke(2.dp, VegaColors.Accent)
                                else if (isSelected) BorderStroke(1.dp, VegaColors.Accent)
                                else BorderStroke(1.dp, VegaColors.Border),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .then(if (isSelected) Modifier.focusRequester(firstFocus) else Modifier)
                                    .onFocusChanged { isFocused = it.isFocused }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab.title,
                                        fontSize = VegaText.Secondary,
                                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected || isFocused) VegaColors.Accent else VegaColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // AKTİF SEKME İÇERİĞİ (Kaydırmasız kompakt alan)
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

                    // YAYIN TEŞHİS BİLGİSİ (Stream Diagnostics)
                    if (diagnostics != null) {
                        DiagnosticsFooter(diagnostics = diagnostics)
                    }
                }
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            audioTracks.forEach { track ->
                val isSelected = track.isSelected
                var isFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = { onSelectAudioTrack(track) },
                    shape = VegaShapes.Small,
                    color = if (isFocused) VegaColors.Accent.copy(alpha = 0.2f) else if (isSelected) VegaColors.Accent.copy(alpha = 0.18f) else Color.Transparent,
                    border = if (isFocused) BorderStroke(2.dp, VegaColors.Accent) else if (isSelected) BorderStroke(1.dp, VegaColors.Accent) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .onFocusChanged { isFocused = it.isFocused }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectAudioTrack(track) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = VegaColors.Accent,
                                unselectedColor = VegaColors.TextMuted
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = track.name,
                            color = if (isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = VegaText.Body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// 2. ALTYAZI SEKMESİ İÇERİĞİ
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
        // Altyazı Parçaları Listesi
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 110.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Kapalı Seçeneği
            var isOffFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = onDisableSubtitles,
                shape = VegaShapes.Small,
                color = if (isOffFocused) VegaColors.Accent.copy(alpha = 0.2f) else if (!anySubSelected) VegaColors.Accent.copy(alpha = 0.18f) else Color.Transparent,
                border = if (isOffFocused) BorderStroke(2.dp, VegaColors.Accent) else if (!anySubSelected) BorderStroke(1.dp, VegaColors.Accent) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .onFocusChanged { isOffFocused = it.isFocused }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !anySubSelected,
                        onClick = onDisableSubtitles,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = VegaColors.Accent,
                            unselectedColor = VegaColors.TextMuted
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Kapalı",
                        color = if (!anySubSelected) VegaColors.Accent else VegaColors.TextPrimary,
                        fontWeight = if (!anySubSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = VegaText.Body
                    )
                }
            }

            // Mevcut Altyazılar
            subtitleTracks.forEach { track ->
                val isSelected = track.isSelected
                var isTrackFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = { onSelectSubtitleTrack(track) },
                    shape = VegaShapes.Small,
                    color = if (isTrackFocused) VegaColors.Accent.copy(alpha = 0.2f) else if (isSelected) VegaColors.Accent.copy(alpha = 0.18f) else Color.Transparent,
                    border = if (isTrackFocused) BorderStroke(2.dp, VegaColors.Accent) else if (isSelected) BorderStroke(1.dp, VegaColors.Accent) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .onFocusChanged { isTrackFocused = it.isFocused }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectSubtitleTrack(track) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = VegaColors.Accent,
                                unselectedColor = VegaColors.TextMuted
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = track.name,
                            color = if (isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = VegaText.Body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = VegaColors.Border,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // GECİKME SATIRI: Gecikme  [-]  +0,3 sn  [+]  ⟲
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Gecikme",
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.SemiBold,
                color = if (anySubSelected) VegaColors.TextPrimary else VegaColors.TextMuted
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Azalt [-]
                var isMinusFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = { onSubtitleDelayChange((subtitleDelayMs - 100L).coerceAtLeast(0L)) },
                    enabled = anySubSelected && subtitleDelayMs > 0L,
                    shape = VegaShapes.Small,
                    color = VegaColors.SurfaceVariant,
                    border = if (isMinusFocused) BorderStroke(2.dp, VegaColors.Accent) else null,
                    modifier = Modifier
                        .size(VegaSizes.IconButton)
                        .onFocusChanged { isMinusFocused = it.isFocused }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Gecikmeyi azalt",
                            tint = if (anySubSelected && subtitleDelayMs > 0L) VegaColors.TextPrimary else VegaColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Gecikme Metni
                val delaySec = subtitleDelayMs / 1000f
                Text(
                    text = String.format(Locale.getDefault(), "+%.1f sn", delaySec),
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.Bold,
                    color = if (anySubSelected) VegaColors.Accent else VegaColors.TextMuted,
                    modifier = Modifier.width(54.dp),
                    textAlign = TextAlign.Center
                )

                // Artır [+]
                var isPlusFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = { onSubtitleDelayChange((subtitleDelayMs + 100L).coerceAtMost(5000L)) },
                    enabled = anySubSelected && subtitleDelayMs < 5000L,
                    shape = VegaShapes.Small,
                    color = VegaColors.SurfaceVariant,
                    border = if (isPlusFocused) BorderStroke(2.dp, VegaColors.Accent) else null,
                    modifier = Modifier
                        .size(VegaSizes.IconButton)
                        .onFocusChanged { isPlusFocused = it.isFocused }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Gecikmeyi artır",
                            tint = if (anySubSelected && subtitleDelayMs < 5000L) VegaColors.TextPrimary else VegaColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Sıfırla ⟲
                var isResetFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = { onSubtitleDelayChange(0L) },
                    enabled = anySubSelected && subtitleDelayMs > 0L,
                    shape = VegaShapes.Small,
                    color = VegaColors.SurfaceVariant,
                    border = if (isResetFocused) BorderStroke(2.dp, VegaColors.Accent) else null,
                    modifier = Modifier
                        .size(VegaSizes.IconButton)
                        .onFocusChanged { isResetFocused = it.isFocused }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Gecikmeyi sıfırla",
                            tint = if (anySubSelected && subtitleDelayMs > 0L) VegaColors.Accent else VegaColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // BOYUT SATIRI: Boyut [K] [O] [B] [ÇB]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Boyut",
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.SemiBold,
                color = VegaColors.TextPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        onClick = { onSubtitleSizeChange(sizeVal) },
                        shape = VegaShapes.Small,
                        color = if (isSelected) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.SurfaceVariant,
                        border = if (isSizeFocused) BorderStroke(2.dp, VegaColors.Accent)
                        else if (isSelected) BorderStroke(1.dp, VegaColors.Accent)
                        else BorderStroke(1.dp, VegaColors.Border),
                        modifier = Modifier
                            .size(VegaSizes.IconButton)
                            .onFocusChanged { isSizeFocused = it.isFocused }
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                fontSize = VegaText.Secondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) VegaColors.Accent else VegaColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // RENK SATIRI: Renk ⚪ 🟡 🟢 🔵
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Renk",
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.SemiBold,
                color = VegaColors.TextPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val colorOptions = listOf(
                    "BEYAZ" to Color(0xFFFFFFFF),
                    "SARI" to Color(0xFFFFEB3B),
                    "YESIL" to Color(0xFF4CAF50),
                    "MAVI" to Color(0xFF2196F3)
                )

                colorOptions.forEach { (colorKey, colorVal) ->
                    val isSelected = (subtitleColor == colorKey)
                    var isColorFocused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { onSubtitleColorChange(colorKey) },
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(
                            width = if (isColorFocused) 2.dp else if (isSelected) 2.dp else 1.dp,
                            color = if (isColorFocused) VegaColors.Accent else if (isSelected) VegaColors.Accent else VegaColors.Border
                        ),
                        modifier = Modifier
                            .size(VegaSizes.IconButton)
                            .onFocusChanged { isColorFocused = it.isFocused }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
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
            val isSelected = (aspectRatio == mode)
            var isFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = { onAspectRatioChange(mode) },
                shape = VegaShapes.Small,
                color = if (isFocused) VegaColors.Accent.copy(alpha = 0.2f) else if (isSelected) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.SurfaceVariant,
                border = if (isFocused) BorderStroke(2.dp, VegaColors.Accent) else if (isSelected) BorderStroke(1.dp, VegaColors.Accent) else BorderStroke(1.dp, VegaColors.Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .onFocusChanged { isFocused = it.isFocused }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = VegaText.Body,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected || isFocused) VegaColors.Accent else VegaColors.TextPrimary
                    )
                    if (isSelected) {
                        Text(
                            text = "✓",
                            fontSize = VegaText.Body,
                            fontWeight = FontWeight.Bold,
                            color = VegaColors.Accent
                        )
                    }
                }
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
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        speeds.forEach { spd ->
            val isSelected = (playbackSpeed == spd)
            val label = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x"
            var isFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = { onPlaybackSpeedChange(spd) },
                shape = VegaShapes.Small,
                color = if (isFocused) VegaColors.Accent.copy(alpha = 0.2f) else if (isSelected) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.SurfaceVariant,
                border = if (isFocused) BorderStroke(2.dp, VegaColors.Accent) else if (isSelected) BorderStroke(1.dp, VegaColors.Accent) else BorderStroke(1.dp, VegaColors.Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .onFocusChanged { isFocused = it.isFocused }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = VegaText.Body,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected || isFocused) VegaColors.Accent else VegaColors.TextPrimary
                    )
                    if (isSelected) {
                        Text(
                            text = "✓",
                            fontSize = VegaText.Body,
                            fontWeight = FontWeight.Bold,
                            color = VegaColors.Accent
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// YAYIN TEŞHİS BİLGİSİ ALT ALANI (Stream Diagnostics)
// ----------------------------------------------------------------------------
@Composable
private fun DiagnosticsFooter(diagnostics: StreamDiagnostics) {
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
    val bufferPart = if (diagnostics.bufferHealthSecs.isNotBlank()) "Tampon: ${diagnostics.bufferHealthSecs}" else ""
    val audioBufferText = listOf(audioPart, bufferPart).filter { it.isNotBlank() }.joinToString(" | ")

    Surface(
        onClick = { isExpanded = !isExpanded },
        shape = VegaShapes.Small,
        color = if (isFocused) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.SurfaceVariant,
        border = if (isFocused) BorderStroke(2.dp, VegaColors.Accent) else BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = VegaColors.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Yayın bilgisi",
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Medium,
                        color = VegaColors.TextSecondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = headerText,
                        fontSize = VegaText.Caption,
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
            if (isExpanded) {
                if (videoText.isNotBlank() || audioBufferText.isNotBlank()) {
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
}
