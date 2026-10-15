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
import kotlinx.coroutines.delay

private val DarkBackground = Color(0xFF000000)
private val CardBackground = Color(0xFF0F141C)
private val BorderMuted = Color(0xFF1E2638)
private val AccentIndigo = Color(0xFF6366F1)
private val AccentPurple = Color(0xFF8B5CF6)
private val SuccessGreen = Color(0xFF10B981)
private val ErrorRed = Color(0xFFEF4444)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF94A3B8)

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
            .background(DarkBackground)
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(AccentIndigo, AccentPurple))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text(text = "VEGA IPTV", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                            Text(text = "İndeksleme & Kurulum", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
                                syncState.failedStepIndex != null -> ErrorRed
                                syncState.isCompleted -> SuccessGreen
                                else -> AccentIndigo
                            },
                            trackColor = Color(0xFF1E2638)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "%$percentage",
                                color = TextWhite,
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
                                    syncState.failedStepIndex != null -> ErrorRed
                                    syncState.isCompleted -> SuccessGreen
                                    else -> TextMuted
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // O An Kaydedilen İçerik Sayısı / Detay
                    val activeDetail = activeStep?.detailText?.ifBlank { null }
                        ?: if (syncState.isCompleted) "Tüm içerikler senkronize edildi (0ms)"
                        else if (syncState.failedStepIndex != null) "İnternet bağlantınızı kontrol edin."
                        else "İçerik arşivleri hazırlanıyor..."

                    Text(
                        text = activeDetail,
                        color = if (syncState.failedStepIndex != null) ErrorRed else TextMuted,
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
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .focusRequester(retryFocusRequester)
                                .dpadFocusable(
                                    focusedBorderColor = Color.White,
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = { viewModel.retryFailedSyncStep() }
                                )
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Kaldığı Yerden Yeniden Dene", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else if (syncState.isCompleted) {
                        Button(
                            onClick = { viewModel.navigateTo(Screen.Home) },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .focusRequester(retryFocusRequester)
                                .dpadFocusable(
                                    focusedBorderColor = Color.White,
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = { viewModel.navigateTo(Screen.Home) }
                                )
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Hemen İzlemeye Başla (0ms)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        Text(
                            text = "Ekran açık tutulmaktadır...",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
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
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(AccentIndigo, AccentPurple)))
                        .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = AccentIndigo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "VEGA IPTV",
                    color = TextWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "Oynatma Listesi Kurulumu ve İndeksleme",
                    color = TextMuted,
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
                        color = if (syncState.failedStepIndex != null) ErrorRed else if (syncState.isCompleted) SuccessGreen else AccentIndigo,
                        trackColor = Color(0xFF1E2638)
                    )
                    Text(
                        text = "%$percentage",
                        color = TextWhite,
                        fontSize = 20.sp,
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
                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 400.dp)
                            .height(52.dp)
                            .focusRequester(retryFocusRequester)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = RoundedCornerShape(14.dp),
                                onClick = { viewModel.retryFailedSyncStep() }
                            )
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Kaldığı Yerden Yeniden Dene", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                } else if (syncState.isCompleted) {
                    Button(
                        onClick = { viewModel.navigateTo(Screen.Home) },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 400.dp)
                            .height(52.dp)
                            .focusRequester(retryFocusRequester)
                            .dpadFocusable(
                                focusedBorderColor = Color.White,
                                shape = RoundedCornerShape(14.dp),
                                onClick = { viewModel.navigateTo(Screen.Home) }
                            )
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Hemen İzlemeye Başla (0ms)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    Text(
                        text = "Lütfen işlem bitene kadar bekleyiniz, ekran açık tutulacaktır...",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
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
            isActive -> AccentIndigo
            isCompleted -> SuccessGreen.copy(alpha = 0.5f)
            isFailed -> ErrorRed
            else -> BorderMuted
        },
        label = "borderColor"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
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
                                isCompleted -> SuccessGreen.copy(alpha = 0.15f)
                                isActive -> AccentIndigo.copy(alpha = 0.15f)
                                isFailed -> ErrorRed.copy(alpha = 0.15f)
                                else -> Color(0xFF1E2638)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (step.status) {
                        SyncStepStatus.COMPLETED -> {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        }
                        SyncStepStatus.IN_PROGRESS -> {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = AccentIndigo, modifier = Modifier.size(16.dp))
                        }
                        SyncStepStatus.FAILED -> {
                            Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                        }
                        SyncStepStatus.PENDING -> {
                            Text(text = "${step.stepNumber}", color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Adım Başlığı ve Durum Detayı
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${step.stepNumber}. ${step.title}",
                        color = if (isActive || isCompleted) TextWhite else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = if (isActive || isCompleted) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (step.detailText.isNotEmpty() || step.errorMessage != null) {
                        Text(
                            text = step.errorMessage ?: step.detailText,
                            color = when {
                                isFailed -> ErrorRed
                                isCompleted -> SuccessGreen
                                isActive -> AccentIndigo
                                else -> TextMuted
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
                        color = AccentIndigo,
                        trackColor = Color(0xFF1E2638)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AccentIndigo,
                        trackColor = Color(0xFF1E2638)
                    )
                }
            }
        }
    }
}
