package com.tivimax.player.ui.vm

import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.data.model.XtreamUserInfo
import com.tivimax.player.data.network.BoundedDns
import com.tivimax.player.data.playlist.PlaylistRepository
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.data.repository.XtreamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * G-054: Hesap/abonelik bilgisi, içerik sayıları ve bağlantı (hız) testi
 * (MainViewModel'den taşındı; mantık değişmedi). `userInfo` MainViewModel'de yaşar, kurucudan verilir.
 */
class AccountController(
    private val scope: CoroutineScope,
    private val repository: XtreamRepository,
    private val preferences: UserPreferences,
    private val playlistRepository: PlaylistRepository,
    private val userInfo: MutableStateFlow<XtreamUserInfo?>
) {
    // Hız Testi Durumu
    val speedTestResult = MutableStateFlow<String?>(null)
    val isTestingSpeed = MutableStateFlow(false)

    // Hesap ve Liste Bilgileri & İçerik Sayıları (MainViewModel'den taşındı)
    private val _accountContentCounts = MutableStateFlow<Triple<Int, Int, Int>?>(null)
    val accountContentCounts: StateFlow<Triple<Int, Int, Int>?> = _accountContentCounts.asStateFlow()

    private val _accountRefreshing = MutableStateFlow(false)
    val accountRefreshing: StateFlow<Boolean> = _accountRefreshing.asStateFlow()

    private var lastAccountRefreshTimeMs: Long = 0L

    /** Liste değişince hesap bilgisi hemen tazelenebilsin diye 5 dk'lık sessiz yenileme sınırını sıfırlar. */
    fun resetRefreshThrottle() {
        lastAccountRefreshTimeMs = 0L
    }

    fun loadAccountContentCounts() {
        scope.launch(Dispatchers.IO) {
            val liveCount = repository.database?.liveDao()?.getTotalStreamCount() ?: 0
            val vodCount = repository.database?.vodDao()?.getTotalStreamCount() ?: 0
            val seriesCount = repository.database?.seriesDao()?.getTotalStreamCount() ?: 0
            _accountContentCounts.value = Triple(liveCount, vodCount, seriesCount)
        }
    }

    fun refreshAccountInfo(
        silent: Boolean = false,
        onResult: ((success: Boolean, message: String) -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()
        if (silent && (now - lastAccountRefreshTimeMs) < 5 * 60 * 1000L) {
            return
        }

        scope.launch {
            _accountRefreshing.value = true
            try {
                val active = playlistRepository.getActivePlaylist()
                if (active == null) {
                    _accountRefreshing.value = false
                    if (!silent) {
                        onResult?.invoke(false, "Aktif liste bulunamadı")
                    }
                    return@launch
                }

                val user = active.username
                val pass = active.password
                val server = active.serverUrl
                val effectiveServer = if (server.isBlank() || server.contains("my-iptv-server")) {
                    AppConfig.DEFAULT_SERVER_URL
                } else {
                    server
                }

                if (user.isBlank() || pass.isBlank()) {
                    _accountRefreshing.value = false
                    if (!silent) {
                        onResult?.invoke(false, "Bilgiler alınamadı, bağlantınızı kontrol edin")
                    }
                    return@launch
                }

                val result = repository.authenticate(effectiveServer, user, pass)
                result.fold(
                    onSuccess = { authResp ->
                        lastAccountRefreshTimeMs = System.currentTimeMillis()
                        val uInfo = authResp.userInfo
                        if (uInfo != null) {
                            userInfo.value = uInfo
                            val currentExp = preferences.expDateFlow.first()
                            val currentMax = preferences.maxConnectionsFlow.first()
                            val currentActive = preferences.activeConnectionsFlow.first()
                            val currentStatus = preferences.statusFlow.first()
                            val currentTrial = preferences.isTrialFlow.first()
                            val currentCreated = preferences.createdAtFlow.first()

                            preferences.saveAuth(
                                username = user,
                                password = pass,
                                serverUrl = effectiveServer,
                                expDate = uInfo.expDate ?: currentExp,
                                maxCons = uInfo.maxConnections ?: currentMax,
                                activeCons = uInfo.activeCons ?: currentActive,
                                status = uInfo.status ?: currentStatus,
                                isTrial = uInfo.isTrial ?: currentTrial,
                                createdAt = uInfo.createdAt ?: currentCreated,
                                playlistId = active.id
                            )
                        }
                        if (!silent) {
                            onResult?.invoke(true, "Hesap bilgileri güncellendi")
                        }
                    },
                    onFailure = {
                        if (!silent) {
                            onResult?.invoke(false, "Bilgiler alınamadı, bağlantınızı kontrol edin")
                        }
                    }
                )
            } catch (_: Exception) {
                if (!silent) {
                    onResult?.invoke(false, "Bilgiler alınamadı, bağlantınızı kontrol edin")
                }
            } finally {
                _accountRefreshing.value = false
            }
        }
    }

    fun runSpeedTest() {
        scope.launch {
            isTestingSpeed.value = true
            speedTestResult.value = "Ölçülüyor…"

            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .dns(BoundedDns.instance)
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build()

                    val startTime = System.currentTimeMillis()
                    val req = Request.Builder().url(repository.serverUrl).head().build()
                    val resp = client.newCall(req).execute()
                    val ping = System.currentTimeMillis() - startTime

                    val quality = when {
                        ping < 150 -> "Bağlantı iyi (gecikme $ping ms)"
                        ping < 400 -> "Bağlantı orta (gecikme $ping ms)"
                        else -> "Bağlantı yavaş (gecikme $ping ms)"
                    }
                    speedTestResult.value = quality
                } catch (e: Exception) {
                    android.util.Log.w("MainViewModel", "Speed test failed", e)
                    speedTestResult.value = "Bağlantı kurulamadı. İnternetini kontrol et."
                } finally {
                    isTestingSpeed.value = false
                }
            }
        }
    }
}
