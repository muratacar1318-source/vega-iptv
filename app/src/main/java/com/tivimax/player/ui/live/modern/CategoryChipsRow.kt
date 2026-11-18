package com.tivimax.player.ui.live.modern

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.ui.components.dpadFocusable
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText

/**
 * Üstte yatay kaydırılabilir yapışkan kategori çipleri satırı (StreamVault görsel dili).
 * Her çip tek odak ve tıklama hedefine sahiptir.
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

    LaunchedEffect(selectedCategory) {
        if (selectedCategory == "all") {
            listState.animateScrollToItem(0)
        } else if (selectedCategory == "favorites") {
            listState.animateScrollToItem(1)
        } else {
            val index = categories.indexOfFirst { it.categoryId == selectedCategory }
            if (index >= 0) {
                listState.animateScrollToItem((index + 2).coerceAtLeast(0))
            }
        }
    }

    Surface(
        color = VegaColors.Background.copy(alpha = 0.92f),
        modifier = modifier.fillMaxWidth()
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. "Tümü" Çipi
            item(key = "cat_all") {
                val isSelected = selectedCategory == "all"
                CategoryChipItem(
                    title = "Tümü",
                    count = totalChannelsCount,
                    isSelected = isSelected,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    onClick = { onCategorySelected("all") }
                )
            }

            // 2. "Favoriler" Çipi
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
                            tint = VegaColors.Gold,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    onClick = { onCategorySelected("favorites") }
                )
            }

            // 3. İçerik Kategorileri
            items(
                items = categories
                    .filter { it.categoryId != "all" && it.categoryId != "favorites" }
                    .distinctBy { it.categoryId },
                key = { "chip_${it.categoryId}" }
            ) { category ->
                val isSelected = selectedCategory == category.categoryId
                CategoryChipItem(
                    title = category.categoryName,
                    count = category.itemCount,
                    isSelected = isSelected,
                    leadingIcon = null,
                    onClick = { onCategorySelected(category.categoryId) }
                )
            }

            // 4. "Tümü" Açılır Liste Butonu (Tek odak hedefli)
            item(key = "btn_all_categories") {
                var isFocused by remember { mutableStateOf(false) }
                Surface(
                    shape = VegaShapes.Pill,
                    color = if (isFocused) VegaColors.SurfaceVariant else VegaColors.SurfaceElevated,
                    border = if (isFocused) null else BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .height(36.dp)
                        .dpadFocusable(
                            focusedBorderColor = VegaColors.FocusBorder,
                            shape = VegaShapes.Pill,
                            scale = 1.03f,
                            onClick = onOpenAllCategories,
                            onFocusStateChange = { isFocused = it }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Tümü",
                            color = VegaColors.Accent,
                            fontSize = VegaText.Secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Tüm kategorileri gör",
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
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
            isFocused -> VegaColors.SurfaceVariant
            isSelected -> VegaColors.Accent.copy(alpha = 0.18f)
            else -> VegaColors.SurfaceElevated
        },
        animationSpec = tween(180),
        label = "chip_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) VegaColors.Accent else VegaColors.TextSecondary,
        animationSpec = tween(180),
        label = "chip_fg"
    )

    Surface(
        shape = VegaShapes.Pill,
        color = backgroundColor,
        border = if (isFocused) null else BorderStroke(
            width = 1.dp,
            color = if (isSelected) VegaColors.Accent else VegaColors.Border
        ),
        modifier = Modifier
            .height(36.dp)
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
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            leadingIcon?.invoke()

            Text(
                text = title,
                color = contentColor,
                fontSize = VegaText.Secondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )

            if (count > 0) {
                Box(
                    modifier = Modifier
                        .clip(VegaShapes.Pill)
                        .background(
                            if (isSelected) VegaColors.Accent.copy(alpha = 0.22f)
                            else VegaColors.SurfaceAccent
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (count > 999) "${count / 1000}k" else count.toString(),
                        color = if (isSelected) VegaColors.Accent else VegaColors.TextMuted,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
