package com.tivimax.player.ui.login

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.BuildConfig
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.components.tvKeyboardOptions
import com.tivimax.player.ui.components.tvOpenKeyboardOnOk
import com.tivimax.player.ui.design.*
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.delay

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
    val keyboardController = LocalSoftwareKeyboardController.current
    val firstFieldFocusRequester = remember { FocusRequester() }

    val configuration = LocalConfiguration.current
    val isTv = LocalIsTvDevice.current
    val isWideScreen = isTv ||
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600

    LaunchedEffect(Unit) {
        for (attempt in 0 until 2) {
            withFrameNanos { }
            delay(60)
            if (firstFieldFocusRequester.requestFocusSafely()) break
        }
    }

    val canSubmit = !isLoading &&
        playlistName.isNotBlank() &&
        username.isNotBlank() &&
        password.isNotBlank() &&
        (!isCustomServer || customServerUrl.isNotBlank())

    val submitLogin: () -> Unit = {
        keyboardController?.hide()
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
        unfocusedContainerColor = VegaColors.Surface,
        focusedTextColor = VegaColors.TextPrimary,
        unfocusedTextColor = VegaColors.TextPrimary
    )

    val formContent: @Composable ColumnScope.() -> Unit = {
        // Minimal Geometrik Vega Amblemi
        Surface(
            shape = VegaShapes.Medium,
            color = VegaColors.SurfaceElevated,
            border = BorderStroke(0.75.dp, VegaColors.Border),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = VegaColors.Accent,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = AppConfig.APP_NAME,
            fontSize = VegaText.ScreenTitle,
            fontWeight = FontWeight.Bold,
            color = VegaColors.TextPrimary,
            letterSpacing = 0.3.sp
        )

        Text(
            text = "Oynatma listesiyle giriş yap",
            fontSize = VegaText.Secondary,
            color = VegaColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(visible = errorMessage != null) {
            errorMessage?.let { error ->
                Surface(
                    color = VegaColors.Danger.copy(alpha = 0.14f),
                    border = BorderStroke(0.75.dp, VegaColors.Danger.copy(alpha = 0.4f)),
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
                            tint = VegaColors.Danger,
                            modifier = Modifier.size(18.dp)
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
                    tint = VegaColors.TextSecondary
                )
            },
            colors = fieldColors,
            shape = VegaShapes.Medium,
            modifier = Modifier
                .tvOpenKeyboardOnOk()
                .focusRequester(firstFieldFocusRequester)
                .fillMaxWidth(),
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

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Sunucu",
                color = VegaColors.TextSecondary,
                fontSize = VegaText.Caption,
                fontWeight = FontWeight.Medium
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
                    placeholder = { Text("Sağlayıcından aldığın adres") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = VegaColors.TextSecondary
                        )
                    },
                    colors = fieldColors,
                    shape = VegaShapes.Medium,
                    modifier = Modifier
                        .tvOpenKeyboardOnOk()
                        .fillMaxWidth(),
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

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Kullanıcı adı") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = VegaColors.TextSecondary
                )
            },
            colors = fieldColors,
            shape = VegaShapes.Medium,
            modifier = Modifier
                .tvOpenKeyboardOnOk()
                .fillMaxWidth(),
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

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Şifre") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = VegaColors.TextSecondary
                )
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
            modifier = Modifier
                .tvOpenKeyboardOnOk()
                .fillMaxWidth(),
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

        Spacer(modifier = Modifier.height(22.dp))

        Surface(
            shape = VegaShapes.Medium,
            color = if (canSubmit) VegaColors.Accent else VegaColors.SurfaceVariant,
            contentColor = if (canSubmit) VegaColors.OnAccent else VegaColors.TextMuted,
            border = if (!canSubmit) BorderStroke(0.75.dp, VegaColors.Border) else null,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isWideScreen) 48.dp else 50.dp)
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
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
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
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .vegaBackdrop()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        if (isWideScreen) {
            Surface(
                shape = VegaShapes.Large,
                color = VegaColors.Surface,
                border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.7f)),
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = formContent
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                content = formContent
            )
        }
    }
}
