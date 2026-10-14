package com.tivimax.player.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
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

@Composable
fun TiviMaxTheme(content: @Composable () -> Unit) {
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
