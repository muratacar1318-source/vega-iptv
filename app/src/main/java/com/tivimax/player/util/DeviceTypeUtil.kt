package com.tivimax.player.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

val LocalIsTvDevice = staticCompositionLocalOf { false }

fun Context.isTvDevice(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val hasTvFeature = packageManager.hasSystemFeature("android.hardware.type.television") || 
                       packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    val hasNoTouch = !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    return isTvMode || hasLeanback || hasTvFeature || hasNoTouch
}
