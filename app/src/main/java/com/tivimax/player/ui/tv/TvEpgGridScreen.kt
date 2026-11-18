package com.tivimax.player.ui.tv

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.vegaBackdrop
import com.tivimax.player.ui.design.vegaPanel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.*

private data class PositionedGuideProgram(
    val program: EpgProgram?,
    val title: String,
    val timeText: String,
    val isCurrent: Boolean,
    val offsetX: Dp,
    val width: Dp
)

/**
 * Android TV Tam Program Rehberi (StreamVault Guide.png uyarlaması)
 * - Üst hero kartı: solda 16:9 canlı mini video önizleme + sağda kanal logosu, no, ad, aktif program,
 *   saat aralığı, ilerleme çubuğu ve 2 satır özet.
 * - Aksiyon çubuğu: [ Aktif Kategori ], [ -3 saat ], [ Şimdi ], [ +3 saat ], [ Geri ] hap butonları.
 * - Senkronize zaman cetveli: tek yatay kaydırma durumu (timelineScrollState) üzerinde 30'ar dakikalık
 *   saat dilimleri ve gerçek zaman ofsetli dikey mavi şimdiki zaman çizgisi.
 * - Kanal ve program ızgarası: zaman damgalarına oranlı konumlanan StreamVault hücreleri ve detay diyaloğu.
 */
@OptIn(UnstableApi::class, FlowPreview::class)
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
    val favoriteStreams by viewModel.favoriteLiveStreams.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val activePreviewChannel by viewModel.activePreviewChannel.collectAsState()
    val previewError by viewModel.playerManager.errorMessage.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isPlayerScreen = currentScreen is Screen.Player

    var focusedProgram by remember { mutableStateOf<Pair<LiveStream, EpgProgram?>?>(null) }
    var selectedProgramDetail by remember { mutableStateOf<Pair<LiveStream, EpgProgram>?>(null) }
    var nowEpochSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    var windowOffsetHours by remember { mutableIntStateOf(0) }
    var nowJumpTick by remember { mutableIntStateOf(0) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val displayedChannels = remember(channels, selectedCategory, favoriteStreams, recentChannels) {
        when (selectedCategory) {
            "favorites" -> favoriteStreams
            "recents" -> recentChannels
            else -> channels
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
    val timelineScrollState = rememberScrollState()
    val initialChannelFocus = remember { FocusRequester() }
    var hasInitialFocused by remember { mutableStateOf(false) }

    // Aktif önizleme kanalı yoksa ilk kanalı önizlemeye al
    LaunchedEffect(displayedChannels) {
        if (activePreviewChannel == null && displayedChannels.isNotEmpty()) {
            viewModel.previewChannel(displayedChannels.first())
        }
    }

    // Açılışta aktif (veya ilk) kanal satırına odaklan
    val initialFocusChannelIndex = remember(displayedChannels, activePreviewChannel?.streamId) {
        val idx = displayedChannels.indexOfFirst { it.streamId == activePreviewChannel?.streamId }
        if (idx >= 0) idx else 0
    }

    LaunchedEffect(displayedChannels.isNotEmpty()) {
        if (!hasInitialFocused && displayedChannels.isNotEmpty()) {
            runCatching {
                gridListState.scrollToItem((initialFocusChannelIndex - 1).coerceAtLeast(0))
            }
            delay(60L)
            if (initialChannelFocus.requestFocusSafely()) {
                hasInitialFocused = true
            }
        }
    }

    // Görünür kanallar için toplu ve gecikmeli EPG yüklemesi
    LaunchedEffect(gridListState, displayedChannels) {
        snapshotFlow {
            gridListState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                displayedChannels.getOrNull(info.index)?.streamId
            }
        }
            .distinctUntilChanged()
            .debounce(150L)
            .collect { visibleIds ->
                if (visibleIds.isNotEmpty()) {
                    viewModel.loadEpgSummariesBatch(visibleIds)
                }
            }
    }

    // Canlı saat ve şimdiki zaman çizgisi ofseti güncelleme
    LaunchedEffect(Unit) {
        while (true) {
            nowEpochSec = System.currentTimeMillis() / 1000L
            delay(15_000L)
        }
    }

    // Zaman penceresi değiştiğinde veya "Şimdi" basıldığında ortak zaman çizelgesini başa al
    LaunchedEffect(windowOffsetHours, nowJumpTick) {
        if (nowJumpTick > 0 || windowOffsetHours != 0) {
            runCatching { timelineScrollState.animateScrollTo(0) }
        }
    }

    val currentTimeText = remember(nowEpochSec / 60L) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowEpochSec * 1000L))
    }

    // 3 saatlik (180 dk) senkronize zaman penceresi: 6.dp/dk -> 1080.dp toplam genişlik
    val dpPerMinute = 6.dp
    val totalTimelineMinutes = 180
    val totalTimelineWidth = dpPerMinute * totalTimelineMinutes
    val slotWidth = dpPerMinute * 30 // 180.dp her 30 dk dilimi

    val windowStartSec = remember(nowEpochSec / 60L, windowOffsetHours, nowJumpTick) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowEpochSec * 1000L
            val min = get(Calendar.MINUTE)
            set(Calendar.MINUTE, if (min < 30) 0 else 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, windowOffsetHours)
        }
        cal.timeInMillis / 1000L
    }
    val windowEndSec = windowStartSec + totalTimelineMinutes * 60L

    val timeSlots = remember(windowStartSec) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        List(6) { idx ->
            sdf.format(Date((windowStartSec + idx * 1800L) * 1000L))
        }
    }

    val windowRangeLabel = remember(windowStartSec, windowOffsetHours) {
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val startStr = sdfTime.format(Date(windowStartSec * 1000L))
        val endStr = sdfTime.format(Date(windowEndSec * 1000L))
        if (windowOffsetHours == 0) {
            "$startStr - $endStr"
        } else {
            val sdfDay = SimpleDateFormat("dd MMM", Locale("tr", "TR"))
            "${sdfDay.format(Date(windowStartSec * 1000L))} • $startStr - $endStr"
        }
    }

    val isNowInWindow = nowEpochSec in windowStartSec..windowEndSec
    val nowOffsetDp = (dpPerMinute * ((nowEpochSec - windowStartSec).toFloat() / 60f))
        .coerceIn(0.dp, (totalTimelineWidth - 2.dp).coerceAtLeast(0.dp))

    val channelRailWidth = 224.dp
    val timelineGap = 6.dp

    val handleChannelClick: (LiveStream) -> Unit = { channel ->
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
        // 2. STREAMVAULT AKSİYON ÇUBUĞU: [ Aktif Kategori ] [ -3 saat ] [ Şimdi ] [ +3 saat ] [ Geri ]
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GuideToolbarPill(
                label = activeCategoryName,
                trailingIcon = Icons.Default.ExpandMore,
                minWidth = 170.dp,
                onClick = { showCategoryPicker = true }
            )

            GuideToolbarPill(
                label = "-3 saat",
                leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                onClick = { windowOffsetHours -= 3 }
            )

            GuideToolbarPill(
                label = "Şimdi",
                isHighlighted = windowOffsetHours == 0,
                onClick = {
                    windowOffsetHours = 0
                    nowJumpTick++
                }
            )

            GuideToolbarPill(
                label = "+3 saat",
                trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                onClick = { windowOffsetHours += 3 }
            )

            GuideToolbarPill(
                label = "Geri",
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "${displayedChannels.size} kanal • $windowRangeLabel",
                color = VegaColors.TextMuted,
                fontSize = VegaText.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ==========================================
        // 3. SENKRONİZE ZAMAN CETVELİ + DİKEY MAVİ ŞİMDİKİ ZAMAN ÇİZGİSİ
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(timelineScrollState)
            ) {
                Box(
                    modifier = Modifier
                        .width(totalTimelineWidth)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        timeSlots.forEach { slot ->
                            Column(
                                modifier = Modifier
                                    .width(slotWidth)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(
                                    text = slot,
                                    color = VegaColors.TextSecondary,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(7.dp)
                                        .background(VegaColors.Border)
                                )
                            }
                        }
                    }

                    // Gerçek zaman ofsetli dikey mavi şimdiki zaman göstergesi
                    if (isNowInWindow) {
                        Box(
                            modifier = Modifier
                                .offset(x = nowOffsetDp)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(VegaColors.Accent)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ==========================================
        // 4. STREAMVAULT KANAL & PROGRAM IZGARASI (SENKRONİZE YATAY KAYDIRMA)
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
                itemsIndexed(
                    items = displayedChannels,
                    key = { index, channel -> "epg_ch_${channel.streamId}_${channel.categoryId}_$index" }
                ) { index, channel ->
                    val epg = epgSummaryMap[channel.streamId]
                    val channelPrograms = epgProgramsMap[channel.streamId]
                    val isFav = favoriteIds.contains(channel.streamId.toString())
                    val isCurrentPreview = activePreviewChannel?.streamId == channel.streamId
                    val isInitialFocusTarget = index == initialFocusChannelIndex

                    val positionedBlocks = remember(
                        channelPrograms,
                        epg,
                        channel.currentProgram,
                        channel.nextProgram,
                        windowStartSec,
                        windowEndSec,
                        nowEpochSec,
                        windowOffsetHours
                    ) {
                        val result = mutableListOf<PositionedGuideProgram>()
                        val validTimestamped = channelPrograms
                            ?.filter { it.startTimestamp > 0L && it.stopTimestamp > it.startTimestamp }
                            ?.filter { it.stopTimestamp > windowStartSec && it.startTimestamp < windowEndSec }
                            ?.sortedBy { it.startTimestamp }
                            .orEmpty()

                        if (validTimestamped.isNotEmpty()) {
                            validTimestamped.forEach { prog ->
                                val visStart = maxOf(prog.startTimestamp, windowStartSec)
                                val visEnd = minOf(prog.stopTimestamp, windowEndSec)
                                val startMin = (visStart - windowStartSec).toFloat() / 60f
                                val durMin = ((visEnd - visStart).toFloat() / 60f).coerceAtLeast(1f)
                                val offsetX = dpPerMinute * startMin
                                val rawWidth = dpPerMinute * durMin
                                val maxAvail = (totalTimelineWidth - offsetX).coerceAtLeast(0.dp)
                                val blockWidth = (rawWidth - 4.dp).coerceAtLeast(36.dp).coerceAtMost(maxAvail)
                                if (blockWidth >= 24.dp) {
                                    val isCurrent = prog.nowPlaying == 1 ||
                                        (nowEpochSec in prog.startTimestamp..prog.stopTimestamp)
                                    result.add(
                                        PositionedGuideProgram(
                                            program = prog,
                                            title = prog.getDecodedTitle(),
                                            timeText = prog.getFormattedTime(),
                                            isCurrent = isCurrent,
                                            offsetX = offsetX,
                                            width = blockWidth
                                        )
                                    )
                                }
                            }
                        } else if (epg != null && epg.startTimestamp > 0L && epg.stopTimestamp > epg.startTimestamp &&
                            epg.stopTimestamp > windowStartSec && epg.startTimestamp < windowEndSec
                        ) {
                            val visStart = maxOf(epg.startTimestamp, windowStartSec)
                            val visEnd = minOf(epg.stopTimestamp, windowEndSec)
                            val startMin = (visStart - windowStartSec).toFloat() / 60f
                            val durMin = ((visEnd - visStart).toFloat() / 60f).coerceAtLeast(1f)
                            val offsetX = dpPerMinute * startMin
                            val maxAvail = (totalTimelineWidth - offsetX).coerceAtLeast(0.dp)
                            val blockWidth = (dpPerMinute * durMin - 4.dp).coerceAtLeast(60.dp).coerceAtMost(maxAvail)
                            val isCurrent = epg.nowPlaying == 1 || (nowEpochSec in epg.startTimestamp..epg.stopTimestamp)
                            result.add(
                                PositionedGuideProgram(
                                    program = epg,
                                    title = epg.getDecodedTitle(),
                                    timeText = epg.getFormattedTime(),
                                    isCurrent = isCurrent,
                                    offsetX = offsetX,
                                    width = blockWidth
                                )
                            )
                        } else if (windowOffsetHours == 0) {
                            val nowTitle = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı yayın"
                            val nowTime = epg?.getFormattedTime() ?: "Şu an yayında"
                            val hasNext = !channel.nextProgram.isNullOrBlank()
                            val firstWidth = if (hasNext) (dpPerMinute * 60) - 4.dp else totalTimelineWidth - 4.dp
                            result.add(
                                PositionedGuideProgram(
                                    program = epg,
                                    title = nowTitle,
                                    timeText = nowTime,
                                    isCurrent = true,
                                    offsetX = 0.dp,
                                    width = firstWidth
                                )
                            )
                            if (hasNext) {
                                val nextOffset = dpPerMinute * 60
                                result.add(
                                    PositionedGuideProgram(
                                        program = null,
                                        title = channel.nextProgram!!,
                                        timeText = "Sonraki program",
                                        isCurrent = false,
                                        offsetX = nextOffset,
                                        width = (totalTimelineWidth - nextOffset - 4.dp).coerceAtLeast(120.dp)
                                    )
                                )
                            }
                        } else {
                            result.add(
                                PositionedGuideProgram(
                                    program = null,
                                    title = "Bu saat aralığı için program bilgisi yok",
                                    timeText = windowRangeLabel,
                                    isCurrent = false,
                                    offsetX = 0.dp,
                                    width = totalTimelineWidth - 4.dp
                                )
                            )
                        }
                        result
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
                                .then(if (isInitialFocusTarget) Modifier.focusRequester(initialChannelFocus) else Modifier)
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
                                    onClick = { handleChannelClick(channel) }
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

                        // SAĞ: Ortak timelineScrollState ile Senkronize Program Blokları + Şimdiki Zaman Çizgisi
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(VegaShapes.Small)
                                .background(VegaColors.Surface.copy(alpha = 0.72f))
                                .horizontalScroll(timelineScrollState)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(totalTimelineWidth)
                                    .fillMaxHeight()
                                    .padding(vertical = 3.dp)
                            ) {
                                positionedBlocks.forEach { block ->
                                    GuideProgramCell(
                                        title = block.title,
                                        timeText = block.timeText,
                                        isCurrent = block.isCurrent,
                                        width = block.width,
                                        modifier = Modifier.offset(x = block.offsetX),
                                        onFocused = { focusedProgram = Pair(channel, block.program ?: epg) },
                                        onClick = {
                                            val prog = block.program
                                            if (prog != null) {
                                                selectedProgramDetail = Pair(channel, prog)
                                            } else {
                                                selectedProgramDetail = Pair(
                                                    channel,
                                                    EpgProgram(
                                                        title = block.title,
                                                        description = "",
                                                        nowPlaying = if (block.isCurrent) 1 else 0
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }

                                // Zaman çizelgesiyle piksel piksel hizalı dikey mavi şimdiki zaman çizgisi
                                if (isNowInWindow) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = nowOffsetDp)
                                            .width(2.dp)
                                            .fillMaxHeight()
                                            .background(VegaColors.Accent.copy(alpha = 0.65f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Program Detay Diyaloğu (StreamVault tarzı)
    selectedProgramDetail?.let { (channel, program) ->
        val watchButtonFocus = remember(channel.streamId, program.startTimestamp, program.title) { FocusRequester() }
        val isCurrentPreview = activePreviewChannel?.streamId == channel.streamId
        val isLiveNow = program.nowPlaying == 1 ||
            (program.startTimestamp > 0L && program.stopTimestamp > 0L && nowEpochSec in program.startTimestamp..program.stopTimestamp)
        val progProgress = if (program.startTimestamp > 0L && program.stopTimestamp > program.startTimestamp && isLiveNow) {
            ((nowEpochSec - program.startTimestamp).toFloat() / (program.stopTimestamp - program.startTimestamp).toFloat())
                .coerceIn(0.05f, 0.98f)
        } else null
        val desc = program.getDecodedDescription()

        LaunchedEffect(channel.streamId, program.startTimestamp, program.title) {
            delay(40L)
            watchButtonFocus.requestFocusSafely()
        }

        Dialog(onDismissRequest = { selectedProgramDetail = null }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 380.dp, max = 520.dp)
                    .heightIn(max = 460.dp)
                    .vegaPanel(VegaShapes.Large)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Kanal Başlığı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(VegaShapes.Small)
                            .background(VegaColors.SurfaceAccent)
                            .padding(5.dp),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            color = VegaColors.Accent,
                            fontSize = VegaText.Body,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isLiveNow) {
                                Box(
                                    modifier = Modifier
                                        .clip(VegaShapes.Small)
                                        .background(VegaColors.Live)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Canlı",
                                        color = Color.White,
                                        fontSize = VegaText.Caption,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = program.getFormattedTime(),
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Program Başlığı
                Text(
                    text = program.getDecodedTitle(),
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (progProgress != null) {
                    StreamVaultProgressBar(
                        progress = progProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Program Açıklaması
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (desc.isNotBlank()) desc else "Bu program için detaylı özet bilgisi bulunmuyor.",
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Body
                    )
                }

                HorizontalDivider(color = VegaColors.Border, thickness = 0.5.dp)

                // Aksiyon Butonları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.Accent,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .focusRequester(watchButtonFocus)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = Color.White,
                                scale = 1.03f,
                                onClick = {
                                    selectedProgramDetail = null
                                    onSelectChannel(channel)
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = VegaColors.OnAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Kanalı İzle",
                                color = VegaColors.OnAccent,
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!isCurrentPreview) {
                        GuideToolbarPill(
                            label = "Önizle",
                            onClick = {
                                selectedProgramDetail = null
                                viewModel.previewChannel(channel)
                            }
                        )
                    }

                    GuideToolbarPill(
                        label = "Kapat",
                        leadingIcon = Icons.Default.Close,
                        onClick = { selectedProgramDetail = null }
                    )
                }
            }
        }
    }

    // Kategori Seçim Penceresi (Tek Odak Hedefli)
    if (showCategoryPicker) {
        val pickerCategories = remember(categories) {
            categories.filter { it.categoryId != "all" }.distinctBy { it.categoryId }
        }
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
                    item(key = "pick_recents") {
                        GuideCategoryPickerRow(
                            title = "Son İzlenenler",
                            count = recentChannels.size,
                            isSelected = selectedCategory == "recents",
                            onClick = {
                                viewModel.selectedLiveCategory.value = "recents"
                                showCategoryPicker = false
                            }
                        )
                    }
                    itemsIndexed(
                        items = pickerCategories,
                        key = { index, cat -> "pick_${cat.categoryId}_$index" }
                    ) { _, category ->
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
    minWidth: Dp = 0.dp,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = VegaShapes.Medium,
        color = when {
            isFocused -> VegaColors.SurfaceVariant
            isHighlighted -> VegaColors.Accent.copy(alpha = 0.18f)
            else -> VegaColors.SurfaceElevated
        },
        border = if (isFocused) null else BorderStroke(
            width = 1.dp,
            color = if (isHighlighted) VegaColors.Accent else VegaColors.Border
        ),
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
                    tint = if (isFocused || isHighlighted) VegaColors.Accent else VegaColors.TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                color = if (isHighlighted && !isFocused) VegaColors.Accent else VegaColors.TextPrimary,
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
                    tint = if (isFocused || isHighlighted) VegaColors.Accent else VegaColors.TextSecondary,
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
    width: Dp,
    modifier: Modifier = Modifier,
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
        modifier = modifier
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
