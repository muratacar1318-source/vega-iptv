package com.tivimax.player.ui.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.player.AspectRatioMode
import com.tivimax.player.player.StreamDiagnostics
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText

/**
 * Canlı TV Tam Ekran Oynatıcı İkincil Aksiyonlar BottomSheet'i ("Daha Fazla ⋯").
 * Ekranı kalabalıklaştırmadan ikincil aksiyonlara (En-boy oranı, çift ekran, numpad, kilit) tek dokunuşla erişim sağlar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePlayerMoreSheet(
    aspectRatio: AspectRatioMode,
    onCycleAspectRatio: () -> Unit,
    isDualViewActive: Boolean,
    onOpenDualView: () -> Unit,
    onOpenNumpad: () -> Unit,
    onOpenChannelDrawer: () -> Unit,
    onLockControls: (() -> Unit)? = null,
    diagnostics: StreamDiagnostics,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VegaColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = VegaColors.TextMuted) },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Başlık
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Oynatıcı seçenekleri",
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = VegaColors.TextSecondary
                    )
                }
            }

            // 1. En-Boy Oranı
            val aspectLabel = when (aspectRatio) {
                AspectRatioMode.FIT -> "Orijinal"
                AspectRatioMode.FILL -> "Ekranı doldur"
                AspectRatioMode.ZOOM -> "Yakınlaştır"
                AspectRatioMode.RATIO_16_9 -> "16:9 geniş ekran"
                AspectRatioMode.RATIO_4_3 -> "4:3 klasik"
            }
            MoreActionRow(
                icon = Icons.Default.AspectRatio,
                title = "En-boy oranı",
                subtitle = "Şu an: $aspectLabel (değiştirmek için dokun)",
                badge = aspectLabel,
                onClick = onCycleAspectRatio
            )

            // 2. Çift Ekran / PiP
            MoreActionRow(
                icon = Icons.Default.GridView,
                title = "Çift ekran",
                subtitle = if (isDualViewActive) "Çift ekran modu aktif" else "Aynı anda iki kanalı yan yana izle",
                isHighlighted = isDualViewActive,
                onClick = onOpenDualView
            )

            // 3. Sayısal Tuş Takımı (123)
            MoreActionRow(
                icon = Icons.Default.Dialpad,
                title = "Kanal numarası (123)",
                subtitle = "Kanal numarası yazarak anında geçiş yap",
                onClick = onOpenNumpad
            )

            // 4. Kanal & Kategori Çekmecesi
            MoreActionRow(
                icon = Icons.Default.Category,
                title = "Kategoriler ve kanallar",
                subtitle = "Yayını kesmeden tam kanal listesine göz at",
                onClick = onOpenChannelDrawer
            )

            // 5. Teknik Bilgi Şeridi
            val resText = diagnostics.resolutionBadge.ifBlank { diagnostics.resolution }.trim()
            val fpsText = diagnostics.frameRateBadge.ifBlank { diagnostics.frameRate }.trim()
            val vCodec = diagnostics.videoCodecBadge.ifBlank { diagnostics.videoCodec }.trim()
            val aCodec = diagnostics.audioCodecBadge.ifBlank { diagnostics.audioCodec }.trim()

            val codecPart = when {
                vCodec.isNotBlank() && aCodec.isNotBlank() -> "$vCodec / $aCodec"
                vCodec.isNotBlank() -> vCodec
                aCodec.isNotBlank() -> aCodec
                else -> ""
            }

            val diagnosticParts = listOf(resText, fpsText, codecPart).filter { it.isNotBlank() }
            val diagnosticText = if (diagnosticParts.isNotEmpty()) {
                diagnosticParts.joinToString(" • ")
            } else {
                "Bilgi bekleniyor…"
            }

            Surface(
                shape = VegaShapes.Medium,
                color = VegaColors.SurfaceVariant,
                border = BorderStroke(1.dp, VegaColors.Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Yayın akışı tanılama",
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = diagnosticText,
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = VegaShapes.Medium,
        color = if (isHighlighted) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.SurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (isHighlighted) VegaColors.Accent else VegaColors.Border
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(VegaShapes.Small),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isHighlighted) VegaColors.Accent else VegaColors.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.Body,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Caption,
                    maxLines = 1
                )
            }

            if (!badge.isNullOrBlank()) {
                Surface(
                    shape = VegaShapes.Small,
                    color = VegaColors.Accent.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, VegaColors.Accent)
                ) {
                    Text(
                        text = badge,
                        color = VegaColors.Accent,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = VegaColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
