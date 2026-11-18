package com.tivimax.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaSizes
import com.tivimax.player.ui.design.VegaText
import kotlinx.coroutines.delay

/** Tüm otomatik denemeler tükenince gösterilen hata katmanı (TV kumanda odaklı). */
@Composable
internal fun PlayerErrorOverlay(
    error: String,
    onRetry: () -> Unit,
    onRetryFallback: () -> Unit,
    onBack: () -> Unit
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(4) {
            delay(60)
            if (runCatching { retryFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = VegaShapes.Large,
            color = VegaColors.OverlayPanel,
            border = BorderStroke(1.dp, VegaColors.Border),
            shadowElevation = 16.dp,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 460.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = VegaColors.Danger,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.BodyProminent,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var retryFocused by remember { mutableStateOf(false) }
                    Surface(
                        shape = VegaShapes.Medium,
                        color = VegaColors.Accent,
                        border = if (retryFocused) BorderStroke(2.dp, Color.White) else null,
                        modifier = Modifier
                            .height(VegaSizes.ButtonHeight)
                            .focusRequester(retryFocus)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = Color.White,
                                scale = 1.04f,
                                onClick = onRetry,
                                onFocusStateChange = { retryFocused = it }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = VegaColors.OnAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Tekrar dene",
                                color = VegaColors.OnAccent,
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    var altFocused by remember { mutableStateOf(false) }
                    Surface(
                        shape = VegaShapes.Medium,
                        color = if (altFocused) VegaColors.Accent else VegaColors.SurfaceVariant,
                        border = BorderStroke(1.dp, if (altFocused) Color.White else VegaColors.Accent),
                        modifier = Modifier
                            .height(VegaSizes.ButtonHeight)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = Color.White,
                                scale = 1.04f,
                                onClick = onRetryFallback,
                                onFocusStateChange = { altFocused = it }
                            )
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Alternatif akış dene",
                                color = if (altFocused) VegaColors.OnAccent else VegaColors.Accent,
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                var backFocused by remember { mutableStateOf(false) }
                Surface(
                    shape = VegaShapes.Medium,
                    color = if (backFocused) VegaColors.SurfaceVariant else Color.Transparent,
                    border = if (backFocused) BorderStroke(1.dp, VegaColors.Accent) else null,
                    modifier = Modifier
                        .height(40.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Medium,
                            focusedBorderColor = VegaColors.Accent,
                            scale = 1.03f,
                            onClick = onBack,
                            onFocusStateChange = { backFocused = it }
                        )
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Listeye geri dön",
                            color = if (backFocused) VegaColors.TextPrimary else VegaColors.TextSecondary,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** Sol kenardaki dikey parlaklık göstergesi. */
@Composable
internal fun BoxScope.BrightnessFeedbackBar(brightnessFeedback: Int?) {
    AnimatedVisibility(
        visible = brightnessFeedback != null,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300)),
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 24.dp)
    ) {
        brightnessFeedback?.let { pct ->
            val fraction = (pct / 100f).coerceIn(0f, 1f)
            Surface(
                shape = VegaShapes.Hero,
                color = VegaColors.OverlayPanel,
                border = BorderStroke(1.dp, VegaColors.Border),
                modifier = Modifier
                    .width(44.dp)
                    .height(180.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BrightnessMedium,
                        contentDescription = null,
                        tint = VegaColors.Gold,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(104.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .background(VegaColors.Gold)
                        )
                    }
                    Text(
                        text = "%$pct",
                        color = Color.White,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Sağ kenardaki dikey ses seviyesi göstergesi. */
@Composable
internal fun BoxScope.VolumeFeedbackBar(volumeFeedback: Int?) {
    AnimatedVisibility(
        visible = volumeFeedback != null,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300)),
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 24.dp)
    ) {
        volumeFeedback?.let { pct ->
            val fraction = (pct / 100f).coerceIn(0f, 1f)
            Surface(
                shape = VegaShapes.Hero,
                color = VegaColors.OverlayPanel,
                border = BorderStroke(1.dp, VegaColors.Border),
                modifier = Modifier
                    .width(44.dp)
                    .height(180.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = if (pct == 0) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(104.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .background(VegaColors.Accent)
                        )
                    }
                    Text(
                        text = "%$pct",
                        color = Color.White,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Uyku zamanlayıcısı seçim diyaloğu (StreamVault cam stil + tek odaklı TV desteği). */
@Composable
internal fun SleepTimerDialog(
    sleepTimerSeconds: Int?,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstOptionFocus = remember { FocusRequester() }
    val activeMinutes = sleepTimerSeconds?.let { (it + 59) / 60 }

    LaunchedEffect(Unit) {
        repeat(4) {
            delay(60)
            if (runCatching { firstOptionFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = VegaShapes.Large,
            color = VegaColors.OverlayPanel,
            border = BorderStroke(1.dp, VegaColors.Border),
            shadowElevation = 16.dp,
            modifier = Modifier.widthIn(min = 280.dp, max = 340.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Uyku zamanlayıcısı",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.SectionTitle,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                listOf(15, 30, 45, 60, 90).forEachIndexed { idx, mins ->
                    val isSelected = activeMinutes != null && kotlin.math.abs(activeMinutes - mins) <= 1
                    var isFocused by remember { mutableStateOf(false) }

                    Surface(
                        shape = VegaShapes.Medium,
                        color = when {
                            isFocused -> VegaColors.Accent
                            isSelected -> VegaColors.Accent.copy(alpha = 0.18f)
                            else -> VegaColors.SurfaceVariant
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isFocused -> Color.White
                                isSelected -> VegaColors.Accent
                                else -> VegaColors.Border
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(VegaSizes.ButtonHeight)
                            .then(if (idx == 0) Modifier.focusRequester(firstOptionFocus) else Modifier)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = Color.White,
                                scale = 1.02f,
                                onClick = {
                                    onSetTimer(mins)
                                    onDismiss()
                                },
                                onFocusStateChange = { isFocused = it }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$mins dakika sonra kapat",
                                color = when {
                                    isFocused -> VegaColors.OnAccent
                                    isSelected -> VegaColors.Accent
                                    else -> VegaColors.TextPrimary
                                },
                                fontSize = VegaText.Body,
                                fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (isFocused) VegaColors.OnAccent else VegaColors.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (sleepTimerSeconds != null) {
                    var cancelFocused by remember { mutableStateOf(false) }
                    Surface(
                        shape = VegaShapes.Medium,
                        color = if (cancelFocused) VegaColors.Danger else VegaColors.Danger.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, if (cancelFocused) Color.White else VegaColors.Danger),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(VegaSizes.ButtonHeight)
                            .dpadFocusable(
                                shape = VegaShapes.Medium,
                                focusedBorderColor = Color.White,
                                scale = 1.02f,
                                onClick = {
                                    onCancelTimer()
                                    onDismiss()
                                },
                                onFocusStateChange = { cancelFocused = it }
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Zamanlayıcıyı kapat",
                                color = if (cancelFocused) Color.White else VegaColors.Danger,
                                fontSize = VegaText.Body,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                var dismissFocused by remember { mutableStateOf(false) }
                Surface(
                    shape = VegaShapes.Medium,
                    color = if (dismissFocused) VegaColors.SurfaceVariant else Color.Transparent,
                    border = if (dismissFocused) BorderStroke(1.dp, VegaColors.Accent) else null,
                    modifier = Modifier
                        .align(Alignment.End)
                        .height(40.dp)
                        .dpadFocusable(
                            shape = VegaShapes.Medium,
                            focusedBorderColor = VegaColors.Accent,
                            scale = 1.03f,
                            onClick = onDismiss,
                            onFocusStateChange = { dismissFocused = it }
                        )
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Vazgeç",
                            color = if (dismissFocused) VegaColors.TextPrimary else VegaColors.TextSecondary,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
