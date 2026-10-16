package com.tivimax.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ActionChipButton(
    text: String,
    icon: ImageVector? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color? = null
) {
    val effectiveTint = iconTint ?: if (isSelected) Color(0xFF00E5FF) else Color.White
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF7C4DFF).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.07f),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.15f)
        ),
        modifier = modifier
            .height(34.dp)
            .dpadFocusable(
                focusedBorderColor = Color(0xFF00E5FF),
                shape = RoundedCornerShape(10.dp),
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
                    tint = effectiveTint,
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
