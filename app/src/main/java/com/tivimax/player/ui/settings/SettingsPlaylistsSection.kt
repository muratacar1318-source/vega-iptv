package com.tivimax.player.ui.settings

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.data.playlist.Playlist
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
import com.tivimax.player.ui.design.*
import kotlinx.coroutines.delay

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
            for (attempt in 0 until 2) {
                withFrameNanos { }
                delay(60)
                if (cancelRequester.requestFocusSafely()) break
            }
        }

        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = VegaColors.SurfaceElevated,
            shape = VegaShapes.Large,
            title = {
                Text(
                    text = "Oynatma listesini sil",
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Bu listenin kanalları ve favorileri bu cihazdan silinir.",
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Secondary,
                    lineHeight = VegaText.SectionTitle
                )
            },
            confirmButton = {
                SettingsActionButton(
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
                SettingsActionButton(
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
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (playlists.isEmpty()) {
            Surface(
                shape = VegaShapes.Large,
                color = VegaColors.Surface,
                border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Kayıtlı oynatma listesi yok",
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.BodyProminent,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Yeni bir oynatma listesi ekleyerek izlemeye başla.",
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Secondary
                    )
                }
            }
        } else {
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
        }

        // Yeni Liste Ekle Butonu
        SettingsActionButton(
            text = "Yeni liste ekle",
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
    Surface(
        shape = VegaShapes.Large,
        color = VegaColors.Surface,
        border = BorderStroke(
            width = if (isActive) 1.dp else 0.75.dp,
            color = if (isActive) VegaColors.Accent.copy(alpha = 0.55f) else VegaColors.Border.copy(alpha = 0.65f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(VegaShapes.Small)
                        .background(
                            if (isActive) VegaColors.Accent.copy(alpha = 0.16f) else VegaColors.SurfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = if (isActive) VegaColors.Accent else VegaColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = playlist.name,
                        color = VegaColors.TextPrimary,
                        fontSize = VegaText.BodyProminent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val formattedSync = AccountInfoFormatter.formatLastSync(playlist.lastSyncAt)
                    Text(
                        text = "${playlist.username} · $formattedSync",
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val (statusLabel, statusColor) = when {
                    isActive -> "Aktif" to VegaColors.Accent
                    playlist.isSynced -> "Hazır" to VegaColors.Success
                    else -> "İndirilmedi" to VegaColors.TextMuted
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isActive) {
                    SettingsActionButton(
                        text = "Geç",
                        variant = VegaButtonVariant.PRIMARY,
                        onClick = onSwitch,
                        modifier = Modifier.weight(1f)
                    )
                }
                SettingsActionButton(
                    text = "Düzenle",
                    variant = VegaButtonVariant.SECONDARY,
                    icon = Icons.Default.Edit,
                    onClick = onEdit,
                    modifier = if (isActive) Modifier.weight(1f) else Modifier
                )
                SettingsActionButton(
                    text = "Sil",
                    variant = VegaButtonVariant.DANGER,
                    icon = Icons.Default.DeleteOutline,
                    onClick = onDelete,
                    modifier = if (isActive) Modifier.weight(1f) else Modifier
                )
            }
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

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocusRequester = remember { FocusRequester() }

    val canSave = name.isNotBlank() && username.isNotBlank() && password.isNotBlank() &&
        (!isCustomServer || customServerUrl.isNotBlank())

    val submitSave: () -> Unit = {
        keyboardController?.hide()
        if (!isChecking && canSave) {
            isChecking = true
            errorMessage = null
            val effectiveServer = if (isCustomServer) customServerUrl.trim() else AppConfig.DEFAULT_SERVER_URL
            onSave(name.trim(), effectiveServer, username.trim(), password.trim()) { result ->
                isChecking = false
                result.fold(
                    onSuccess = { onDismiss() },
                    onFailure = { err ->
                        Log.w("SettingsPlaylists", "Liste kaydedilemedi: ${err.javaClass.simpleName}")
                        errorMessage = "Sunucuya bağlanılamadı. Bilgilerini kontrol et."
                    }
                )
            }
        } else {
            focusManager.moveFocus(FocusDirection.Down)
        }
    }

    Dialog(
        onDismissRequest = { if (!isChecking) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(Unit) {
            for (attempt in 0 until 2) {
                withFrameNanos { }
                delay(60)
                if (nameFocusRequester.requestFocusSafely()) break
            }
        }
        Surface(
            shape = VegaShapes.Large,
            color = VegaColors.SurfaceElevated,
            border = BorderStroke(0.75.dp, VegaColors.Border),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (playlist == null) "Yeni liste ekle" else "Listeyi düzenle",
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.Bold
                )

                if (errorMessage != null) {
                    Surface(
                        color = VegaColors.Danger.copy(alpha = 0.14f),
                        shape = VegaShapes.Medium,
                        border = BorderStroke(0.75.dp, VegaColors.Danger.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Secondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VegaColors.Accent,
                    unfocusedBorderColor = VegaColors.Border,
                    focusedLabelColor = VegaColors.Accent,
                    unfocusedLabelColor = VegaColors.TextSecondary,
                    focusedContainerColor = VegaColors.Surface,
                    unfocusedContainerColor = VegaColors.Surface,
                    focusedTextColor = VegaColors.TextPrimary,
                    unfocusedTextColor = VegaColors.TextPrimary
                )

                OutlinedTextField(
                    keyboardOptions = tvKeyboardOptions(
                        KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Liste adı") },
                    placeholder = { Text("Evdeki liste") },
                    singleLine = true,
                    colors = fieldColors,
                    shape = VegaShapes.Medium,
                    modifier = Modifier
                        .tvOpenKeyboardOnOk()
                        .focusRequester(nameFocusRequester)
                        .fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Sunucu",
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Secondary,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsChoiceChip(
                            label = "Vega sunucusu",
                            selected = !isCustomServer,
                            onClick = { isCustomServer = false },
                            modifier = Modifier.weight(1f)
                        )
                        SettingsChoiceChip(
                            label = "Başka sunucu",
                            selected = isCustomServer,
                            onClick = { isCustomServer = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (isCustomServer) {
                    OutlinedTextField(
                        keyboardOptions = tvKeyboardOptions(
                            KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            )
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        value = customServerUrl,
                        onValueChange = { customServerUrl = it },
                        label = { Text("Sunucu adresi") },
                        placeholder = { Text("Sağlayıcından aldığın adres") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = VegaColors.TextSecondary)
                        },
                        colors = fieldColors,
                        shape = VegaShapes.Medium,
                        modifier = Modifier
                            .tvOpenKeyboardOnOk()
                            .fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    keyboardOptions = tvKeyboardOptions(
                        KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Kullanıcı adı") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = VegaColors.TextSecondary)
                    },
                    colors = fieldColors,
                    shape = VegaShapes.Medium,
                    modifier = Modifier
                        .tvOpenKeyboardOnOk()
                        .fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Şifre") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = VegaColors.TextSecondary)
                    },
                    trailingIcon = {
                        SettingsIconActionButton(
                            icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Şifreyi gizle" else "Şifreyi göster",
                            onClick = { passwordVisible = !passwordVisible },
                            tint = VegaColors.TextSecondary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = fieldColors,
                    shape = VegaShapes.Medium,
                    keyboardOptions = tvKeyboardOptions(
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        )
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { submitSave() }
                    ),
                    modifier = Modifier
                        .tvOpenKeyboardOnOk()
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsActionButton(
                        text = "Vazgeç",
                        variant = VegaButtonVariant.SECONDARY,
                        enabled = !isChecking,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )

                    SettingsActionButton(
                        text = if (isChecking) "Kontrol ediliyor…" else "Kaydet",
                        variant = VegaButtonVariant.PRIMARY,
                        enabled = !isChecking && canSave,
                        isLoading = isChecking,
                        onClick = submitSave,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
