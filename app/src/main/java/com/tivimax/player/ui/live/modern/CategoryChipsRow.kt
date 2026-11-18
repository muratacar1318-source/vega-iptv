package com.tivimax.player.ui.live.modern

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText

/**
 * Sade yatay kategori filtre sekmeleri:
 * Seçili sekmede yumuşak tonal vurgu dolgusu, pasif sekmelerde sakin hap yüzey (ağır parlak çerçeveler yok).
 */
@Composable
fun CategoryChipsRow(
    categories: List<LiveCategory>,
    selectedCategory: String,
    totalChannelsCount: Int,
    favoriteChannelsCount: Int,
    onCategorySelected: (String) -> Unit,
    onOpenAllCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val contentCategories = remember(categories) {
        categories
            .filter { it.categoryId != "all" && it.categoryId != "favorites" }
            .distinctBy { it.categoryId }
    }

    LaunchedEffect(selectedCategory, contentCategories) {
        if (selectedCategory == "all") {
            listState.animateScrollToItem(0)
        } else if (selectedCategory == "favorites") {
            listState.animateScrollToItem(1)
        } else {
            val index = contentCategories.indexOfFirst { it.categoryId == selectedCategory }
            if (index >= 0) {
                listState.animateScrollToItem((index + 2).coerceAtLeast(0))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(VegaColors.Background.copy(alpha = 0.94f))
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = "cat_all") {
                CategoryChipItem(
                    title = "Tümü",
                    count = totalChannelsCount,
                    isSelected = selectedCategory == "all",
                    leadingIcon = null,
                    onClick = { onCategorySelected("all") }
                )
            }

            item(key = "cat_favorites") {
                val isSelected = selectedCategory == "favorites"
                CategoryChipItem(
                    title = "Favoriler",
                    count = favoriteChannelsCount,
                    isSelected = isSelected,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (isSelected) VegaColors.Accent else VegaColors.Gold,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    onClick = { onCategorySelected("favorites") }
                )
            }

            itemsIndexed(
                items = contentCategories,
                key = { index, cat -> "chip_${cat.categoryId}_$index" }
            ) { _, category ->
                CategoryChipItem(
                    title = category.categoryName,
                    count = category.itemCount,
                    isSelected = selectedCategory == category.categoryId,
                    leadingIcon = null,
                    onClick = { onCategorySelected(category.categoryId) }
                )
            }

            item(key = "btn_all_categories") {
                var isFocused by remember { mutableStateOf(false) }
                Surface(
                    shape = VegaShapes.Pill,
                    color = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated.copy(alpha = 0.72f),
                    modifier = Modifier
                        .height(34.dp)
                        .dpadFocusable(
                            focusedBorderColor = VegaColors.FocusBorder,
                            shape = VegaShapes.Pill,
                            scale = 1.03f,
                            onClick = onOpenAllCategories,
                            onFocusStateChange = { isFocused = it }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Kategoriler",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Tüm kategorileri gör",
                            tint = VegaColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = VegaColors.Border.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun CategoryChipItem(
    title: String,
    count: Int,
    isSelected: Boolean,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> VegaColors.Accent.copy(alpha = 0.18f)
            isFocused -> VegaColors.SurfaceVariant
            else -> VegaColors.SurfaceElevated.copy(alpha = 0.72f)
        },
        animationSpec = tween(160),
        label = "chip_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> VegaColors.Accent
            isFocused -> VegaColors.TextPrimary
            else -> VegaColors.TextSecondary
        },
        animationSpec = tween(160),
        label = "chip_fg"
    )

    Surface(
        shape = VegaShapes.Pill,
        color = backgroundColor,
        modifier = Modifier
            .height(34.dp)
            .wrapContentWidth()
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Pill,
                scale = 1.03f,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            leadingIcon?.invoke()

            Text(
                text = title,
                color = contentColor,
                fontSize = VegaText.Secondary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )

            if (count > 0) {
                Text(
                    text = if (count > 999) "${count / 1000}k" else count.toString(),
                    color = if (isSelected) VegaColors.Accent.copy(alpha = 0.8f) else VegaColors.TextMuted,
                    fontSize = VegaText.Caption,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
