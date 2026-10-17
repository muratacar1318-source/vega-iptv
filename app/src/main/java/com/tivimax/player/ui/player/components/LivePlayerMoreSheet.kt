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
import com.tivimax.player.theme.OpusCyan
import com.tivimax.player.theme.TiviSurface

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
        containerColor = TiviSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF64748B)) },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
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
                    text = "Oynatıcı Seçenekleri",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            // 1. En-Boy Oranı
            val aspectLabel = when (aspectRatio) {
                AspectRatioMode.FIT -> "Orijinal"
                AspectRatioMode.FILL -> "Ekranı Doldur"
                AspectRatioMode.ZOOM -> "Yakınlaştır (Zoom)"
                AspectRatioMode.RATIO_16_9 -> "16:9 Geniş Ekran"
                AspectRatioMode.RATIO_4_3 -> "4:3 Klasik"
            }
            MoreActionRow(
                icon = Icons.Default.AspectRatio,
                title = "En-Boy Oranı",
                subtitle = "Şu an: $aspectLabel (Değiştirmek için dokunun)",
                badge = aspectLabel,
                onClick = onCycleAspectRatio
            )

            // 2. Çift Ekran / PiP
            MoreActionRow(
                icon = Icons.Default.GridView,
                title = "Çift Ekran (Dual View)",
                subtitle = if (isDualViewActive) "Çift ekran modu aktif" else "Aynı anda iki kanalı yan yana izleyin",
                isHighlighted = isDualViewActive,
                onClick = onOpenDualView
            )

            // 3. Sayısal Tuş Takımı (123)
            MoreActionRow(
                icon = Icons.Default.Dialpad,
                title = "Kanal Numarası (123)",
                subtitle = "Kanal numarası yazarak anında geçiş yapın",
                onClick = onOpenNumpad
            )

            // 4. Kanal & Kategori Çekmecesi
            MoreActionRow(
                icon = Icons.Default.Category,
                title = "Kategoriler & Kanallar",
                subtitle = "Yayını kesmeden tam kanal listesine göz atın",
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
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF131B2A),
                border = BorderStroke(0.8.dp, Color(0xFF1E293B)),
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
                            text = "Yayın Akışı Tanılama",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = diagnosticText,
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = OpusCyan,
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
        shape = RoundedCornerShape(12.dp),
        color = if (isHighlighted) OpusCyan.copy(alpha = 0.12f) else Color(0xFF161F2E),
        border = BorderStroke(
            1.dp,
            if (isHighlighted) OpusCyan.copy(alpha = 0.6f) else Color(0xFF1E293B).copy(alpha = 0.8f)
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
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isHighlighted) OpusCyan else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    maxLines = 1
                )
            }

            if (!badge.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = OpusCyan.copy(alpha = 0.2f),
                    border = BorderStroke(0.8.dp, OpusCyan.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = badge,
                        color = OpusCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
