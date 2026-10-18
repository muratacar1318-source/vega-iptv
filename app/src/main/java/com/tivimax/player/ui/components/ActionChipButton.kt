package com.tivimax.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes

@Composable
fun ActionChipButton(
    text: String,
    icon: ImageVector? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color? = null
) {
    val effectiveTint = iconTint ?: if (isSelected) VegaColors.Accent else VegaColors.TextPrimary
    Surface(
        shape = VegaShapes.Medium,
        color = if (isSelected) VegaColors.Accent.copy(alpha = 0.2f) else VegaColors.Card,
        border = BorderStroke(
            1.dp,
            if (isSelected) VegaColors.Accent else VegaColors.Border
        ),
        modifier = modifier
            .height(34.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
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
                color = if (isSelected) VegaColors.TextPrimary else VegaColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
