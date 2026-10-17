package com.tivimax.player.player

import android.os.SystemClock
import android.util.Log
import com.tivimax.player.BuildConfig
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.data.preferences.UserPreferences
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * VOD Akıllı Ön Isıtma (Prewarming) Yöneticisi
 *
 * Xtream Codes sunucularında VOD (film ve dizi) açılış sürelerini kısaltmak için,
 * kullanıcı detay ekranında beklerken dosyanın ilk 64 KB'lık başlığını sunucudan ister.
 *
 * EMNİYET & TEK BAĞLANTI İLKELERİ:
 * - Aynı anda asla birden fazla ön ısıtma isteği yapılmaz.
 * - Kullanıcı oynata bastığında veya ekrandan çıktığında süren istek hemen iptal edilir.
 * - Player meşgulken (oynatırken/tamponlarken/mini player/dual view) ön ısıtma yapılmaz.
 * - 60 saniyelik URL cooldown uygulanır.
 * - Demo içerikler (101, 102, 103, 201) atlanır.
 * - Circuit Breaker: Ön ısıtma sonrası 15 saniyede VOD oynatıcı üst üste 2 kez HTTP 403/429/509
 *   alırsa, ön ısıtma kalıcı olarak devre dışı bırakılır (tek bağlantı kotasını aşmamak için).
 */
class VodPrewarmer(
    private val playerManager: TiviPlayerManager,
    private val preferences: UserPreferences,
    private val coroutineScope: CoroutineScope,
    private val isBusyProvider: () -> Boolean
) {
    companion object {
        private const val TAG_TIMING = "VodTiming"
        private const val COOLDOWN_MS = 60_000L
        private const val CIRCUIT_BREAKER_WINDOW_MS = 15_000L
    }

    private val prewarmClient: OkHttpClient by lazy {
        playerManager.baseOkHttpClient.newBuilder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Volatile
    private var isCircuitBreakerTripped: Boolean = false

    @Volatile
    private var lastPrewarmCompletedElapsedMs: Long = 0L

    @Volatile
    private var consecutiveAuthFailures: Int = 0

    private val urlCooldownMap = ConcurrentHashMap<String, Long>()

    private var currentPrewarmJob: Job? = null
    private var currentCall: Call? = null
    private val jobLock = Any()

    init {
        coroutineScope.launch {
            preferences.vodPrewarmDisabledFlow.collect { disabled ->
                isCircuitBreakerTripped = disabled
            }
        }
    }

    fun schedulePrewarm(url: String, streamId: Int? = null) {
        if (!AppConfig.ENABLE_VOD_PREWARM) {
            return
        }

        if (isCircuitBreakerTripped) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG_TIMING, "Ön ısıtma atlandı: Circuit breaker devrede (kalıcı kapalı)")
            }
            return
        }

        if (streamId != null && streamId in listOf(101, 102, 103, 201)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG_TIMING, "Ön ısıtma atlandı: Demo içerik ($streamId)")
            }
            return
        }

        if (url.isBlank() || url.contains("commondatastorage.googleapis.com")) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        val lastPrewarm = urlCooldownMap[url]
        if (lastPrewarm != null && (now - lastPrewarm) < COOLDOWN_MS) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG_TIMING, "Ön ısıtma atlandı (cooldown 60s): ${TiviPlayerManager.maskUrl(url)}")
            }
            return
        }

        if (isBusyProvider()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG_TIMING, "Ön ısıtma atlandı (player meşgul)")
            }
            return
        }

        synchronized(jobLock) {
            cancelAllInternal("Yeni ön ısıtma planlandı")

            currentPrewarmJob = coroutineScope.launch(Dispatchers.IO) {
                delay(1500L)
                if (!isActive) return@launch

                if (isBusyProvider()) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG_TIMING, "Ön ısıtma iptal edildi (neden: player meşgul)")
                    }
                    return@launch
                }

                executePrewarm(url)
            }
        }
    }

    private fun executePrewarm(url: String) {
        val maskedUrl = TiviPlayerManager.maskUrl(url)
        val startTime = SystemClock.elapsedRealtime()

        if (BuildConfig.DEBUG) {
            Log.d(TAG_TIMING, "Ön ısıtma başladı: url=$maskedUrl")
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", TiviPlayerManager.USER_AGENT)
            .header("Range", "bytes=0-65535")
            .header("Connection", "close")
            .build()

        val call = prewarmClient.newCall(request)
        synchronized(jobLock) {
            currentCall = call
        }

        try {
            val response = call.execute()
            val durationMs = SystemClock.elapsedRealtime() - startTime
            val code = response.code
            // Yanıt geldiğinde: response body'yi OKUMA, hemen response.close() çağır
            response.close()

            urlCooldownMap[url] = SystemClock.elapsedRealtime()
            lastPrewarmCompletedElapsedMs = SystemClock.elapsedRealtime()

            if (BuildConfig.DEBUG) {
                Log.d(TAG_TIMING, "Ön ısıtma tamamlandı (HTTP $code, süre: ${durationMs}ms, url=$maskedUrl)")
            }
        } catch (e: Exception) {
            val durationMs = SystemClock.elapsedRealtime() - startTime
            if (call.isCanceled()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG_TIMING, "Ön ısıtma iptal edildi: url=$maskedUrl (süre: ${durationMs}ms)")
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG_TIMING, "Ön ısıtma hatası (${e.javaClass.simpleName}: ${e.message}, süre: ${durationMs}ms, url=$maskedUrl)")
                }
            }
        } finally {
            synchronized(jobLock) {
                if (currentCall == call) {
                    currentCall = null
                }
            }
        }
    }

    fun cancelAll(reason: String = "") {
        synchronized(jobLock) {
            cancelAllInternal(reason)
        }
    }

    private fun cancelAllInternal(reason: String) {
        val hasActiveWork = (currentPrewarmJob?.isActive == true) || (currentCall != null && !currentCall!!.isCanceled())
        if (hasActiveWork && BuildConfig.DEBUG && reason.isNotEmpty()) {
            Log.d(TAG_TIMING, "Ön ısıtma iptal edildi (neden: $reason)")
        }
        currentPrewarmJob?.cancel()
        currentPrewarmJob = null
        currentCall?.cancel()
        currentCall = null
    }

    fun onPlaybackAuthError(httpCode: Int) {
        val now = SystemClock.elapsedRealtime()
        if (lastPrewarmCompletedElapsedMs > 0 && (now - lastPrewarmCompletedElapsedMs) <= CIRCUIT_BREAKER_WINDOW_MS) {
            consecutiveAuthFailures++
            if (BuildConfig.DEBUG) {
                Log.w(TAG_TIMING, "Ön ısıtma sonrası VOD oynatmada HTTP $httpCode hatası alındı (ardışık: $consecutiveAuthFailures/2)")
            }
            if (consecutiveAuthFailures >= 2) {
                Log.w(TAG_TIMING, "Ön ısıtma sunucu bağlantı limitini aşıyor, kalıcı kapatıldı")
                isCircuitBreakerTripped = true
                coroutineScope.launch {
                    preferences.setVodPrewarmDisabled(true)
                }
            }
        }
    }

    fun onPlaybackSuccess() {
        consecutiveAuthFailures = 0
    }
}
