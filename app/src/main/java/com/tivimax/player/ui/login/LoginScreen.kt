package com.tivimax.player.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.MainViewModel

@Composable
fun LoginScreen(viewModel: MainViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var customServerUrl by remember { mutableStateOf("") }
    var showCustomServerInput by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val effectiveServerUrl = remember(customServerUrl, showCustomServerInput) {
        if (showCustomServerInput && customServerUrl.isNotBlank()) {
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
                        Color(0xFF0F141C),
                        TiviBackground,
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
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(TiviPrimary, TiviPrimaryDark)
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
                color = TiviTextPrimary,
                letterSpacing = 1.sp
            )

            Text(
                text = AppConfig.APP_TAGLINE,
                fontSize = 13.sp,
                color = TiviTextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Error Banner
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TiviAccentLive.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
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
                                tint = TiviAccentLive
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = error,
                                color = TiviTextPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Input: Kullanıcı Adı (İleri tuşu şifreye odaklar)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Kullanıcı Adı") },
                singleLine = true,
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = TiviPrimary)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TiviPrimary,
                    unfocusedBorderColor = TiviCardBorder,
                    focusedLabelColor = TiviPrimary,
                    unfocusedLabelColor = TiviTextSecondary,
                    focusedContainerColor = TiviCard,
                    unfocusedContainerColor = TiviCard,
                    focusedTextColor = TiviTextPrimary,
                    unfocusedTextColor = TiviTextPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
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
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TiviPrimary)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = TiviTextSecondary
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TiviPrimary,
                    unfocusedBorderColor = TiviCardBorder,
                    focusedLabelColor = TiviPrimary,
                    unfocusedLabelColor = TiviTextSecondary,
                    focusedContainerColor = TiviCard,
                    unfocusedContainerColor = TiviCard,
                    focusedTextColor = TiviTextPrimary,
                    unfocusedTextColor = TiviTextPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (username.isNotBlank() && password.isNotBlank()) {
                            viewModel.login(username, password, effectiveServerUrl)
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Giriş Yap Butonu
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.login(username, password, effectiveServerUrl)
                },
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TiviPrimary,
                    contentColor = VegaColors.OnAccent,
                    disabledContainerColor = TiviCardBorder,
                    disabledContentColor = TiviTextMuted
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = VegaColors.OnAccent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Bağlanıyor...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "GİRİŞ YAP",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Demo Modu ile Test Et Butonu
            OutlinedButton(
                onClick = { viewModel.loginDemo() },
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TiviPrimary.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TiviPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayCircleOutline, contentDescription = null, tint = TiviPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hemen Dene (Canlı Demo)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Gelişmiş / Özel Sunucu Seçeneği
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { showCustomServerInput = !showCustomServerInput }) {
                    Text(
                        text = if (showCustomServerInput) "Varsayılan Sunucuya Dön" else "Farklı Sunucu Gir",
                        fontSize = 11.sp,
                        color = TiviTextMuted
                    )
                }
            }

            AnimatedVisibility(visible = showCustomServerInput) {
                OutlinedTextField(
                    value = customServerUrl,
                    onValueChange = { customServerUrl = it },
                    label = { Text("Özel Sunucu Adresi") },
                    placeholder = { Text("http://sunucu.com:8080") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Dns, contentDescription = null, tint = TiviPrimary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TiviPrimary,
                        unfocusedBorderColor = TiviCardBorder,
                        focusedContainerColor = TiviCard,
                        unfocusedContainerColor = TiviCard,
                        focusedTextColor = TiviTextPrimary,
                        unfocusedTextColor = TiviTextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Destek Bilgisi
            Text(
                text = "Sunucu adresi otomatik yapılandırılmıştır. Yalnızca kullanıcı adı ve şifrenizi girmeniz yeterlidir.",
                fontSize = 11.sp,
                color = TiviTextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
