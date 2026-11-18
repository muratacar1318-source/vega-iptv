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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.RectangleShape
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

/** Tüm otomatik denemeler tükenince gösterilen sakin koyu yayın hata katmanı (TV kumanda odaklı). */
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
            shape = VegaShapes.Medium,
            color = VegaColors.OverlayPanel,
            border = BorderStroke(0.5.dp, VegaColors.Border.copy(alpha = 0.45f)),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = VegaColors.Danger,
                    modifier = Modifier.size(28.dp)
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
                    Box(
                        modifier = Modifier
                            .height(VegaSizes.ButtonHeight)
                            .clip(VegaShapes.Small)
                            .background(
                                if (retryFocused) VegaColors.Accent else VegaColors.Accent.copy(alpha = 0.88f)
                            )
                            .focusRequester(retryFocus)
                            .dpadFocusable(
                                shape = VegaShapes.Small,
                                drawFocusBorder = false,
                                scale = 1.0f,
                                onClick = onRetry,
                                onFocusStateChange = { retryFocused = it }
                            )
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
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
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    var altFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .height(VegaSizes.ButtonHeight)
                            .clip(VegaShapes.Small)
                            .background(
                                if (altFocused) VegaColors.Accent else Color.White.copy(alpha = 0.08f)
                            )
                            .dpadFocusable(
                                shape = VegaShapes.Small,
                                drawFocusBorder = false,
                                scale = 1.0f,
                                onClick = onRetryFallback,
                                onFocusStateChange = { altFocused = it }
                            )
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Alternatif akış dene",
                            color = if (altFocused) VegaColors.OnAccent else VegaColors.TextPrimary,
                            fontSize = VegaText.Body,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                var backFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(VegaShapes.Small)
                        .background(if (backFocused) VegaColors.SurfaceVariant else Color.Transparent)
                        .dpadFocusable(
                            shape = VegaShapes.Small,
                            drawFocusBorder = false,
                            scale = 1.0f,
                            onClick = onBack,
                            onFocusStateChange = { backFocused = it }
                        )
                        .padding(horizontal = 16.dp),
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

/** Sol kenardaki ince dikey yayın parlaklık kapsülü (34x156dp). */
@Composable
internal fun BoxScope.BrightnessFeedbackBar(brightnessFeedback: Int?) {
    AnimatedVisibility(
        visible = brightnessFeedback != null,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(220)),
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 22.dp)
    ) {
        brightnessFeedback?.let { pct ->
            val fraction = (pct / 100f).coerceIn(0f, 1f)
            Surface(
                shape = RoundedCornerShape(17.dp),
                color = Color.Black.copy(alpha = 0.68f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier
                    .width(34.dp)
                    .height(156.dp)
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
                        modifier = Modifier.size(15.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(90.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(CircleShape)
                                .background(VegaColors.Gold)
                        )
                    }
                    Text(
                        text = "%$pct",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** Sağ kenardaki ince dikey yayın ses seviyesi kapsülü (34x156dp). */
@Composable
internal fun BoxScope.VolumeFeedbackBar(volumeFeedback: Int?) {
    AnimatedVisibility(
        visible = volumeFeedback != null,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(220)),
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 22.dp)
    ) {
        volumeFeedback?.let { pct ->
            val fraction = (pct / 100f).coerceIn(0f, 1f)
            Surface(
                shape = RoundedCornerShape(17.dp),
                color = Color.Black.copy(alpha = 0.68f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier
                    .width(34.dp)
                    .height(156.dp)
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
                        modifier = Modifier.size(15.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(90.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(CircleShape)
                                .background(VegaColors.Accent)
                        )
                    }
                    Text(
                        text = "%$pct",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** TiviMate tarzı uyku zamanlayıcısı seçim menüsü (düz 48dp satırlar, 0.5dp ayırıcı, sol 3dp şerit). */
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
            shape = VegaShapes.Medium,
            color = VegaColors.OverlayPanel,
            border = BorderStroke(0.5.dp, VegaColors.Border.copy(alpha = 0.45f)),
            modifier = Modifier.widthIn(min = 280.dp, max = 336.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "Uyku zamanlayıcısı",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.SectionTitle,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(thickness = 0.5.dp, color = VegaColors.Border.copy(alpha = 0.45f))

                val options = listOf(15, 30, 45, 60, 90)
                options.forEachIndexed { idx, mins ->
                    val isSelected = activeMinutes != null && kotlin.math.abs(activeMinutes - mins) <= 1
                    var isFocused by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(if (isFocused) VegaColors.SurfaceVariant else Color.Transparent)
                            .then(if (idx == 0) Modifier.focusRequester(firstOptionFocus) else Modifier)
                            .dpadFocusable(
                                shape = RectangleShape,
                                drawFocusBorder = false,
                                scale = 1.0f,
                                onClick = {
                                    onSetTimer(mins)
                                    onDismiss()
                                },
                                onFocusStateChange = { isFocused = it }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(if (isFocused || isSelected) VegaColors.Accent else Color.Transparent)
                        )
                        Spacer(modifier = Modifier.width(13.dp))
                        Text(
                            text = "$mins dakika sonra kapat",
                            color = when {
                                isSelected -> VegaColors.Accent
                                isFocused -> VegaColors.TextPrimary
                                else -> VegaColors.TextPrimary.copy(alpha = 0.92f)
                            },
                            fontSize = VegaText.Body,
                            fontWeight = if (isFocused || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = VegaColors.Accent,
                                modifier = Modifier
                                    .padding(end = 14.dp)
                                    .size(18.dp)
                            )
                        }
                    }

                    if (idx < options.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp, color = VegaColors.Border.copy(alpha = 0.35f))
                    }
                }

                if (sleepTimerSeconds != null) {
                    HorizontalDivider(thickness = 0.5.dp, color = VegaColors.Border.copy(alpha = 0.45f))
                    var cancelFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(if (cancelFocused) VegaColors.SurfaceVariant else Color.Transparent)
                            .dpadFocusable(
                                shape = RectangleShape,
                                drawFocusBorder = false,
                                scale = 1.0f,
                                onClick = {
                                    onCancelTimer()
                                    onDismiss()
                                },
                                onFocusStateChange = { cancelFocused = it }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(if (cancelFocused) VegaColors.Danger else Color.Transparent)
                        )
                        Spacer(modifier = Modifier.width(13.dp))
                        Text(
                            text = "Zamanlayıcıyı kapat",
                            color = VegaColors.Danger,
                            fontSize = VegaText.Body,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = VegaColors.Border.copy(alpha = 0.45f))

                var dismissFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(if (dismissFocused) VegaColors.SurfaceVariant else Color.Transparent)
                        .dpadFocusable(
                            shape = RectangleShape,
                            drawFocusBorder = false,
                            scale = 1.0f,
                            onClick = onDismiss,
                            onFocusStateChange = { dismissFocused = it }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(if (dismissFocused) VegaColors.Accent else Color.Transparent)
                    )
                    Spacer(modifier = Modifier.width(13.dp))
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
