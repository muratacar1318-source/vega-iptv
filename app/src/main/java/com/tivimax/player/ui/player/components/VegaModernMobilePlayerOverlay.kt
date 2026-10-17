package com.tivimax.player.ui.player.components

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText

// ============================================================================
// VEGA MODERN MOBILE VIDEO PLAYER UI (DARK MODE, FROSTED GLASS & NEON ACCENTS)
// ============================================================================

val NeonPurple: Color get() = VegaColors.Accent
val NeonPink: Color get() = VegaColors.Accent
val FrostedGlassBackground: Color get() = VegaColors.OverlayPill
val FrostedGlassBorder: Color get() = VegaColors.Border

/**
 * Root Responsive Player Screen supporting both Portrait and Landscape layouts.
 */
@Composable
fun VegaModernMobilePlayerScreen(
    title: String = "Muhtemel Aşk - S1 B1",
    subtitle: String = "Bölüm 1: Karşılaşma ve Yeni Başlangıç",
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
            // ================================================================
            // LANDSCAPE: Full-Screen Video with Frosted Overlay
            // ================================================================
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
                    // Floating Frosted Unlock Pill
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = { isLocked = false },
                            shape = VegaShapes.Pill,
                            color = FrostedGlassBackground,
                            border = BorderStroke(1.dp, NeonPink.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Kilidi aç",
                                    tint = NeonPink,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Ekran kilidini aç",
                                    color = VegaColors.TextPrimary,
                                    fontSize = VegaText.Secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ================================================================
            // PORTRAIT: Top-Half Video (16:9) + Bottom-Half Clean Menu
            // ================================================================
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Half: Video Player Container (Aspect ratio 16:9)
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                onClick = { isLocked = false },
                                shape = RoundedCornerShape(50),
                                color = FrostedGlassBackground,
                                border = BorderStroke(1.dp, NeonPink.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Kilidi Aç",
                                        tint = NeonPink,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Kilidi Aç",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Half: Clean Menu Area
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

/**
 * Frosted Glass Controls Overlay with Center Controls, Top Bar and Bottom Timeline.
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.82f),
                        Color.Black.copy(alpha = 0.25f),
                        Color.Black.copy(alpha = 0.88f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ====================================================================
        // TOP BAR
        // ====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Back Arrow & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    onClick = onBackClick,
                    shape = CircleShape,
                    color = FrostedGlassBackground,
                    border = BorderStroke(1.dp, FrostedGlassBorder),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeonPurple.copy(alpha = 0.25f))
                                .border(0.5.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "1080p",
                                color = NeonPurple,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Screencast & Screen Lock Icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Screencast Icon
                Surface(
                    onClick = onCastClick,
                    shape = CircleShape,
                    color = FrostedGlassBackground,
                    border = BorderStroke(1.dp, FrostedGlassBorder),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = "Yansıt",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Screen Lock Icon
                Surface(
                    onClick = onLockToggle,
                    shape = CircleShape,
                    color = FrostedGlassBackground,
                    border = BorderStroke(1.dp, FrostedGlassBorder),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Ekran kilidi",
                            tint = if (isLocked) NeonPink else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ====================================================================
        // CENTER: Thin Modern Icons + Non-Overlapping Buffer Ring
        // ====================================================================
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // 10s Backward
            Surface(
                onClick = onSkipBackward,
                shape = CircleShape,
                color = FrostedGlassBackground,
                border = BorderStroke(1.dp, FrostedGlassBorder),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "10 sn geri",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Play / Pause Button with PERFECT OUTSIDE BUFFER RING
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(76.dp)
            ) {
                // Perfect Outer Buffer Ring (Strictly outside play button, never overlapping)
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 3.dp,
                        color = NeonPink,
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )
                }

                // Play / Pause Circle
                Surface(
                    onClick = onPlayPauseToggle,
                    shape = CircleShape,
                    color = FrostedGlassBackground,
                    border = BorderStroke(1.dp, if (isPlaying) NeonPurple.copy(alpha = 0.6f) else FrostedGlassBorder),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // 10s Forward
            Surface(
                onClick = onSkipForward,
                shape = CircleShape,
                color = FrostedGlassBackground,
                border = BorderStroke(1.dp, FrostedGlassBorder),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "10 sn ileri",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // ====================================================================
        // BOTTOM: Prominent Timeline & Minimalist Icons Row
        // ====================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Timeline (Elapsed Time - Slider - Total Duration)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatDuration(currentTimeMs),
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.Medium
                )

                val progress = if (totalDurationMs > 0) (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
                Slider(
                    value = progress,
                    onValueChange = { newPct ->
                        onSeek((newPct * totalDurationMs).toLong())
                    },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = VegaColors.Accent,
                        activeTrackColor = VegaColors.Accent,
                        inactiveTrackColor = VegaColors.SurfaceVariant
                    )
                )

                Text(
                    text = formatDuration(totalDurationMs),
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.Medium
                )
            }

            // Minimal Icons Row: Next Episode, Episodes List, Settings Gear
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Active Episode indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(VegaShapes.Pill)
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(NeonPink)
                    )
                    Text(
                        text = "S1 : Bölüm 1",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Minimal Action Icons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Next Episode Icon
                    IconButton(onClick = onNextEpisode, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Sonraki bölüm",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Episodes List Icon
                    IconButton(onClick = onEpisodesClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Bölüm listesi",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Settings Gear Icon (Audio, Subtitles, Playback Speed)
                    IconButton(onClick = onSettingsClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Oynatıcı ayarları",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom Half Menu Area for Portrait Mode (Episodes list, Details, Audio/Subtitles).
 */
@Composable
fun VegaPlayerPortraitMenuArea(
    title: String,
    onNextEpisode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Bölümler (16)", "Detay ve konu", "Ses ve altyazı")

    Column(
        modifier = modifier
            .background(VegaColors.Background)
            .padding(12.dp)
    ) {
        // Header title & IMDb
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title.substringBefore("-").trim(),
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.BodyProminent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sezon 1 • 2026 • Romantik, Dram",
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Caption
                )
            }
            Box(
                modifier = Modifier
                    .clip(VegaShapes.Small)
                    .background(VegaColors.Gold.copy(alpha = 0.15f))
                    .border(1.dp, VegaColors.Gold.copy(alpha = 0.3f), VegaShapes.Small)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "IMDb 8.9",
                    color = VegaColors.Gold,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tabs Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, tabName ->
                val isSelected = selectedTab == index
                Surface(
                    onClick = { selectedTab = index },
                    shape = VegaShapes.Small,
                    color = if (isSelected) VegaColors.SurfaceVariant else VegaColors.Surface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) VegaColors.Accent else VegaColors.Border
                    )
                ) {
                    Text(
                        text = tabName,
                        color = if (isSelected) VegaColors.TextPrimary else VegaColors.TextSecondary,
                        fontSize = VegaText.Secondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab Content
        when (selectedTab) {
            0 -> {
                // Episodes List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        // Episode 1 (Active)
                        Surface(
                            shape = VegaShapes.Medium,
                            color = VegaColors.Accent.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 48.dp)
                                        .clip(VegaShapes.Small)
                                        .background(Color.Black.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("S1 • B1", color = VegaColors.Accent, fontSize = VegaText.Caption, fontWeight = FontWeight.Bold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("1. Karşılaşma", color = VegaColors.TextPrimary, fontSize = VegaText.Body, fontWeight = FontWeight.Bold)
                                        Text("İzleniyor", color = VegaColors.Accent, fontSize = VegaText.Caption, fontWeight = FontWeight.Medium)
                                    }
                                    Text(
                                        "Yağmurlu bir gecede yolları kesişen iki yabancı...",
                                        color = VegaColors.TextSecondary,
                                        fontSize = VegaText.Caption,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("45 dk", color = VegaColors.TextSecondary, fontSize = VegaText.Caption)
                                }
                            }
                        }
                    }

                    item {
                        // Episode 2
                        Surface(
                            onClick = onNextEpisode,
                            shape = VegaShapes.Medium,
                            color = VegaColors.Surface,
                            border = BorderStroke(1.dp, VegaColors.Border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 48.dp)
                                        .clip(VegaShapes.Small)
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("S1 • B2", color = VegaColors.TextSecondary, fontSize = VegaText.Caption, fontWeight = FontWeight.Bold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("2. Beklenmedik Misafir", color = VegaColors.TextPrimary, fontSize = VegaText.Body, fontWeight = FontWeight.SemiBold)
                                        Text("Sıradaki", color = VegaColors.TextSecondary, fontSize = VegaText.Caption)
                                    }
                                    Text(
                                        "Geçmişin gölgeleri aralanırken verilen ilk sözler...",
                                        color = VegaColors.TextSecondary,
                                        fontSize = VegaText.Caption,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("48 dk", color = VegaColors.TextSecondary, fontSize = VegaText.Caption)
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                // Details
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Konu özeti",
                        color = VegaColors.Accent,
                        fontSize = VegaText.Secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Yolları beklenmedik bir anda kesişen iki farklı dünyanın insanı, imkansız gibi görünen bir bağ kurar. Ancak geçmişin çözülmemiş gizemleri ve çevrelerindeki engeller, aşklarını büyük bir sınavdan geçirecektir.",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.Secondary,
                        lineHeight = 18.sp
                    )
                }
            }
            2 -> {
                // Audio & Subtitles
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Seslendirme (Dublaj)", color = VegaColors.TextPrimary, fontSize = VegaText.Body, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = VegaShapes.Small,
                            color = VegaColors.Accent.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, VegaColors.Accent)
                        ) {
                            Text("Türkçe (Orijinal)", color = VegaColors.TextPrimary, fontSize = VegaText.Caption, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                        Surface(
                            shape = VegaShapes.Small,
                            color = VegaColors.SurfaceVariant,
                            border = BorderStroke(1.dp, VegaColors.Border)
                        ) {
                            Text("İngilizce", color = VegaColors.TextSecondary, fontSize = VegaText.Caption, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }

                    Text(text = "Altyazı", color = VegaColors.TextPrimary, fontSize = VegaText.Body, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = VegaShapes.Small,
                            color = VegaColors.Accent.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, VegaColors.Accent)
                        ) {
                            Text("Türkçe", color = VegaColors.TextPrimary, fontSize = VegaText.Caption, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                        Surface(
                            shape = VegaShapes.Small,
                            color = VegaColors.SurfaceVariant,
                            border = BorderStroke(1.dp, VegaColors.Border)
                        ) {
                            Text("Kapalı", color = VegaColors.TextSecondary, fontSize = VegaText.Caption, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
