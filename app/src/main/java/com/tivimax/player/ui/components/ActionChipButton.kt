package com.tivimax.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var isFocused by remember { mutableStateOf(false) }
    val effectiveTint = iconTint ?: when {
        isSelected || isFocused -> VegaColors.Accent
        else -> VegaColors.TextSecondary
    }
    val containerColor = when {
        isSelected && isFocused -> VegaColors.Accent.copy(alpha = 0.24f)
        isSelected -> VegaColors.Accent.copy(alpha = 0.16f)
        isFocused -> VegaColors.SurfaceElevated
        else -> VegaColors.Surface
    }

    Surface(
        shape = VegaShapes.Small,
        color = containerColor,
        border = if (!isFocused) {
            BorderStroke(
                0.75.dp,
                if (isSelected) VegaColors.Accent.copy(alpha = 0.65f) else VegaColors.Border
            )
        } else null,
        modifier = modifier
            .height(34.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Small,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
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
                color = if (isSelected || isFocused) VegaColors.TextPrimary else VegaColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
