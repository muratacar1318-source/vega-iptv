package com.tivimax.player.ui.settings

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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tivimax.player.theme.VegaPalette
import com.tivimax.player.ui.components.requestFocusSafely
import com.tivimax.player.ui.design.VegaButtonVariant
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaSizes
import com.tivimax.player.ui.design.VegaText
import com.tivimax.player.util.LocalIsTvDevice
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Tek odak + tek tıklama hedefli (Android TV D-Pad & dokunmatik) temel ayar etkileşim yüzeyi.
 * Animasyon ve kalıcı graphicsLayer olmadan anlık ton geçişi + keskin odak çerçevesi sunar (60fps akıcı kaydırma).
 */
@Composable
fun SettingsSingleFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = VegaShapes.Medium,
    containerColor: Color = Color.Transparent,
    focusedContainerColor: Color = VegaColors.SurfaceElevated,
    border: BorderStroke? = null,
    focusedBorderColor: Color = VegaColors.Accent,
    @Suppress("UNUSED_PARAMETER") focusedScale: Float = 1.03f,
    role: Role? = null,
    content: @Composable (isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val bgColor = when {
        !enabled -> containerColor.copy(alpha = if (containerColor == Color.Transparent) 0f else 0.45f)
        isFocused -> focusedContainerColor
        else -> containerColor
    }
    val activeBorder = if (isFocused && enabled) {
        BorderStroke(2.dp, focusedBorderColor)
    } else {
        border
    }

    Surface(
        shape = shape,
        color = bgColor,
        border = activeBorder,
        modifier = modifier
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
 * Sağ içerik paneli ve bölüm başlığı.
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
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VegaColors.Accent,
                modifier = Modifier.size(22.dp)
            )
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Dikey telefon alt sayfaları için yerel üst geri çubuğu ("← Oynatıcı").
 */
@Composable
fun SettingsMobileTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingsSingleFocusSurface(
                onClick = onBack,
                shape = VegaShapes.Pill,
                containerColor = Color.Transparent,
                focusedContainerColor = VegaColors.SurfaceElevated,
                role = Role.Button,
                modifier = Modifier.size(VegaSizes.MinTouch)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = VegaColors.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                text = title,
                color = VegaColors.TextPrimary,
                fontSize = VegaText.SectionTitle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
        }
        HorizontalDivider(
            color = VegaColors.Border.copy(alpha = 0.55f),
            thickness = 0.5.dp
        )
    }
}

/**
 * Dikey telefon alt sayfaları için geri hapı (geriye dönük uyumluluk için korunur).
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
        containerColor = VegaColors.Surface,
        focusedContainerColor = VegaColors.SurfaceElevated,
        border = BorderStroke(0.75.dp, VegaColors.Border),
        focusedBorderColor = VegaColors.Accent,
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
 * Sol menü kategori satırı (Yatay / Android TV).
 * Çerçevesiz tonlu aktif hap + 3dp sol vurgu çubuğu.
 */
@Composable
fun SettingsRailItem(
    title: String,
    subtitle: String = "",
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSingleFocusSurface(
        onClick = onClick,
        shape = VegaShapes.Medium,
        containerColor = if (selected) VegaColors.Accent.copy(alpha = 0.14f) else Color.Transparent,
        focusedContainerColor = if (selected) VegaColors.Accent.copy(alpha = 0.22f) else VegaColors.SurfaceElevated,
        border = null,
        focusedBorderColor = VegaColors.Accent,
        role = Role.Tab,
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 46.dp)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .clip(VegaShapes.Pill)
                    .background(if (selected) VegaColors.Accent else Color.Transparent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    selected || isFocused -> VegaColors.Accent
                    else -> VegaColors.TextSecondary
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    color = if (selected || isFocused) VegaColors.TextPrimary else VegaColors.TextSecondary,
                    fontSize = VegaText.Body,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = VegaColors.TextMuted,
                        fontSize = VegaText.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Dikey telefon görünümü için gruplanabilir kategori liste satırı.
 */
@Composable
fun SettingsCategoryListItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueSummary: String = "",
    inGroup: Boolean = false
) {
    SettingsSingleFocusSurface(
        onClick = onClick,
        shape = if (inGroup) VegaShapes.Small else VegaShapes.Large,
        containerColor = if (inGroup) Color.Transparent else VegaColors.Surface,
        focusedContainerColor = VegaColors.SurfaceElevated,
        border = if (inGroup) null else BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.7f)),
        focusedBorderColor = VegaColors.Accent,
        role = Role.Button,
        modifier = modifier.fillMaxWidth()
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(VegaShapes.Small)
                    .background(if (isFocused) VegaColors.Accent.copy(alpha = 0.16f) else VegaColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused) VegaColors.Accent else VegaColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    color = VegaColors.TextPrimary,
                    fontSize = VegaText.BodyProminent,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = VegaColors.TextMuted,
                        fontSize = VegaText.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (valueSummary.isNotBlank()) {
                Text(
                    text = valueSummary,
                    color = VegaColors.TextSecondary,
                    fontSize = VegaText.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = VegaColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * iOS / Infuse tarzı gruplanmış ayar kartı:
 * Üstte sakin büyük harfli bölüm etiketi + altında temiz yüzey kartı (iç satırlar çerçevesiz, 0.5dp ince ayırıcıyla bölünür).
 */
@Composable
fun SettingsGroupCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val trLocale = remember { Locale("tr", "TR") }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (title.isNotBlank() || !subtitle.isNullOrBlank()) {
            Column(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (title.isNotBlank()) {
                    Text(
                        text = title.uppercase(trLocale),
                        color = VegaColors.TextMuted,
                        fontSize = VegaText.Caption,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Surface(
            shape = VegaShapes.Large,
            color = VegaColors.Surface,
            border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.65f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                content = content
            )
        }
    }
}

/**
 * Gruplanmış ayar kartı içindeki satırlar arasında ince 0.5dp ayırıcı çizgi.
 */
@Composable
fun SettingsGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = VegaColors.Border.copy(alpha = 0.55f),
        thickness = 0.5.dp
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
        shape = VegaShapes.Small,
        containerColor = Color.Transparent,
        focusedContainerColor = VegaColors.SurfaceElevated,
        focusedBorderColor = VegaColors.Accent,
        role = Role.Switch,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            }
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(VegaShapes.Small)
                        .background(if (isFocused) VegaColors.Accent.copy(alpha = 0.16f) else VegaColors.SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isFocused) VegaColors.Accent else VegaColors.TextSecondary,
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
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
 * Seçim diyaloğunu açan değer satırı:
 * Solda ikon (opsiyonel) + başlık + tek satır açıklama, sağda sakin değer metni + ok ikonu.
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
        shape = VegaShapes.Small,
        containerColor = Color.Transparent,
        focusedContainerColor = VegaColors.SurfaceElevated,
        focusedBorderColor = VegaColors.Accent,
        role = Role.Button,
        modifier = modifier.fillMaxWidth()
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(VegaShapes.Small)
                        .background(if (isFocused) VegaColors.Accent.copy(alpha = 0.16f) else VegaColors.SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isFocused) VegaColors.Accent else VegaColors.TextSecondary,
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
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (valueText.isNotBlank()) {
                Text(
                    text = valueText,
                    color = if (isFocused) VegaColors.Accent else VegaColors.TextSecondary,
                    fontSize = VegaText.Secondary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = VegaColors.TextMuted,
                modifier = Modifier.size(18.dp)
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
    val isCustomAccent = accentColor != VegaColors.Accent
    SettingsSingleFocusSurface(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = VegaShapes.Small,
        containerColor = Color.Transparent,
        focusedContainerColor = VegaColors.SurfaceElevated,
        focusedBorderColor = VegaColors.Accent,
        role = Role.Button,
        modifier = modifier.fillMaxWidth()
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(VegaShapes.Small)
                        .background(
                            when {
                                isCustomAccent -> accentColor.copy(alpha = 0.14f)
                                isFocused -> VegaColors.Accent.copy(alpha = 0.16f)
                                else -> VegaColors.SurfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when {
                            isCustomAccent -> accentColor
                            isFocused -> VegaColors.Accent
                            else -> VegaColors.TextSecondary
                        },
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
                    color = when {
                        !enabled -> VegaColors.TextMuted
                        accentColor == VegaColors.Danger -> VegaColors.Danger
                        else -> VegaColors.TextPrimary
                    },
                    fontSize = VegaText.Body,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = VegaColors.Accent,
                        strokeWidth = 2.dp
                    )
                }
                !trailingBadge.isNullOrBlank() -> {
                    Text(
                        text = trailingBadge,
                        color = accentColor,
                        fontSize = VegaText.Secondary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = VegaColors.TextMuted,
                        modifier = Modifier.size(18.dp)
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
            .defaultMinSize(minHeight = 46.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = VegaColors.TextSecondary,
            fontSize = VegaText.Body,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = VegaText.Body,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * TEK ODAK HEDEFLİ standart aksiyon butonu.
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
        VegaButtonVariant.SECONDARY -> VegaColors.SurfaceElevated
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
        VegaButtonVariant.SECONDARY -> BorderStroke(0.75.dp, VegaColors.Border)
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
        focusedContainerColor = VegaColors.SurfaceElevated,
        border = BorderStroke(0.75.dp, VegaColors.Border),
        focusedBorderColor = VegaColors.Accent,
        role = Role.Button,
        modifier = modifier.size(VegaSizes.IconButton)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
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
        containerColor = if (selected) VegaColors.Accent.copy(alpha = 0.16f) else VegaColors.SurfaceVariant,
        focusedContainerColor = if (selected) VegaColors.Accent.copy(alpha = 0.24f) else VegaColors.SurfaceElevated,
        border = BorderStroke(1.dp, if (selected) VegaColors.Accent else VegaColors.Border.copy(alpha = 0.7f)),
        focusedBorderColor = VegaColors.Accent,
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
        containerColor = if (selected) VegaColors.Accent.copy(alpha = 0.12f) else VegaColors.SurfaceVariant.copy(alpha = 0.65f),
        focusedContainerColor = if (selected) VegaColors.Accent.copy(alpha = 0.20f) else VegaColors.SurfaceElevated,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.75.dp,
            color = if (selected) VegaColors.Accent else VegaColors.Border.copy(alpha = 0.7f)
        ),
        focusedBorderColor = VegaColors.Accent,
        role = Role.RadioButton,
        modifier = modifier.semantics { this.selected = selected }
    ) {
        if (compactVertical) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) VegaColors.Accent else VegaColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = VegaColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        color = if (selected) VegaColors.Accent else VegaColors.TextPrimary,
                        fontSize = VegaText.Body,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = description,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        maxLines = 2,
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) VegaColors.Accent else VegaColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        color = if (selected) VegaColors.Accent else VegaColors.TextPrimary,
                        fontSize = VegaText.Body,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = description,
                        color = VegaColors.TextSecondary,
                        fontSize = VegaText.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 8 tema paletini mimari mini ekran önizlemesi olarak gösteren TEK ODAK HEDEFLİ tema kartı.
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
        containerColor = if (selected) VegaColors.Accent.copy(alpha = 0.12f) else VegaColors.SurfaceVariant.copy(alpha = 0.6f),
        focusedContainerColor = if (selected) VegaColors.Accent.copy(alpha = 0.20f) else VegaColors.SurfaceElevated,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.75.dp,
            color = if (selected) VegaColors.Accent else VegaColors.Border.copy(alpha = 0.7f)
        ),
        focusedBorderColor = VegaColors.Accent,
        role = Role.RadioButton,
        modifier = modifier.semantics { this.selected = selected }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Mini Ekran Önizlemesi
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(VegaShapes.Small)
                    .background(palette.canvas)
                    .border(0.75.dp, palette.border, VegaShapes.Small)
                    .padding(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(16.dp)
                            .fillMaxHeight()
                            .clip(VegaShapes.Small)
                            .background(palette.surface)
                            .padding(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(VegaShapes.Pill)
                                .background(palette.brand)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(VegaShapes.Pill)
                                .background(palette.surfaceVariant)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(VegaShapes.Pill)
                                .background(palette.surfaceVariant)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(9.dp)
                                .clip(VegaShapes.Small)
                                .background(palette.surfaceElevated)
                                .padding(horizontal = 4.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .fillMaxHeight()
                                    .clip(VegaShapes.Pill)
                                    .background(palette.brand)
                            )
                        }
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
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = VegaColors.Accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Tek seçimli ayar diyaloğu seçeneği.
 */
data class SettingsChoiceItem<T>(
    val value: T,
    val label: String,
    val description: String? = null
)

/**
 * Tek seçimli ayar diyaloğu:
 * Kumanda ile açıldığında aktif seçeneğe odaklanır, 1 OK ile seçer ve kapatır.
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(Unit) {
            for (attempt in 0 until 2) {
                withFrameNanos { }
                delay(60)
                if (initialFocusRequester.requestFocusSafely()) break
            }
        }
        Surface(
            shape = VegaShapes.Large,
            color = VegaColors.SurfaceElevated,
            border = BorderStroke(0.75.dp, VegaColors.Border),
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                            fontSize = VegaText.Secondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = VegaShapes.Medium,
                    color = VegaColors.Surface,
                    border = BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        options.forEachIndexed { index, item ->
                            val isSelected = item.value == selectedValue
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = VegaColors.Border.copy(alpha = 0.55f),
                                    thickness = 0.5.dp
                                )
                            }
                            SettingsSingleFocusSurface(
                                onClick = {
                                    onSelect(item.value)
                                    onDismiss()
                                },
                                shape = VegaShapes.Small,
                                containerColor = if (isSelected) VegaColors.Accent.copy(alpha = 0.12f) else Color.Transparent,
                                focusedContainerColor = if (isSelected) VegaColors.Accent.copy(alpha = 0.22f) else VegaColors.SurfaceElevated,
                                border = null,
                                focusedBorderColor = VegaColors.Accent,
                                role = Role.RadioButton,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == selectedIndex) Modifier.focusRequester(initialFocusRequester) else Modifier)
                                    .semantics { selected = isSelected }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 50.dp)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
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
                                                fontSize = VegaText.Caption,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = VegaColors.Accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
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
}
