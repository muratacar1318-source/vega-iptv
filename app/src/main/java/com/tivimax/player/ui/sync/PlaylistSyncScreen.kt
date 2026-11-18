package com.tivimax.player.ui.sync

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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

    BackHandler(enabled = true) {
        if (syncState.isSyncing) {
            showCancelDialog = true
        } else {
            handleExit()
        }
    }

    if (showCancelDialog) {
        Dialog(
            onDismissRequest = { showCancelDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            LaunchedEffect(Unit) {
                for (attempt in 0 until 2) {
                    withFrameNanos { }
                    delay(60)
                    if (continueFocusRequester.requestFocusSafely()) break
                }
            }
            Surface(
                shape = VegaShapes.Large,
                color = VegaColors.SurfaceElevated,
                border = BorderStroke(0.75.dp, VegaColors.Border),
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(0.88f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
    }

    LaunchedEffect(syncState.failedStepIndex, syncState.isCompleted, syncState.isLiveReady) {
        if (syncState.failedStepIndex != null || syncState.isCompleted || syncState.isLiveReady) {
            for (attempt in 0 until 2) {
                withFrameNanos { }
                delay(60)
                if (primaryActionFocusRequester.requestFocusSafely()) break
            }
        }
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val completedCount = syncState.steps.count { it.status == SyncStepStatus.COMPLETED }
    val activeStep = syncState.steps.firstOrNull { it.status == SyncStepStatus.IN_PROGRESS }
    val activeProgress = activeStep?.progress?.coerceIn(0f, 1f) ?: 0f
    val totalSteps = syncState.steps.size.coerceAtLeast(1).toFloat()
    val overallProgress = if (syncState.isCompleted) 1f else ((completedCount + activeProgress) / totalSteps).coerceIn(0f, 1f)
    val percentage = (overallProgress * 100).toInt()

    val statusColor = when {
        syncState.failedStepIndex != null -> VegaColors.Danger
        syncState.isCompleted -> VegaColors.Success
        else -> VegaColors.Accent
    }

    val activeDetail = activeStep?.detailText?.ifBlank { null }
        ?: if (syncState.isCompleted) "Tüm içerikler hazır"
        else if (syncState.failedStepIndex != null) "İnternet bağlantını kontrol et"
        else "İçerikler hazırlanıyor…"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .vegaBackdrop()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SOL ÖZET PANELİ
                Surface(
                    shape = VegaShapes.Large,
                    color = VegaColors.Surface,
                    border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.65f)),
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = VegaShapes.Small,
                                color = VegaColors.SurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = VegaColors.Accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = AppConfig.APP_NAME,
                                    color = VegaColors.TextPrimary,
                                    fontSize = VegaText.SectionTitle,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Liste kurulumu",
                                    color = VegaColors.TextSecondary,
                                    fontSize = VegaText.Caption
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Box(
                            modifier = Modifier.size(112.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { overallProgress },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 7.dp,
                                color = statusColor,
                                trackColor = VegaColors.Border.copy(alpha = 0.55f)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "%$percentage",
                                    color = VegaColors.TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        syncState.failedStepIndex != null -> "Hata"
                                        syncState.isCompleted -> "Hazır"
                                        else -> "İndiriliyor"
                                    },
                                    color = if (syncState.failedStepIndex != null || syncState.isCompleted) statusColor else VegaColors.TextSecondary,
                                    fontSize = VegaText.Caption,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = activeDetail,
                            color = if (syncState.failedStepIndex != null) VegaColors.Danger else VegaColors.TextSecondary,
                            fontSize = VegaText.Caption,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        SyncActionButtons(
                            failed = syncState.failedStepIndex != null,
                            completed = syncState.isCompleted,
                            liveReady = syncState.isLiveReady,
                            primaryFocusRequester = primaryActionFocusRequester,
                            onRetry = { viewModel.retryFailedSyncStep() },
                            onBack = { handleExit() },
                            onEnterHome = { viewModel.navigateTo(Screen.Home) }
                        )
                    }
                }

                // SAĞ ADIM LİSTESİ (İnce çizgi ayırıcılı, iç içe kart yok)
                Surface(
                    shape = VegaShapes.Large,
                    color = VegaColors.Surface,
                    border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.65f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        syncState.steps.forEachIndexed { idx, step ->
                            SyncStepRow(step = step)
                            if (idx < syncState.steps.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = VegaColors.Border.copy(alpha = 0.5f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // DİKEY MOBİL DÜZEN
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                Surface(
                    shape = VegaShapes.Medium,
                    color = VegaColors.SurfaceElevated,
                    border = BorderStroke(0.75.dp, VegaColors.Border),
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = AppConfig.APP_NAME,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.ScreenTitle,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = activeDetail,
                    color = if (syncState.failedStepIndex != null) VegaColors.Danger else VegaColors.TextSecondary,
                    fontSize = VegaText.Secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // İlerleme çubuğu + yüzde
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                syncState.failedStepIndex != null -> "Kurulum durdu"
                                syncState.isCompleted -> "Kurulum tamamlandı"
                                else -> "Liste hazırlanıyor"
                            },
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "%$percentage",
                            color = statusColor,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { overallProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = statusColor,
                        trackColor = VegaColors.Border.copy(alpha = 0.55f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    shape = VegaShapes.Large,
                    color = VegaColors.Surface,
                    border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        syncState.steps.forEachIndexed { idx, step ->
                            SyncStepRow(step = step)
                            if (idx < syncState.steps.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = VegaColors.Border.copy(alpha = 0.5f),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SyncActionButtons(
                        failed = syncState.failedStepIndex != null,
                        completed = syncState.isCompleted,
                        liveReady = syncState.isLiveReady,
                        primaryFocusRequester = primaryActionFocusRequester,
                        onRetry = { viewModel.retryFailedSyncStep() },
                        onBack = { handleExit() },
                        onEnterHome = { viewModel.navigateTo(Screen.Home) }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SyncActionButtons(
    failed: Boolean,
    completed: Boolean,
    liveReady: Boolean,
    primaryFocusRequester: FocusRequester,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onEnterHome: () -> Unit
) {
    if (failed) {
        VegaButton(
            text = "Kaldığı yerden yeniden dene",
            variant = VegaButtonVariant.DANGER,
            icon = Icons.Default.Refresh,
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(primaryFocusRequester)
        )
        Spacer(modifier = Modifier.height(8.dp))
        VegaButton(
            text = "Geri dön",
            variant = VegaButtonVariant.SECONDARY,
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        )
    } else if (completed) {
        VegaButton(
            text = "İzlemeye başla",
            variant = VegaButtonVariant.PRIMARY,
            icon = Icons.Default.PlayArrow,
            onClick = onEnterHome,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(primaryFocusRequester)
        )
    } else if (liveReady) {
        VegaButton(
            text = "Arayüze geç",
            variant = VegaButtonVariant.PRIMARY,
            icon = Icons.Default.PlayArrow,
            onClick = onEnterHome,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(primaryFocusRequester)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Canlı TV hazır, arşiv arka planda yükleniyor.",
            color = VegaColors.TextMuted,
            fontSize = VegaText.Caption,
            textAlign = TextAlign.Center
        )
    } else {
        Text(
            text = "Ekran açık tutuluyor…",
            color = VegaColors.TextMuted,
            fontSize = VegaText.Caption,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SyncStepRow(step: SyncStepInfo) {
    val isActive = step.status == SyncStepStatus.IN_PROGRESS
    val isCompleted = step.status == SyncStepStatus.COMPLETED
    val isFailed = step.status == SyncStepStatus.FAILED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) VegaColors.SurfaceElevated.copy(alpha = 0.65f) else VegaColors.Surface.copy(alpha = 0f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCompleted -> VegaColors.Success.copy(alpha = 0.14f)
                            isActive -> VegaColors.Accent.copy(alpha = 0.14f)
                            isFailed -> VegaColors.Danger.copy(alpha = 0.14f)
                            else -> VegaColors.SurfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (step.status) {
                    SyncStepStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = VegaColors.Success,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    SyncStepStatus.IN_PROGRESS -> {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = VegaColors.Accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    SyncStepStatus.FAILED -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = VegaColors.Danger,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    SyncStepStatus.PENDING -> {
                        Text(
                            text = "${step.stepNumber}",
                            color = VegaColors.TextMuted,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = step.title,
                    color = if (isActive || isCompleted) VegaColors.TextPrimary else VegaColors.TextSecondary,
                    fontSize = VegaText.Body,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (isActive) {
            Spacer(modifier = Modifier.height(8.dp))
            if (step.progress > 0f) {
                LinearProgressIndicator(
                    progress = { step.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = VegaColors.Accent,
                    trackColor = VegaColors.Border.copy(alpha = 0.55f)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = VegaColors.Accent,
                    trackColor = VegaColors.Border.copy(alpha = 0.55f)
                )
            }
        }
    }
}
