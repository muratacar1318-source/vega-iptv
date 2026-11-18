package com.tivimax.player.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.tivimax.player.theme.VegaPalette
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.design.VegaButtonVariant
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaSizes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.ui.design.vegaPanel
import com.tivimax.player.util.LocalIsTvDevice
import kotlinx.coroutines.delay

/**
 * Tek odak + tek tıklama hedefli (Android TV D-Pad & dokunmatik) temel ayar etkileşim yüzeyi.
 * Surface'in clickable OLMAYAN aşırı yüklemesini kullanır; böylece çift odak / çift tıklama
 * çakışması kesinlikle oluşmaz.
 */
@Composable
fun SettingsSingleFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = VegaShapes.Medium,
    containerColor: Color = Color.Transparent,
    focusedContainerColor: Color = VegaColors.SurfaceAccent,
    border: BorderStroke? = null,
    focusedBorderColor: Color = VegaColors.Accent,
    focusedScale: Float = 1.03f,
    role: Role? = null,
    content: @Composable (isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused && enabled) focusedScale else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "settingsFocusScale"
    )
    val animatedBg by animateColorAsState(
        targetValue = when {
            !enabled -> containerColor.copy(alpha = if (containerColor == Color.Transparent) 0f else 0.45f)
            isFocused -> focusedContainerColor
            else -> containerColor
        },
        animationSpec = tween(durationMillis = 120),
        label = "settingsFocusBg"
    )
    val activeBorder = if (isFocused && enabled) {
        BorderStroke(2.dp, focusedBorderColor)
    } else {
        border
    }

    Surface(
        shape = shape,
        color = animatedBg,
        border = activeBorder,
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clip(shape)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = role,
                onClick = onClick
            )
    ) {
        content(isFocused)
    }
}

/**
 * StreamVault SettingsLocalHeader uyarlaması: sağ içerik paneli ve dikey alt sayfa başlığı.
 */
@Composable
fun SettingsSectionHeader(
    title: String,
    description: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(VegaShapes.Medium)
                    .background(VegaColors.Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = VegaColors.Accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = VegaColors.TextPrimary,
                fontSize = VegaText.ScreenTitle,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Dikey telefon alt sayfaları için StreamVault tarzı "← Ayarlar" geri hapı.
 */
@Composable
fun SettingsBackPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSingleFocusSurface(
        onClick = onClick,
        shape = VegaShapes.Pill,
        containerColor = VegaColors.SurfaceElevated,
        focusedContainerColor = VegaColors.SurfaceAccent,
        border = BorderStroke(1.dp, VegaColors.Border),
        focusedBorderColor = VegaColors.Accent,
        focusedScale = 1.03f,
        role = Role.Button,
        modifier = modifier.defaultMinSize(minHeight = 40.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Geri",
                tint = VegaColors.Accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = VegaColors.TextPrimary,
                fontSize = VegaText.Body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/**
 * StreamVault tarzı sol menü kategori satırı (Yatay / Android TV).
 * Seçili satırda sol mavi vurgu şeridi + yumuşak vurgu zemini + renkli ikon kutusu.
 */
@Composable
fun SettingsRailItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSingleFocusSurface(
        onClick = onClick,
        shape = VegaShapes.Medium,
        containerColor = if (selected) VegaColors.Accent.copy(alpha = 0.14f) else Color.Transparent,
        focusedContainerColor = if (selected) VegaColors.Accent.copy(alpha = 0.22f) else VegaColors.SurfaceAccent,
        border = if (selected) BorderStroke(1.dp, VegaColors.Accent.copy(alpha = 0.45f)) else null,
        focusedBorderColor = VegaColors.Accent,
        focusedScale = 1.02f,
        role = Role.Tab,
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sol vurgu şeridi
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(VegaShapes.Pill)
                    .background(if (selected) VegaColors.Accent else Color.Transparent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Yumuşak ikon kutusu (36dp)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(VegaShapes.Small)
                    .background(
                        when {
                            selected -> VegaColors.Accent.copy(alpha = 0.22f)
                            isFocused -> VegaColors.Accent.copy(alpha = 0.14f)
                            else -> VegaColors.SurfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected || isFocused) VegaColors.Accent else VegaColors.TextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = if (selected) VegaColors.Accent else VegaColors.TextPrimary,
                    fontSize = VegaText.Body,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = if (selected) VegaColors.TextPrimary.copy(alpha = 0.78f) else VegaColors.TextSecondary,
                    fontSize = VegaText.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Dikey telefon görünümü için StreamVault kategori liste öğesi.
 */
@Composable
fun SettingsCategoryListItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSingleFocusSurface(
        onClick = onClick,
        shape = VegaShapes.Large,
        containerColor = VegaColors.Card,
        focusedContainerColor = VegaColors.SurfaceAccent,
        border = BorderStroke(1.dp, VegaColors.Border),
        focusedBorderColor = VegaColors.Accent,
        focusedScale = 1.02f,
        role = Role.Button,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(VegaShapes.Medium)
                    .background(VegaColors.Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = VegaColors.Accent,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.BodyProminent,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = VegaColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * StreamVault gruplanmış ayar kartı:
 * Üstte vurgulu grup başlığı + altında vegaPanel(VegaShapes.Large) içinde satırlar.
 */
@Composable
fun SettingsGroupCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = VegaColors.Accent,
                fontSize = VegaText.Secondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Caption
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vegaPanel(VegaShapes.Large)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            content = content
        )
    }
}

/**
 * Gruplanmış ayar kartı içindeki satırlar arasında ince ayırıcı çizgi.
 */
@Composable
fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        color = VegaColors.Border,
        thickness = 1.dp
    )
}

/**
 * TEK ODAK HEDEFLİ anahtar (Switch) satırı.
 * Switch bileşeni odaklanamaz (`onCheckedChange = null`, `canFocus = false`);
 * tüm satır tek bir OK/dokunuş ile anında açılıp kapanır.
 */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    SettingsSingleFocusSurface(
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        shape = VegaShapes.Medium,
        containerColor = Color.Transparent,
        focusedContainerColor = VegaColors.SurfaceAccent,
        focusedBorderColor = VegaColors.Accent,
        focusedScale = 1.015f,
        role = Role.Switch,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(VegaShapes.Small)
                        .background(VegaColors.Accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = if (enabled) VegaColors.TextPrimary else VegaColors.TextMuted,
                    fontSize = VegaText.Body,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        lineHeight = VegaText.Body
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.focusProperties { canFocus = false },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = VegaColors.OnAccent,
                    checkedTrackColor = VegaColors.Accent,
                    uncheckedThumbColor = VegaColors.TextSecondary,
                    uncheckedTrackColor = VegaColors.SurfaceVariant,
                    uncheckedBorderColor = VegaColors.Border
                )
            )
        }
    }
}

/**
 * StreamVault ClickableSettingsRow uyarlaması:
 * Solda ikon (opsiyonel) + başlık + açıklama, sağda seçili değer hapı + ok ikonu.
 * Kumandada 1 OK basışıyla seçim diyaloğunu açar.
 */
@Composable
fun SettingsValueRow(
    title: String,
    subtitle: String,
    valueText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    SettingsSingleFocusSurface(
        onClick = onClick,
        enabled = enabled,
        shape = VegaShapes.Medium,
        containerColor = Color.Transparent,
        focusedContainerColor = VegaColors.SurfaceAccent,
        focusedBorderColor = VegaColors.Accent,
        focusedScale = 1.015f,
        role = Role.Button,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(VegaShapes.Small)
                        .background(VegaColors.Accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = if (enabled) VegaColors.TextPrimary else VegaColors.TextMuted,
                    fontSize = VegaText.Body,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        lineHeight = VegaText.Body
                    )
                }
            }
            if (valueText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(VegaShapes.Small)
                        .background(VegaColors.Accent.copy(alpha = 0.14f))
                        .border(1.dp, VegaColors.Accent.copy(alpha = 0.35f), VegaShapes.Small)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = valueText,
                        color = VegaColors.Accent,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = VegaColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Tek tıklamalı aksiyon satırı (örn. Güncelleme kontrolü, Bağlantı testi, Önbellek temizle, PIN değiştir).
 */
@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = VegaColors.Accent,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    trailingBadge: String? = null
) {
    SettingsSingleFocusSurface(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = VegaShapes.Medium,
        containerColor = Color.Transparent,
        focusedContainerColor = VegaColors.SurfaceAccent,
        focusedBorderColor = VegaColors.Accent,
        focusedScale = 1.015f,
        role = Role.Button,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 58.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(VegaShapes.Small)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = if (enabled) VegaColors.TextPrimary else VegaColors.TextMuted,
                    fontSize = VegaText.Body,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        lineHeight = VegaText.Body
                    )
                }
            }
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = VegaColors.Accent,
                        strokeWidth = 2.dp
                    )
                }
                !trailingBadge.isNullOrBlank() -> {
                    Box(
                        modifier = Modifier
                            .clip(VegaShapes.Small)
                            .background(accentColor.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = trailingBadge,
                            color = accentColor,
                            fontSize = VegaText.Caption,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = VegaColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Salt okunur bilgi satırı (Hesabım ve Hakkında grupları için).
 */
@Composable
fun SettingsInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = VegaColors.TextPrimary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = VegaColors.TextSecondary,
            fontSize = VegaText.Secondary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = VegaText.Secondary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
    }
}

/**
 * TEK ODAK HEDEFLİ standart aksiyon butonu (VegaButton'un çift odak bug'ı olmayan karşılığı).
 */
@Composable
fun SettingsActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: VegaButtonVariant = VegaButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val isTv = LocalIsTvDevice.current
    val minHeight = if (isTv) VegaSizes.ButtonHeightTv else VegaSizes.ButtonHeight

    val containerColor = when (variant) {
        VegaButtonVariant.PRIMARY -> VegaColors.Accent
        VegaButtonVariant.SECONDARY -> VegaColors.SurfaceVariant
        VegaButtonVariant.DANGER -> VegaColors.Danger
        VegaButtonVariant.TEXT -> Color.Transparent
    }
    val focusedContainer = when (variant) {
        VegaButtonVariant.PRIMARY -> VegaColors.FocusBorder
        VegaButtonVariant.SECONDARY -> VegaColors.SurfaceAccent
        VegaButtonVariant.DANGER -> VegaColors.Danger
        VegaButtonVariant.TEXT -> VegaColors.Accent.copy(alpha = 0.14f)
    }
    val contentColor = when (variant) {
        VegaButtonVariant.PRIMARY -> VegaColors.OnAccent
        VegaButtonVariant.SECONDARY -> VegaColors.TextPrimary
        VegaButtonVariant.DANGER -> Color.White
        VegaButtonVariant.TEXT -> VegaColors.Accent
    }
    val restingBorder = when (variant) {
        VegaButtonVariant.SECONDARY -> BorderStroke(1.dp, VegaColors.Border)
        else -> null
    }
    val focusedBorder = when (variant) {
        VegaButtonVariant.PRIMARY, VegaButtonVariant.DANGER -> Color.White
        else -> VegaColors.Accent
    }
    val effectiveEnabled = enabled && !isLoading

    SettingsSingleFocusSurface(
        onClick = onClick,
        enabled = effectiveEnabled,
        shape = VegaShapes.Medium,
        containerColor = containerColor,
        focusedContainerColor = focusedContainer,
        border = restingBorder,
        focusedBorderColor = focusedBorder,
        focusedScale = 1.03f,
        role = Role.Button,
        modifier = modifier.defaultMinSize(minHeight = minHeight)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = contentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (effectiveEnabled) contentColor else contentColor.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (effectiveEnabled) contentColor else contentColor.copy(alpha = 0.45f),
                fontSize = VegaText.Body,
                fontWeight = if (variant == VegaButtonVariant.PRIMARY || variant == VegaButtonVariant.DANGER) {
                    FontWeight.Bold
                } else {
                    FontWeight.SemiBold
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * TEK ODAK HEDEFLİ yuvarlak ikon butonu (44dp dokunma alanı).
 */
@Composable
fun SettingsIconActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = VegaColors.Accent,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    SettingsSingleFocusSurface(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = CircleShape,
        containerColor = VegaColors.SurfaceVariant,
        focusedContainerColor = VegaColors.SurfaceAccent,
        border = BorderStroke(1.dp, VegaColors.Border),
        focusedBorderColor = VegaColors.Accent,
        focusedScale = 1.04f,
        role = Role.Button,
        modifier = modifier.size(VegaSizes.IconButton)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = VegaColors.Accent,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (enabled) tint else VegaColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * TEK ODAK HEDEFLİ seçim çipi (Altyazı boyutu/rengi, Sunucu seçimi vb.).
 */
@Composable
fun SettingsChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    swatchColor: Color? = null,
    enabled: Boolean = true
) {
    SettingsSingleFocusSurface(
        onClick = onClick,
        enabled = enabled,
        shape = VegaShapes.Small,
        containerColor = if (selected) VegaColors.Accent.copy(alpha = 0.18f) else VegaColors.SurfaceVariant,
        focusedContainerColor = if (selected) VegaColors.Accent.copy(alpha = 0.26f) else VegaColors.SurfaceAccent,
        border = BorderStroke(1.dp, if (selected) VegaColors.Accent else VegaColors.Border),
        focusedBorderColor = VegaColors.Accent,
        focusedScale = 1.03f,
        role = Role.RadioButton,
        modifier = modifier
            .defaultMinSize(minHeight = VegaSizes.MinTouch)
            .semantics { this.selected = selected }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (swatchColor != null) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .border(0.5.dp, VegaColors.Border, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = if (selected) VegaColors.Accent else VegaColors.TextSecondary,
                fontSize = VegaText.Secondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * TEK ODAK HEDEFLİ açıklamalı seçim kartı (Canlı TV düzeni, Cihaz türü vb.).
 * İçinde ayrı RadioButton kullanmaz; tüm kart tek odak ve tıklama hedefidir.
 */
@Composable
fun SettingsOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compactVertical: Boolean = false
) {
    SettingsSingleFocusSurface(
        onClick = onClick,
        shape = VegaShapes.Medium,
        containerColor = if (selected) VegaColors.Accent.copy(alpha = 0.14f) else VegaColors.SurfaceVariant,
        focusedContainerColor = if (selected) VegaColors.Accent.copy(alpha = 0.22f) else VegaColors.SurfaceAccent,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) VegaColors.Accent else VegaColors.Border),
        focusedBorderColor = VegaColors.Accent,
        focusedScale = 1.02f,
        role = Role.RadioButton,
        modifier = modifier.semantics { this.selected = selected }
    ) {
        if (compactVertical) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(VegaShapes.Small)
                            .background(if (selected) VegaColors.Accent.copy(alpha = 0.22f) else VegaColors.Card),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) VegaColors.Accent else VegaColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = title,
                        color = if (selected) VegaColors.Accent else VegaColors.TextPrimary,
                        fontSize = VegaText.Body,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = description,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        lineHeight = VegaText.Body,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(VegaShapes.Small)
                        .background(if (selected) VegaColors.Accent.copy(alpha = 0.22f) else VegaColors.Card),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) VegaColors.Accent else VegaColors.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        color = if (selected) VegaColors.Accent else VegaColors.TextPrimary,
                        fontSize = VegaText.Body,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        lineHeight = VegaText.Body
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 8 StreamVault temasını mini ekran önizlemesi olarak gösteren TEK ODAK HEDEFLİ tema kartı.
 * Koyu arka plan + mini yan menü + mini içerik kartları + vurgu rengi şeridi + tema adı + onay işareti.
 */
@Composable
fun SettingsThemePreviewCard(
    label: String,
    palette: VegaPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSingleFocusSurface(
        onClick = onClick,
        shape = VegaShapes.Medium,
        containerColor = if (selected) VegaColors.Accent.copy(alpha = 0.14f) else VegaColors.SurfaceVariant,
        focusedContainerColor = if (selected) VegaColors.Accent.copy(alpha = 0.22f) else VegaColors.SurfaceAccent,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) VegaColors.Accent else VegaColors.Border
        ),
        focusedBorderColor = VegaColors.Accent,
        focusedScale = 1.035f,
        role = Role.RadioButton,
        modifier = modifier.semantics { this.selected = selected }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mini Ekran Önizlemesi
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(VegaShapes.Small)
                    .background(palette.canvas)
                    .border(1.dp, palette.border, VegaShapes.Small)
                    .padding(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Mini sol menü şeridi
                    Column(
                        modifier = Modifier
                            .width(18.dp)
                            .fillMaxHeight()
                            .clip(VegaShapes.Small)
                            .background(palette.surface)
                            .padding(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(VegaShapes.Pill)
                                .background(palette.brand)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(VegaShapes.Pill)
                                .background(palette.surfaceVariant)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(VegaShapes.Pill)
                                .background(palette.surfaceVariant)
                        )
                    }
                    // Mini sağ içerik alanı
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Üst vurgu şeridi
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(VegaShapes.Small)
                                .background(palette.surfaceElevated)
                                .padding(horizontal = 4.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .fillMaxHeight()
                                    .clip(VegaShapes.Pill)
                                    .background(palette.brand)
                            )
                        }
                        // Alt mini kartlar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(VegaShapes.Small)
                                    .background(palette.surfaceElevated)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(VegaShapes.Small)
                                    .background(palette.surfaceVariant)
                            )
                        }
                    }
                }
            }

            // Alt Tema Adı + Onay İşareti
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(palette.brand)
                    )
                    Text(
                        text = label,
                        color = if (selected) VegaColors.Accent else VegaColors.TextPrimary,
                        fontSize = VegaText.Caption,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * StreamVault tek seçimli ayar diyaloğu seçeneği.
 */
data class SettingsChoiceItem<T>(
    val value: T,
    val label: String,
    val description: String? = null
)

/**
 * StreamVault SettingsSingleChoiceDialog uyarlaması:
 * Kumanda ile açıldığında aktif seçeneğe otomatik odaklanır, 1 OK ile seçer ve kapatır.
 */
@Composable
fun <T> SettingsSelectionDialog(
    title: String,
    subtitle: String? = null,
    options: List<SettingsChoiceItem<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val initialFocusRequester = remember { FocusRequester() }
    val selectedIndex = remember(options, selectedValue) {
        options.indexOfFirst { it.value == selectedValue }.coerceAtLeast(0)
    }

    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            for (attempt in 0 until 2) {
                withFrameNanos { }
                delay(60)
                if (initialFocusRequester.requestFocusSafely()) break
            }
        }
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .vegaPanel(VegaShapes.Large)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.SectionTitle,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Secondary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEachIndexed { index, item ->
                    val isSelected = item.value == selectedValue
                    SettingsSingleFocusSurface(
                        onClick = {
                            onSelect(item.value)
                            onDismiss()
                        },
                        shape = VegaShapes.Medium,
                        containerColor = if (isSelected) VegaColors.Accent.copy(alpha = 0.16f) else VegaColors.SurfaceElevated,
                        focusedContainerColor = if (isSelected) VegaColors.Accent.copy(alpha = 0.24f) else VegaColors.SurfaceAccent,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) VegaColors.Accent else VegaColors.Border
                        ),
                        focusedBorderColor = VegaColors.Accent,
                        focusedScale = 1.02f,
                        role = Role.RadioButton,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == selectedIndex) Modifier.focusRequester(initialFocusRequester) else Modifier)
                            .semantics { selected = isSelected }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.label,
                                    color = if (isSelected) VegaColors.Accent else VegaColors.TextPrimary,
                                    fontSize = VegaText.Body,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (!item.description.isNullOrBlank()) {
                                    Text(
                                        text = item.description,
                                        color = VegaColors.TextSecondary,
                                        fontSize = VegaText.Caption
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = VegaColors.Accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SettingsActionButton(
                    text = "Kapat",
                    variant = VegaButtonVariant.SECONDARY,
                    onClick = onDismiss
                )
            }
        }
    }
}
