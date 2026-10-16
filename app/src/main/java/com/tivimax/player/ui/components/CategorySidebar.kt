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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CategoryUiItem(
    val id: String,
    val name: String,
    val count: Int,
    val icon: ImageVector = Icons.Default.Folder,
    val iconTint: Color = Color(0xFF7C4DFF)
)

@Composable
fun CategorySidebar(
    categories: List<CategoryUiItem>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    title: String = "KATEGORİLER",
    onFilterClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.30f)
            .background(Color(0xFF0E1022))
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        // Üst Başlık: "KATEGORİLER" / "TÜRLER" + Filtre İkonu
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
                color = Color(0xFF8E92AA),
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onFilterClick != null) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filtrele",
                    tint = Color(0xFF8E92AA),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onFilterClick() }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filtre",
                    tint = Color(0xFF8E92AA),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(categories, key = { it.id }) { cat ->
                val isSelected = cat.id == selectedCategoryId
                CategoryItem(
                    name = cat.name,
                    count = cat.count,
                    icon = cat.icon,
                    iconTint = cat.iconTint,
                    isSelected = isSelected,
                    onClick = { onCategorySelected(cat.id) }
                )
            }
        }
    }
}

@Composable
fun ActionChipButton(
    text: String,
    icon: ImageVector? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF7C4DFF).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.07f),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.15f)
        ),
        modifier = modifier
            .height(34.dp)
            .dpadFocusable(
                shape = RoundedCornerShape(10.dp),
                focusedBorderColor = Color(0xFF00E5FF),
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = if (isSelected) Color(0xFF00E5FF) else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                color = if (isSelected) Color.White else Color(0xFFE0E0E0),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
