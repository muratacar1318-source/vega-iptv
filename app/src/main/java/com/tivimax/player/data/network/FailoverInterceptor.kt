package com.tivimax.player.data.network

import android.util.Log
import com.tivimax.player.data.config.AppConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Otomatik Sunucu Yönlendirme (Failover) Interceptor:
 * Ana sunucuya ulaşılamadığında (zaman aşımı, DNS hatası, bağlantı reddi, SSL hatası veya 5xx sunucu hataları),
 * isteği otomatik olarak [AppConfig.FALLBACK_DOMAINS] listesindeki çalışan bir yedek sunucuya yönlendirir.
 * Başarılı olan yeni sunucu adresini oturum boyunca geçerli varsayılan adres yapar.
 */
class FailoverInterceptor : Interceptor {

    companion object {
        private const val TAG = "FailoverInterceptor"

        // Oturum boyunca çalışan aktif sunucu adresi
        @Volatile
        var currentWorkingBaseUrl: String = AppConfig.DEFAULT_SERVER_URL
            private set

        fun setWorkingBaseUrl(url: String) {
            val clean = url.trimEnd('/')
            if (clean.isNotBlank()) {
                currentWorkingBaseUrl = clean
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        // Eğer istek hedefi bilinen sunucu domainlerimizden biriyse ve aktif çalışan domain farklıysa,
        // isteği doğrudan şu an aktif çalışan domaine yönlendirerek başlat
        val activeHttpUrl = currentWorkingBaseUrl.toHttpUrlOrNull()
        val initialRequest = if (activeHttpUrl != null && isManagedHost(originalUrl)) {
            rewriteUrl(originalRequest, activeHttpUrl)
        } else {
            originalRequest
        }

        var response: Response? = null
        var lastException: IOException? = null

        // 1. İlk deneme
        try {
            response = chain.proceed(initialRequest)
            if (response.isSuccessful || !isServerError(response.code)) {
                return response
            }
            Log.w(TAG, "Request to ${initialRequest.url} returned server error: ${response.code}")
        } catch (e: IOException) {
            if (isNetworkFailure(e)) {
                Log.w(TAG, "Request to ${initialRequest.url} failed: ${e.javaClass.simpleName} - ${e.message}")
                lastException = e
            } else {
                throw e
            }
        }

        // Eğer istek failover yönetimi altında olmayan bir adrese gittiyse hatayı dön
        if (!isManagedHost(initialRequest.url)) {
            response?.let { return it }
            throw lastException ?: IOException("Request failed without response")
        }

        // 2. Başarısızlık durumunda Fallback listesini hazırla
        val candidateDomains = LinkedHashSet<String>().apply {
            add(AppConfig.DEFAULT_SERVER_URL)
            addAll(AppConfig.FALLBACK_DOMAINS)
            add(currentWorkingBaseUrl)
        }.map { it.trimEnd('/') }.filter { it.isNotBlank() }

        val failedOrigin = "${initialRequest.url.scheme}://${initialRequest.url.host}:${initialRequest.url.port}"

        for (candidate in candidateDomains) {
            val candidateHttpUrl = candidate.toHttpUrlOrNull() ?: continue
            val candidateOrigin = "${candidateHttpUrl.scheme}://${candidateHttpUrl.host}:${candidateHttpUrl.port}"

            // Az önce başarısız olan sunucu ve protokolü atla
            if (candidateOrigin.equals(failedOrigin, ignoreCase = true)) {
                continue
            }

            Log.i(TAG, "Failing over request from $failedOrigin to $candidate")
            response?.close() // Önceki 5xx cevabı kapat

            val failoverRequest = rewriteUrl(originalRequest, candidateHttpUrl)
            try {
                val candidateResponse = chain.proceed(failoverRequest)
                if (candidateResponse.isSuccessful || !isServerError(candidateResponse.code)) {
                    Log.i(TAG, "Failover successful! New session-wide active domain: $candidate")
                    setWorkingBaseUrl(candidate)
                    return candidateResponse
                } else {
                    Log.w(TAG, "Fallback candidate $candidate returned code ${candidateResponse.code}")
                    response = candidateResponse
                }
            } catch (e: IOException) {
                if (isNetworkFailure(e)) {
                    Log.w(TAG, "Fallback candidate $candidate failed: ${e.javaClass.simpleName} - ${e.message}")
                    lastException = e
                } else {
                    throw e
                }
            }
        }

        // Tüm adaylar tüketildiyse son cevabı dön veya hatayı fırlat
        response?.let { return it }
        throw lastException ?: IOException("All fallback domains exhausted for request to ${originalRequest.url}")
    }

    private fun rewriteUrl(request: Request, targetBase: HttpUrl): Request {
        val newHttpUrl = request.url.newBuilder()
            .scheme(targetBase.scheme)
            .host(targetBase.host)
            .port(targetBase.port)
            .build()
        return request.newBuilder()
            .url(newHttpUrl)
            .build()
    }

    private fun isManagedHost(url: HttpUrl): Boolean {
        val currentHost = url.host.lowercase()
        val managedHosts = LinkedHashSet<String>().apply {
            AppConfig.DEFAULT_SERVER_URL.toHttpUrlOrNull()?.host?.lowercase()?.let { add(it) }
            AppConfig.FALLBACK_DOMAINS.forEach { domain ->
                domain.toHttpUrlOrNull()?.host?.lowercase()?.let { add(it) }
            }
            currentWorkingBaseUrl.toHttpUrlOrNull()?.host?.lowercase()?.let { add(it) }
        }
        return managedHosts.contains(currentHost)
    }


    private fun isServerError(code: Int): Boolean {
        return code in 500..599
    }

    private fun isNetworkFailure(e: IOException): Boolean {
        return e is SocketTimeoutException ||
                e is UnknownHostException ||
                e is ConnectException ||
                e is SocketException ||
                e is SSLException
    }
}
