package com.tivimax.player.ui.sync

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowManager
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
import androidx.compose.ui.draw.shadow
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
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.SyncStepInfo
import com.tivimax.player.ui.SyncStepStatus
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import kotlinx.coroutines.delay

@Composable
fun PlaylistSyncScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val syncState by viewModel.playlistSyncState.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

    val retryFocusRequester = remember { FocusRequester() }

    // Hata oluştuğunda "Kaldığı Yerden Yeniden Dene" butonuna otomatik odaklan
    LaunchedEffect(syncState.failedStepIndex) {
        if (syncState.failedStepIndex != null || syncState.isCompleted) {
            delay(150)
            try {
                retryFocusRequester.requestFocus()
            } catch (_: Exception) {}
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VegaColors.Background)
    ) {
        if (isLandscape) {
            // ==========================================
            // YATAY / ANDROID TV EKRANI (2 SÜTUNLU KOMPAKT DÜZEN)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SOL TARAF: Logo, Dairesel Sayaç, Durum & Aksiyon Butonu
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Logo ve Başlık
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
                            Text(text = "VEGA IPTV", color = VegaColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                            Text(text = "İndeksleme ve kurulum", color = VegaColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Büyük Dairesel Yüzde Sayacı (%0 - %100)
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
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // O An Kaydedilen İçerik Sayısı / Detay
                    val activeDetail = activeStep?.detailText?.ifBlank { null }
                        ?: if (syncState.isCompleted) "Tüm içerikler senkronize edildi"
                        else if (syncState.failedStepIndex != null) "İnternet bağlantını kontrol et."
                        else "İçerik arşivleri hazırlanıyor..."

                    Text(
                        text = activeDetail,
                        color = if (syncState.failedStepIndex != null) VegaColors.Danger else VegaColors.TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Aksiyon Butonları (TV Focusable)
                    if (syncState.failedStepIndex != null) {
                        Button(
                            onClick = { viewModel.retryFailedSyncStep() },
                            colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Danger),
                            shape = VegaShapes.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .focusRequester(retryFocusRequester)
                                .dpadFocusable(
                                    focusedBorderColor = Color.White,
                                    shape = VegaShapes.Medium,
                                    onClick = { viewModel.retryFailedSyncStep() }
                                )
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Kaldığı yerden yeniden dene", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else if (syncState.isCompleted) {
                        Button(
                            onClick = { viewModel.navigateTo(Screen.Home) },
                            colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                            shape = VegaShapes.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .focusRequester(retryFocusRequester)
                                .dpadFocusable(
                                    focusedBorderColor = Color.White,
                                    shape = VegaShapes.Medium,
                                    onClick = { viewModel.navigateTo(Screen.Home) }
                                )
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Hemen izlemeye başla", color = VegaColors.OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else if (syncState.isLiveReady) {
                        Button(
                            onClick = { viewModel.navigateTo(Screen.Home) },
                            colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                            shape = VegaShapes.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .focusRequester(retryFocusRequester)
                                .dpadFocusable(
                                    focusedBorderColor = Color.White,
                                    shape = VegaShapes.Medium,
                                    onClick = { viewModel.navigateTo(Screen.Home) }
                                )
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Arayüze geç (arka planda yükle)", color = VegaColors.OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Canlı TV hazır. Arşiv arka planda kaydediliyor...",
                            color = VegaColors.TextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "Ekran açık tutulmaktadır...",
                            color = VegaColors.TextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // SAĞ TARAF: 5 Kurulum Adımının Sığdığı Kompakt Kart Listesi
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
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
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(36.dp))

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(VegaShapes.Large)
                        .background(Brush.linearGradient(listOf(VegaColors.Accent, VegaColors.Accent.copy(alpha = 0.7f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(36.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "VEGA IPTV",
                    color = VegaColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "Oynatma listesi kurulumu ve indeksleme",
                    color = VegaColors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Dairesel Yüzde Göstergesi
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
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    syncState.steps.forEach { step ->
                        SyncStepCardCompact(step = step)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (syncState.failedStepIndex != null) {
                    Button(
                        onClick = { viewModel.retryFailedSyncStep() },
                        colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Danger),
                        shape = VegaShapes.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 400.dp)
                            .height(48.dp)
                            .focusRequester(retryFocusRequester)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = VegaShapes.Medium,
                                onClick = { viewModel.retryFailedSyncStep() }
                            )
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Kaldığı yerden yeniden dene", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                } else if (syncState.isCompleted) {
                    Button(
                        onClick = { viewModel.navigateTo(Screen.Home) },
                        colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                        shape = VegaShapes.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 400.dp)
                            .height(48.dp)
                            .focusRequester(retryFocusRequester)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = VegaShapes.Medium,
                                onClick = { viewModel.navigateTo(Screen.Home) }
                            )
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Hemen izlemeye başla", color = VegaColors.OnAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else if (syncState.isLiveReady) {
                    Button(
                        onClick = { viewModel.navigateTo(Screen.Home) },
                        colors = ButtonDefaults.buttonColors(containerColor = VegaColors.Accent, contentColor = VegaColors.OnAccent),
                        shape = VegaShapes.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 400.dp)
                            .height(48.dp)
                            .focusRequester(retryFocusRequester)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = VegaShapes.Medium,
                                onClick = { viewModel.navigateTo(Screen.Home) }
                            )
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = VegaColors.OnAccent, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Arayüze geç (arka planda yükle)", color = VegaColors.OnAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Canlı TV kanalları hazır. Film ve dizi arşivi arka planda kaydedilmeye devam edecektir.",
                        color = VegaColors.TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Lütfen işlem bitene kadar bekle, ekran açık tutulacaktır...",
                        color = VegaColors.TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))
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

    Card(
        shape = VegaShapes.Medium,
        colors = CardDefaults.cardColors(containerColor = VegaColors.Card),
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
                // Sol Durum Göstergesi
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
                            Text(text = "${step.stepNumber}", color = VegaColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Adım Başlığı ve Durum Detayı
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${step.stepNumber}. ${step.title}",
                        color = if (isActive || isCompleted) VegaColors.TextPrimary else VegaColors.TextSecondary,
                        fontSize = 13.sp,
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Aktif Adımda Canlı İlerleme Çubuğu
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
