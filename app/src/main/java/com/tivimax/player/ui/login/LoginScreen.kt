package com.tivimax.player.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.BuildConfig
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
import com.tivimax.player.ui.design.*

@Composable
fun LoginScreen(viewModel: MainViewModel) {
    var playlistName by remember { mutableStateOf("Ana liste") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isCustomServer by remember { mutableStateOf(false) }
    var customServerUrl by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val effectiveServerUrl = remember(customServerUrl, isCustomServer) {
        if (isCustomServer && customServerUrl.isNotBlank()) {
            customServerUrl.trim()
        } else {
            AppConfig.DEFAULT_SERVER_URL
        }
    }

    val isLoading by viewModel.loginLoading.collectAsState()
    val errorMessage by viewModel.loginError.collectAsState()
    val focusManager = LocalFocusManager.current

    val canSubmit = !isLoading && playlistName.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    val submitLogin: () -> Unit = {
        focusManager.clearFocus()
        if (canSubmit) {
            viewModel.login(
                user = username.trim(),
                pass = password.trim(),
                serverUrl = effectiveServerUrl,
                playlistName = playlistName.trim()
            )
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VegaColors.FocusBorder,
        unfocusedBorderColor = VegaColors.Border,
        focusedLabelColor = VegaColors.Accent,
        unfocusedLabelColor = VegaColors.TextSecondary,
        focusedContainerColor = VegaColors.SurfaceElevated,
        unfocusedContainerColor = VegaColors.Card,
        focusedTextColor = VegaColors.TextPrimary,
        unfocusedTextColor = VegaColors.TextPrimary
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .vegaBackdrop()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 468.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .vegaPanel(VegaShapes.Hero)
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Marka Logosu ve Adı
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(VegaShapes.Large)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(VegaColors.Accent, VegaColors.Accent.copy(alpha = 0.72f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = VegaColors.OnAccent,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = AppConfig.APP_NAME,
                    fontSize = VegaText.ScreenTitle,
                    fontWeight = FontWeight.ExtraBold,
                    color = VegaColors.TextPrimary,
                    letterSpacing = 0.4.sp
                )

                Text(
                    text = "İlk oynatma listeni ekle",
                    fontSize = VegaText.Secondary,
                    color = VegaColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Hata Mesajı Bandı
                AnimatedVisibility(visible = errorMessage != null) {
                    errorMessage?.let { error ->
                        Surface(
                            color = VegaColors.Danger.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, VegaColors.Danger.copy(alpha = 0.35f)),
                            shape = VegaShapes.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = VegaColors.Danger
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = error,
                                    color = VegaColors.TextPrimary,
                                    fontSize = VegaText.Secondary
                                )
                            }
                        }
                    }
                }

                // Giriş: Liste Adı
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Liste adı") },
                    placeholder = { Text("Ana liste") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = VegaColors.Accent
                        )
                    },
                    colors = fieldColors,
                    shape = VegaShapes.Medium,
                    modifier = Modifier.tvOpenKeyboardOnOk().fillMaxWidth(),
                    keyboardOptions = tvKeyboardOptions(
                        KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Sunucu Seçimi (Tek odaklı VegaChoiceChip)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Sunucu",
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
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

                AnimatedVisibility(visible = isCustomServer) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = customServerUrl,
                            onValueChange = { customServerUrl = it },
                            label = { Text("Sunucu adresi") },
                            placeholder = { Text("Yayın sunucu adresini gir") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Dns, contentDescription = null, tint = VegaColors.Accent)
                            },
                            colors = fieldColors,
                            shape = VegaShapes.Medium,
                            modifier = Modifier.tvOpenKeyboardOnOk().fillMaxWidth(),
                            keyboardOptions = tvKeyboardOptions(
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Next
                                )
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Giriş: Kullanıcı Adı
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Kullanıcı adı") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = VegaColors.Accent)
                    },
                    colors = fieldColors,
                    shape = VegaShapes.Medium,
                    modifier = Modifier.tvOpenKeyboardOnOk().fillMaxWidth(),
                    keyboardOptions = tvKeyboardOptions(
                        KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Giriş: Şifre (Tamam tuşu doğrudan girişi tetikler)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Şifre") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = VegaColors.Accent)
                    },
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .dpadFocusable(
                                    shape = VegaShapes.Pill,
                                    focusedBorderColor = VegaColors.FocusBorder,
                                    onClick = { passwordVisible = !passwordVisible }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Şifreyi gizle" else "Şifreyi göster",
                                tint = VegaColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = fieldColors,
                    shape = VegaShapes.Medium,
                    modifier = Modifier.tvOpenKeyboardOnOk().fillMaxWidth(),
                    keyboardOptions = tvKeyboardOptions(
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        )
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { submitLogin() }
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Giriş Yap Butonu (Tek odak hedefi — TV kumandası uyumlu)
                Surface(
                    shape = VegaShapes.Medium,
                    color = if (canSubmit) VegaColors.Accent else VegaColors.SurfaceVariant,
                    contentColor = if (canSubmit) VegaColors.OnAccent else VegaColors.TextMuted,
                    border = if (!canSubmit) BorderStroke(1.dp, VegaColors.Border) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .then(
                            if (canSubmit) {
                                Modifier.dpadFocusable(
                                    shape = VegaShapes.Medium,
                                    focusedBorderColor = Color.White,
                                    onClick = submitLogin
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = VegaColors.OnAccent,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Kontrol ediliyor…",
                                fontSize = VegaText.BodyProminent,
                                fontWeight = FontWeight.Bold,
                                color = VegaColors.OnAccent
                            )
                        } else {
                            Text(
                                text = "Giriş yap",
                                fontSize = VegaText.BodyProminent,
                                fontWeight = FontWeight.Bold,
                                color = if (canSubmit) VegaColors.OnAccent else VegaColors.TextMuted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = if (canSubmit) VegaColors.OnAccent else VegaColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Demo girişi yalnızca geliştirici derlemesinde görünür (G-049)
                if (BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(10.dp))
                    VegaButton(
                        text = "Hemen dene (canlı demo)",
                        variant = VegaButtonVariant.SECONDARY,
                        icon = Icons.Default.PlayCircleOutline,
                        onClick = { viewModel.loginDemo() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Destek Bilgisi
                Text(
                    text = "Vega sunucusu seçildiğinde adres otomatik kullanılır. Yalnızca kullanıcı adı ve şifreni girmen yeterlidir.",
                    fontSize = VegaText.Caption,
                    color = VegaColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}
