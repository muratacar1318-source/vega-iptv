package com.tivimax.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CategoryItem(
    name: String,
    count: Int,
    iconTint: Color = Color(0xFF7C4DFF),
    icon: ImageVector = Icons.Default.Folder,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundModifier = if (isSelected) {
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF7C4DFF).copy(alpha = 0.28f),
                        Color(0xFF00E5FF).copy(alpha = 0.10f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color(0xFF7C4DFF).copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp)
            )
    } else {
        Modifier.clip(RoundedCornerShape(10.dp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .then(backgroundModifier)
            .dpadFocusable(
                focusedBorderColor = Color(0xFF00E5FF),
                shape = RoundedCornerShape(10.dp),
                onClick = onClick
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF00E5FF))
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) Color(0xFF00E5FF) else iconTint,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // İsim ve sayı tek Text içinde olduğu için isim asla kaybolmaz, maxLines=1 ile kelime bölünmesi önlenir
        Text(
            text = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        color = if (isSelected) Color.White else Color(0xFFE0E0E0),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                ) {
                    append(name)
                }
                if (count > 0) {
                    withStyle(
                        style = SpanStyle(
                            color = Color(0xFF8E92AA),
                            fontWeight = FontWeight.Normal
                        )
                    ) {
                        append(" ($count)")
                    }
                }
            },
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
