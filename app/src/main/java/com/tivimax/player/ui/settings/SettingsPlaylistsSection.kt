package com.tivimax.player.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.data.playlist.Playlist
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.*

@Composable
fun SettingsPlaylistsSection(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.playlists.collectAsState()
    val activePlaylist by viewModel.activePlaylist.collectAsState()

    var showFormDialog by remember { mutableStateOf(false) }
    var playlistToEdit by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    // Silme Onay Diyaloğu
    if (playlistToDelete != null) {
        val target = playlistToDelete!!
        val cancelRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            cancelRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = VegaColors.Surface,
            shape = VegaShapes.Large,
            title = {
                Text(
                    text = "Oynatma listesini sil",
                    color = VegaColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Bu listenin kanalları ve favorileri bu cihazdan silinir.",
                    color = VegaColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                VegaButton(
                    text = "Sil",
                    variant = VegaButtonVariant.DANGER,
                    onClick = {
                        val id = target.id
                        playlistToDelete = null
                        viewModel.removePlaylist(id)
                    }
                )
            },
            dismissButton = {
                VegaButton(
                    text = "Vazgeç",
                    variant = VegaButtonVariant.SECONDARY,
                    onClick = { playlistToDelete = null },
                    modifier = Modifier.focusRequester(cancelRequester)
                )
            }
        )
    }

    // Ekle / Düzenle Form Diyaloğu
    if (showFormDialog) {
        PlaylistFormDialog(
            playlist = playlistToEdit,
            onDismiss = {
                showFormDialog = false
                playlistToEdit = null
            },
            onSave = { name, serverUrl, username, password, onDone ->
                if (playlistToEdit != null) {
                    viewModel.updatePlaylist(
                        id = playlistToEdit!!.id,
                        name = name,
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        onResult = onDone
                    )
                } else {
                    viewModel.addPlaylist(
                        name = name,
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        onResult = onDone
                    )
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Bölüm Başlığı
        Column {
            Text(
                text = "Listelerim",
                color = VegaColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Oynatma listelerini ekle ve aralarında geç",
                color = VegaColors.TextSecondary,
                fontSize = 13.sp
            )
        }

        // Listeler
        playlists.forEach { pl ->
            val isActive = (pl.id == activePlaylist?.id)
            PlaylistCard(
                playlist = pl,
                isActive = isActive,
                onSwitch = { viewModel.switchPlaylist(pl.id) },
                onEdit = {
                    playlistToEdit = pl
                    showFormDialog = true
                },
                onDelete = { playlistToDelete = pl }
            )
        }

        // Yeni Liste Ekle Butonu
        Spacer(modifier = Modifier.height(6.dp))
        VegaButton(
            text = "+ Yeni liste ekle",
            variant = VegaButtonVariant.PRIMARY,
            icon = Icons.Default.Add,
            onClick = {
                playlistToEdit = null
                showFormDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    VegaSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    color = VegaColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Kullanıcı: ${playlist.username}",
                    color = VegaColors.TextSecondary,
                    fontSize = 12.sp
                )
            }

            // Durum Rozeti
            if (isActive) {
                Surface(
                    shape = VegaShapes.Small,
                    color = VegaColors.Accent.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "Şu an kullanılıyor",
                        color = VegaColors.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            } else if (playlist.isSynced) {
                Surface(
                    shape = VegaShapes.Small,
                    color = VegaColors.Success.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, VegaColors.Success.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Hazır",
                        color = VegaColors.Success,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                Surface(
                    shape = VegaShapes.Small,
                    color = VegaColors.SurfaceVariant,
                    border = BorderStroke(1.dp, VegaColors.Border)
                ) {
                    Text(
                        text = "İndirilmedi",
                        color = VegaColors.TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        val formattedSync = AccountInfoFormatter.formatLastSync(playlist.lastSyncAt)
        Text(
            text = "Son güncelleme: $formattedSync",
            color = VegaColors.TextMuted,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Aksiyon Butonları
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isActive) {
                VegaButton(
                    text = "Geç",
                    variant = VegaButtonVariant.PRIMARY,
                    onClick = onSwitch,
                    modifier = Modifier.weight(1f)
                )
            }
            VegaButton(
                text = "Düzenle",
                variant = VegaButtonVariant.SECONDARY,
                icon = Icons.Default.Edit,
                onClick = onEdit,
                modifier = if (isActive) Modifier.weight(1f) else Modifier
            )
            VegaButton(
                text = "Sil",
                variant = VegaButtonVariant.DANGER,
                icon = Icons.Default.DeleteOutline,
                onClick = onDelete,
                modifier = if (isActive) Modifier.weight(1f) else Modifier
            )
        }
    }
}

@Composable
fun PlaylistFormDialog(
    playlist: Playlist?,
    onDismiss: () -> Unit,
    onSave: (name: String, serverUrl: String, username: String, password: String, onDone: (Result<Playlist>) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(playlist?.name ?: "") }
    var username by remember { mutableStateOf(playlist?.username ?: "") }
    var password by remember { mutableStateOf(playlist?.password ?: "") }
    var isCustomServer by remember {
        mutableStateOf(playlist != null && playlist.serverUrl.isNotBlank() && !AppConfig.isVegaServer(playlist.serverUrl))
    }
    var customServerUrl by remember {
        mutableStateOf(if (playlist != null && !AppConfig.isVegaServer(playlist.serverUrl)) playlist.serverUrl else "")
    }
    var passwordVisible by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isChecking) onDismiss() }) {
        Card(
            shape = VegaShapes.Large,
            colors = CardDefaults.cardColors(containerColor = VegaColors.Surface),
            border = BorderStroke(1.dp, VegaColors.Border),
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Başlık
                Text(
                    text = if (playlist == null) "Yeni liste ekle" else "Listeyi düzenle",
                    color = VegaColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // Hata Bildirimi
                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VegaColors.Danger.copy(alpha = 0.15f)),
                        shape = VegaShapes.Medium,
                        border = BorderStroke(1.dp, VegaColors.Danger.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = VegaColors.TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Liste Adı
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Liste adı") },
                    placeholder = { Text("Evdeki liste") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VegaColors.Accent,
                        unfocusedBorderColor = VegaColors.Border,
                        focusedLabelColor = VegaColors.Accent,
                        unfocusedLabelColor = VegaColors.TextSecondary,
                        focusedContainerColor = VegaColors.Card,
                        unfocusedContainerColor = VegaColors.Card,
                        focusedTextColor = VegaColors.TextPrimary,
                        unfocusedTextColor = VegaColors.TextPrimary
                    ),
                    shape = VegaShapes.Medium,
                    modifier = Modifier.fillMaxWidth()
                )

                // Sunucu Seçimi
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Sunucu",
                        color = VegaColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VegaChoiceChip(
                            label = "Vega sunucusu",
                            selected = !isCustomServer,
                            onClick = { isCustomServer = false },
                            modifier = Modifier.weight(1f)
                        )
                        VegaChoiceChip(
                            label = "Başka sunucu",
                            selected = isCustomServer,
                            onClick = { isCustomServer = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Başka Sunucu Adresi Girişi
                if (isCustomServer) {
                    OutlinedTextField(
                        value = customServerUrl,
                        onValueChange = { customServerUrl = it },
                        label = { Text("Sunucu adresi") },
                        placeholder = { Text("http://sunucu.com:8080") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = VegaColors.Accent)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VegaColors.Accent,
                            unfocusedBorderColor = VegaColors.Border,
                            focusedLabelColor = VegaColors.Accent,
                            unfocusedLabelColor = VegaColors.TextSecondary,
                            focusedContainerColor = VegaColors.Card,
                            unfocusedContainerColor = VegaColors.Card,
                            focusedTextColor = VegaColors.TextPrimary,
                            unfocusedTextColor = VegaColors.TextPrimary
                        ),
                        shape = VegaShapes.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Kullanıcı Adı
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Kullanıcı adı") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = VegaColors.Accent)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VegaColors.Accent,
                        unfocusedBorderColor = VegaColors.Border,
                        focusedLabelColor = VegaColors.Accent,
                        unfocusedLabelColor = VegaColors.TextSecondary,
                        focusedContainerColor = VegaColors.Card,
                        unfocusedContainerColor = VegaColors.Card,
                        focusedTextColor = VegaColors.TextPrimary,
                        unfocusedTextColor = VegaColors.TextPrimary
                    ),
                    shape = VegaShapes.Medium,
                    modifier = Modifier.fillMaxWidth()
                )

                // Şifre
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Şifre") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = VegaColors.Accent)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = VegaColors.TextSecondary
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VegaColors.Accent,
                        unfocusedBorderColor = VegaColors.Border,
                        focusedLabelColor = VegaColors.Accent,
                        unfocusedLabelColor = VegaColors.TextSecondary,
                        focusedContainerColor = VegaColors.Card,
                        unfocusedContainerColor = VegaColors.Card,
                        focusedTextColor = VegaColors.TextPrimary,
                        unfocusedTextColor = VegaColors.TextPrimary
                    ),
                    shape = VegaShapes.Medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Butonlar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VegaButton(
                        text = "Vazgeç",
                        variant = VegaButtonVariant.SECONDARY,
                        enabled = !isChecking,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )

                    val canSave = name.isNotBlank() && username.isNotBlank() && password.isNotBlank() &&
                            (!isCustomServer || customServerUrl.isNotBlank())

                    VegaButton(
                        text = if (isChecking) "Kontrol ediliyor…" else "Kaydet",
                        variant = VegaButtonVariant.PRIMARY,
                        enabled = !isChecking && canSave,
                        onClick = {
                            isChecking = true
                            errorMessage = null
                            val effectiveServer = if (isCustomServer) customServerUrl.trim() else AppConfig.DEFAULT_SERVER_URL
                            onSave(name.trim(), effectiveServer, username.trim(), password.trim()) { result ->
                                isChecking = false
                                result.fold(
                                    onSuccess = { onDismiss() },
                                    onFailure = { err ->
                                        errorMessage = err.message ?: "Sunucuya bağlanılamadı. Bilgilerini kontrol et."
                                    }
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
