package com.tivimax.player.ui.devicemode

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.design.*
import com.tivimax.player.util.DeviceMode
import com.tivimax.player.util.detectRecommendedDeviceMode
import kotlinx.coroutines.delay

/**
 * İlk Açılış Cihaz Modu Seçim Ekranı (Telefon / Tablet / TV).
 * Cihaz türünü otomatik tespit edip önerir, tek odaklı tam D-pad kumanda ve dokunmatik desteği sunar.
 */
@Composable
fun DeviceModeSetupScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = isLandscape || configuration.screenWidthDp >= 600

    val recommendedMode = remember { context.detectRecommendedDeviceMode() }
    var selectedMode by remember { mutableStateOf(recommendedMode) }

    // TV kumandası için ilk kart focus requester
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200)
        initialFocusRequester.requestFocusSafely()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .vegaBackdrop()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 920.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Başlık ve Logo
            Surface(
                shape = CircleShape,
                color = VegaColors.Accent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.36f)),
                modifier = Modifier.size(58.dp)
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
                fontSize = VegaText.ScreenTitle,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.4.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.device_mode_subtitle),
                color = VegaColors.TextSecondary,
                fontSize = VegaText.Secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(26.dp))

            // ÜÇ SEÇİM KARTI (Telefon / Tablet / TV)
            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeviceModeCard(
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DeviceModeCard(
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

            Spacer(modifier = Modifier.height(26.dp))

            // DEVAM BUTONU (Tek odak hedefi — VegaButton)
            VegaButton(
                text = stringResource(R.string.device_mode_continue),
                variant = VegaButtonVariant.PRIMARY,
                onClick = {
                    val infoMsg = context.getString(R.string.device_mode_info)
                    Toast.makeText(context, infoMsg, Toast.LENGTH_LONG).show()
                    viewModel.onDeviceModeSetupCompleted(selectedMode)
                },
                modifier = Modifier.fillMaxWidth(if (isWideScreen) 0.42f else 1f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Bilgi Metni
            Text(
                text = stringResource(R.string.device_mode_info),
                color = VegaColors.TextMuted,
                fontSize = VegaText.Caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

/**
 * Cihaz Seçim Kartı (Tek odaklı, StreamVault yüzey derinliği, seçili vurgu ve önerilen rozeti).
 */
@Composable
private fun DeviceModeCard(
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

    val backgroundColor = when {
        isSelected && isFocused -> VegaColors.Accent.copy(alpha = 0.20f)
        isSelected -> VegaColors.Accent.copy(alpha = 0.13f)
        isFocused -> VegaColors.SurfaceElevated
        else -> VegaColors.Surface.copy(alpha = 0.92f)
    }

    Surface(
        shape = VegaShapes.Large,
        color = backgroundColor,
        border = if (!isFocused) {
            BorderStroke(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) VegaColors.Accent else VegaColors.Border
            )
        } else null,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
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
                    color = if (isSelected) VegaColors.Accent else VegaColors.SurfaceVariant,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) VegaColors.OnAccent else VegaColors.TextPrimary,
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
                                fontSize = VegaText.Caption,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Seçim Çemberi
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) VegaColors.Accent else Color.Transparent)
                            .border(
                                1.5.dp,
                                if (isSelected) VegaColors.Accent else VegaColors.Border,
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
                fontSize = VegaText.SectionTitle,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                color = VegaColors.TextSecondary,
                fontSize = VegaText.Secondary,
                lineHeight = 18.sp
            )
        }
    }
}
