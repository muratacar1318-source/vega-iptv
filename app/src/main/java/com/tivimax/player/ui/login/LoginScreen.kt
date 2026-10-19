package com.tivimax.player.ui.login

import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.theme.*
import com.tivimax.player.ui.design.VegaChoiceChip
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.MainViewModel

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        VegaColors.Surface,
                        VegaColors.Background,
                        Color(0xFF000000)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Brand Logo & Icon (OLED Midnight Glow)
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(VegaShapes.Large)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(VegaColors.Accent, VegaColors.Accent.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "Logo",
                    tint = VegaColors.OnAccent,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = AppConfig.APP_NAME,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = VegaColors.TextPrimary,
                letterSpacing = 1.sp
            )

            Text(
                text = "İlk oynatma listeni ekle",
                fontSize = 13.sp,
                color = VegaColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Error Banner
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VegaColors.Danger.copy(alpha = 0.15f)),
                        shape = VegaShapes.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
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
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Input: Liste Adı
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Liste adı") },
                placeholder = { Text("Ana liste") },
                singleLine = true,
                leadingIcon = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = VegaColors.Accent)
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
                modifier = Modifier.tvOpenKeyboardOnOk().fillMaxWidth(),
                keyboardOptions = tvKeyboardOptions(KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Sunucu Seçimi
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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

            AnimatedVisibility(visible = isCustomServer) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = customServerUrl,
                        onValueChange = { customServerUrl = it },
                        label = { Text("Sunucu adresi") },
                        placeholder = { Text("http://sunucu.com:8080") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Dns, contentDescription = null, tint = VegaColors.Accent)
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
                        modifier = Modifier.tvOpenKeyboardOnOk().fillMaxWidth(),
                        keyboardOptions = tvKeyboardOptions(KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        )),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Input: Kullanıcı Adı (İleri tuşu şifreye odaklar)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Kullanıcı adı") },
                singleLine = true,
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = VegaColors.Accent)
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
                modifier = Modifier.tvOpenKeyboardOnOk().fillMaxWidth(),
                keyboardOptions = tvKeyboardOptions(KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Input: Şifre (Tamam tuşu doğrudan girişi tetikler)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifre") },
                singleLine = true,
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = VegaColors.Accent)
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
                modifier = Modifier.tvOpenKeyboardOnOk().fillMaxWidth(),
                keyboardOptions = tvKeyboardOptions(KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (playlistName.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                            viewModel.login(
                                user = username.trim(),
                                pass = password.trim(),
                                serverUrl = effectiveServerUrl,
                                playlistName = playlistName.trim()
                            )
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.height(22.dp))

            val canSubmit = !isLoading && playlistName.isNotBlank() && username.isNotBlank() && password.isNotBlank()

            // Giriş Yap Butonu
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.login(
                        user = username.trim(),
                        pass = password.trim(),
                        serverUrl = effectiveServerUrl,
                        playlistName = playlistName.trim()
                    )
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VegaColors.Accent,
                    contentColor = VegaColors.OnAccent,
                    disabledContainerColor = VegaColors.Border,
                    disabledContentColor = VegaColors.TextMuted
                ),
                shape = VegaShapes.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = VegaColors.OnAccent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Kontrol ediliyor…",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Giriş yap",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Demo Modu ile Test Et Butonu
            OutlinedButton(
                onClick = { viewModel.loginDemo() },
                shape = VegaShapes.Medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, VegaColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = VegaColors.SurfaceVariant,
                    contentColor = VegaColors.TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayCircleOutline, contentDescription = null, tint = VegaColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hemen dene (canlı demo)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Destek Bilgisi
            Text(
                text = "Vega sunucusu seçildiğinde adres otomatik kullanılır. Yalnızca kullanıcı adı ve şifreni girmen yeterlidir.",
                fontSize = 11.sp,
                color = VegaColors.TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
