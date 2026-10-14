package com.tivimax.player.ui.player.components

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.decodeBase64IfNeeded
import com.tivimax.player.player.StreamDiagnostics
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage

// ============================================================================
// 1. TIVIMATE OVERLAY GUIDE (CANLI YAYIN KESİLMEDEN YARI SAYDAM REHBER KATMANI)
// ============================================================================

@Composable
fun TiviOverlayGuide(
    visible: Boolean,
    currentStreamId: Int,
    viewModel: MainViewModel,
    onChannelSelected: (LiveStream) -> Unit,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
    ) {
        val categories by viewModel.visibleLiveCategories.collectAsState()
        val allLiveStreams by viewModel.liveStreams.collectAsState()
        val favoriteIds by viewModel.favoriteLiveIds.collectAsState()
        val recentChannels by viewModel.recentChannels.collectAsState()
        val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()

        var selectedCategoryKey by remember { mutableStateOf("all") }
        var channelSearchQuery by remember { mutableStateOf("") }

        // Seçili kategoriye göre kanal listesini hazırla
        val displayedChannels = remember(selectedCategoryKey, allLiveStreams, favoriteIds, recentChannels, channelSearchQuery) {
            val baseList = when (selectedCategoryKey) {
                "favorites" -> allLiveStreams.filter { favoriteIds.contains(it.streamId.toString()) }
                "recents" -> recentChannels
                "all" -> allLiveStreams
                else -> allLiveStreams.filter { it.categoryId == selectedCategoryKey }
            }
            if (channelSearchQuery.isBlank()) {
                baseList
            } else {
                baseList.filter { it.name.contains(channelSearchQuery, ignoreCase = true) }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClose) // Boşluğa dokununca kapat
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(520.dp)
                    .clickable(enabled = false) {} // Tıklamayı arka plana geçirme
                    .border(
                        1.dp,
                        Color(0xFF1E2638).copy(alpha = 0.7f),
                        RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                    ),
                color = Color(0xD10A0E14), // %82 Opaklıkta TiviMate Buzlu Cam
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // SOL SÜTUN: KATEGORİLER (200.dp)
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                            .background(Color(0x66080C12))
                            .border(width = 0.5.dp, color = Color(0xFF1E2638).copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "REHBER",
                                color = OpusCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color(0xFF94A3B8))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Sabit Hızlı Kategoriler: Favoriler, Son İzlenenler, Tüm Kanallar
                            item {
                                GuideCategoryItem(
                                    title = "Favoriler",
                                    icon = Icons.Default.Star,
                                    count = favoriteIds.size,
                                    isSelected = selectedCategoryKey == "favorites",
                                    iconTint = Color(0xFFFFB300),
                                    onClick = { selectedCategoryKey = "favorites" }
                                )
                            }
                            item {
                                GuideCategoryItem(
                                    title = "Son İzlenenler",
                                    icon = Icons.Default.History,
                                    count = recentChannels.size,
                                    isSelected = selectedCategoryKey == "recents",
                                    iconTint = OpusCyan,
                                    onClick = { selectedCategoryKey = "recents" }
                                )
                            }
                            item {
                                GuideCategoryItem(
                                    title = "Tüm Kanallar",
                                    icon = Icons.Default.Tv,
                                    count = allLiveStreams.size,
                                    isSelected = selectedCategoryKey == "all",
                                    iconTint = TiviPrimary,
                                    onClick = { selectedCategoryKey = "all" }
                                )
                            }

                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = Color(0xFF1E2638)
                                )
                            }

                            // Dinamik Kategoriler
                            items(categories.filter { it.categoryId != "all" }, key = { it.categoryId }) { cat ->
                                GuideCategoryItem(
                                    title = cat.categoryName,
                                    icon = null,
                                    count = cat.itemCount,
                                    isSelected = selectedCategoryKey == cat.categoryId,
                                    iconTint = Color.White,
                                    onClick = { selectedCategoryKey = cat.categoryId }
                                )
                            }
                        }
                    }

                    // SAĞ SÜTUN: KANALLAR (320.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(10.dp)
                    ) {
                        // Kanal Arama
                        OutlinedTextField(
                            value = channelSearchQuery,
                            onValueChange = { channelSearchQuery = it },
                            placeholder = { Text("Kanal ara...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OpusCyan,
                                unfocusedBorderColor = Color(0xFF1E2638),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF0F141C),
                                unfocusedContainerColor = Color(0xFF0F141C)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val listState = rememberLazyListState()

                        // Aktif kanala otomatik kaydır
                        LaunchedEffect(selectedCategoryKey, visible) {
                            if (visible) {
                                val idx = displayedChannels.indexOfFirst { it.streamId == currentStreamId }
                                if (idx >= 0) {
                                    listState.scrollToItem((idx - 2).coerceAtLeast(0))
                                }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(displayedChannels, key = { it.streamId }) { channel ->
                                val isPlayingThis = channel.streamId == currentStreamId
                                val epg = epgSummaryMap[channel.streamId]

                                LaunchedEffect(channel.streamId) {
                                    if (epg == null) {
                                        viewModel.loadEpgSummary(channel.streamId)
                                    }
                                }

                                GuideChannelItem(
                                    channel = channel,
                                    epg = epg,
                                    isPlaying = isPlayingThis,
                                    onClick = {
                                        if (isPlayingThis) {
                                            onClose()
                                        } else {
                                            onChannelSelected(channel)
                                        }
                                    }
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
private fun GuideCategoryItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    count: Int,
    isSelected: Boolean,
    iconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) TiviPrimary.copy(alpha = 0.25f) else Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, OpusCyan.copy(alpha = 0.6f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = title,
                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (count > 0) {
                Text(
                    text = "$count",
                    color = if (isSelected) OpusCyan else Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun GuideChannelItem(
    channel: LiveStream,
    epg: EpgProgram?,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isPlaying) Color(0x3300E5FF) else Color(0x331E2638),
        border = if (isPlaying) androidx.compose.foundation.BorderStroke(1.dp, OpusCyan) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kanal Numarası
            Text(
                text = if (channel.num > 0) "${channel.num}" else "•",
                color = if (isPlaying) OpusCyan else Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.width(28.dp)
            )

            // Logo
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161D28)),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.streamIcon.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.streamIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Kanal Adı & EPG
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val programTitle = epg?.getDecodedTitle() ?: channel.currentProgram.decodeBase64IfNeeded().ifBlank { "Yayın Akışı Mevcut Değil" }
                Text(
                    text = programTitle,
                    color = if (isPlaying) OpusCyan.copy(alpha = 0.9f) else Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // EPG İlerleme Çubuğu
                if (epg != null && epg.startTimestamp > 0 && epg.stopTimestamp > epg.startTimestamp) {
                    val now = System.currentTimeMillis() / 1000L
                    val progress = ((now - epg.startTimestamp).toFloat() / (epg.stopTimestamp - epg.startTimestamp)).coerceIn(0f, 1f)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (isPlaying) OpusCyan else TiviPrimary,
                        trackColor = Color(0xFF1E2638)
                    )
                }
            }

            if (isPlaying) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(OpusCyan)
                )
            }
        }
    }
}

// ============================================================================
// 2. TIVIMATE TARZI ALT BİLGİ PANELİ (BOTTOM CHANNEL INFO OSD) & TEKNİK ROZETLER
// ============================================================================

@Composable
fun TiviBottomInfoCard(
    visible: Boolean,
    channel: LiveStream,
    epg: EpgProgram?,
    isFavorite: Boolean,
    diagnostics: StreamDiagnostics,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isPortrait) 10.dp else 16.dp,
                    vertical = if (isPortrait) 6.dp else 12.dp
                ),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xEE0A0E14), // %93 Opak OLED Gece Paneli
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2638).copy(alpha = 0.8f))
        ) {
            Column(modifier = Modifier.padding(if (isPortrait) 10.dp else 14.dp)) {
                val currentTitle = epg?.getDecodedTitle() ?: channel.currentProgram.decodeBase64IfNeeded().ifBlank { "Canlı Yayın" }
                val timeString = if (epg?.start != null && epg.end != null) {
                    "${epg.start} - ${epg.end}"
                } else ""

                if (isPortrait) {
                    // DİKEY MOD (PORTRAIT): Katmanlı, Ferah ve Başlıkları Asla Ezmeyen Düzen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (channel.num > 0) {
                            Text(
                                text = "${channel.num}",
                                color = OpusCyan,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        }

                        // Logo
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF161D28)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!channel.streamIcon.isNullOrBlank()) {
                                AsyncImage(
                                    model = channel.streamIcon,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(4.dp)
                                )
                            } else {
                                Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(22.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Kanal Adı ve Favori Butonu (Tüm satır genişliğini kullanır)
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = channel.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favori",
                                    tint = if (isFavorite) Color(0xFFFFB300) else Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 2. Satır: Canlı Program Başlığı & Zaman
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = currentTitle,
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (timeString.isNotBlank()) {
                            Text(
                                text = "($timeString)",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3. Satır: Gerçek Teknik Rozetler (Tam Genişlikte, Asla Harf Kayması Olmaz)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TechBadge(text = diagnostics.resolutionBadge, isHighlight = true)
                        TechBadge(text = diagnostics.frameRateBadge)
                        TechBadge(text = diagnostics.videoCodecBadge)
                        TechBadge(text = diagnostics.audioCodecBadge)
                    }
                } else {
                    // YATAY MOD (LANDSCAPE): Geniş Ekran TiviMate Yan Yana Düzen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (channel.num > 0) {
                            Text(
                                text = "${channel.num}",
                                color = OpusCyan,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }

                        // Logo
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF161D28)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!channel.streamIcon.isNullOrBlank()) {
                                AsyncImage(
                                    model = channel.streamIcon,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(6.dp)
                                )
                            } else {
                                Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(28.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Orta Kısım: Kanal Adı & Canlı Program Bilgisi
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = channel.name,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Favori",
                                        tint = if (isFavorite) Color(0xFFFFB300) else Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = currentTitle,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (timeString.isNotBlank()) {
                                    Text(
                                        text = "($timeString)",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Sağ Üst Köşe: Gerçek Teknik Rozetler (Resolution, FPS, Video/Audio Codec)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TechBadge(text = diagnostics.resolutionBadge, isHighlight = true)
                            TechBadge(text = diagnostics.frameRateBadge)
                            TechBadge(text = diagnostics.videoCodecBadge)
                            TechBadge(text = diagnostics.audioCodecBadge)
                        }
                    }
                }

                // EPG İlerleme Çubuğu (Parlak #00E5FF)
                val start = epg?.startTimestamp ?: 0L
                val stop = epg?.stopTimestamp ?: 0L
                val nowSec = System.currentTimeMillis() / 1000L
                val progress = if (stop > start && nowSec in start..stop) {
                    ((nowSec - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
                } else 0.5f

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = OpusCyan,
                    trackColor = Color(0xFF1E2638)
                )

                // Sıradaki Program Satırı (Base64 Çözülmüş)
                val rawNext = channel.nextProgram ?: epg?.getDecodedDescription()
                val nextProg = rawNext.decodeBase64IfNeeded()
                if (nextProg.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sıradaki: $nextProg",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TechBadge(text: String, isHighlight: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHighlight) Color(0x3300E5FF) else Color(0xFF1E2638))
            .border(
                0.8.dp,
                if (isHighlight) OpusCyan.copy(alpha = 0.8f) else Color(0xFF334155),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = if (isHighlight) OpusCyan else Color(0xFFE2E8F0),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

// ============================================================================
// 3. ALTTAN AÇILAN "SON İZLENEN KANALLAR" ŞERİDİ & ÖNCEKİ KANAL (RECALL)
// ============================================================================

@Composable
fun TiviRecentChannelsBar(
    visible: Boolean,
    recentChannels: List<LiveStream>,
    currentStreamId: Int,
    onChannelSelected: (LiveStream) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && recentChannels.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xD90A0E14),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2638))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.History, contentDescription = null, tint = OpusCyan, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Son İzlenen Kanallar (${recentChannels.size})",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Geçiş yapmak için dokunun",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recentChannels, key = { it.streamId }) { ch ->
                        val isCurrent = ch.streamId == currentStreamId
                        Surface(
                            onClick = { onChannelSelected(ch) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) Color(0x3300E5FF) else Color(0xFF161D28),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isCurrent) OpusCyan else Color(0xFF1E2638)
                            ),
                            modifier = Modifier.width(135.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F141C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!ch.streamIcon.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ch.streamIcon,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().padding(4.dp)
                                        )
                                    } else {
                                        Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = ch.name,
                                    color = if (isCurrent) OpusCyan else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                if (ch.num > 0) {
                                    Text(
                                        text = "Kanal ${ch.num}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. HIZLI KANAL NUMARASIYLA GEÇİŞ (NUMERIC ZAPPER KUTUSU & TUŞ TAKIMI)
// ============================================================================

@Composable
fun TiviNumericZapperBox(
    visible: Boolean,
    zapperNumber: String,
    matchedChannel: LiveStream?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && zapperNumber.isNotEmpty(),
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xF20A0E14),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, OpusCyan),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Rakam Kutusu: [ 0 2 4 ]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val padded = zapperNumber.padStart(3, '0')
                    padded.forEach { char ->
                        Box(
                            modifier = Modifier
                                .size(36.dp, 44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF161D28))
                                .border(1.dp, OpusCyan.copy(alpha = 0.7f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$char",
                                color = OpusCyan,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Önizleme Kanal Adı & Logo
                if (matchedChannel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!matchedChannel.streamIcon.isNullOrBlank()) {
                            AsyncImage(
                                model = matchedChannel.streamIcon,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                        Text(
                            text = matchedChannel.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = "Kanal aranıyor...",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(100.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = OpusCyan,
                    trackColor = Color(0xFF1E2638)
                )
            }
        }
    }
}

@Composable
fun NumericKeypadDialog(
    onDigitClicked: (Int) -> Unit,
    onEnterClicked: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F141C),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2638)),
            modifier = Modifier.width(260.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Kanal Numarası Girin",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                val digits = listOf(
                    listOf(1, 2, 3),
                    listOf(4, 5, 6),
                    listOf(7, 8, 9)
                )

                digits.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { d ->
                            KeypadButton(text = "$d") { onDigitClicked(d) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    KeypadButton(text = "✕", color = Color(0xFFEF4444)) { onDismiss() }
                    KeypadButton(text = "0") { onDigitClicked(0) }
                    KeypadButton(text = "✓", color = OpusCyan) { onEnterClicked(); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    color: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161D28),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2638)),
        modifier = Modifier.size(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
