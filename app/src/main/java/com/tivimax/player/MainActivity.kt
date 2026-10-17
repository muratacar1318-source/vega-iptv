package com.tivimax.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.tivimax.player.theme.TiviMaxTheme
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen
import com.tivimax.player.util.DeviceMode
import com.tivimax.player.util.LocalDeviceMode
import com.tivimax.player.util.LocalIsTvDevice
import com.tivimax.player.util.LocalOrientationManager
import com.tivimax.player.util.OrientationManager
import com.tivimax.player.util.detectRecommendedDeviceMode
import com.tivimax.player.util.isTvDevice

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var orientationManager: OrientationManager
    private var wasPlayingBeforeStop = false

    private fun isTvModeActive(): Boolean {
        if (::viewModel.isInitialized) {
            return viewModel.deviceMode.value == DeviceMode.TV
        }
        val cached = OrientationManager.getFastCachedDeviceMode(this)
        return cached == DeviceMode.TV || isTvDevice()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Soğuk başlatmada (Cold start) sıfır gecikmeyle doğru yönü Activity'ye uygula (Flicker önleme)
        val fastMode = OrientationManager.getFastCachedDeviceMode(this)
        if (fastMode != null) {
            OrientationManager.applyInitialOrientation(this, fastMode)
        } else {
            val detected = detectRecommendedDeviceMode()
            if (detected == DeviceMode.TV) {
                OrientationManager.applyInitialOrientation(this, DeviceMode.TV)
            }
        }

        orientationManager = OrientationManager(this)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            val deviceMode by viewModel.deviceMode.collectAsState()
            val currentScreen by viewModel.currentScreen.collectAsState()
            val inPip by viewModel.isInPipMode.collectAsState()
            val isVideoFullscreen by viewModel.isVideoFullscreen.collectAsState()

            // Merkezi Yön ve Tam Ekran Yönetimi
            LaunchedEffect(deviceMode, currentScreen, inPip, isVideoFullscreen) {
                orientationManager.applyOrientation(deviceMode, currentScreen, inPip, isVideoFullscreen)
            }

            val isTv = deviceMode == DeviceMode.TV

            CompositionLocalProvider(
                LocalDeviceMode provides deviceMode,
                LocalIsTvDevice provides isTv,
                LocalOrientationManager provides orientationManager
            ) {
                TiviMaxTheme(themeName = appTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (::viewModel.isInitialized) {
            viewModel.isInPipMode.value = isInPictureInPictureMode
            if (!isInPictureInPictureMode && ::orientationManager.isInitialized) {
                orientationManager.applyOrientation(
                    viewModel.deviceMode.value,
                    viewModel.currentScreen.value,
                    false,
                    viewModel.isVideoFullscreen.value
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::viewModel.isInitialized && ::orientationManager.isInitialized) {
            val isPlayer = viewModel.currentScreen.value is Screen.Player
            val isFullscreen = isPlayer || viewModel.isVideoFullscreen.value
            val isTv = viewModel.deviceMode.value == DeviceMode.TV
            if (isFullscreen || isTv) {
                orientationManager.setImmersiveMode(true)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::viewModel.isInitialized && ::orientationManager.isInitialized) {
            val isPlayer = viewModel.currentScreen.value is Screen.Player
            val isFullscreen = isPlayer || viewModel.isVideoFullscreen.value
            val isTv = viewModel.deviceMode.value == DeviceMode.TV
            if (isFullscreen || isTv) {
                orientationManager.setImmersiveMode(true)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isTvModeActive()) {
            // TV kumandasında Ana Ekran (Home) tuşuna basıldığında arkaplanda oynatmayı anında durdur
            if (::viewModel.isInitialized) {
                if (viewModel.playerManager.isPlaying.value) {
                    wasPlayingBeforeStop = true
                }
                viewModel.playerManager.pause()
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ::viewModel.isInitialized) {
            try {
                val hasPip = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
                val isPlaying = viewModel.playerManager.isPlaying.value
                val isPlayerScreen = viewModel.currentScreen.value is Screen.Player
                val autoPip = viewModel.autoPipEnabled.value
                if (hasPip && autoPip && isPlayerScreen && isPlaying) {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                }
            } catch (e: Throwable) {
                // Ignore unsupported devices / ROMs
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::viewModel.isInitialized) {
            val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
            if (isTvModeActive() || !inPip) {
                if (viewModel.playerManager.isPlaying.value) {
                    wasPlayingBeforeStop = true
                }
                viewModel.playerManager.pause()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (::viewModel.isInitialized) {
            val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
            if (isTvModeActive() || !inPip) {
                if (viewModel.playerManager.isPlaying.value) {
                    wasPlayingBeforeStop = true
                }
                viewModel.playerManager.pause()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized && ::orientationManager.isInitialized) {
            orientationManager.applyOrientation(
                viewModel.deviceMode.value,
                viewModel.currentScreen.value,
                viewModel.isInPipMode.value,
                viewModel.isVideoFullscreen.value
            )
        }
        if (::viewModel.isInitialized && wasPlayingBeforeStop) {
            wasPlayingBeforeStop = false
            val isPlayerScreen = viewModel.currentScreen.value is Screen.Player
            val isHomeScreen = viewModel.currentScreen.value is Screen.Home
            if (isPlayerScreen || isHomeScreen) {
                if (viewModel.playerManager.isLiveStream) {
                    try {
                        viewModel.playerManager.player?.seekToDefaultPosition()
                    } catch (_: Throwable) {}
                }
                viewModel.playerManager.play()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::viewModel.isInitialized) {
            viewModel.playerManager.release()
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (!::viewModel.isInitialized) return super.dispatchKeyEvent(event)

        val isPlayerScreen = viewModel.currentScreen.value is Screen.Player

        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_0,
                android.view.KeyEvent.KEYCODE_1,
                android.view.KeyEvent.KEYCODE_2,
                android.view.KeyEvent.KEYCODE_3,
                android.view.KeyEvent.KEYCODE_4,
                android.view.KeyEvent.KEYCODE_5,
                android.view.KeyEvent.KEYCODE_6,
                android.view.KeyEvent.KEYCODE_7,
                android.view.KeyEvent.KEYCODE_8,
                android.view.KeyEvent.KEYCODE_9 -> {
                    // Sadece mobil tam ekran oynatıcıda yakala; diğer ekranlarda (Giriş, Arama, PIN)
                    // ve TV rehberinde Compose katmanına bırak (yazı alanları çalışsın)
                    if (isPlayerScreen) {
                        viewModel.onNumericKeyPressed(event.keyCode - android.view.KeyEvent.KEYCODE_0)
                        return true
                    }
                }
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
                    if (viewModel.isZapperActive.value) {
                        viewModel.executeZapperSwitch()
                        return true
                    }
                    if (event.repeatCount == 0) {
                        event.startTracking()
                    }
                }
                android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (isPlayerScreen) {
                        val currentStreamId = (viewModel.currentScreen.value as Screen.Player).streamId
                        viewModel.zapNextChannel(currentStreamId)
                        return true
                    } else if (viewModel.deviceMode.value == DeviceMode.TV && viewModel.selectedHomeTab.value == com.tivimax.player.ui.main.HomeTab.LIVE_TV) {
                        val currentId = viewModel.activePreviewChannel.value?.streamId
                        if (currentId != null) {
                            viewModel.zapNextChannel(currentId)
                            return true
                        }
                    }
                }
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (isPlayerScreen) {
                        val currentStreamId = (viewModel.currentScreen.value as Screen.Player).streamId
                        viewModel.zapPreviousChannel(currentStreamId)
                        return true
                    } else if (viewModel.deviceMode.value == DeviceMode.TV && viewModel.selectedHomeTab.value == com.tivimax.player.ui.main.HomeTab.LIVE_TV) {
                        val currentId = viewModel.activePreviewChannel.value?.streamId
                        if (currentId != null) {
                            viewModel.zapPreviousChannel(currentId)
                            return true
                        }
                    }
                }
                android.view.KeyEvent.KEYCODE_LAST_CHANNEL,
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    if (isPlayerScreen || (viewModel.deviceMode.value == DeviceMode.TV && viewModel.selectedHomeTab.value == com.tivimax.player.ui.main.HomeTab.LIVE_TV)) {
                        viewModel.zapRecall()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
            if (event.repeatCount == 0) {
                event.startTracking()
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
            val isPlayerScreen = ::viewModel.isInitialized && viewModel.currentScreen.value is Screen.Player
            val isLiveTv = ::viewModel.isInitialized &&
                viewModel.deviceMode.value == DeviceMode.TV &&
                viewModel.selectedHomeTab.value == com.tivimax.player.ui.main.HomeTab.LIVE_TV
            if (isPlayerScreen || isLiveTv) {
                viewModel.zapRecall()
                return true
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }
}
