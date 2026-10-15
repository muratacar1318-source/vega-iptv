package com.tivimax.player.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun TiviMaxTheme(
    themeName: String = "MIDNIGHT",
    content: @Composable () -> Unit
) {
    // Statik renkleri güncelleyelim
    when (themeName) {
        "OCEAN" -> {
            TiviBackground = AppThemes.OceanBackground
            TiviSurface = AppThemes.OceanSurface
            TiviSurfaceVariant = AppThemes.OceanCardDark
            TiviCard = AppThemes.OceanCard
            TiviCardBorder = AppThemes.OceanCardBorder
            TiviPrimary = AppThemes.OceanPrimary
            TiviPrimaryDark = AppThemes.OceanPrimary
            TiviSecondary = AppThemes.OceanPrimary
            OpusCyan = AppThemes.OceanCyan
            TiviCardDark = AppThemes.OceanCardDark
            TiviAccentLive = AppThemes.OceanAccentLive
        }
        "FOREST" -> {
            TiviBackground = AppThemes.ForestBackground
            TiviSurface = AppThemes.ForestSurface
            TiviSurfaceVariant = AppThemes.ForestCardDark
            TiviCard = AppThemes.ForestCard
            TiviCardBorder = AppThemes.ForestCardBorder
            TiviPrimary = AppThemes.ForestPrimary
            TiviPrimaryDark = AppThemes.ForestPrimary
            TiviSecondary = AppThemes.ForestPrimary
            OpusCyan = AppThemes.ForestCyan
            TiviCardDark = AppThemes.ForestCardDark
            TiviAccentLive = AppThemes.ForestAccentLive
        }
        "VIOLET" -> {
            TiviBackground = AppThemes.VioletBackground
            TiviSurface = AppThemes.VioletSurface
            TiviSurfaceVariant = AppThemes.VioletCardDark
            TiviCard = AppThemes.VioletCard
            TiviCardBorder = AppThemes.VioletCardBorder
            TiviPrimary = AppThemes.VioletPrimary
            TiviPrimaryDark = AppThemes.VioletPrimary
            TiviSecondary = AppThemes.VioletPrimary
            OpusCyan = AppThemes.VioletCyan
            TiviCardDark = AppThemes.VioletCardDark
            TiviAccentLive = AppThemes.VioletAccentLive
        }
        else -> { // MIDNIGHT (Varsayılan)
            TiviBackground = AppThemes.MidnightBackground
            TiviSurface = AppThemes.MidnightSurface
            TiviSurfaceVariant = AppThemes.MidnightCardDark
            TiviCard = AppThemes.MidnightCard
            TiviCardBorder = AppThemes.MidnightCardBorder
            TiviPrimary = AppThemes.MidnightPrimary
            TiviPrimaryDark = AppThemes.MidnightPrimary
            TiviSecondary = AppThemes.MidnightPrimary
            OpusCyan = AppThemes.MidnightCyan
            TiviCardDark = AppThemes.MidnightCardDark
            TiviAccentLive = AppThemes.MidnightAccentLive
        }
    }

    val DarkColorScheme = darkColorScheme(
        primary = TiviPrimary,
        onPrimary = TiviBackground,
        secondary = TiviSecondary,
        onSecondary = TiviTextPrimary,
        background = TiviBackground,
        onBackground = TiviTextPrimary,
        surface = TiviSurface,
        onSurface = TiviTextPrimary,
        surfaceVariant = TiviSurfaceVariant,
        onSurfaceVariant = TiviTextSecondary,
        error = TiviAccentLive
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = TiviBackground.toArgb()
                it.navigationBarColor = TiviBackground.toArgb()
                val controller = WindowCompat.getInsetsController(it, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
