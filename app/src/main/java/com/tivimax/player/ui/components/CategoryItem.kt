package com.tivimax.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes

@Composable
fun CategoryItem(
    name: String,
    count: Int,
    iconTint: Color = VegaColors.Accent,
    icon: ImageVector = Icons.Default.Folder,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val containerColor = when {
        isSelected && isFocused -> VegaColors.Accent.copy(alpha = 0.22f)
        isSelected -> VegaColors.Accent.copy(alpha = 0.14f)
        isFocused -> VegaColors.SurfaceElevated
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(VegaShapes.Small)
            .background(containerColor)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Small,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VegaColors.Accent)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected || isFocused) VegaColors.Accent else iconTint,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // İsim ve sayı tek Text içinde olduğu için isim asla kaybolmaz, maxLines=1 ile kelime bölünmesi önlenir
        Text(
            text = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        color = if (isSelected || isFocused) VegaColors.TextPrimary else VegaColors.TextSecondary,
                        fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal
                    )
                ) {
                    append(name)
                }
                if (count > 0) {
                    withStyle(
                        style = SpanStyle(
                            color = if (isFocused) VegaColors.TextSecondary else VegaColors.TextMuted,
                            fontWeight = FontWeight.Normal
                        )
                    ) {
                        append(" ($count)")
                    }
                }
            },
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
