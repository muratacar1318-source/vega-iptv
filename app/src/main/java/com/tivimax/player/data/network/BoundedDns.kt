package com.tivimax.player.data.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Bounded DNS resolver with stale IP fallback.
 *
 * The Android platform DNS resolver (InetAddress.getAllByName) has no configurable timeout.
 * When an ISP blocks an IPTV domain, throttles UDP/DNS, or the network blips, a platform lookup
 * can block for up to 80 seconds, freezing the player on a black screen without failing or retrying.
 *
 * BoundedDns bounds the DNS lookup time:
 * - [timeoutMs] (default 5,000 ms) for initial lookups.
 * - [staleTimeoutMs] (default 1,000 ms) once a working IP is already known in [lastGood].
 * - In-flight deduplication: multiple concurrent lookups for the same hostname share the same query.
 * - Stale IP Fallback: if the lookup times out or fails (e.g. ISP DNS throttling/poisoning),
 *   it immediately returns the last working addresses for that host, keeping playback uninterrupted.
 */
class BoundedDns(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val staleTimeoutMs: Long = DEFAULT_STALE_TIMEOUT_MS,
    val delegate: Dns = Dns.SYSTEM,
    private val logFallback: ((String) -> Unit)? = null
) : Dns {

    companion object {
        private const val TAG = "BoundedDns"
        const val DEFAULT_TIMEOUT_MS = 5_000L
        const val DEFAULT_STALE_TIMEOUT_MS = 1_000L

        /** Singleton instance for app-wide DNS routing with shared stale cache */
        val instance by lazy { BoundedDns() }

        private fun logWarn(message: String) {
            try {
                android.util.Log.w(TAG, message)
            } catch (_: Throwable) {
                // JVM unit tests without android.util.Log mock
                System.err.println("[$TAG] $message")
            }
        }
    }

    private val lastGood = ConcurrentHashMap<String, List<InetAddress>>()
    private val inFlight = ConcurrentHashMap<String, Future<List<InetAddress>>>()
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "vegaplayer-dns").apply { isDaemon = true }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val mine = object : FutureTask<List<InetAddress>>(Callable { delegate.lookup(hostname) }) {
            override fun done() {
                inFlight.remove(hostname, this)
            }
        }
        val pending = inFlight.compute(hostname) { _, current ->
            if (current == null || current.isDone) mine else current
        }!!
        if (pending === mine) {
            executor.execute(mine)
        }

        val hasStale = lastGood.containsKey(hostname)
        val waitMs = if (hasStale) staleTimeoutMs else timeoutMs

        val failure: UnknownHostException = try {
            val result = pending.get(waitMs, TimeUnit.MILLISECONDS)
            if (result.isNotEmpty()) {
                lastGood[hostname] = result
            }
            return result
        } catch (e: TimeoutException) {
            UnknownHostException("DNS zaman aşımı ($waitMs ms): $hostname")
        } catch (e: java.util.concurrent.ExecutionException) {
            e.cause as? UnknownHostException ?: UnknownHostException(hostname).apply { initCause(e.cause) }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            UnknownHostException("DNS sorgusu kesildi: $hostname")
        }

        // DNS hatası veya zaman aşımı durumunda, hafızadaki son çalışan IP adreslerini kullan
        val stale = lastGood[hostname]
        if (!stale.isNullOrEmpty()) {
            val msg = "DNS sorgusu başarısız ($hostname), önbellekteki son geçerli IP kullanılıyor: ${stale.map { it.hostAddress }}"
            logWarn(msg)
            logFallback?.invoke(msg)
            return stale
        }

        throw failure
    }

    /** Put a known working address directly into the stale cache (useful for testing or bootstrap) */
    fun putStale(hostname: String, addresses: List<InetAddress>) {
        if (addresses.isNotEmpty()) {
            lastGood[hostname] = addresses
        }
    }

    /** Get cached addresses for a hostname if any */
    fun getStale(hostname: String): List<InetAddress>? = lastGood[hostname]

    /** Clear stale cache if needed (e.g. server address changed or logged out) */
    fun clearCache() {
        lastGood.clear()
    }
}
