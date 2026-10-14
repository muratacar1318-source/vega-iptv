package com.tivimax.player.ui.sync

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.ui.SyncStepInfo
import com.tivimax.player.ui.SyncStepStatus

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

    // Ekranın senkronizasyon sırasında kapanmaması için KEEP_SCREEN_ON bayrağını aktif et
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // Üst Logo & Başlık Alanı
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AccentIndigo, AccentPurple)
                        )
                    )
                    .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = AccentIndigo),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
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

            Text(
                text = "Tüm canlı kanallar, film arşivi ve diziler yerel SQLite veritabanına kaydediliyor. İşlem bittiğinde 0ms gecikmeyle anında erişilecektir.",
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 24.dp)
                    .widthIn(max = 500.dp)
            )

            // 5 Adımlı Kontrol Listesi (Checklist)
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                syncState.steps.forEach { step ->
                    SyncStepCard(step = step)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Hata / Başarı Aksiyon Butonları
            if (syncState.failedStepIndex != null) {
                Button(
                    onClick = { viewModel.retryFailedSyncStep() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 400.dp)
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kaldığı Yerden Yeniden Dene",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
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
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hemen İzlemeye Başla (0ms)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            } else {
                Text(
                    text = "Lütfen işlem bitene kadar bekleyiniz, ekran açık tutulacaktır...",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SyncStepCard(step: SyncStepInfo) {
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sol Durum Göstergesi
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        SyncStepStatus.IN_PROGRESS -> {
                            CircularProgressIndicator(
                                strokeWidth = 2.5.dp,
                                color = AccentIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        SyncStepStatus.FAILED -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        SyncStepStatus.PENDING -> {
                            Text(
                                text = "${step.stepNumber}",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Adım Başlığı ve Durum Detayı
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${step.stepNumber}. ${step.title}",
                        color = if (isActive || isCompleted) TextWhite else TextMuted,
                        fontSize = 14.sp,
                        fontWeight = if (isActive || isCompleted) FontWeight.SemiBold else FontWeight.Normal
                    )

                    if (step.detailText.isNotEmpty() || step.errorMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = step.errorMessage ?: step.detailText,
                            color = when {
                                isFailed -> ErrorRed
                                isCompleted -> SuccessGreen
                                isActive -> AccentIndigo
                                else -> TextMuted
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Aktif Adımda Canlı İlerleme Çubuğu
            if (isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                if (step.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { step.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AccentIndigo,
                        trackColor = Color(0xFF1E2638)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AccentIndigo,
                        trackColor = Color(0xFF1E2638)
                    )
                }
            }
        }
    }
}
