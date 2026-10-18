package com.tivimax.player.ui.player.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaSizes
import com.tivimax.player.ui.design.VegaText
import kotlinx.coroutines.delay

/**
 * Oynatıcı içi ortak kompakt yan/alt panel kabı (G-023).
 *
 * - Yatayda: Sağdan 340dp, tam yükseklik, video solda görünmeye devam eder.
 * - Dikeyde: Alttan maksimum %55 yükseklik.
 * - Karartma (scrim): Dokunulduğunda paneli kapatır.
 * - Pencere içi yerleşim: Durum çubuğu (saat/pil) çıkmaz, immersive mod bozulmaz.
 * - BackHandler ile geri tuşu kapatır.
 * - İlk açılışta kapatma / odak kontrolü.
 */
@Composable
fun PlayerSidePanel(
    visible: Boolean,
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isPortrait: Boolean,
    firstFocusRequester: FocusRequester? = null,
    headerTrailingContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible) return

    BackHandler(enabled = visible) {
        onClose()
    }

    val defaultFocus = remember { FocusRequester() }
    val focusToUse = firstFocusRequester ?: defaultFocus

    LaunchedEffect(visible) {
        if (visible) {
            delay(100)
            runCatching { focusToUse.requestFocus() }
        }
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .displayCutoutPadding()
    ) {
        // Karartma / Scrim (Dış alana dokununca kapat)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VegaColors.Scrim)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onClose()
                }
        )

        // Panel Gövdesi
        AnimatedVisibility(
            visible = visible,
            enter = if (isPortrait) {
                slideInVertically(initialOffsetY = { it }) + fadeIn()
            } else {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            },
            exit = if (isPortrait) {
                slideOutVertically(targetOffsetY = { it }) + fadeOut()
            } else {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            },
            modifier = Modifier.align(if (isPortrait) Alignment.BottomCenter else Alignment.CenterEnd)
        ) {
            Surface(
                modifier = if (isPortrait) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeightDp * 0.55f)
                } else {
                    Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* Dokunmayı panel içinde yut */ },
                shape = if (isPortrait) {
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                } else {
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                },
                color = VegaColors.OverlayPanel,
                border = BorderStroke(1.dp, VegaColors.Border),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // ÜST BAR: Başlık ve Kapat Butonu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            fontSize = VegaText.SectionTitle,
                            fontWeight = FontWeight.Bold,
                            color = VegaColors.TextPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (headerTrailingContent != null) {
                                headerTrailingContent()
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            var isCloseFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(VegaSizes.IconButton)
                                    .onFocusChanged { isCloseFocused = it.isFocused }
                                    .then(
                                        if (firstFocusRequester == null) {
                                            Modifier.focusRequester(focusToUse)
                                        } else Modifier
                                    )
                                    .then(
                                        if (isCloseFocused) {
                                            Modifier.border(2.dp, VegaColors.Accent, CircleShape)
                                        } else Modifier
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kapat",
                                    tint = if (isCloseFocused) VegaColors.Accent else VegaColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    content()
                }
            }
        }
    }
}
