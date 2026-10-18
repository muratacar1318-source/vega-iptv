package com.tivimax.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tivimax.player.data.playlist.Playlist
import com.tivimax.player.ui.design.*

@Composable
fun QuickPlaylistSwitchDialog(
    playlists: List<Playlist>,
    activePlaylistId: String?,
    onSelectPlaylist: (Playlist) -> Unit,
    onManagePlaylists: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = VegaShapes.Large,
            colors = CardDefaults.cardColors(containerColor = VegaColors.Surface),
            border = BorderStroke(1.dp, VegaColors.Border),
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Başlık
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Oynatma Listeleri",
                        color = VegaColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Liste Elemanları
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    playlists.forEach { pl ->
                        val isActive = (pl.id == activePlaylistId)
                        val backgroundColor = if (isActive) VegaColors.Accent.copy(alpha = 0.16f) else VegaColors.Card
                        val borderColor = if (isActive) VegaColors.Accent else VegaColors.Border

                        Surface(
                            shape = VegaShapes.Medium,
                            color = backgroundColor,
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .dpadFocusable(
                                    shape = VegaShapes.Medium,
                                    focusedBorderColor = VegaColors.Accent,
                                    onClick = {
                                        onSelectPlaylist(pl)
                                        onDismiss()
                                    }
                                )
                                .clickable {
                                    onSelectPlaylist(pl)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isActive) VegaColors.Accent else VegaColors.TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = pl.name,
                                            color = if (isActive) VegaColors.TextPrimary else VegaColors.TextSecondary,
                                            fontSize = 14.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = pl.username,
                                            color = VegaColors.TextMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (isActive) {
                                    Surface(
                                        shape = VegaShapes.Small,
                                        color = VegaColors.Accent.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "Aktif",
                                            color = VegaColors.Accent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = VegaColors.Border, thickness = 0.5.dp)

                // Listeleri Yönet Butonu
                VegaButton(
                    text = "Listeleri yönet",
                    variant = VegaButtonVariant.SECONDARY,
                    icon = Icons.Default.Settings,
                    onClick = {
                        onDismiss()
                        onManagePlaylists()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
