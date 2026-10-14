package com.tivimax.player.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.tivimax.player.ui.MainViewModel

@Composable
fun LoginScreen(viewModel: MainViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useInsecureConnection by remember { mutableStateOf(false) }
    var customServerUrl by remember { mutableStateOf("") }
    var showCustomServerInput by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val effectiveServerUrl = remember(useInsecureConnection, customServerUrl, showCustomServerInput) {
        if (showCustomServerInput && customServerUrl.isNotBlank()) {
            customServerUrl.trim()
        } else if (useInsecureConnection) {
            AppConfig.SERVER_URL_HTTP
        } else {
            AppConfig.SERVER_URL_HTTPS
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
                        Color(0xFF0F172A),
                        TiviBackground,
                        Color(0xFF05070B)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Brand Logo & Icon
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(TiviPrimary, TiviSecondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "Logo",
                    tint = TiviBackground,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = AppConfig.APP_NAME,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TiviTextPrimary,
                letterSpacing = 1.sp
            )

            Text(
                text = AppConfig.APP_TAGLINE,
                fontSize = 14.sp,
                color = TiviTextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Error Banner
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TiviAccentLive.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
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

            // Input: Kullanıcı Adı
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
                    unfocusedContainerColor = TiviCard
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Input: Şifre
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
                    unfocusedContainerColor = TiviCard
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

            Spacer(modifier = Modifier.height(18.dp))

            // GÜVENLİ BAĞLANTI (HTTPS / HTTP) SEÇENEĞİ
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (useInsecureConnection) TiviGold.copy(alpha = 0.12f) else TiviCard
                ),
                shape = RoundedCornerShape(14.dp),
                border = if (useInsecureConnection) androidx.compose.foundation.BorderStroke(1.dp, TiviGold) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { useInsecureConnection = !useInsecureConnection }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (useInsecureConnection) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (useInsecureConnection) TiviGold else TiviPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Güvenli bağlantı kullanma",
                                color = TiviTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (useInsecureConnection) {
                                "http://mntrdns.com (HTTP - Hızlı / Eski Paneller)"
                            } else {
                                "https://mntrdns.com (HTTPS - Şifreli ve Güvenli)"
                            },
                            color = if (useInsecureConnection) TiviGold else TiviTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Switch(
                        checked = useInsecureConnection,
                        onCheckedChange = { useInsecureConnection = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TiviGold,
                            checkedTrackColor = TiviGold.copy(alpha = 0.35f),
                            uncheckedThumbColor = TiviPrimary,
                            uncheckedTrackColor = TiviPrimary.copy(alpha = 0.25f)
                        )
                    )
                }
            }

            // Opsiyonel Farklı Sunucu Girişi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showCustomServerInput = !showCustomServerInput }) {
                    Text(
                        text = if (showCustomServerInput) "Gömülü Sunucuya Dön" else "Farklı Sunucu Gir",
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
                    placeholder = { Text("http://ornek-sunucu.com:8080") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Dns, contentDescription = null, tint = TiviSecondary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TiviSecondary,
                        unfocusedBorderColor = TiviCardBorder,
                        focusedContainerColor = TiviCard,
                        unfocusedContainerColor = TiviCard
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Giriş Yap Butonu
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.login(username, password, effectiveServerUrl)
                },
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TiviPrimary,
                    contentColor = TiviBackground,
                    disabledContainerColor = TiviCardBorder,
                    disabledContentColor = TiviTextMuted
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = TiviBackground,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Bağlanıyor...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "GİRİŞ YAP",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Demo Modu ile Test Et Butonu
            OutlinedButton(
                onClick = { viewModel.loginDemo() },
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TiviPrimary.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TiviPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayCircleOutline, contentDescription = null, tint = TiviPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hemen Dene (Canlı Demo Modu)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Destek Bilgisi
            Text(
                text = "Aboneliğiniz yoksa veya şifrenizi unuttuysanız servis sağlayıcınız ile iletişime geçin.",
                fontSize = 12.sp,
                color = TiviTextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
