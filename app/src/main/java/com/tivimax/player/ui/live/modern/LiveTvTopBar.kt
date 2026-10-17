package com.tivimax.player.ui.live.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.theme.*
import com.tivimax.player.ui.components.dpadFocusable

/**
 * Modern ve Kompakt Canlı TV Üst Çubuğu.
 * Arama alanı, yenileme butonu, görünüm modu değiştirici (Kompakt / Izgara / Detaylı),
 * sıralama BottomSheet butonu ve raf düzeni geçişini barındırır.
 */
@Composable
fun LiveTvTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    currentViewMode: String,
    onViewModeToggle: () -> Unit,
    onOpenSortSheet: () -> Unit,
    onRefresh: () -> Unit,
    onToggleShelfLayout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Surface(
        color = TiviBackground,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Arama Alanı (Kanal veya Program Ara) - Metin kırpılmasını önleyen BasicTextField tabanlı arama kutusu
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = TiviCard,
                border = BorderStroke(1.dp, TiviCardBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Ara",
                        tint = OpusCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Kanal veya program ara...",
                                color = TiviTextMuted,
                                fontSize = 12.5.sp,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 12.5.sp
                            ),
                            cursorBrush = SolidColor(OpusCyan),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Temizle",
                                tint = TiviTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Sağ Taraf Eylemleri: Yenile, Görünüm Modu, Sıralama, Raf Düzenine Geç
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Görünüm Modu Değiştirici Buton (Kompakt / Izgara / Detaylı)
                val (modeIcon, modeDesc) = when (currentViewMode) {
                    UserPreferences.LIVE_VIEW_GRID -> Pair(Icons.Default.GridView, "Izgara Görünümü")
                    UserPreferences.LIVE_VIEW_DETAILED -> Pair(Icons.Default.TableRows, "Detaylı Görünüm")
                    else -> Pair(Icons.AutoMirrored.Filled.ViewList, "Kompakt Liste")
                }

                IconButton(
                    onClick = onViewModeToggle,
                    modifier = Modifier
                        .size(38.dp)
                        .dpadFocusable(
                            focusedBorderColor = OpusCyan,
                            shape = RoundedCornerShape(10.dp),
                            onClick = onViewModeToggle
                        )
                ) {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = modeDesc,
                        tint = OpusCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Sıralama Butonu (BottomSheet Açar)
                IconButton(
                    onClick = onOpenSortSheet,
                    modifier = Modifier
                        .size(38.dp)
                        .dpadFocusable(
                            focusedBorderColor = OpusCyan,
                            shape = RoundedCornerShape(10.dp),
                            onClick = onOpenSortSheet
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sırala",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Yenile Butonu
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(38.dp)
                        .dpadFocusable(
                            focusedBorderColor = OpusCyan,
                            shape = RoundedCornerShape(10.dp),
                            onClick = onRefresh
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Kanal Listesini Yenile",
                        tint = TiviTextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Raf Düzenine Hızlı Geçiş Butonu
                IconButton(
                    onClick = onToggleShelfLayout,
                    modifier = Modifier
                        .size(38.dp)
                        .dpadFocusable(
                            focusedBorderColor = OpusCyan,
                            shape = RoundedCornerShape(10.dp),
                            onClick = onToggleShelfLayout
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewCarousel,
                        contentDescription = "Raf Düzenine Geç",
                        tint = OpusCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
