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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.tivimax.player.theme.OpusCyan
import com.tivimax.player.theme.TiviCardBorder
import com.tivimax.player.theme.TiviPrimary
import com.tivimax.player.theme.TiviTextMuted
import com.tivimax.player.theme.TiviTextPrimary
import com.tivimax.player.theme.TiviTextSecondary
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
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                } else {
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                },
                color = Color(0xF2121722), // %95 Opak Koyu Buzlu Cam
                border = BorderStroke(1.dp, TiviCardBorder.copy(alpha = 0.6f)),
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
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // SEKME BUTONLARI (Tabs)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableTabs.forEach { tab ->
                            val isSelected = (selectedTab == tab)
                            Surface(
                                onClick = { selectedTab = tab },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) TiviPrimary else Color.White.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 38.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab.title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.85f)
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
                text = "Ek ses parçası bulunamadı\n(Varsayılan Ses Aktif)",
                color = TiviTextSecondary,
                fontSize = 13.sp,
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
                Surface(
                    onClick = { onSelectAudioTrack(track) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) TiviPrimary.copy(alpha = 0.12f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
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
                                selectedColor = TiviPrimary,
                                unselectedColor = Color.White.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = track.name,
                            color = if (isSelected) TiviPrimary else TiviTextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
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
            Surface(
                onClick = onDisableSubtitles,
                shape = RoundedCornerShape(8.dp),
                color = if (!anySubSelected) TiviPrimary.copy(alpha = 0.12f) else Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
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
                            selectedColor = TiviPrimary,
                            unselectedColor = Color.White.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Kapalı",
                        color = if (!anySubSelected) TiviPrimary else TiviTextPrimary,
                        fontWeight = if (!anySubSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }

            // Mevcut Altyazılar
            subtitleTracks.forEach { track ->
                val isSelected = track.isSelected
                Surface(
                    onClick = { onSelectSubtitleTrack(track) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) TiviPrimary.copy(alpha = 0.12f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
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
                                selectedColor = TiviPrimary,
                                unselectedColor = Color.White.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = track.name,
                            color = if (isSelected) TiviPrimary else TiviTextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = Color.White.copy(alpha = 0.1f),
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
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (anySubSelected) Color.White else TiviTextMuted
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Azalt [-]
                Surface(
                    onClick = { onSubtitleDelayChange((subtitleDelayMs - 100L).coerceAtLeast(0L)) },
                    enabled = anySubSelected && subtitleDelayMs > 0L,
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Gecikmeyi Azalt",
                            tint = if (anySubSelected && subtitleDelayMs > 0L) Color.White else TiviTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Gecikme Metni
                val delaySec = subtitleDelayMs / 1000f
                Text(
                    text = String.format(Locale.getDefault(), "+%.1f sn", delaySec),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (anySubSelected) OpusCyan else TiviTextMuted,
                    modifier = Modifier.width(54.dp),
                    textAlign = TextAlign.Center
                )

                // Artır [+]
                Surface(
                    onClick = { onSubtitleDelayChange((subtitleDelayMs + 100L).coerceAtMost(5000L)) },
                    enabled = anySubSelected && subtitleDelayMs < 5000L,
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Gecikmeyi Artır",
                            tint = if (anySubSelected && subtitleDelayMs < 5000L) Color.White else TiviTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Sıfırla ⟲
                Surface(
                    onClick = { onSubtitleDelayChange(0L) },
                    enabled = anySubSelected && subtitleDelayMs > 0L,
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Gecikmeyi Sıfırla",
                            tint = if (anySubSelected && subtitleDelayMs > 0L) OpusCyan else TiviTextMuted,
                            modifier = Modifier.size(16.dp)
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
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
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
                    Surface(
                        onClick = { onSubtitleSizeChange(sizeVal) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) TiviPrimary else Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else Color.White
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
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val colorOptions = listOf(
                    "BEYAZ" to Color(0xFFFFFFFF),
                    "SARI" to Color(0xFFFFEB3B),
                    "YESIL" to Color(0xFF4CAF50),
                    "MAVI" to Color(0xFF2196F3)
                )

                colorOptions.forEach { (colorKey, colorVal) ->
                    val isSelected = (subtitleColor == colorKey)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                onSubtitleColorChange(colorKey)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colorVal)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.5.dp, Color.White, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, Color.Black.copy(alpha = 0.3f), CircleShape)
                                    }
                                )
                        )
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
            Surface(
                onClick = { onAspectRatioChange(mode) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) TiviPrimary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, if (isSelected) TiviPrimary else Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
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
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) TiviPrimary else Color.White
                    )
                    if (isSelected) {
                        Text(
                            text = "✓",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TiviPrimary
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
            Surface(
                onClick = { onPlaybackSpeedChange(spd) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) TiviPrimary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, if (isSelected) TiviPrimary else Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
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
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) TiviPrimary else Color.White
                    )
                    if (isSelected) {
                        Text(
                            text = "✓",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TiviPrimary
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

    Surface(
        onClick = { isExpanded = !isExpanded },
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ℹ Yayın Bilgisi",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${diagnostics.resolutionBadge} • ${diagnostics.bitrate}",
                        fontSize = 11.sp,
                        color = OpusCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Video: ${diagnostics.videoCodec} (${diagnostics.frameRate})",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Text(
                    text = "Ses: ${diagnostics.audioCodec} | Tampon: ${diagnostics.bufferHealthSecs}",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}
