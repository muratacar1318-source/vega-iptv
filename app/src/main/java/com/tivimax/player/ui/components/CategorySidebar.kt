package com.tivimax.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.ui.design.VegaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class CategoryUiItem(
    val id: String,
    val name: String,
    val count: Int,
    val icon: ImageVector = Icons.Default.Folder,
    val iconTint: Color = VegaColors.Accent
)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CategorySidebar(
    categories: List<CategoryUiItem>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    title: String = "Kategoriler",
    totalCount: Int = 0,
    allLabel: String = "Tümü",
    onFilterClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    // G-045: TV'de kategori seçilince odak içeriğe geçer; sola dönünce seçili kategoriye gelir
    tvFocusBehavior: Boolean = false
) {
    val safeCategories = remember(categories) {
        categories
            .filter { it.id != "all" }
            .distinctBy { it.id }
    }
    val effectiveTotalCount = remember(categories, totalCount, safeCategories) {
        if (totalCount > 0) totalCount
        else categories.firstOrNull { it.id == "all" }?.count ?: safeCategories.sumOf { it.count }
    }

    // G-045: TV odak davranışı
    val focusManager = LocalFocusManager.current
    val focusScope = rememberCoroutineScope()
    var listHasFocus by remember { mutableStateOf(false) }
    val selectedRequester = remember { FocusRequester() }
    var selectedItemAttached by remember { mutableStateOf(false) }
    val selectCategory: (String) -> Unit = { id ->
        onCategorySelected(id)
        if (tvFocusBehavior) {
            focusScope.launch {
                delay(300)
                repeat(6) {
                    if (!listHasFocus) return@launch
                    focusManager.moveFocus(FocusDirection.Right)
                    delay(250)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = 110.dp)
            .fillMaxWidth(0.31f)
            .background(VegaColors.Background)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        // Üst Başlık: "Kategoriler" / "Türler" + Filtre İkonu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = VegaColors.TextSecondary,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onFilterClick != null) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filtrele",
                    tint = VegaColors.TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onFilterClick() }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filtre",
                    tint = VegaColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { listHasFocus = it.hasFocus }
                .then(
                    if (tvFocusBehavior) {
                        Modifier.focusProperties {
                            onEnter = {
                                if (selectedItemAttached) {
                                    try { selectedRequester.requestFocus() } catch (_: Exception) {}
                                }
                            }
                        }
                    } else {
                        Modifier.focusRestorer()
                    }
                ),
            contentPadding = PaddingValues(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "sidebar_cat_all") {
                val isSelected = selectedCategoryId == "all" || selectedCategoryId.isEmpty()
                SelectedCategoryAnchor(
                    isSelected = isSelected,
                    requester = selectedRequester,
                    onAttachedChange = { selectedItemAttached = it }
                ) {
                    CategoryItem(
                        name = allLabel,
                        count = effectiveTotalCount,
                        icon = Icons.Default.Folder,
                        iconTint = VegaColors.Accent,
                        isSelected = isSelected,
                        onClick = { selectCategory("all") }
                    )
                }
            }

            items(safeCategories, key = { "sidebar_cat_${it.id}" }) { cat ->
                val isSelected = cat.id == selectedCategoryId
                SelectedCategoryAnchor(
                    isSelected = isSelected,
                    requester = selectedRequester,
                    onAttachedChange = { selectedItemAttached = it }
                ) {
                    CategoryItem(
                        name = cat.name,
                        count = cat.count,
                        icon = cat.icon,
                        iconTint = cat.iconTint,
                        isSelected = isSelected,
                        onClick = { selectCategory(cat.id) }
                    )
                }
            }
        }
    }
}

/**
 * G-045: Seçili kategoriye odak bağlantısı. Yapı her durumda aynı kalır (sadece modifier değişir),
 * böylece seçim değişince öğe yeniden oluşturulmaz ve odak kaybolmaz.
 */
@Composable
private fun SelectedCategoryAnchor(
    isSelected: Boolean,
    requester: FocusRequester,
    onAttachedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    DisposableEffect(isSelected) {
        if (isSelected) onAttachedChange(true)
        onDispose {
            if (isSelected) onAttachedChange(false)
        }
    }
    Box(modifier = if (isSelected) Modifier.focusRequester(requester) else Modifier) {
        content()
    }
}
