package com.tivimax.player.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(Unit) {
            for (attempt in 0 until 2) {
                withFrameNanos { }
                delay(60)
                if (initialFocusRequester.requestFocusSafely()) break
            }
            delay(180)
            canInteract = true
        }
        Surface(
            shape = VegaShapes.Large,
            color = VegaColors.SurfaceElevated,
            border = BorderStroke(0.75.dp, VegaColors.Border),
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.9f)
                .padding(vertical = 16.dp)
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
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Oynatma listeleri",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.SectionTitle,
                        fontWeight = FontWeight.Bold
                    )
                    VegaIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Kapat",
                        onClick = onDismiss
                    )
                }

                // İnce çizgi ayırıcılı liste (iç içe kart yok)
                Surface(
                    shape = VegaShapes.Medium,
                    color = VegaColors.Surface,
                    border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        playlists.forEachIndexed { idx, pl ->
                            val isActive = (pl.id == activePlaylistId)
                            var isFocused by remember { mutableStateOf(false) }

                            if (idx > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = VegaColors.Border.copy(alpha = 0.5f),
                                    thickness = 0.5.dp
                                )
                            }

                            val rowBg = when {
                                isActive && isFocused -> VegaColors.Accent.copy(alpha = 0.22f)
                                isActive -> VegaColors.Accent.copy(alpha = 0.10f)
                                isFocused -> VegaColors.SurfaceElevated
                                else -> Color.Transparent
                            }

                            Surface(
                                shape = VegaShapes.Small,
                                color = rowBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (pl.id == focusTargetId) Modifier.focusRequester(initialFocusRequester) else Modifier
                                    )
                                    .dpadFocusable(
                                        shape = VegaShapes.Small,
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
                                        .defaultMinSize(minHeight = 54.dp)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = pl.name,
                                            color = if (isActive) VegaColors.Accent else VegaColors.TextPrimary,
                                            fontSize = VegaText.Body,
                                            fontWeight = if (isActive || isFocused) FontWeight.Bold else FontWeight.Medium,
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

                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Aktif",
                                            tint = VegaColors.Accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

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
