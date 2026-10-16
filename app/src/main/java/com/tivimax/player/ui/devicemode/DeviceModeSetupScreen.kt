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
            .background(TiviBackground),
        contentAlignment = Alignment.Center
    ) {
        // Arka plan neon gradyan ışıltıları
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(OpusPurple.copy(alpha = 0.18f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.BottomEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(OpusCyan.copy(alpha = 0.15f), Color.Transparent)
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
                color = OpusCyan.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, OpusCyan.copy(alpha = 0.35f)),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = OpusCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.device_mode_title),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.device_mode_subtitle),
                color = TiviTextSecondary,
                fontSize = 13.5.sp,
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
                    containerColor = OpusCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth(if (isWideScreen) 0.45f else 1f)
                    .height(50.dp)
                    .dpadFocusable(
                        focusedBorderColor = Color.White,
                        shape = RoundedCornerShape(14.dp),
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
                color = TiviTextSecondary.copy(alpha = 0.75f),
                fontSize = 12.sp,
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
        isSelected -> OpusCyan
        else -> TiviCardBorder
    }

    val backgroundColor = when {
        isSelected -> OpusCyan.copy(alpha = 0.12f)
        isFocused -> TiviCard.copy(alpha = 0.9f)
        else -> TiviCard.copy(alpha = 0.6f)
    }

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(18.dp),
        color = backgroundColor,
        border = BorderStroke(if (isSelected || isFocused) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .dpadFocusable(
                focusedBorderColor = Color.White,
                shape = RoundedCornerShape(18.dp),
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
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) OpusCyan else Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.Black else Color.White,
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.horizontalGradient(listOf(OpusCyan, OpusPurple)))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.device_mode_recommended),
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Radio Check Circle
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) OpusCyan else Color.Transparent)
                            .border(
                                1.5.dp,
                                if (isSelected) OpusCyan else Color.White.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                color = TiviTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
