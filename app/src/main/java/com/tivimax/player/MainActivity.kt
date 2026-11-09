package com.tivimax.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
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

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_PLAY_PAUSE && ::viewModel.isInitialized) {
                viewModel.playerManager.togglePlayPause()
                updatePipActions()
            }
        }
    }

    fun updatePipActions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode && ::viewModel.isInitialized) {
            try {
                val isPlaying = viewModel.playerManager.isPlaying.value
                val action = createPipPlayPauseAction(this, isPlaying)
                val rational = calculatePipAspectRatio(viewModel.playerManager.player)
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(rational)
                if (action != null) {
                    builder.setActions(listOf(action))
                }
                setPictureInPictureParams(builder.build())
            } catch (_: Throwable) {}
        }
    }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val filter = IntentFilter(ACTION_PIP_PLAY_PAUSE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipActionReceiver, filter)
            }
        }

        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            val deviceMode by viewModel.deviceMode.collectAsState()
            val currentScreen by viewModel.currentScreen.collectAsState()
            val inPip by viewModel.isInPipMode.collectAsState()
            val isVideoFullscreen by viewModel.isVideoFullscreen.collectAsState()
            val isPlaying by viewModel.playerManager.isPlaying.collectAsState()

            LaunchedEffect(inPip, isPlaying) {
                if (inPip) {
                    updatePipActions()
                }
            }

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
            if (isInPictureInPictureMode) {
                // Küçük pencereye (PiP) geçildi: Oynatmanın kesintisiz devam etmesini sağla
                if (wasPlayingBeforeStop || !viewModel.playerManager.isPlaying.value) {
                    viewModel.playerManager.play()
                }
                wasPlayingBeforeStop = false
                // Çift ekran açıksa tek bağlantı kuralı için ikincil ekranı kapat
                if (viewModel.playerManager.isDualViewActive.value) {
                    viewModel.playerManager.closeDualView()
                }
                updatePipActions()
            } else {
                // G-031: Küçük pencere X ile kapatıldıysa (aktivite artık görünür değil) oynatmayı durdur
                if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    // Kullanıcı küçük pencereyi X ile kapattı. IPTV sağlayıcısında tek bağlantı kuralı
                    // olduğundan soketi tamamen kapatıp bağlantıyı serbest bırakıyoruz.
                    viewModel.playerManager.stopAndClear()
                    wasPlayingBeforeStop = false
                }
                if (::orientationManager.isInitialized) {
                    orientationManager.applyOrientation(
                        viewModel.deviceMode.value,
                        viewModel.currentScreen.value,
                        false,
                        viewModel.isVideoFullscreen.value
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        val inMultiWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInMultiWindowMode else false
        if (!inPip && !inMultiWindow && hasFocus && ::viewModel.isInitialized && ::orientationManager.isInitialized) {
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
        val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        val inMultiWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInMultiWindowMode else false
        if (!inPip && !inMultiWindow && ::viewModel.isInitialized && ::orientationManager.isInitialized) {
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
                val isLive = viewModel.playerManager.isLiveStream
                if (hasPip && autoPip && isPlayerScreen && isPlaying && isLive) {
                    val rational = calculatePipAspectRatio(viewModel.playerManager.player)
                    val builder = PictureInPictureParams.Builder()
                        .setAspectRatio(rational)
                    val action = createPipPlayPauseAction(this, isPlaying)
                    if (action != null) {
                        builder.setActions(listOf(action))
                    }
                    enterPictureInPictureMode(builder.build())
                }
            } catch (e: Throwable) {
                // Ignore unsupported devices / ROMs
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::viewModel.isInitialized) {
            // TV modunda ekran her zaman tektir; odak kaybında anında duraklatılır.
            // Mobil/tablette ise küçük pencereye (PiP) geçiş anında ve çoklu pencerede (floating window / split screen)
            // video donmasını/kesilmesini engellemek için duraklatma onStop'a bırakılır.
            if (isTvModeActive()) {
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
            val inMultiWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInMultiWindowMode else false
            // Kullanıcı uygulamadan tamamen ayrıldığında (PiP veya çoklu pencerede değilse) oynatıcıyı duraklat
            if (isTvModeActive() || (!inPip && !inMultiWindow)) {
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
            if ((isPlayerScreen || isHomeScreen) && viewModel.playerManager.isLiveStream) {
                try {
                    viewModel.playerManager.player?.seekToDefaultPosition()
                } catch (_: Throwable) {}
                viewModel.playerManager.play()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                unregisterReceiver(pipActionReceiver)
            } catch (_: Exception) {}
        }
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
                android.view.KeyEvent.KEYCODE_9,
                android.view.KeyEvent.KEYCODE_NUMPAD_0,
                android.view.KeyEvent.KEYCODE_NUMPAD_1,
                android.view.KeyEvent.KEYCODE_NUMPAD_2,
                android.view.KeyEvent.KEYCODE_NUMPAD_3,
                android.view.KeyEvent.KEYCODE_NUMPAD_4,
                android.view.KeyEvent.KEYCODE_NUMPAD_5,
                android.view.KeyEvent.KEYCODE_NUMPAD_6,
                android.view.KeyEvent.KEYCODE_NUMPAD_7,
                android.view.KeyEvent.KEYCODE_NUMPAD_8,
                android.view.KeyEvent.KEYCODE_NUMPAD_9 -> {
                    // Sadece mobil tam ekran oynatıcıda yakala; diğer ekranlarda (Giriş, Arama, PIN)
                    // ve TV rehberinde Compose katmanına bırak (yazı alanları çalışsın)
                    if (isPlayerScreen) {
                        val digit = if (event.keyCode in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9) {
                            event.keyCode - android.view.KeyEvent.KEYCODE_NUMPAD_0
                        } else {
                            event.keyCode - android.view.KeyEvent.KEYCODE_0
                        }
                        viewModel.onNumericKeyPressed(digit)
                        return true
                    }
                }
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (viewModel.isZapperActive.value) {
                        viewModel.executeZapperSwitch()
                        return true
                    }
                    if (event.repeatCount == 0) {
                        event.startTracking()
                    }
                }
                android.view.KeyEvent.KEYCODE_CHANNEL_UP,
                android.view.KeyEvent.KEYCODE_PAGE_UP -> {
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
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
                android.view.KeyEvent.KEYCODE_PAGE_DOWN -> {
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
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (isPlayerScreen) {
                        viewModel.playerManager.togglePlayPause()
                        return true
                    }
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    if (isPlayerScreen) {
                        viewModel.playerManager.play()
                        return true
                    }
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    if (isPlayerScreen) {
                        viewModel.playerManager.pause()
                        return true
                    }
                }
                android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                    if (isPlayerScreen) {
                        viewModel.playerManager.stop()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
            keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.repeatCount == 0) {
                event.startTracking()
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
            keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
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

    companion object {
        const val ACTION_PIP_PLAY_PAUSE = "com.tivimax.player.PIP_PLAY_PAUSE"

        fun calculatePipAspectRatio(player: androidx.media3.common.Player?): Rational {
            val size = player?.videoSize
            if (size != null && size.width > 0 && size.height > 0) {
                val w = size.width
                val h = size.height
                val ratio = w.toFloat() / h.toFloat()
                if (ratio in 0.42f..2.38f) {
                    return Rational(w, h)
                }
            }
            return Rational(16, 9)
        }

        fun createPipPlayPauseAction(context: Context, isPlaying: Boolean): RemoteAction? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
            val iconRes = if (isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
            val title = if (isPlaying) "Duraklat" else "Oynat"
            val intent = Intent(ACTION_PIP_PLAY_PAUSE).setPackage(context.packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 101, intent, flags)
            return RemoteAction(
                Icon.createWithResource(context, iconRes),
                title,
                title,
                pendingIntent
            )
        }
    }
}
