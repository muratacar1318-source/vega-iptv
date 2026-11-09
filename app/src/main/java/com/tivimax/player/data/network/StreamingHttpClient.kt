package com.tivimax.player.data.network

import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Video ve canlı yayın akışları için özel olarak yapılandırılmış OkHttpClient.
 *
 * IPTV sağlayıcıları tek bağlantı (single-connection) kuralı uyguladığından,
 * kanal değişiminde veya oynatıcı durdurulduğunda eski yayına ait TCP soketi OkHttp
 * havuzunda boşta açık kalırsa, sunucu yeni kanal isteğini "Bağlantı sınırı aşıldı (429/458)"
 * gerekçesiyle reddeder.
 *
 * Bu sınıf:
 * 1. Yayınlar için bağımsız bir [ConnectionPool] barındırır.
 * 2. [evictAll] çağrıldığında yalnızca yayın bağlantılarını kapatır;
 *    afiş (Coil) ve API bağlantılarına dokunmaz, arayüz akıcılığını korur.
 * 3. [BoundedDns] ile DNS zaman aşımlarını sınırlandırır ve bayat IP ile kesintisiz bağlanır.
 */
class StreamingHttpClient(
    base: OkHttpClient? = null,
    val dns: Dns = BoundedDns.instance
) {
    companion object {
        private const val TAG = "StreamingHttpClient"
        private const val MAX_IDLE_CONNECTIONS = 5
        private const val KEEP_ALIVE_MINUTES = 5L

        private fun logDebug(message: String) {
            try {
                android.util.Log.d(TAG, message)
            } catch (_: Throwable) {
                // JVM test or logging fallback
            }
        }
    }

    private val pool = ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_MINUTES, TimeUnit.MINUTES)

    val client: OkHttpClient = (base?.newBuilder() ?: OkHttpClient.Builder())
        .connectionPool(pool)
        .dns(dns)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Boşta bekleyen tüm yayın soketlerini anında kapatır.
     * Kanal değişimlerinde, kanal durdurulduğunda veya oynatıcı sıfırlandığında çağrılır.
     * Bu sayede IPTV panelinde eski kanalın oturumu anında sonlanır ve yeni kanal "429 Too Many Requests"
     * veya "Max connections exceeded" hatası almadan hemen başlar.
     */
    fun evictAll() {
        try {
            pool.evictAll()
            logDebug("Yayın bağlantı havuzu boşaltıldı (evictAll).")
        } catch (_: Throwable) {}
    }

    /**
     * Havuzdaki mevcut boşta bağlantı sayısını döndürür (test ve teşhis amaçlı).
     */
    fun idleConnectionCount(): Int = pool.idleConnectionCount()

    /**
     * Havuzdaki toplam bağlantı sayısını döndürür.
     */
    fun connectionCount(): Int = pool.connectionCount()
}
