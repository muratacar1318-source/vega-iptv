package com.tivimax.player.ui.live.modern

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.theme.*
import com.tivimax.player.ui.components.dpadFocusable

/**
 * Üstte yatay kaydırılabilir yapışkan (sticky) kategori çipleri satırı.
 * - Metinler kırpılmaz, çip genişliği içeriğe göre büyür.
 * - Seçili çip OpusCyan vurgulu, kanal sayısı rozet olarak içinde.
 * - Sonda "Tümü ▾" butonu bulunur ve tam kategori BottomSheet'ini açar.
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

    // Seçilen kategori değiştiğinde ilgili çipe kaydır
    LaunchedEffect(selectedCategory) {
        if (selectedCategory == "all") {
            listState.animateScrollToItem(0)
        } else if (selectedCategory == "favorites") {
            listState.animateScrollToItem(1)
        } else {
            val index = categories.indexOfFirst { it.categoryId == selectedCategory }
            if (index >= 0) {
                // +2 çünkü "all" ve "favorites" başta
                listState.animateScrollToItem((index + 2).coerceAtLeast(0))
            }
        }
    }

    Surface(
        color = TiviBackground,
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
                            tint = if (isSelected) Color.Black else OpusCyan,
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
                            tint = if (isSelected) Color.Black else TiviGold,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    onClick = { onCategorySelected("favorites") }
                )
            }

            // 3. İçerik Kategorileri (Haber, Belgesel, Sinema...)
            items(
                items = categories.filter { it.categoryId != "all" && it.categoryId != "favorites" },
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

            // 4. "Tümü ▾" Butonu (BottomSheet Açıcı)
            item(key = "btn_all_categories") {
                Surface(
                    onClick = onOpenAllCategories,
                    shape = RoundedCornerShape(20.dp),
                    color = TiviSurfaceVariant,
                    border = BorderStroke(1.dp, OpusCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .height(36.dp)
                        .dpadFocusable(
                            focusedBorderColor = OpusCyan,
                            shape = RoundedCornerShape(20.dp),
                            onClick = onOpenAllCategories
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Tümü",
                            color = OpusCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Tüm Kategorileri Gör",
                            tint = OpusCyan,
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
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) OpusCyan else TiviCard,
        animationSpec = tween(180),
        label = "chip_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.Black else Color.White,
        animationSpec = tween(180),
        label = "chip_fg"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) OpusCyan else TiviCardBorder
        ),
        modifier = Modifier
            .height(36.dp)
            .wrapContentWidth()
            .dpadFocusable(
                focusedBorderColor = OpusCyan,
                shape = RoundedCornerShape(20.dp),
                onClick = onClick
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
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )

            if (count > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) Color.Black.copy(alpha = 0.2f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (count > 999) "${count / 1000}k" else count.toString(),
                        color = contentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
