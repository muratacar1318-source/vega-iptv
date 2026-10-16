package com.tivimax.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.model.LiveStream

@Composable
fun LiveChannelCard(
    channel: LiveStream,
    epg: EpgProgram?,
    isFavorite: Boolean,
    onChannelClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentProgram = epg?.getDecodedTitle() ?: channel.currentProgram ?: "Canlı Yayın"
    val timeString = if (epg?.start != null && epg.end != null) "${epg.start} - ${epg.end}" else "20:00 - 22:00"
    val progress = epg?.getProgressFraction() ?: 0.5f

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF15172B),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
            .fillMaxWidth()
            .dpadFocusable(
                focusedBorderColor = Color(0xFF00E5FF),
                shape = RoundedCornerShape(12.dp),
                onClick = onChannelClick
            )
            .clickable(onClick = onChannelClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SOL: 44.dp Yuvarlatılmış Logo Kutusu
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A2230)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncShimmerImage(
                        model = channel.streamIcon,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        placeholderIcon = Icons.Default.Tv,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }

                // ORTA: Kanal Adı, Program ve Yayın Saati (Column weight = 1f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    // Satır 1: Kanal Adı
                    Text(
                        text = channel.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Satır 2: Şu Anki Program Adı
                    Text(
                        text = currentProgram,
                        fontSize = 12.sp,
                        color = Color(0xFFB0B3C6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Satır 3: Yayın Saati (KESİNLİKLE TEK SATIRDA - maxLines = 1)
                    Text(
                        text = timeString,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF00E5FF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // SAĞ: Yan Yana 32.dp Oynat ve Favori Butonları
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onChannelClick,
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Oynat",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onFavoriteToggle,
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favori",
                                tint = if (isFavorite) Color(0xFFFFD700) else Color(0xFF8E92AA),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ALT: 3.dp İlerleme Çubuğu (StrokeCap.Round)
            LinearProgressIndicator(
                progress = { progress },
                color = Color(0xFF00E5FF),
                trackColor = Color.White.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
            )
        }
    }
}
