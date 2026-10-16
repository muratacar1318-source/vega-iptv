package com.tivimax.player.ui.player.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.theme.OpusCyan
import com.tivimax.player.theme.TiviGold

/**
 * Canlı TV Tam Ekran Oynatıcısı Alt Aksiyon Çubuğu (Bottom Navigation Bar Düzeni).
 *
 * Özellikler:
 * 1. Asla yatay kaydırma (horizontal scroll) gerektirmez; tüm ana aksiyonlar tek satırda görünür.
 * 2. İkon üstte, etiket altta olacak şekilde dikey dengeli tasarım.
 * 3. Her buton en az 48-52dp dokunma alanına ve dokunma geri bildirimine (haptic + ripple + scale) sahiptir.
 * 4. "Önceki Kanal (TLC)" butonu belirgin altın/turuncu renkle vurgulanır.
 * 5. İkincil aksiyonlar "Daha Fazla" (⋯) butonuyla modern bir BottomSheet'e aktarılır.
 */
@Composable
fun LivePlayerActionBar(
    onOpenGuide: () -> Unit,
    isGuideActive: Boolean = false,
    onToggleRecentChannels: () -> Unit,
    isRecentActive: Boolean = false,
    hasPreviousChannel: Boolean = false,
    previousChannelName: String? = null,
    onZapRecall: () -> Unit,
    onOpenAudioSubtitles: () -> Unit,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier,
    firstButtonFocusRequester: FocusRequester? = null
) {
    Surface(
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. REHBER (EPG)
            LiveActionButtonItem(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                label = "Rehber",
                contentDescription = "Yayın Rehberini Aç",
                isActive = isGuideActive,
                activeColor = OpusCyan,
                onClick = onOpenGuide,
                focusRequester = firstButtonFocusRequester,
                modifier = Modifier.weight(1f)
            )

            // 2. SON KANALLAR
            LiveActionButtonItem(
                icon = Icons.Default.History,
                label = "Son",
                contentDescription = "Son İzlenen Kanalları Göster",
                isActive = isRecentActive,
                activeColor = OpusCyan,
                onClick = onToggleRecentChannels,
                modifier = Modifier.weight(1f)
            )

            // 3. ÖNCEKİ KANAL (TLC ZAPPING RECALL)
            val prevLabel = if (!previousChannelName.isNullOrBlank()) {
                val clean = previousChannelName.take(6).trim()
                "↩ $clean"
            } else {
                "Önceki"
            }

            LiveActionButtonItem(
                icon = Icons.Default.Replay,
                label = prevLabel,
                contentDescription = "Önceki Kanala Geri Dön",
                isActive = hasPreviousChannel,
                activeColor = TiviGold,
                defaultColor = if (hasPreviousChannel) TiviGold else Color(0xFF64748B),
                onClick = {
                    if (hasPreviousChannel) {
                        onZapRecall()
                    }
                },
                modifier = Modifier.weight(1f)
            )

            // 4. SES & ALTYAZI
            LiveActionButtonItem(
                icon = Icons.Default.Subtitles,
                label = "Ses & Dil",
                contentDescription = "Ses ve Altyazı Parçalarını Seç",
                onClick = onOpenAudioSubtitles,
                modifier = Modifier.weight(1f)
            )

            // 5. DAHA FAZLA (⋯)
            LiveActionButtonItem(
                icon = Icons.Default.MoreHoriz,
                label = "Daha Fazla",
                contentDescription = "Diğer Oynatıcı Seçeneklerini Aç",
                onClick = onOpenMore,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LiveActionButtonItem(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeColor: Color = OpusCyan,
    defaultColor: Color = Color(0xFFE2E8F0),
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val registerInteraction = LocalPlayerInteraction.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "btnScale"
    )

    val tintColor by animateColorAsState(
        targetValue = if (isActive) activeColor else defaultColor,
        label = "btnColor"
    )

    val itemModifier = modifier
        .heightIn(min = 52.dp)
        .scale(scale)
        .clip(RoundedCornerShape(12.dp))
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true, color = activeColor),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                registerInteraction()
                onClick()
            }
        )
        .padding(vertical = 4.dp, horizontal = 2.dp)

    Column(
        modifier = itemModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tintColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = tintColor,
            fontSize = 10.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
