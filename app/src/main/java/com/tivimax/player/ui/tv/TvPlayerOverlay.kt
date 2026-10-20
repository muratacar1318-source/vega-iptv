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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.components.AsyncShimmerImage
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.player.components.PlayerPillButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val startupDebugInfo by playerManager.startupDebugInfo.collectAsState()
    // G-046: Oynatma hatası / yeniden bağlanma mesajı TV'de de görünsün
    val playerError by playerManager.errorMessage.collectAsState()

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
    // G-045: Liste açıldıktan sonra kullanıcı ▲▼ ile gezindi mi? (Sadece gezinince kanal kendiliğinden açılır)
    var guideNavigated by remember { mutableStateOf(false) }
    var showQuickSettings by remember { mutableStateOf(false) }
    var showTrackSheet by remember { mutableStateOf(false) }

    var seekRippleMessage by remember { mutableStateOf<String?>(null) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val zapperNumber by viewModel.zapperNumber.collectAsState()
    val isZapperActive by viewModel.isZapperActive.collectAsState()
    val zapperMatchedChannel by viewModel.zapperMatchedChannel.collectAsState()

    val overlayFocusRequester = remember { FocusRequester() }
    val actionDockFocusRequester = remember { FocusRequester() }
    // G-032: Kanal listesi ve ses/altyazı paneli için odak + kaydırma
    val guideFocusRequester = remember { FocusRequester() }
    val trackSheetFocusRequester = remember { FocusRequester() }
    val guideListState = rememberLazyListState()
    val guideFocusIndex = remember(allStreams, activeChannel.streamId) {
        allStreams.indexOfFirst { it.streamId == activeChannel.streamId }.coerceAtLeast(0)
    }

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

    // G-032: Kanal listesi açılınca oynayan kanala kaydır ve odakla
    LaunchedEffect(showOverlayGuide) {
        if (showOverlayGuide && allStreams.isNotEmpty()) {
            try { guideListState.scrollToItem(guideFocusIndex) } catch (_: Exception) {}
            delay(120)
            try { guideFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // G-032: Ses/altyazı paneli açılınca panele, kapanınca menüye odak
    LaunchedEffect(showTrackSheet) {
        if (showTrackSheet) {
            delay(150)
            try { trackSheetFocusRequester.requestFocus() } catch (_: Exception) {}
        } else if (showActionTray) {
            delay(80)
            try { actionDockFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // G-038: Bilgi kartı açıkken tampon/teknik bilgileri her saniye güncelle
    LaunchedEffect(showInfoCard) {
        while (showInfoCard) {
            playerManager.updateDiagnostics()
            delay(1000L)
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
                    // G-032: Kanal listesi açıkken ▶ listeyi kapatır
                    if (showOverlayGuide && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        showOverlayGuide = false
                        return@onPreviewKeyEvent true
                    }
                    // G-045: Listede ▲▼ ile gezinme başladı
                    if (showOverlayGuide && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
                        guideNavigated = true
                    }
                    return@onPreviewKeyEvent false
                }

                // G-032: Menü (alt tepsi) açıkken tuşlar menünün içinde çalışsın; Geri menüyü kapatır
                if (showActionTray) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        showActionTray = false
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (keyEvent.nativeKeyEvent.repeatCount > 0) {
                            return@onPreviewKeyEvent true
                        }
                        // G-046: Hata mesajı varken OK kanalı yeniden açar
                        if (playerError != null) {
                            viewModel.retryLiveChannel(activeChannel)
                            return@onPreviewKeyEvent true
                        }
                        // G-038: OK yayını durdurmaz; bilgi kartını açar/kapatır
                        showInfoCard = !showInfoCard
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                        val next = viewModel.zapNextChannel(activeChannel.streamId)
                        if (next != null) seekRippleMessage = "Sonraki: ${next.name} ⏭"
                        showInfoCard = true
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        val prev = viewModel.zapPreviousChannel(activeChannel.streamId)
                        if (prev != null) seekRippleMessage = "⏮ ${prev.name}"
                        showInfoCard = true
                        return@onPreviewKeyEvent true
                    }

                    // G-032: ▶ menüyü açar ve odağı ilk düğmeye verir
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        showInfoCard = true
                        showActionTray = true
                        coroutineScope.launch {
                            delay(150)
                            try { actionDockFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        guideNavigated = false // G-045
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
                // G-042: En-boy oranı (telefon oynatıcısıyla aynı eşleme)
                val (targetResizeMode, targetScaleX, targetScaleY) = when (aspectRatio) {
                    AspectRatioMode.FIT -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.0f, 1.0f)
                    AspectRatioMode.FILL -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                    AspectRatioMode.ZOOM -> Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 1.18f, 1.18f)
                    AspectRatioMode.RATIO_16_9 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, 1.0f, 1.0f)
                    AspectRatioMode.RATIO_4_3 -> Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, 0.75f, 1.0f)
                }
                if (playerView.resizeMode != targetResizeMode) playerView.resizeMode = targetResizeMode
                if (playerView.scaleX != targetScaleX || playerView.scaleY != targetScaleY) {
                    playerView.scaleX = targetScaleX
                    playerView.scaleY = targetScaleY
                }
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
                CircularProgressIndicator(color = VegaColors.Accent, strokeWidth = 3.dp, modifier = Modifier.size(54.dp))
            }
        }

        // G-046: HATA / YENİDEN BAĞLANMA MESAJI
        playerError?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 140.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = VegaShapes.Medium,
                    color = VegaColors.OverlayPanel,
                    border = BorderStroke(1.dp, VegaColors.Border)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = msg,
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "OK: Tekrar dene   ▲▼: Kanal değiştir",
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Caption
                        )
                    }
                }
            }
        }

        // 2. SAĞ ÜST KÖŞE: SAAT & TIVIMATE RAKAM TUŞLAMA OVERLAY
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 24.dp),
            horizontalAlignment = Alignment.End
        ) {
            // SAAT (G-038: sadece bilgi kartı/menü açıkken)
            if (showInfoCard || showActionTray) {
                Surface(
                    shape = VegaShapes.Medium,
                    color = VegaColors.OverlayPanel,
                    border = BorderStroke(1.dp, VegaColors.Border)
                ) {
                    Text(
                        text = clockText,
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.Body,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // RAKAM İLE KANAL GEÇİŞİ (NUMERIC ZAPPER)
            if (isZapperActive && zapperNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = VegaShapes.Medium,
                    color = VegaColors.Accent,
                    border = BorderStroke(1.5.dp, Color.White)
                ) {
                    Text(
                        text = "[ $zapperNumber ] ${zapperMatchedChannel?.name ?: ""}".trim(),
                        color = VegaColors.OnAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // 3. TIVIMATE ALT BİLGİ KARTI (BOTTOM INFO CARD)
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
            val currentTitle = epg?.getDecodedTitle() ?: activeChannel.currentProgram ?: "Canlı yayın"
            val timeString = epg?.getFormattedTime() ?: if (epg?.start != null && epg.end != null) "${epg.start} - ${epg.end}" else ""
            val progress = epg?.getProgressFraction()
            val nextTitle = activeChannel.nextProgram ?: epg?.getDecodedDescription() ?: ""

            Surface(
                shape = VegaShapes.Large,
                color = VegaColors.OverlayPanel,
                border = BorderStroke(1.dp, VegaColors.Border)
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
                                color = VegaColors.Accent,
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
                                .clip(VegaShapes.Medium)
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
                                    color = VegaColors.TextPrimary,
                                    fontSize = VegaText.SectionTitle,
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
                                        TechBadge(text = "Tampon: ${streamDiagnostics.bufferHealthSecs}")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentTitle,
                                    color = VegaColors.TextPrimary,
                                    fontSize = VegaText.Body,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (timeString.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("($timeString)", color = VegaColors.TextSecondary, fontSize = VegaText.Secondary)
                                }
                            }

                            if (progress != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = VegaColors.Accent,
                                    trackColor = VegaColors.Border,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(VegaShapes.Small)
                                )
                            }

                            if (nextTitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Sonraki: $nextTitle",
                                    color = VegaColors.TextSecondary,
                                    fontSize = VegaText.Caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (com.tivimax.player.BuildConfig.DEBUG && startupDebugInfo.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Açılış ölçümü: $startupDebugInfo",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            maxLines = 2
                        )
                    }

                    if (!showActionTray) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "▲▼ Kanal değiştir   ◀ Kanal listesi   ▶ Menü",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            maxLines = 1
                        )
                    }

                    // 4. KATMAN A & B: RECENT CHANNELS & ACTION CHIPS (▶ TUŞU İLE AÇILIR)
                    AnimatedVisibility(
                        visible = showActionTray,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(color = VegaColors.Border, thickness = 1.dp)

                            // KATMAN A: SON İZLENEN KANAL ŞERİDİ
                            if (recentChannels.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Son izlenen kanallar", color = VegaColors.Accent, fontSize = VegaText.Caption, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(recentChannels.take(10), key = { "rec_${it.streamId}" }) { rec ->
                                        Surface(
                                            shape = VegaShapes.Small,
                                            color = VegaColors.Card,
                                            border = BorderStroke(1.dp, VegaColors.Border),
                                            modifier = Modifier.dpadFocusable(
                                                focusedBorderColor = VegaColors.Accent,
                                                shape = VegaShapes.Small,
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
                                                    modifier = Modifier.size(20.dp).clip(VegaShapes.Small)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(rec.name, color = VegaColors.TextPrimary, fontSize = VegaText.Caption, maxLines = 1)
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
                                    text = "Ses ve altyazı",
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
                                    text = if (isFavorite) "Favorilerde" else "Favoriye ekle",
                                    icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    onClick = { viewModel.toggleFavoriteLive(activeChannel.streamId.toString()) },
                                    isActive = isFavorite,
                                    activeColor = VegaColors.Gold,
                                    isPortrait = false
                                )
                                if (onOpenEpgGrid != null) {
                                    PlayerPillButton(
                                        text = "Program rehberi",
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
                color = VegaColors.OverlayPanel,
                border = BorderStroke(1.dp, VegaColors.Border)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Rehber", color = VegaColors.Accent, fontSize = VegaText.Secondary, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        state = guideListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(allStreams, key = { _, it -> "guide_${it.streamId}" }) { index, st ->
                            val isSel = st.streamId == activeChannel.streamId
                            Surface(
                                shape = VegaShapes.Small,
                                color = if (isSel) VegaColors.Accent.copy(alpha = 0.18f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == guideFocusIndex) Modifier.focusRequester(guideFocusRequester) else Modifier)
                                    .dpadFocusable(
                                        focusedBorderColor = VegaColors.Accent,
                                        shape = VegaShapes.Small,
                                        onClick = {
                                            // G-045: Kanal zaten açıldıysa OK sadece listeyi kapatır (yeniden bağlanmaz)
                                            if (st.streamId != activeChannel.streamId) {
                                                viewModel.playLiveChannel(st)
                                            }
                                            showOverlayGuide = false
                                            showInfoCard = true
                                        },
                                        onFocusStateChange = { focused ->
                                            // G-045: Listede gezinince kanal OK'a basmadan açılır
                                            if (focused && showOverlayGuide && guideNavigated && st.streamId != activeChannel.streamId) {
                                                viewModel.tvGuidePreviewChannel(st)
                                            }
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${st.num}", color = VegaColors.Accent, fontSize = VegaText.Caption, modifier = Modifier.width(24.dp))
                                    Text(st.name, color = VegaColors.TextPrimary, fontSize = VegaText.Secondary, fontWeight = FontWeight.Bold, maxLines = 1)
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
                color = VegaColors.OverlayPanel,
                border = BorderStroke(1.dp, VegaColors.Border)
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
                            text = "Ses ve altyazı ayarları",
                            color = VegaColors.Accent,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(
                            onClick = { showTrackSheet = false },
                            modifier = Modifier
                                .size(32.dp)
                                .focusRequester(trackSheetFocusRequester)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = VegaColors.TextSecondary,
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
                                text = "Ses dili",
                                color = VegaColors.Accent,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (audioTracks.isEmpty()) {
                            item {
                                Text(
                                    text = "Ek ses parçası bulunamadı",
                                    color = VegaColors.TextSecondary,
                                    fontSize = VegaText.Caption
                                )
                            }
                        } else {
                            items(audioTracks) { track ->
                                Surface(
                                    shape = VegaShapes.Small,
                                    color = if (track.isSelected) VegaColors.Accent.copy(alpha = 0.18f) else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .dpadFocusable(
                                            focusedBorderColor = VegaColors.Accent,
                                            shape = VegaShapes.Small,
                                            onClick = { playerManager.selectTrack(track, C.TRACK_TYPE_AUDIO) }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (track.isSelected) "●" else "○",
                                            color = if (track.isSelected) VegaColors.Accent else VegaColors.TextSecondary,
                                            fontSize = VegaText.Secondary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = track.name,
                                            color = if (track.isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                                            fontSize = VegaText.Secondary,
                                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = VegaColors.Border)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 2. ALTYAZI
                        item {
                            Text(
                                text = "Altyazı",
                                color = VegaColors.Accent,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Altyazı Kapalı Seçeneği
                        item {
                            Surface(
                                shape = VegaShapes.Small,
                                color = if (!anySubSelected) VegaColors.Accent.copy(alpha = 0.18f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dpadFocusable(
                                        focusedBorderColor = VegaColors.Accent,
                                        shape = VegaShapes.Small,
                                        onClick = { playerManager.disableSubtitles() }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (!anySubSelected) "●" else "○",
                                        color = if (!anySubSelected) VegaColors.Accent else VegaColors.TextSecondary,
                                        fontSize = VegaText.Secondary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Kapalı",
                                        color = if (!anySubSelected) VegaColors.Accent else VegaColors.TextPrimary,
                                        fontSize = VegaText.Secondary,
                                        fontWeight = if (!anySubSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        items(subtitleTracks) { track ->
                            Surface(
                                shape = VegaShapes.Small,
                                color = if (track.isSelected) VegaColors.Accent.copy(alpha = 0.18f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dpadFocusable(
                                        focusedBorderColor = VegaColors.Accent,
                                        shape = VegaShapes.Small,
                                        onClick = { playerManager.selectTrack(track, C.TRACK_TYPE_TEXT) }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (track.isSelected) "●" else "○",
                                        color = if (track.isSelected) VegaColors.Accent else VegaColors.TextSecondary,
                                        fontSize = VegaText.Secondary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = track.name,
                                        color = if (track.isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                                        fontSize = VegaText.Secondary,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // 3. ALTYAZI GECİKMESİ
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = VegaColors.Border)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        item {
                            Text(
                                text = "Altyazı gecikmesi",
                                color = if (anySubSelected) VegaColors.Accent else VegaColors.TextMuted,
                                fontSize = VegaText.Secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!anySubSelected) {
                            item {
                                Text(
                                    text = "Gecikme ayarı için önce bir altyazı seç.",
                                    color = VegaColors.TextMuted,
                                    fontSize = VegaText.Caption
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
                                        shape = VegaShapes.Small,
                                        color = VegaColors.SurfaceVariant,
                                        border = BorderStroke(1.dp, VegaColors.Border),
                                        modifier = Modifier
                                            .weight(1f)
                                            .dpadFocusable(
                                                focusedBorderColor = VegaColors.Accent,
                                                shape = VegaShapes.Small,
                                                onClick = {
                                                    viewModel.setSubtitleDelayMs(subtitleDelayMs - 100L)
                                                }
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("-100 ms", color = VegaColors.Accent, fontSize = VegaText.Caption, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Değer göstergesi
                                    Surface(
                                        shape = VegaShapes.Small,
                                        color = VegaColors.Accent.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, VegaColors.Accent),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = formattedDelay,
                                                color = VegaColors.TextPrimary,
                                                fontSize = VegaText.Secondary,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }

                                    // [+] butonu
                                    Surface(
                                        shape = VegaShapes.Small,
                                        color = VegaColors.SurfaceVariant,
                                        border = BorderStroke(1.dp, VegaColors.Border),
                                        modifier = Modifier
                                            .weight(1f)
                                            .dpadFocusable(
                                                focusedBorderColor = VegaColors.Accent,
                                                shape = VegaShapes.Small,
                                                onClick = {
                                                    viewModel.setSubtitleDelayMs(subtitleDelayMs + 100L)
                                                }
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("+100 ms", color = VegaColors.Accent, fontSize = VegaText.Caption, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Sıfırla
                                    Surface(
                                        shape = VegaShapes.Small,
                                        color = VegaColors.SurfaceVariant,
                                        border = BorderStroke(1.dp, VegaColors.Border),
                                        modifier = Modifier
                                            .weight(1f)
                                            .dpadFocusable(
                                                focusedBorderColor = VegaColors.Accent,
                                                shape = VegaShapes.Small,
                                                onClick = {
                                                    viewModel.setSubtitleDelayMs(0L)
                                                }
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Sıfırla", color = VegaColors.TextPrimary, fontSize = VegaText.Caption, fontWeight = FontWeight.Bold)
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
        shape = VegaShapes.Small,
        color = if (isHighlight) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.SurfaceVariant,
        border = BorderStroke(1.dp, if (isHighlight) VegaColors.Accent else VegaColors.Border)
    ) {
        Text(
            text = text,
            color = if (isHighlight) VegaColors.Accent else VegaColors.TextPrimary,
            fontSize = VegaText.Caption,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
        )
    }
}
