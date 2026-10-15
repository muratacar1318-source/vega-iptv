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
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.tivimax.player.theme.TiviMaxTheme
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.Screen

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private var wasPlayingBeforeStop = false

    private fun isTvDevice(): Boolean {
        val uiModeManager = getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        val hasNoTouch = !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
        return isTvMode || hasLeanback || hasNoTouch
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setContent {
            val appTheme by viewModel.appTheme.collectAsState()

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

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (::viewModel.isInitialized) {
            viewModel.isInPipMode.value = isInPictureInPictureMode
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isTvDevice()) {
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
            if (isTvDevice() || !inPip) {
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
            if (isTvDevice() || !inPip) {
                if (viewModel.playerManager.isPlaying.value) {
                    wasPlayingBeforeStop = true
                }
                viewModel.playerManager.pause()
            }
        }
    }

    override fun onResume() {
        super.onResume()
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
                android.view.KeyEvent.KEYCODE_0 -> { viewModel.onNumericKeyPressed(0); return true }
                android.view.KeyEvent.KEYCODE_1 -> { viewModel.onNumericKeyPressed(1); return true }
                android.view.KeyEvent.KEYCODE_2 -> { viewModel.onNumericKeyPressed(2); return true }
                android.view.KeyEvent.KEYCODE_3 -> { viewModel.onNumericKeyPressed(3); return true }
                android.view.KeyEvent.KEYCODE_4 -> { viewModel.onNumericKeyPressed(4); return true }
                android.view.KeyEvent.KEYCODE_5 -> { viewModel.onNumericKeyPressed(5); return true }
                android.view.KeyEvent.KEYCODE_6 -> { viewModel.onNumericKeyPressed(6); return true }
                android.view.KeyEvent.KEYCODE_7 -> { viewModel.onNumericKeyPressed(7); return true }
                android.view.KeyEvent.KEYCODE_8 -> { viewModel.onNumericKeyPressed(8); return true }
                android.view.KeyEvent.KEYCODE_9 -> { viewModel.onNumericKeyPressed(9); return true }
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
                    if (viewModel.isZapperActive.value) {
                        viewModel.executeZapperSwitch()
                        return true
                    }
                    if (event.isLongPress && isPlayerScreen) {
                        viewModel.zapRecall()
                        return true
                    }
                }
                android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (isPlayerScreen) {
                        val currentStreamId = (viewModel.currentScreen.value as Screen.Player).streamId
                        viewModel.zapNextChannel(currentStreamId)
                        return true
                    }
                }
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (isPlayerScreen) {
                        val currentStreamId = (viewModel.currentScreen.value as Screen.Player).streamId
                        viewModel.zapPreviousChannel(currentStreamId)
                        return true
                    }
                }
                android.view.KeyEvent.KEYCODE_LAST_CHANNEL,
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    if (isPlayerScreen) {
                        viewModel.zapRecall()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
