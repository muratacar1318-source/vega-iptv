package com.tivimax.player.ui.tv

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.DelayedSubtitleController
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.player.components.PlayerPillButton
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * OTANTİK TIVIMATE TAM EKRAN OYNATICI VE KUMANDA (D-PAD) OSD KATMANI (SADECE ANDROID TV)
 */
@OptIn(UnstableApi::class)
@Composable
fun TvPlayerOverlay(
    viewModel: MainViewModel,
    channel: LiveStream,
    onCloseFullScreen: () -> Unit,
    onOpenEpgGrid: (() -> Unit)? = null
) {
    val playerManager = viewModel.playerManager
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val aspectRatio by playerManager.aspectRatio.collectAsState()
    val streamDiagnostics by playerManager.streamDiagnostics.collectAsState()
    val favoriteIds by viewModel.favoriteLiveIds.collectAsState()

    val activePreviewChan by viewModel.activePreviewChannel.collectAsState()
    val activeChannel = activePreviewChan ?: channel
    val isFavorite = favoriteIds.contains(activeChannel.streamId.toString())

    val categories by viewModel.visibleLiveCategories.collectAsState()
    val allStreams by viewModel.liveStreams.collectAsState()
    val recentChannels by viewModel.recentChannels.collectAsState()
    val epgSummaryMap by viewModel.epgSummaryMap.collectAsState()
    val audioTracks by playerManager.audioTracks.collectAsState()
    val subtitleTracks by playerManager.subtitleTracks.collectAsState()
    val subtitleDelayMs by viewModel.subtitleDelayMs.collectAsState()
    val subtitleColor by viewModel.subtitleColor.collectAsState()
    val subtitleSize by viewModel.subtitleSize.collectAsState()
    val anySubSelected = subtitleTracks.any { it.isSelected }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val delayedController = remember {
        DelayedSubtitleController(context, coroutineScope)
    }

    DisposableEffect(delayedController) {
        onDispose {
            delayedController.detach()
        }
    }

    var showInfoCard by remember { mutableStateOf(true) }
    var showActionTray by remember { mutableStateOf(false) }
    var showOverlayGuide by remember { mutableStateOf(false) }
    var showQuickSettings by remember { mutableStateOf(false) }
    var showTrackSheet by remember { mutableStateOf(false) }

    var seekRippleMessage by remember { mutableStateOf<String?>(null) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val zapperNumber by viewModel.zapperNumber.collectAsState()
    val isZapperActive by viewModel.isZapperActive.collectAsState()
    val zapperMatchedChannel by viewModel.zapperMatchedChannel.collectAsState()

    val overlayFocusRequester = remember { FocusRequester() }
    val actionDockFocusRequester = remember { FocusRequester() }

    // Rakam Tuşlaması & Saat
    var clockText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            clockText = sdf.format(Date())
            delay(10000L)
        }
    }

    LaunchedEffect(Unit) {
        delay(150)
        try { overlayFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    LaunchedEffect(showInfoCard, showActionTray, showOverlayGuide, showQuickSettings, showTrackSheet) {
        if (!showActionTray && !showOverlayGuide && !showQuickSettings && !showTrackSheet) {
            delay(50)
            try { overlayFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // 5 Saniyelik Otomatik Gizlenme Zamanlayıcısı
    LaunchedEffect(showInfoCard, showActionTray, lastInteractionTime) {
        if ((showInfoCard || showActionTray) && !showOverlayGuide && !showQuickSettings && !showTrackSheet) {
            val elapsed = System.currentTimeMillis() - lastInteractionTime
            val remaining = 5000L - elapsed
            if (remaining > 0) {
                delay(remaining)
            }
            showInfoCard = false
            showActionTray = false
        }
    }

    val registerInteraction = {
        lastInteractionTime = System.currentTimeMillis()
    }

    BackHandler(enabled = true) {
        if (isZapperActive) {
            viewModel.dismissZapper()
        } else if (showTrackSheet) {
            showTrackSheet = false
        } else if (showQuickSettings) {
            showQuickSettings = false
        } else if (showOverlayGuide) {
            showOverlayGuide = false
        } else if (showActionTray) {
            showActionTray = false
        } else if (showInfoCard) {
            showInfoCard = false
        } else {
            onCloseFullScreen()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(overlayFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                registerInteraction()

                // Rakam Tuşlaması (0-9)
                if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                    val digit = keyCode - KeyEvent.KEYCODE_0
                    viewModel.onNumericKeyPressed(digit)
                    return@onPreviewKeyEvent true
                }

                // EĞER AÇIK SÜTÜN VEYA YAN PANEL VARSA
                if (showOverlayGuide || showQuickSettings || showTrackSheet) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        showOverlayGuide = false
                        showQuickSettings = false
                        showTrackSheet = false
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (keyEvent.nativeKeyEvent.isLongPress) {
                            showQuickSettings = true
                            return@onPreviewKeyEvent true
                        }
                        if (!showInfoCard) {
                            showInfoCard = true
                        } else {
                            playerManager.togglePlayPause()
                        }
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                        val next = viewModel.zapNextChannel(activeChannel.streamId)
                        if (next != null) seekRippleMessage = "Sonraki: ${next.name} ⏭"
                        showInfoCard = true
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (showInfoCard && !showActionTray) {
                            showActionTray = true
                            try { actionDockFocusRequester.requestFocus() } catch (_: Exception) {}
                        } else {
                            val prev = viewModel.zapPreviousChannel(activeChannel.streamId)
                            if (prev != null) seekRippleMessage = "⏮ ${prev.name}"
                            showInfoCard = true
                        }
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        showOverlayGuide = true
                        showInfoCard = false
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_BACK -> {
                        if (isZapperActive) {
                            viewModel.dismissZapper()
                            return@onPreviewKeyEvent true
                        }
                        if (showActionTray) showActionTray = false
                        else if (showInfoCard) showInfoCard = false
                        else onCloseFullScreen()
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
    ) {
        // 1. TAM EKRAN VİDEO YÜZEYİ (SINGLETON EXOPLAYER - ZERO REBUFFERING)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    setKeepContentOnPlayerReset(true)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val initialPlayer = playerManager.player ?: playerManager.initializePlayer(isLive = true)
                    playerManager.attachView(this, isPrimary = true, tag = "TvPlayerOverlay")
                    delayedController.attach(this, initialPlayer)
                    delayedController.updateDelay(subtitleDelayMs)
                    delayedController.updateStyle(subtitleColor, subtitleSize)
                }
            },
            update = { playerView ->
                val currentPlayer = playerManager.player ?: playerManager.initializePlayer(isLive = true)
                playerManager.attachView(playerView, isPrimary = true, tag = "TvPlayerOverlay")
                try {
                    delayedController.attach(playerView, currentPlayer)
                    delayedController.updateDelay(subtitleDelayMs)
                    delayedController.updateStyle(subtitleColor, subtitleSize)
                } catch (e: Throwable) {
                    // Ignore
                }
            },
            onRelease = { playerView ->
                delayedController.detach()
                playerManager.detachView(playerView, tag = "TvPlayerOverlay")
            },
            modifier = Modifier.fillMaxSize()
        )

        // BUFFET / YÜKLENİYOR
        if (isBuffering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OpusCyan, strokeWidth = 3.dp, modifier = Modifier.size(54.dp))
            }
        }

        // 2. SAĞ ÜST KÖŞE: SAAT & TIVIMATE RAKAM TUŞLAMA OVERLAY
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 24.dp),
            horizontalAlignment = Alignment.End
        ) {
            // SAAT
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xD910141D),
                border = BorderStroke(1.dp, Color(0xFF1E2638))
            ) {
                Text(
                    text = clockText,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // RAKAM İLE KANAL GEÇİŞİ (NUMERIC ZAPPER)
            if (isZapperActive && zapperNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OpusCyan,
                    border = BorderStroke(1.5.dp, Color.White)
                ) {
                    Text(
                        text = "[ $zapperNumber ] ${zapperMatchedChannel?.name ?: ""}".trim(),
                        color = Color.Black,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // 3. TIVIMATE ALT BİLGİ KARTI (BOTTOM INFO CARD - D910141D)
        AnimatedVisibility(
            visible = showInfoCard,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
        ) {
            val epg = epgSummaryMap[activeChannel.streamId]
            val currentTitle = epg?.getDecodedTitle() ?: activeChannel.currentProgram ?: "Canlı Yayın"
            val timeString = epg?.getFormattedTime() ?: if (epg?.start != null && epg.end != null) "${epg.start} - ${epg.end}" else ""
            val progress = epg?.getProgressFraction()
            val nextTitle = activeChannel.nextProgram ?: epg?.getDecodedDescription() ?: ""

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xD910141D),
                border = BorderStroke(1.dp, Color(0xFF1E2638))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // SOL: Kanal No & Logo
                        if (activeChannel.num > 0) {
                            Text(
                                text = "${activeChannel.num}",
                                color = OpusCyan,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(end = 14.dp)
                            )
                        }

                        AsyncShimmerImage(
                            model = activeChannel.streamIcon,
                            contentDescription = activeChannel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black),
                            placeholderIcon = Icons.Default.Tv
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        // ORTA: EPG & Yayın Bilgileri
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = activeChannel.name,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Rozetler
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    if (streamDiagnostics.resolutionBadge.isNotBlank()) {
                                        TechBadge(text = streamDiagnostics.resolutionBadge, isHighlight = true)
                                    }
                                    if (streamDiagnostics.frameRateBadge.isNotBlank()) {
                                        TechBadge(text = streamDiagnostics.frameRateBadge)
                                    }
                                    if (streamDiagnostics.bufferHealthSecs.isNotBlank()) {
                                        TechBadge(text = "Buffer: ${streamDiagnostics.bufferHealthSecs}")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentTitle,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (timeString.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("($timeString)", color = TiviTextSecondary, fontSize = 12.sp)
                                }
                            }

                            if (progress != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = OpusCyan,
                                    trackColor = Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                )
                            }

                            if (nextTitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Sonraki: $nextTitle",
                                    color = TiviTextSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 4. KATMAN A & B: RECENT CHANNELS & ACTION CHIPS (DPAD_DOWN BASILINCA AÇILIR)
                    AnimatedVisibility(
                        visible = showActionTray,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(color = TiviCardBorder, thickness = 0.5.dp)

                            // KATMAN A: SON İZLENEN KANAL ŞERİDİ
                            if (recentChannels.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Son İzlenen Kanallar", color = TiviGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(recentChannels.take(10), key = { "rec_${it.streamId}" }) { rec ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = TiviCardDark,
                                            border = BorderStroke(1.dp, TiviCardBorder),
                                            modifier = Modifier.dpadFocusable(
                                                focusedBorderColor = TiviGold,
                                                shape = RoundedCornerShape(8.dp),
                                                onClick = { viewModel.playLiveChannel(rec) }
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AsyncShimmerImage(
                                                    model = rec.streamIcon,
                                                    contentDescription = rec.name,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(3.dp))
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(rec.name, color = Color.White, fontSize = 11.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }

                            // KATMAN B: YATAY KONTROL İKONLARI (ACTION DOCK)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PlayerPillButton(
                                    text = "Ses & Altyazı",
                                    icon = Icons.Default.Subtitles,
                                    onClick = { showTrackSheet = true },
                                    isPortrait = false,
                                    modifier = Modifier.focusRequester(actionDockFocusRequester)
                                )
                                PlayerPillButton(
                                    text = when (aspectRatio) {
                                        AspectRatioMode.FIT -> "Orijinal"
                                        AspectRatioMode.FILL -> "Doldur"
                                        AspectRatioMode.ZOOM -> "Zoom"
                                        AspectRatioMode.RATIO_16_9 -> "16:9"
                                        else -> "Orijinal"
                                    },
                                    icon = Icons.Default.AspectRatio,
                                    onClick = { playerManager.cycleAspectRatio() },
                                    isPortrait = false
                                )
                                PlayerPillButton(
                                    text = if (isFavorite) "Favorilerde" else "Favoriye Ekle",
                                    icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    onClick = { viewModel.toggleFavoriteLive(activeChannel.streamId.toString()) },
                                    isActive = isFavorite,
                                    activeColor = TiviGold,
                                    isPortrait = false
                                )
                                if (onOpenEpgGrid != null) {
                                    PlayerPillButton(
                                        text = "Program Rehberi",
                                        icon = Icons.Default.Schedule,
                                        onClick = onOpenEpgGrid,
                                        isPortrait = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. YAYIN ÜSTÜ YARI SAYDAM KANAL LİSTESİ (OVERLAY TV GUIDE - SOL / BACK TUŞU)
        AnimatedVisibility(
            visible = showOverlayGuide,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(420.dp),
                color = Color(0xE60A0E14),
                border = BorderStroke(1.dp, Color(0xFF1E2638))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("TIVIMATE YARI SAYDAM REHBER", color = OpusCyan, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(allStreams, key = { "guide_${it.streamId}" }) { st ->
                            val isSel = st.streamId == activeChannel.streamId
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) OpusCyan.copy(alpha = 0.2f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dpadFocusable(
                                        focusedBorderColor = OpusCyan,
                                        shape = RoundedCornerShape(8.dp),
                                        onClick = { viewModel.playLiveChannel(st) }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${st.num}", color = OpusCyan, fontSize = 11.sp, modifier = Modifier.width(24.dp))
                                    Text(st.name, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. YAYIN ÜSTÜ SES & ALTYAZI AYARLARI PANELİ (D-PAD KUMANDA UYUMLU)
        AnimatedVisibility(
            visible = showTrackSheet,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(420.dp),
                color = Color(0xE60A0E14),
                border = BorderStroke(1.dp, Color(0xFF1E2638))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SES & ALTYAZI AYARLARI",
                            color = OpusCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(
                            onClick = { showTrackSheet = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. SES DİLİ
                        item {
                            Text(
                                text = "Ses Dili",
                                color = TiviPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (audioTracks.isEmpty()) {
                            item {
                                Text(
                                    text = "Ek ses parçası bulunamadı",
                                    color = TiviTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            items(audioTracks) { track ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (track.isSelected) TiviPrimary.copy(alpha = 0.2f) else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .dpadFocusable(
                                            focusedBorderColor = TiviPrimary,
                                            shape = RoundedCornerShape(8.dp),
                                            onClick = { playerManager.selectTrack(track, C.TRACK_TYPE_AUDIO) }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (track.isSelected) "●" else "○",
                                            color = if (track.isSelected) TiviPrimary else TiviTextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = track.name,
                                            color = if (track.isSelected) TiviPrimary else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = Color(0xFF1E2638))
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 2. ALTYAZI
                        item {
                            Text(
                                text = "Altyazı",
                                color = TiviGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Altyazı Kapalı Seçeneği
                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (!anySubSelected) TiviGold.copy(alpha = 0.2f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dpadFocusable(
                                        focusedBorderColor = TiviGold,
                                        shape = RoundedCornerShape(8.dp),
                                        onClick = { playerManager.disableSubtitles() }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (!anySubSelected) "●" else "○",
                                        color = if (!anySubSelected) TiviGold else TiviTextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Altyazı Kapalı",
                                        color = if (!anySubSelected) TiviGold else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (!anySubSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        items(subtitleTracks) { track ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (track.isSelected) TiviGold.copy(alpha = 0.2f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dpadFocusable(
                                        focusedBorderColor = TiviGold,
                                        shape = RoundedCornerShape(8.dp),
                                        onClick = { playerManager.selectTrack(track, C.TRACK_TYPE_TEXT) }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (track.isSelected) "●" else "○",
                                        color = if (track.isSelected) TiviGold else TiviTextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = track.name,
                                        color = if (track.isSelected) TiviGold else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // 3. ALTYAZI GECİKMESİ
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = Color(0xFF1E2638))
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        item {
                            Text(
                                text = "Altyazı Gecikmesi",
                                color = if (anySubSelected) OpusCyan else TiviTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!anySubSelected) {
                            item {
                                Text(
                                    text = "Gecikme ayarı için önce bir altyazı seçin.",
                                    color = TiviTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            item {
                                val delaySeconds = subtitleDelayMs / 1000f
                                val formattedDelay = String.format(Locale.getDefault(), "+%.1f sn", delaySeconds)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // [-] butonu
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF161F2E),
                                        border = BorderStroke(1.dp, OpusCyan.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .dpadFocusable(
                                                focusedBorderColor = OpusCyan,
                                                shape = RoundedCornerShape(8.dp),
                                                onClick = {
                                                    viewModel.setSubtitleDelayMs(subtitleDelayMs - 100L)
                                                }
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("-100 ms", color = OpusCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Değer göstergesi
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = OpusCyan.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, OpusCyan),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = formattedDelay,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }

                                    // [+] butonu
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF161F2E),
                                        border = BorderStroke(1.dp, OpusCyan.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .dpadFocusable(
                                                focusedBorderColor = OpusCyan,
                                                shape = RoundedCornerShape(8.dp),
                                                onClick = {
                                                    viewModel.setSubtitleDelayMs(subtitleDelayMs + 100L)
                                                }
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("+100 ms", color = OpusCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Sıfırla
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF161F2E),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .dpadFocusable(
                                                focusedBorderColor = TiviGold,
                                                shape = RoundedCornerShape(8.dp),
                                                onClick = {
                                                    viewModel.setSubtitleDelayMs(0L)
                                                }
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Sıfırla", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

@Composable
private fun TechBadge(text: String, isHighlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isHighlight) OpusCyan.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
        border = BorderStroke(0.5.dp, if (isHighlight) OpusCyan else Color.White.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            color = if (isHighlight) OpusCyan else Color.White,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
        )
    }
}
