package com.tivimax.player.ui.devicemode

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.R
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.theme.*
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.util.DeviceMode
import com.tivimax.player.util.detectRecommendedDeviceMode
import kotlinx.coroutines.delay

import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes

/**
 * İlk Açılış Cihaz Modu Seçim Ekranı (Telefon / Tablet / TV).
 * Cihaz türünü otomatik tespit edip önerir, tam D-pad kumanda ve dokunmatik desteği sunar.
 */
@Composable
fun DeviceModeSetupScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = isLandscape || configuration.screenWidthDp >= 600

    val recommendedMode = remember { context.detectRecommendedDeviceMode() }
    var selectedMode by remember { mutableStateOf(recommendedMode) }

    // TV kumandası için ilk kart focus requester
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200)
        try {
            initialFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VegaColors.Background),
        contentAlignment = Alignment.Center
    ) {
        // Arka plan gradyan ışıltıları
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(VegaColors.Accent.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.BottomEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(VegaColors.Accent.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Başlık & Logo
            Surface(
                shape = CircleShape,
                color = VegaColors.Accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.35f)),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.device_mode_title),
                color = VegaColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.device_mode_subtitle),
                color = VegaColors.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ÜÇ SEÇİM KARTI (Telefon / Tablet / TV)
            if (isWideScreen) {
                // Geniş Ekran / Yatay TV / Tablet: Yan yana 3 sütun
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeviceModeCard(
                        mode = DeviceMode.PHONE,
                        icon = Icons.Default.PhoneAndroid,
                        title = stringResource(R.string.device_mode_phone),
                        description = stringResource(R.string.device_mode_phone_desc),
                        isRecommended = recommendedMode == DeviceMode.PHONE,
                        isSelected = selectedMode == DeviceMode.PHONE,
                        onSelect = { selectedMode = DeviceMode.PHONE },
                        focusRequester = if (recommendedMode == DeviceMode.PHONE) initialFocusRequester else null,
                        modifier = Modifier.weight(1f)
                    )

                    DeviceModeCard(
                        mode = DeviceMode.TABLET,
                        icon = Icons.Default.TabletAndroid,
                        title = stringResource(R.string.device_mode_tablet),
                        description = stringResource(R.string.device_mode_tablet_desc),
                        isRecommended = recommendedMode == DeviceMode.TABLET,
                        isSelected = selectedMode == DeviceMode.TABLET,
                        onSelect = { selectedMode = DeviceMode.TABLET },
                        focusRequester = if (recommendedMode == DeviceMode.TABLET) initialFocusRequester else null,
                        modifier = Modifier.weight(1f)
                    )

                    DeviceModeCard(
                        mode = DeviceMode.TV,
                        icon = Icons.Default.Tv,
                        title = stringResource(R.string.device_mode_tv),
                        description = stringResource(R.string.device_mode_tv_desc),
                        isRecommended = recommendedMode == DeviceMode.TV,
                        isSelected = selectedMode == DeviceMode.TV,
                        onSelect = { selectedMode = DeviceMode.TV },
                        focusRequester = if (recommendedMode == DeviceMode.TV) initialFocusRequester else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Dikey Telefon: Alt alta 3 kart
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DeviceModeCard(
                        mode = DeviceMode.PHONE,
                        icon = Icons.Default.PhoneAndroid,
                        title = stringResource(R.string.device_mode_phone),
                        description = stringResource(R.string.device_mode_phone_desc),
                        isRecommended = recommendedMode == DeviceMode.PHONE,
                        isSelected = selectedMode == DeviceMode.PHONE,
                        onSelect = { selectedMode = DeviceMode.PHONE },
                        focusRequester = if (recommendedMode == DeviceMode.PHONE) initialFocusRequester else null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    DeviceModeCard(
                        mode = DeviceMode.TABLET,
                        icon = Icons.Default.TabletAndroid,
                        title = stringResource(R.string.device_mode_tablet),
                        description = stringResource(R.string.device_mode_tablet_desc),
                        isRecommended = recommendedMode == DeviceMode.TABLET,
                        isSelected = selectedMode == DeviceMode.TABLET,
                        onSelect = { selectedMode = DeviceMode.TABLET },
                        focusRequester = if (recommendedMode == DeviceMode.TABLET) initialFocusRequester else null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    DeviceModeCard(
                        mode = DeviceMode.TV,
                        icon = Icons.Default.Tv,
                        title = stringResource(R.string.device_mode_tv),
                        description = stringResource(R.string.device_mode_tv_desc),
                        isRecommended = recommendedMode == DeviceMode.TV,
                        isSelected = selectedMode == DeviceMode.TV,
                        onSelect = { selectedMode = DeviceMode.TV },
                        focusRequester = if (recommendedMode == DeviceMode.TV) initialFocusRequester else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // DEVAM BUTONU
            Button(
                onClick = {
                    val infoMsg = context.getString(R.string.device_mode_info)
                    Toast.makeText(context, infoMsg, Toast.LENGTH_LONG).show()
                    viewModel.onDeviceModeSetupCompleted(selectedMode)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VegaColors.Accent,
                    contentColor = VegaColors.OnAccent
                ),
                shape = VegaShapes.Medium,
                modifier = Modifier
                    .fillMaxWidth(if (isWideScreen) 0.45f else 1f)
                    .height(48.dp)
                    .dpadFocusable(
                        focusedBorderColor = Color.White,
                        shape = VegaShapes.Medium,
                        onClick = {
                            val infoMsg = context.getString(R.string.device_mode_info)
                            Toast.makeText(context, infoMsg, Toast.LENGTH_LONG).show()
                            viewModel.onDeviceModeSetupCompleted(selectedMode)
                        }
                    )
            ) {
                Text(
                    text = stringResource(R.string.device_mode_continue),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bilgi Metni
            Text(
                text = stringResource(R.string.device_mode_info),
                color = VegaColors.TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

/**
 * Cihaz Seçim Kartı (D-pad Focusable, Seçili Durum Glow & Önerilen Rozeti).
 */
@Composable
private fun DeviceModeCard(
    mode: DeviceMode,
    icon: ImageVector,
    title: String,
    description: String,
    isRecommended: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor = when {
        isFocused -> Color.White
        isSelected -> VegaColors.Accent
        else -> VegaColors.Border
    }

    val backgroundColor = when {
        isSelected -> VegaColors.Accent.copy(alpha = 0.12f)
        isFocused -> VegaColors.Card.copy(alpha = 0.9f)
        else -> VegaColors.Card.copy(alpha = 0.6f)
    }

    Surface(
        onClick = onSelect,
        shape = VegaShapes.Large,
        color = backgroundColor,
        border = BorderStroke(if (isSelected || isFocused) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .dpadFocusable(
                focusedBorderColor = Color.White,
                shape = VegaShapes.Large,
                scale = 1.04f,
                onClick = onSelect,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Üst Satır: İkon, Önerilen Rozeti ve Seçim İndikatörü
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = VegaShapes.Medium,
                    color = if (isSelected) VegaColors.Accent else Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) VegaColors.OnAccent else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRecommended) {
                        Box(
                            modifier = Modifier
                                .clip(VegaShapes.Small)
                                .background(VegaColors.Accent)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.device_mode_recommended),
                                color = VegaColors.OnAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Radio Check Circle
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) VegaColors.Accent else Color.Transparent)
                            .border(
                                1.5.dp,
                                if (isSelected) VegaColors.Accent else Color.White.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = VegaColors.OnAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                color = VegaColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                color = VegaColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 17.sp
            )
        }
    }
}
