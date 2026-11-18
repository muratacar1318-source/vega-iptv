package com.tivimax.player.ui.sync

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.SyncStepInfo
import com.tivimax.player.ui.SyncStepStatus
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.design.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun PlaylistSyncScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val syncState by viewModel.playlistSyncState.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    val primaryActionFocusRequester = remember { FocusRequester() }
    val continueFocusRequester = remember { FocusRequester() }
    var showCancelDialog by remember { mutableStateOf(false) }

    fun handleExit() {
        coroutineScope.launch {
            val isSynced = viewModel.preferences.isPlaylistSyncedFlow.first()
            if (isSynced) {
                if (!viewModel.navigateBack()) {
                    viewModel.navigateTo(Screen.Home)
                }
            } else {
                viewModel.navigateTo(Screen.Login)
            }
        }
    }

    // Sistem Geri Tuşu Yönetimi
    BackHandler(enabled = true) {
        if (syncState.isSyncing) {
            showCancelDialog = true
        } else {
            handleExit()
        }
    }

    // Senkronizasyonu durdurma onay diyaloğu (StreamVault tarzı, tek odaklı VegaButton'lar)
    if (showCancelDialog) {
        Dialog(
            onDismissRequest = { showCancelDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.88f)
                    .vegaPanel(VegaShapes.Hero)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    VegaColors.Danger.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Güncelleme durdurulsun mu?",
                        fontWeight = FontWeight.Bold,
                        fontSize = VegaText.SectionTitle,
                        color = VegaColors.TextPrimary
                    )
                    Text(
                        text = "Mevcut listen korunur.",
                        fontSize = VegaText.Body,
                        color = VegaColors.TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VegaButton(
                            text = "Durdur",
                            variant = VegaButtonVariant.DANGER,
                            onClick = {
                                showCancelDialog = false
                                viewModel.cancelPlaylistSync()
                                handleExit()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        VegaButton(
                            text = "Devam et",
                            variant = VegaButtonVariant.PRIMARY,
                            onClick = { showCancelDialog = false },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(continueFocusRequester)
                        )
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            delay(100)
            continueFocusRequester.requestFocusSafely()
        }
    }

    // Hata, tamamlanma veya canlı yayın hazır olduğunda ana aksiyon butonuna otomatik odaklan
    LaunchedEffect(syncState.failedStepIndex, syncState.isCompleted, syncState.isLiveReady) {
        if (syncState.failedStepIndex != null || syncState.isCompleted || syncState.isLiveReady) {
            delay(150)
            primaryActionFocusRequester.requestFocusSafely()
        }
    }

    // Ekranın senkronizasyon sırasında kapanmaması için KEEP_SCREEN_ON bayrağını aktif et
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Toplam İlerleme Yüzdesi Hesabı
    val completedCount = syncState.steps.count { it.status == SyncStepStatus.COMPLETED }
    val activeStep = syncState.steps.firstOrNull { it.status == SyncStepStatus.IN_PROGRESS }
    val activeProgress = activeStep?.progress?.coerceIn(0f, 1f) ?: 0f
    val overallProgress = if (syncState.isCompleted) 1f else ((completedCount + activeProgress) / 5f).coerceIn(0f, 1f)
    val percentage = (overallProgress * 100).toInt()

    val activeDetail = activeStep?.detailText?.ifBlank { null }
        ?: if (syncState.isCompleted) "Tüm içerikler senkronize edildi"
        else if (syncState.failedStepIndex != null) "İnternet bağlantını kontrol et."
        else "İçerik arşivleri hazırlanıyor…"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .vegaBackdrop()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (isLandscape) {
            // ==========================================
            // YATAY / ANDROID TV EKRANI (2 SÜTUNLU STREAMVAULT PANEL DÜZENİ)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SOL TARAF: Logo, Dairesel Sayaç, Durum ve Tek Odaklı Aksiyon Butonları
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .vegaPanel(VegaShapes.Hero)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(VegaShapes.Medium)
                                .background(Brush.linearGradient(listOf(VegaColors.Accent, VegaColors.Accent.copy(alpha = 0.7f)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text(
                                text = AppConfig.APP_NAME,
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.SectionTitle,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Liste kurulumu",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { overallProgress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 9.dp,
                            color = when {
                                syncState.failedStepIndex != null -> VegaColors.Danger
                                syncState.isCompleted -> VegaColors.Success
                                else -> VegaColors.Accent
                            },
                            trackColor = VegaColors.Border
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "%$percentage",
                                color = VegaColors.TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = when {
                                    syncState.failedStepIndex != null -> "Hata"
                                    syncState.isCompleted -> "Hazır!"
                                    else -> "İndiriliyor"
                                },
                                color = when {
                                    syncState.failedStepIndex != null -> VegaColors.Danger
                                    syncState.isCompleted -> VegaColors.Success
                                    else -> VegaColors.TextSecondary
                                },
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = activeDetail,
                        color = if (syncState.failedStepIndex != null) VegaColors.Danger else VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Aksiyon Butonları (Tek odaklı VegaButton)
                    if (syncState.failedStepIndex != null) {
                        VegaButton(
                            text = "Kaldığı yerden yeniden dene",
                            variant = VegaButtonVariant.DANGER,
                            icon = Icons.Default.Refresh,
                            onClick = { viewModel.retryFailedSyncStep() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(primaryActionFocusRequester)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        VegaButton(
                            text = "Geri dön",
                            variant = VegaButtonVariant.SECONDARY,
                            onClick = { handleExit() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (syncState.isCompleted) {
                        VegaButton(
                            text = "Hemen izlemeye başla",
                            variant = VegaButtonVariant.PRIMARY,
                            icon = Icons.Default.PlayArrow,
                            onClick = { viewModel.navigateTo(Screen.Home) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(primaryActionFocusRequester)
                        )
                    } else if (syncState.isLiveReady) {
                        VegaButton(
                            text = "Arayüze geç (arka planda yükle)",
                            variant = VegaButtonVariant.PRIMARY,
                            icon = Icons.Default.PlayArrow,
                            onClick = { viewModel.navigateTo(Screen.Home) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(primaryActionFocusRequester)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Canlı TV hazır. Arşiv arka planda kaydediliyor…",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "Ekran açık tutulmaktadır…",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // SAĞ TARAF: 5 Kurulum Adımı Paneli
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .vegaPanel(VegaShapes.Hero)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    syncState.steps.forEach { step ->
                        SyncStepCardCompact(step = step)
                    }
                }
            }
        } else {
            // ==========================================
            // DİKEY MOD (TELEFON / TABLET DİKEY)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(28.dp))

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(VegaShapes.Large)
                        .background(Brush.linearGradient(listOf(VegaColors.Accent, VegaColors.Accent.copy(alpha = 0.7f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(34.dp))
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = AppConfig.APP_NAME,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.ScreenTitle,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = "Oynatma listesi kurulumu",
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Body,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier.size(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { overallProgress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 7.dp,
                        color = if (syncState.failedStepIndex != null) VegaColors.Danger else if (syncState.isCompleted) VegaColors.Success else VegaColors.Accent,
                        trackColor = VegaColors.Border
                    )
                    Text(
                        text = "%$percentage",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.ScreenTitle,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .vegaPanel(VegaShapes.Large)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    syncState.steps.forEach { step ->
                        SyncStepCardCompact(step = step)
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                if (syncState.failedStepIndex != null) {
                    VegaButton(
                        text = "Kaldığı yerden yeniden dene",
                        variant = VegaButtonVariant.DANGER,
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.retryFailedSyncStep() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 400.dp)
                            .focusRequester(primaryActionFocusRequester)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    VegaButton(
                        text = "Geri dön",
                        variant = VegaButtonVariant.SECONDARY,
                        onClick = { handleExit() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 400.dp)
                    )
                } else if (syncState.isCompleted) {
                    VegaButton(
                        text = "Hemen izlemeye başla",
                        variant = VegaButtonVariant.PRIMARY,
                        icon = Icons.Default.PlayArrow,
                        onClick = { viewModel.navigateTo(Screen.Home) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 400.dp)
                            .focusRequester(primaryActionFocusRequester)
                    )
                } else if (syncState.isLiveReady) {
                    VegaButton(
                        text = "Arayüze geç (arka planda yükle)",
                        variant = VegaButtonVariant.PRIMARY,
                        icon = Icons.Default.PlayArrow,
                        onClick = { viewModel.navigateTo(Screen.Home) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 400.dp)
                            .focusRequester(primaryActionFocusRequester)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Canlı TV kanalları hazır. Film ve dizi arşivi arka planda kaydedilmeye devam edecek.",
                        color = VegaColors.TextMuted,
                        fontSize = VegaText.Caption,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Lütfen işlem bitene kadar bekle, ekran açık tutulacak…",
                        color = VegaColors.TextMuted,
                        fontSize = VegaText.Caption,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SyncStepCardCompact(step: SyncStepInfo) {
    val isActive = step.status == SyncStepStatus.IN_PROGRESS
    val isCompleted = step.status == SyncStepStatus.COMPLETED
    val isFailed = step.status == SyncStepStatus.FAILED

    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> VegaColors.Accent
            isCompleted -> VegaColors.Success.copy(alpha = 0.5f)
            isFailed -> VegaColors.Danger
            else -> VegaColors.Border
        },
        label = "borderColor"
    )

    Surface(
        shape = VegaShapes.Medium,
        color = if (isActive) VegaColors.SurfaceElevated else VegaColors.Card,
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCompleted -> VegaColors.Success.copy(alpha = 0.15f)
                                isActive -> VegaColors.Accent.copy(alpha = 0.15f)
                                isFailed -> VegaColors.Danger.copy(alpha = 0.15f)
                                else -> VegaColors.SurfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (step.status) {
                        SyncStepStatus.COMPLETED -> {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = VegaColors.Success, modifier = Modifier.size(18.dp))
                        }
                        SyncStepStatus.IN_PROGRESS -> {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = VegaColors.Accent, modifier = Modifier.size(16.dp))
                        }
                        SyncStepStatus.FAILED -> {
                            Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = VegaColors.Danger, modifier = Modifier.size(18.dp))
                        }
                        SyncStepStatus.PENDING -> {
                            Text(text = "${step.stepNumber}", color = VegaColors.TextMuted, fontSize = VegaText.Caption, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${step.stepNumber}. ${step.title}",
                        color = if (isActive || isCompleted) VegaColors.TextPrimary else VegaColors.TextSecondary,
                        fontSize = VegaText.Secondary,
                        fontWeight = if (isActive || isCompleted) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (step.detailText.isNotEmpty() || step.errorMessage != null) {
                        Text(
                            text = step.errorMessage ?: step.detailText,
                            color = when {
                                isFailed -> VegaColors.Danger
                                isCompleted -> VegaColors.Success
                                isActive -> VegaColors.Accent
                                else -> VegaColors.TextMuted
                            },
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (isActive) {
                Spacer(modifier = Modifier.height(6.dp))
                if (step.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { step.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = VegaColors.Accent,
                        trackColor = VegaColors.Border
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = VegaColors.Accent,
                        trackColor = VegaColors.Border
                    )
                }
            }
        }
    }
}
