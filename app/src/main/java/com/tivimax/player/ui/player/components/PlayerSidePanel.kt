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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import kotlinx.coroutines.delay

/**
 * Oynatıcı içi ortak kompakt yan/alt obsidyen panel kabı.
 *
 * - Yatayda: Sağdan 348dp, tam yükseklik, video solda görünmeye devam eder.
 * - Dikeyde: Alttan maksimum %58 yükseklik.
 * - Karartma (scrim): Dokunulduğunda paneli kapatır, D-pad odak tuzağı oluşturmaz.
 * - Pencere içi yerleşim: Durum çubuğu çıkmaz, tam ekran modu bozulmaz.
 * - BackHandler ile geri tuşu paneli kapatır.
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
    headerIcon: ImageVector? = null,
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
            repeat(4) {
                delay(60)
                val ok = runCatching { focusToUse.requestFocus() }.isSuccess
                if (ok) return@LaunchedEffect
            }
        }
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .displayCutoutPadding()
    ) {
        // Karartma / Scrim (Dokunmada kapatır; .clickable yerine pointerInput ile D-pad odak tuzağı önlenir)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VegaColors.Scrim)
                .pointerInput(onClose) {
                    detectTapGestures { onClose() }
                }
        )

        // Obsidyen Panel Gövdesi
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
                modifier = (if (isPortrait) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeightDp * 0.58f)
                } else {
                    Modifier
                        .width(348.dp)
                        .fillMaxHeight()
                })
                    .pointerInput(Unit) {
                        detectTapGestures { /* Dokunmayı panel içinde yut, D-pad odak hedefi oluşturma */ }
                    },
                shape = if (isPortrait) {
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                } else {
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                },
                color = VegaColors.OverlayPanel,
                border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.55f)),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    if (isPortrait) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 10.dp)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(VegaShapes.Pill)
                                .background(Color.White.copy(alpha = 0.22f))
                        )
                    }

                    // ÜST BAR: İkon, Başlık ve Çerçevesiz Kapat Düğmesi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (headerIcon != null) {
                                Icon(
                                    imageVector = headerIcon,
                                    contentDescription = null,
                                    tint = VegaColors.Accent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Text(
                                text = title,
                                fontSize = VegaText.SectionTitle,
                                fontWeight = FontWeight.SemiBold,
                                color = VegaColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (headerTrailingContent != null) {
                                headerTrailingContent()
                            }

                            var isCloseFocused by remember { mutableStateOf(false) }
                            Surface(
                                shape = VegaShapes.Pill,
                                color = if (isCloseFocused) VegaColors.Accent else Color.White.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .size(36.dp)
                                    .then(
                                        if (firstFocusRequester == null) {
                                            Modifier.focusRequester(focusToUse)
                                        } else Modifier
                                    )
                                    .dpadFocusable(
                                        shape = VegaShapes.Pill,
                                        focusedBorderColor = Color.White,
                                        scale = 1.05f,
                                        onClick = onClose,
                                        onFocusStateChange = { isCloseFocused = it }
                                    )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Kapat",
                                        tint = if (isCloseFocused) VegaColors.OnAccent else VegaColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = VegaColors.Border.copy(alpha = 0.45f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    content()
                }
            }
        }
    }
}
