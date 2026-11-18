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
    val palette = AppThemes.forName(themeName)
    ActivePaletteHolder.current = palette

    val darkScheme = darkColorScheme(
        primary = palette.brand,
        onPrimary = palette.onAccent,
        secondary = palette.brandStrong,
        onSecondary = palette.onAccent,
        background = palette.canvas,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceVariant,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.border,
        error = palette.live,
        onError = palette.textPrimary
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = palette.canvas.toArgb()
                it.navigationBarColor = palette.canvas.toArgb()
                val controller = WindowCompat.getInsetsController(it, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = darkScheme,
        typography = Typography,
        content = content
    )
}
