package com.tivimax.player.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import java.text.SimpleDateFormat
import java.util.*

/**
 * ANDROID TV İÇİN TAM TIVIMATE TARZI SAAT ÇİZELGELİ EPG PROGRAM REHBERİ (EPG GRID)
 */
@Composable
fun TvEpgGridScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSelectChannel: (LiveStream) -> Unit = { channel ->
        viewModel.playLiveChannel(channel)
        onBack()
    }
) {
    BackHandler(enabled = true, onBack = onBack)

    val channels by viewModel.liveStreams.collectAsState()
    val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()
    val epgProgramsMap by viewModel.epgProgramsMap.collectAsState()
    val selectedCategory by viewModel.selectedLiveCategory.collectAsState()
    val categories by viewModel.visibleLiveCategories.collectAsState()

    var focusedProgram by remember { mutableStateOf<Pair<LiveStream, EpgProgram?>?>(null) }
    var currentTimeText by remember { mutableStateOf("") }

    // Canlı Saat Güncelleme
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTimeText = sdf.format(Date())
            kotlinx.coroutines.delay(10000L)
        }
    }

    // Saat Çizelgesi Dilimleri (Şu anki saatten itibaren 3 saatlik 30'ar dakikalık dilimler)
    val timeSlots = remember(currentTimeText) {
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.MINUTE)
        cal.set(Calendar.MINUTE, if (currentMinutes < 30) 0 else 30)
        cal.set(Calendar.SECOND, 0)

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val slots = mutableListOf<String>()
        for (i in 0..5) {
            slots.add(sdf.format(cal.time))
            cal.add(Calendar.MINUTE, 30)
        }
        slots
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VegaColors.Background)
            .padding(12.dp)
    ) {
        // ==========================================
        // 1. ÜST HEADER: SAAT & ODAKLANILAN PROGRAM DETAY BANNER'I
        // ==========================================
        Card(
            shape = VegaShapes.Medium,
            colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
            border = BorderStroke(1.dp, VegaColors.Border),
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val currentChannel = focusedProgram?.first ?: channels.firstOrNull()
                val currentEpg = focusedProgram?.second ?: currentChannel?.let { epgSummaryMap[it.streamId] }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentChannel != null) {
                        AsyncShimmerImage(
                            model = currentChannel.streamIcon,
                            contentDescription = currentChannel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(VegaShapes.Small)
                                .background(Color.Black),
                            placeholderIcon = Icons.Default.Tv
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${currentChannel.num}. ${currentChannel.name}",
                                    color = VegaColors.TextPrimary,
                                    fontSize = VegaText.BodyProminent,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                if (currentEpg != null) {
                                    Surface(
                                        shape = VegaShapes.Small,
                                        color = VegaColors.Accent.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, VegaColors.Accent)
                                    ) {
                                        Text(
                                            text = currentEpg.getFormattedTime(),
                                            color = VegaColors.Accent,
                                            fontSize = VegaText.Caption,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = currentEpg?.getDecodedTitle() ?: currentChannel.currentProgram ?: "Canlı yayın",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = currentEpg?.getDecodedDescription()?.takeIf { it.isNotBlank() } ?: "Bu yayın için EPG rehber detayı sunucudan iletilmedi.",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // SAAT VE GERİ BUTONU
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .dpadFocusable(
                                shape = VegaShapes.Pill,
                                focusedBorderColor = VegaColors.Accent,
                                onClick = onBack
                            )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = VegaColors.TextPrimary)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentTimeText,
                            color = VegaColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ==========================================
        // 2. TİVİMATE SAAT ÇİZELGESİ BAŞLIK SATIRI (TIME HEADER ROW)
        // ==========================================
        Surface(
            shape = VegaShapes.Small,
            color = VegaColors.Surface,
            border = BorderStroke(1.dp, VegaColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SOL KANAL BAŞLIĞI
                Text(
                    text = "Kanallar",
                    color = VegaColors.Accent,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .width(200.dp)
                        .padding(start = 12.dp)
                )

                // SAĞ SAAT DİLİMLERİ
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    timeSlots.forEach { slot ->
                        Text(
                            text = slot,
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(110.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ==========================================
        // 3. EPG KANAL & PROGRAM IZGARASI (GRID ROWS)
        // ==========================================
        if (channels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VegaColors.Accent, modifier = Modifier.size(36.dp), strokeWidth = 2.5.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(channels, key = { "epg_grid_${it.streamId}" }) { channel ->
                    LaunchedEffect(channel.streamId) {
                        viewModel.loadEpgSummary(channel.streamId)
                    }
                    val epg = epgSummaryMap[channel.streamId]

                    Surface(
                        shape = VegaShapes.Small,
                        color = VegaColors.Card,
                        border = BorderStroke(1.dp, VegaColors.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // SOL: KANAL BİLGİSİ (200.dp)
                            Row(
                                modifier = Modifier
                                    .width(200.dp)
                                    .fillMaxHeight()
                                    .background(VegaColors.Surface)
                                    .dpadFocusable(
                                        focusedBorderColor = VegaColors.Accent,
                                        shape = VegaShapes.Small,
                                        onClick = { onSelectChannel(channel) }
                                    )
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${channel.num}",
                                    color = VegaColors.Accent,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(22.dp)
                                )

                                AsyncShimmerImage(
                                    model = channel.streamIcon,
                                    contentDescription = channel.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(VegaShapes.Small)
                                        .background(Color.Black),
                                    placeholderIcon = Icons.Default.Tv
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Text(
                                    text = channel.name,
                                    color = VegaColors.TextPrimary,
                                    fontSize = VegaText.Secondary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // SAĞ: ZAMAN ÇİZELGELİ PROGRAM BLOKLARI
                            val channelPrograms = epgProgramsMap[channel.streamId]
                            val nowSec = remember(currentTimeText) { System.currentTimeMillis() / 1000L }

                            LazyRow(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!channelPrograms.isNullOrEmpty()) {
                                    val filteredPrograms = channelPrograms.filter {
                                        it.stopTimestamp == 0L || it.stopTimestamp > nowSec - 900
                                     }.ifEmpty { channelPrograms }

                                    items(filteredPrograms, key = { prog -> "${channel.streamId}_${prog.startTimestamp}_${prog.title.hashCode()}" }) { prog ->
                                        val isCurrent = prog.nowPlaying == 1 || (prog.startTimestamp > 0 && prog.stopTimestamp > 0 && nowSec in prog.startTimestamp..prog.stopTimestamp)
                                        val durationMinutes = prog.getDurationMinutes()
                                        val blockWidth = (durationMinutes * 3.66f).dp.coerceIn(90.dp, 440.dp)
                                        val programTitle = prog.getDecodedTitle()
                                        val timeText = prog.getFormattedTime()

                                        Surface(
                                            shape = VegaShapes.Small,
                                            color = if (isCurrent) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.SurfaceVariant,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isCurrent) VegaColors.Accent.copy(alpha = 0.6f) else VegaColors.Border
                                            ),
                                            modifier = Modifier
                                                .width(blockWidth)
                                                .fillMaxHeight(0.85f)
                                                .dpadFocusable(
                                                    focusedBorderColor = VegaColors.Accent,
                                                    shape = VegaShapes.Small,
                                                    onFocusStateChange = { focused ->
                                                        if (focused) {
                                                            focusedProgram = Pair(channel, prog)
                                                        }
                                                    },
                                                    onClick = { onSelectChannel(channel) }
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = programTitle,
                                                        color = VegaColors.TextPrimary,
                                                        fontSize = VegaText.Caption,
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = timeText,
                                                        color = if (isCurrent) VegaColors.Accent else VegaColors.TextSecondary,
                                                        fontSize = VegaText.Caption,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                if (isCurrent) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    item {
                                        val programTitle = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı yayın programı"
                                        val timeText = epg?.getFormattedTime() ?: "Şu an oynatılıyor"
                                        val durationMinutes = epg?.getDurationMinutes() ?: 60
                                        val blockWidth = (durationMinutes * 3.66f).dp.coerceIn(120.dp, 400.dp)

                                        Surface(
                                            shape = VegaShapes.Small,
                                            color = VegaColors.Accent.copy(alpha = 0.18f),
                                            border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.6f)),
                                            modifier = Modifier
                                                .width(blockWidth)
                                                .fillMaxHeight(0.85f)
                                                .dpadFocusable(
                                                    focusedBorderColor = VegaColors.Accent,
                                                    shape = VegaShapes.Small,
                                                    onFocusStateChange = { focused ->
                                                        if (focused) {
                                                            focusedProgram = Pair(channel, epg)
                                                        }
                                                    },
                                                    onClick = { onSelectChannel(channel) }
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = programTitle,
                                                        color = VegaColors.TextPrimary,
                                                        fontSize = VegaText.Caption,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = timeText,
                                                        color = VegaColors.Accent,
                                                        fontSize = VegaText.Caption,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }

                                    if (!channel.nextProgram.isNullOrBlank()) {
                                        item {
                                            Surface(
                                                shape = VegaShapes.Small,
                                                color = VegaColors.SurfaceVariant,
                                                border = BorderStroke(1.dp, VegaColors.Border),
                                                modifier = Modifier
                                                    .width(200.dp)
                                                    .fillMaxHeight(0.85f)
                                                    .dpadFocusable(
                                                        focusedBorderColor = VegaColors.Accent,
                                                        shape = VegaShapes.Small,
                                                        onClick = { onSelectChannel(channel) }
                                                    )
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 8.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Text(
                                                        text = channel.nextProgram,
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
                }
            }
        }
    }
}
