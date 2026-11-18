package com.tivimax.player.ui.tv

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.vegaBackdrop
import com.tivimax.player.ui.design.vegaPanel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Android TV Tam Program Rehberi (StreamVault Guide.png uyarlaması)
 * - Üst hero kartı: solda 16:9 canlı mini video önizleme + sağda kanal logosu, no, ad, aktif program,
 *   saat aralığı, ilerleme çubuğu ve 2 satır özet.
 * - Aksiyon çubuğu: [ Aktif Kategori ], [ Şimdi ], [ Geri ] hap butonları.
 * - Zaman cetveli: 30'ar dakikalık saat dilimleri ve dikey mavi şimdiki zaman çizgisi.
 * - Kanal ve program ızgarası: yuvarlatılmış StreamVault hücreleri, tek odak hedefli.
 */
@OptIn(UnstableApi::class)
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
    val favoriteIds by viewModel.favoriteLiveIds.collectAsState()
    val activePreviewChannel by viewModel.activePreviewChannel.collectAsState()
    val previewError by viewModel.playerManager.errorMessage.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isPlayerScreen = currentScreen is Screen.Player

    var focusedProgram by remember { mutableStateOf<Pair<LiveStream, EpgProgram?>?>(null) }
    var currentTimeText by remember { mutableStateOf("") }
    var minuteWithinHalfHour by remember { mutableIntStateOf(0) }
    var nowJumpTick by remember { mutableIntStateOf(0) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val displayedChannels = remember(channels, selectedCategory, favoriteIds) {
        if (selectedCategory == "favorites") {
            channels.filter { favoriteIds.contains(it.streamId.toString()) }
        } else {
            channels
        }
    }

    val activeCategoryName = remember(selectedCategory, categories) {
        when (selectedCategory) {
            "favorites" -> "Favori Kanallar"
            "recents" -> "Son İzlenenler"
            "all", "" -> "Tüm Kanallar"
            else -> categories.firstOrNull { it.categoryId == selectedCategory }?.categoryName ?: "Tüm Kanallar"
        }
    }

    val gridListState = rememberLazyListState()

    // Aktif önizleme kanalı yoksa ilk kanalı önizlemeye al
    LaunchedEffect(displayedChannels) {
        if (activePreviewChannel == null && displayedChannels.isNotEmpty()) {
            viewModel.previewChannel(displayedChannels.first())
        }
    }

    // Canlı saat ve şimdiki zaman çizgisi ofseti güncelleme
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            val cal = Calendar.getInstance()
            currentTimeText = sdf.format(cal.time)
            minuteWithinHalfHour = cal.get(Calendar.MINUTE) % 30
            kotlinx.coroutines.delay(10_000L)
        }
    }

    // 3 saatlik 30'ar dakikalık saat dilimleri
    val timeSlots = remember(currentTimeText, nowJumpTick) {
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.MINUTE)
        cal.set(Calendar.MINUTE, if (currentMinutes < 30) 0 else 30)
        cal.set(Calendar.SECOND, 0)

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        List(6) {
            val label = sdf.format(cal.time)
            cal.add(Calendar.MINUTE, 30)
            label
        }
    }

    val nowIndicatorFraction = (minuteWithinHalfHour.toFloat() / 180f).coerceIn(0f, 0.98f)
    val channelRailWidth = 224.dp
    val timelineGap = 6.dp

    val handleChannelOrProgramClick: (LiveStream) -> Unit = { channel ->
        if (activePreviewChannel?.streamId == channel.streamId) {
            onSelectChannel(channel)
        } else {
            viewModel.previewChannel(channel)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .vegaBackdrop()
            .padding(14.dp)
    ) {
        // ==========================================
        // 1. ÜST HERO KARTI (16:9 CANLI MİNİ ÖNİZLEME + DETAYLAR)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .vegaPanel(VegaShapes.Large)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val heroChannel = focusedProgram?.first ?: activePreviewChannel ?: displayedChannels.firstOrNull()
                val heroEpg = focusedProgram?.second ?: heroChannel?.let { epgSummaryMap[it.streamId] }

                // SOL: 16:9 Canlı Mini Video Önizleme Kutusu
                var isPreviewFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(16f / 9f)
                        .clip(VegaShapes.Medium)
                        .background(Color.Black)
                        .dpadFocusable(
                            shape = VegaShapes.Medium,
                            focusedBorderColor = VegaColors.FocusBorder,
                            scale = 1.02f,
                            onClick = {
                                val target = activePreviewChannel ?: heroChannel
                                if (target != null) onSelectChannel(target)
                            },
                            onFocusStateChange = { isPreviewFocused = it }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isPlayerScreen) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    setKeepContentOnPlayerReset(true)
                                    isFocusable = false
                                    isFocusableInTouchMode = false
                                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    viewModel.playerManager.attachView(this, isPrimary = false, tag = "TvEpgGrid-preview")
                                }
                            },
                            update = { playerView ->
                                viewModel.playerManager.attachView(playerView, isPrimary = false, tag = "TvEpgGrid-preview")
                            },
                            onRelease = { playerView ->
                                viewModel.playerManager.detachView(playerView, tag = "TvEpgGrid-preview")
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    previewError?.let { msg ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.78f))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = msg,
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Canlı rozet
                    if (activePreviewChannel != null && previewError == null) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(VegaShapes.Small)
                                .background(VegaColors.OverlayPill)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(VegaShapes.Pill)
                                    .background(VegaColors.Live)
                            )
                            Text(
                                text = if (isPreviewFocused) "Tam ekran (OK)" else "Canlı",
                                color = Color.White,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // SAĞ: Kanal Logosu, Numarası, Adı, Aktif Program, Saat, İlerleme ve 2 Satır Özet
                if (heroChannel != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(VegaShapes.Small)
                                    .background(VegaColors.SurfaceAccent)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncShimmerImage(
                                    model = heroChannel.streamIcon,
                                    contentDescription = heroChannel.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                    placeholderIcon = Icons.Default.Tv
                                )
                            }

                            val numTitle = if (heroChannel.num > 0) {
                                "${heroChannel.num}. ${heroChannel.name}"
                            } else {
                                heroChannel.name
                            }
                            Text(
                                text = numTitle,
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.SectionTitle,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = VegaColors.Accent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = currentTimeText,
                                    color = VegaColors.TextPrimary,
                                    fontSize = VegaText.BodyProminent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(5.dp))

                        Text(
                            text = heroEpg?.getDecodedTitle() ?: heroChannel.currentProgram ?: "Canlı yayın",
                            color = VegaColors.TextPrimary.copy(alpha = 0.94f),
                            fontSize = VegaText.Body,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val timeStr = heroEpg?.getFormattedTime()
                        if (!timeStr.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = timeStr,
                                color = VegaColors.TextMuted,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (heroEpg != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            StreamVaultProgressBar(
                                progress = heroEpg.getProgressFraction() ?: 0f,
                                modifier = Modifier.fillMaxWidth(0.75f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = heroEpg?.getDecodedDescription()?.takeIf { it.isNotBlank() }
                                ?: "Bu yayın için program özeti bulunmuyor.",
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Secondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Program rehberi",
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.SectionTitle,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ==========================================
        // 2. STREAMVAULT AKSİYON ÇUBUĞU: [ Aktif Kategori ] [ Şimdi ] [ Geri ]
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GuideToolbarPill(
                label = activeCategoryName,
                trailingIcon = Icons.Default.ExpandMore,
                minWidth = 180.dp,
                onClick = { showCategoryPicker = true }
            )

            GuideToolbarPill(
                label = "Şimdi",
                onClick = { nowJumpTick++ }
            )

            GuideToolbarPill(
                label = "Geri",
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "${displayedChannels.size} kanal • Önizleme için OK, tam ekran için tekrar OK",
                color = VegaColors.TextMuted,
                fontSize = VegaText.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ==========================================
        // 3. ZAMAN CETVELİ + DİKEY MAVİ ŞİMDİKİ ZAMAN ÇİZGİSİ
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .width(channelRailWidth)
                    .fillMaxHeight()
                    .padding(start = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = "Kanallar",
                    color = VegaColors.TextMuted,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(timelineGap))

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val trackWidth = maxWidth
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    timeSlots.forEach { slot ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = slot,
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(7.dp)
                                    .background(VegaColors.Border)
                            )
                        }
                    }
                }

                // Dikey mavi şimdiki zaman göstergesi
                Box(
                    modifier = Modifier
                        .offset(x = (trackWidth * nowIndicatorFraction).coerceIn(0.dp, (trackWidth - 2.dp).coerceAtLeast(0.dp)))
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(VegaColors.Accent)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ==========================================
        // 4. STREAMVAULT KANAL & PROGRAM IZGARASI
        // ==========================================
        if (displayedChannels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Bu kategoride kanal bulunamadı",
                    color = VegaColors.TextMuted,
                    fontSize = VegaText.Body
                )
            }
        } else {
            LazyColumn(
                state = gridListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(displayedChannels, key = { "epg_grid_${it.streamId}_${it.num}" }) { channel ->
                    LaunchedEffect(channel.streamId) {
                        viewModel.loadEpgSummary(channel.streamId)
                    }
                    val epg = epgSummaryMap[channel.streamId]
                    val channelPrograms = epgProgramsMap[channel.streamId]
                    val isFav = favoriteIds.contains(channel.streamId.toString())
                    val isCurrentPreview = activePreviewChannel?.streamId == channel.streamId
                    val rowScrollState = rememberLazyListState()

                    LaunchedEffect(nowJumpTick) {
                        if (nowJumpTick > 0) {
                            runCatching { rowScrollState.animateScrollToItem(0) }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // SOL: Yuvarlatılmış Kanal Hücresi (Tek Odak Hedefi)
                        var isChannelFocused by remember { mutableStateOf(false) }
                        Surface(
                            shape = VegaShapes.Small,
                            color = when {
                                isChannelFocused -> VegaColors.SurfaceVariant
                                isCurrentPreview -> VegaColors.Accent.copy(alpha = 0.16f)
                                else -> VegaColors.SurfaceElevated
                            },
                            border = if (isChannelFocused) null else BorderStroke(
                                width = if (isCurrentPreview) 1.5.dp else 1.dp,
                                color = if (isCurrentPreview) VegaColors.Accent.copy(alpha = 0.65f) else VegaColors.Border
                            ),
                            modifier = Modifier
                                .width(channelRailWidth)
                                .fillMaxHeight()
                                .dpadFocusable(
                                    focusedBorderColor = VegaColors.FocusBorder,
                                    shape = VegaShapes.Small,
                                    scale = 1.02f,
                                    onFocusStateChange = { focused ->
                                        isChannelFocused = focused
                                        if (focused) {
                                            focusedProgram = Pair(channel, epg)
                                        }
                                    },
                                    onClick = { handleChannelOrProgramClick(channel) }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(VegaShapes.Small)
                                        .background(VegaColors.SurfaceAccent)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncShimmerImage(
                                        model = channel.streamIcon,
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                        placeholderIcon = Icons.Default.Tv
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val prefix = if (channel.num > 0) "${channel.num}. " else ""
                                    Text(
                                        text = "$prefix${channel.name}",
                                        color = if (isCurrentPreview || isChannelFocused) VegaColors.TextPrimary else VegaColors.TextPrimary.copy(alpha = 0.92f),
                                        fontSize = VegaText.Secondary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Program bilgisi yok",
                                        color = VegaColors.TextMuted,
                                        fontSize = VegaText.Caption,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (channel.tvArchive == 1) {
                                    Box(
                                        modifier = Modifier
                                            .clip(VegaShapes.Pill)
                                            .background(VegaColors.Accent.copy(alpha = 0.16f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Arşiv",
                                            color = VegaColors.Accent,
                                            fontSize = VegaText.Caption,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                } else if (isFav) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = VegaColors.Gold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(timelineGap))

                        // SAĞ: Yuvarlatılmış Program Blokları + Dikey Mavi Şimdiki Zaman Çizgisi
                        val nowSec = remember(currentTimeText, nowJumpTick) { System.currentTimeMillis() / 1000L }

                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(VegaShapes.Small)
                                .background(VegaColors.Surface.copy(alpha = 0.72f))
                        ) {
                            val rowTrackWidth = maxWidth

                            LazyRow(
                                state = rowScrollState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 3.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!channelPrograms.isNullOrEmpty()) {
                                    val filteredPrograms = channelPrograms.filter {
                                        it.stopTimestamp == 0L || it.stopTimestamp > nowSec - 900
                                    }.ifEmpty { channelPrograms }

                                    items(
                                        items = filteredPrograms,
                                        key = { prog -> "${channel.streamId}_${prog.startTimestamp}_${prog.title.hashCode()}" }
                                    ) { prog ->
                                        val isCurrent = prog.nowPlaying == 1 ||
                                            (prog.startTimestamp > 0 && prog.stopTimestamp > 0 && nowSec in prog.startTimestamp..prog.stopTimestamp)
                                        val durationMinutes = prog.getDurationMinutes()
                                        val blockWidth = (durationMinutes * 3.8f).dp.coerceIn(110.dp, 440.dp)

                                        GuideProgramCell(
                                            title = prog.getDecodedTitle(),
                                            timeText = prog.getFormattedTime(),
                                            isCurrent = isCurrent,
                                            width = blockWidth,
                                            onFocused = { focusedProgram = Pair(channel, prog) },
                                            onClick = { handleChannelOrProgramClick(channel) }
                                        )
                                    }
                                } else {
                                    item(key = "fallback_now_${channel.streamId}") {
                                        val programTitle = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı yayın"
                                        val timeText = epg?.getFormattedTime() ?: "Şu an yayında"
                                        val durationMinutes = epg?.getDurationMinutes() ?: 60
                                        val blockWidth = (durationMinutes * 3.8f).dp.coerceIn(150.dp, 400.dp)

                                        GuideProgramCell(
                                            title = programTitle,
                                            timeText = timeText,
                                            isCurrent = true,
                                            width = blockWidth,
                                            onFocused = { focusedProgram = Pair(channel, epg) },
                                            onClick = { handleChannelOrProgramClick(channel) }
                                        )
                                    }

                                    if (!channel.nextProgram.isNullOrBlank()) {
                                        item(key = "fallback_next_${channel.streamId}") {
                                            GuideProgramCell(
                                                title = channel.nextProgram,
                                                timeText = "Sonraki program",
                                                isCurrent = false,
                                                width = 210.dp,
                                                onFocused = { focusedProgram = Pair(channel, epg) },
                                                onClick = { handleChannelOrProgramClick(channel) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Zaman çizelgesi ile hizalı dikey mavi şimdiki zaman çizgisi
                            Box(
                                modifier = Modifier
                                    .offset(x = (rowTrackWidth * nowIndicatorFraction).coerceIn(0.dp, (rowTrackWidth - 2.dp).coerceAtLeast(0.dp)))
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(VegaColors.Accent.copy(alpha = 0.55f))
                            )
                        }
                    }
                }
            }
        }
    }

    // Kategori Seçim Penceresi (Tek Odak Hedefli)
    if (showCategoryPicker) {
        Dialog(onDismissRequest = { showCategoryPicker = false }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 420.dp)
                    .heightIn(max = 460.dp)
                    .vegaPanel(VegaShapes.Large)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Kategori seç",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.SectionTitle,
                        fontWeight = FontWeight.Bold
                    )
                    GuideToolbarPill(
                        label = "Kapat",
                        onClick = { showCategoryPicker = false }
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(key = "pick_all") {
                        GuideCategoryPickerRow(
                            title = "Tüm Kanallar",
                            count = 0,
                            isSelected = selectedCategory == "all" || selectedCategory.isEmpty(),
                            onClick = {
                                viewModel.loadLiveStreams("all")
                                showCategoryPicker = false
                            }
                        )
                    }
                    item(key = "pick_fav") {
                        GuideCategoryPickerRow(
                            title = "Favori Kanallar",
                            count = favoriteIds.size,
                            isSelected = selectedCategory == "favorites",
                            onClick = {
                                viewModel.selectedLiveCategory.value = "favorites"
                                showCategoryPicker = false
                            }
                        )
                    }
                    items(
                        items = categories.filter { it.categoryId != "all" }.distinctBy { it.categoryId },
                        key = { "pick_${it.categoryId}" }
                    ) { category ->
                        GuideCategoryPickerRow(
                            title = category.categoryName,
                            count = category.itemCount,
                            isSelected = selectedCategory == category.categoryId,
                            onClick = {
                                viewModel.loadLiveStreams(category.categoryId)
                                showCategoryPicker = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideToolbarPill(
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    minWidth: androidx.compose.ui.unit.Dp = 0.dp,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Medium,
        color = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated,
        border = if (isFocused) null else BorderStroke(1.dp, VegaColors.Border),
        modifier = Modifier
            .height(38.dp)
            .widthIn(min = minWidth, max = 260.dp)
            .dpadFocusable(
                shape = VegaShapes.Medium,
                focusedBorderColor = VegaColors.FocusBorder,
                scale = 1.03f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isFocused) VegaColors.Accent else VegaColors.TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                color = VegaColors.TextPrimary,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = VegaColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun GuideProgramCell(
    title: String,
    timeText: String,
    isCurrent: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Small,
        color = when {
            isFocused -> VegaColors.SurfaceVariant
            isCurrent -> VegaColors.Accent.copy(alpha = 0.20f)
            else -> VegaColors.SurfaceElevated
        },
        border = if (isFocused) null else BorderStroke(
            width = 1.dp,
            color = if (isCurrent) VegaColors.Accent.copy(alpha = 0.45f) else VegaColors.Border
        ),
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Small,
                scale = 1.015f,
                onFocusStateChange = { focused ->
                    isFocused = focused
                    if (focused) onFocused()
                },
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = when {
                        isFocused -> VegaColors.TextPrimary
                        isCurrent -> VegaColors.Accent
                        else -> VegaColors.TextPrimary
                    },
                    fontSize = VegaText.Secondary,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeText,
                    color = if (isFocused) VegaColors.TextSecondary else VegaColors.TextMuted,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = VegaColors.Accent,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun GuideCategoryPickerRow(
    title: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Small,
        color = when {
            isFocused -> VegaColors.SurfaceVariant
            isSelected -> VegaColors.Accent.copy(alpha = 0.18f)
            else -> VegaColors.SurfaceElevated
        },
        border = if (isFocused) null else BorderStroke(
            width = 1.dp,
            color = if (isSelected) VegaColors.Accent else VegaColors.Border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(
                shape = VegaShapes.Small,
                focusedBorderColor = VegaColors.FocusBorder,
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = if (isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                fontSize = VegaText.Body,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$count",
                    color = if (isSelected) VegaColors.Accent else VegaColors.TextMuted,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
