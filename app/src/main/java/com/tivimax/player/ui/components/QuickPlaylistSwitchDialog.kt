package com.tivimax.player.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tivimax.player.data.playlist.Playlist
import com.tivimax.player.ui.design.*
import kotlinx.coroutines.delay

@Composable
fun QuickPlaylistSwitchDialog(
    playlists: List<Playlist>,
    activePlaylistId: String?,
    onSelectPlaylist: (Playlist) -> Unit,
    onManagePlaylists: () -> Unit,
    onDismiss: () -> Unit
) {
    var canInteract by remember { mutableStateOf(false) }
    val initialFocusRequester = remember { FocusRequester() }
    val focusTargetId = remember(playlists, activePlaylistId) {
        playlists.firstOrNull { it.id == activePlaylistId }?.id ?: playlists.firstOrNull()?.id
    }

    LaunchedEffect(Unit) {
        initialFocusRequester.requestFocusSafely()
        delay(250)
        canInteract = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.9f)
                .padding(vertical = 16.dp)
                .vegaPanel(VegaShapes.Hero)
                .onPreviewKeyEvent { event ->
                    !canInteract &&
                        event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_UP &&
                        when (event.nativeKeyEvent.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                            AndroidKeyEvent.KEYCODE_BUTTON_A -> true
                            else -> false
                        }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                VegaColors.Accent.copy(alpha = 0.12f),
                                Color.Transparent,
                                Color.Transparent
                            )
                        )
                    )
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Başlık
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = VegaShapes.Medium,
                            color = VegaColors.Accent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.32f)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = null,
                                    tint = VegaColors.Accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Oynatma listeleri",
                                color = VegaColors.TextPrimary,
                                fontSize = VegaText.SectionTitle,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Aktif listeni tek tıkla değiştir",
                                color = VegaColors.TextSecondary,
                                fontSize = VegaText.Caption
                            )
                        }
                    }
                    VegaIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Kapat",
                        onClick = onDismiss
                    )
                }

                // Liste Elemanları (Tek odak hedefi — çift tıklama yok)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    playlists.forEach { pl ->
                        val isActive = (pl.id == activePlaylistId)
                        var isFocused by remember { mutableStateOf(false) }

                        val backgroundColor = when {
                            isActive && isFocused -> VegaColors.Accent.copy(alpha = 0.26f)
                            isActive -> VegaColors.Accent.copy(alpha = 0.16f)
                            isFocused -> VegaColors.SurfaceElevated
                            else -> VegaColors.Card
                        }
                        val borderColor = if (isActive) VegaColors.Accent else VegaColors.Border

                        Surface(
                            shape = VegaShapes.Medium,
                            color = backgroundColor,
                            border = if (!isFocused) BorderStroke(1.dp, borderColor) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (pl.id == focusTargetId) Modifier.focusRequester(initialFocusRequester) else Modifier
                                )
                                .dpadFocusable(
                                    shape = VegaShapes.Medium,
                                    focusedBorderColor = VegaColors.FocusBorder,
                                    onClick = {
                                        onSelectPlaylist(pl)
                                        onDismiss()
                                    },
                                    onFocusStateChange = { isFocused = it }
                                )
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
                                        tint = if (isActive || isFocused) VegaColors.Accent else VegaColors.TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = pl.name,
                                            color = if (isActive || isFocused) VegaColors.TextPrimary else VegaColors.TextSecondary,
                                            fontSize = VegaText.Body,
                                            fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = pl.username,
                                            color = VegaColors.TextMuted,
                                            fontSize = VegaText.Caption,
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
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
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
