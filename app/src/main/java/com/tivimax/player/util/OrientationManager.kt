package com.tivimax.player.util

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tivimax.player.ui.Screen

/**
 * Merkezi Yön (Orientation) ve Tam Ekran (Immersive Mode) Yöneticisi.
 * Tüm Activity requestedOrientation çağrıları ve sistem çubuğu görünürlüğü tek noktadan yönetilir.
 */
class OrientationManager(private val activity: Activity) {

    companion object {
        private const val PREFS_FAST_CACHE = "vega_fast_cache"
        private const val KEY_FAST_DEVICE_MODE = "fast_device_mode"

        /**
         * Soğuk başlatmada (Cold start) sıfır gecikmeyle (flicker olmadan) Activity yönünü belirlemek için
         * senkron önbellekten DeviceMode'u okur.
         */
        fun getFastCachedDeviceMode(context: Context): DeviceMode? {
            val prefs = context.getSharedPreferences(PREFS_FAST_CACHE, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_FAST_DEVICE_MODE, null) ?: return null
            return try {
                DeviceMode.valueOf(name)
            } catch (_: Exception) {
                null
            }
        }

        fun saveFastCache(context: Context, mode: DeviceMode) {
            context.getSharedPreferences(PREFS_FAST_CACHE, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FAST_DEVICE_MODE, mode.name)
                .apply()
        }

        fun clearFastCache(context: Context) {
            context.getSharedPreferences(PREFS_FAST_CACHE, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_FAST_DEVICE_MODE)
                .apply()
        }

        /**
         * Activity.onCreate() anında henüz UI çizilmeden önce ilk yönü ayarlar.
         */
        fun applyInitialOrientation(activity: Activity, mode: DeviceMode) {
            when (mode) {
                DeviceMode.PHONE -> {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                DeviceMode.TABLET -> {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                }
                DeviceMode.TV -> {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }
        }
    }

    // Telefon modunda oynatıcı içindeyken kullanıcının elle dikey moda geçmesini sağlayan kilit durumu
    private var isPhonePlayerLockedPortrait: Boolean = false

    /**
     * Kullanıcının telefon modundayken video oynatıcıda dikey / yatay yön kilidini değiştirmesini sağlar.
     * @return Yeni durum: true ise dikey kilitli, false ise otomatik sensör yatay
     */
    fun togglePhonePlayerOrientationLock(currentScreen: Screen, mode: DeviceMode): Boolean {
        if (mode != DeviceMode.PHONE || currentScreen !is Screen.Player) return false
        isPhonePlayerLockedPortrait = !isPhonePlayerLockedPortrait
        applyOrientation(mode, currentScreen, inPip = false)
        return isPhonePlayerLockedPortrait
    }

    fun isPhonePlayerPortraitLocked(): Boolean = isPhonePlayerLockedPortrait

    /**
     * Ekran geçişlerinde, mod değişikliklerinde veya PiP durumlarında merkezi yönlendirmeyi uygular.
     */
    fun applyOrientation(mode: DeviceMode, currentScreen: Screen, inPip: Boolean = false) {
        // PiP modundayken yön zorlaması yapılmaz
        if (inPip) return

        val isPlayerScreen = currentScreen is Screen.Player

        when (mode) {
            DeviceMode.PHONE -> {
                if (isPlayerScreen) {
                    // Video oynatıcı açık: Kullanıcı dikey kilitlemediyse otomatik SENSOR_LANDSCAPE
                    if (isPhonePlayerLockedPortrait) {
                        setOrientationSafe(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    } else {
                        setOrientationSafe(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                    }
                    setImmersiveMode(true)
                } else {
                    // Oynatıcıdan çıkıldı: Kesinlikle dikey moda dön ve sistem çubuklarını geri getir
                    isPhonePlayerLockedPortrait = false
                    setOrientationSafe(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    setImmersiveMode(false)
                }
            }

            DeviceMode.TABLET -> {
                // Tablette yön serbest (FULL_SENSOR): Sistem otomatik döndürmesine saygılı 360° rotasyon
                setOrientationSafe(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)
                setImmersiveMode(isPlayerScreen)
            }

            DeviceMode.TV -> {
                // TV modunda uygulama baştan sona yatayda kilitli ve tam ekran
                setOrientationSafe(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                setImmersiveMode(true)
            }
        }
    }

    private fun setOrientationSafe(orientation: Int) {
        try {
            if (activity.requestedOrientation != orientation) {
                activity.requestedOrientation = orientation
            }
        } catch (_: Exception) {}
    }

    /**
     * Sistem çubuklarını gizler veya gösterir, çentik/cutout alanını tam ekrana dahil eder.
     */
    fun setImmersiveMode(enable: Boolean) {
        try {
            val window = activity.window ?: return
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (enable) {
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        } catch (_: Exception) {}
    }
}
