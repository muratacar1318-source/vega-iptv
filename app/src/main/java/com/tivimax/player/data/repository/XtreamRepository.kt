package com.tivimax.player.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

import com.tivimax.player.data.network.FailoverInterceptor

class XtreamRepository {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(FailoverInterceptor())
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", "VegaIPTV/1.0 (Android; ExoPlayer; Mobile)")
                .header("Accept", "*/*")
                .build()
            chain.proceed(request)
        }
        .build()

    var serverUrl: String
        get() = FailoverInterceptor.currentWorkingBaseUrl
        set(value) {
            FailoverInterceptor.setWorkingBaseUrl(value)
        }

    var username: String = ""
    var password: String = ""

    fun updateCredentials(url: String, user: String, pass: String) {
        this.serverUrl = url.trimEnd('/')
        this.username = user.trim()
        this.password = pass.trim()
    }

    private fun buildApiUrl(action: String? = null, extraParams: Map<String, String> = emptyMap()): String {
        val base = "$serverUrl/player_api.php?username=$username&password=$password"
        val actionParam = if (action != null) "&action=$action" else ""
        val extra = extraParams.entries.joinToString("") { "&${it.key}=${it.value}" }
        return "$base$actionParam$extra"
    }

    suspend fun authenticate(url: String, user: String, pass: String): Result<XtreamAuthResponse> = withContext(Dispatchers.IO) {
        try {
            updateCredentials(url, user, pass)
            val apiUrl = buildApiUrl()
            val request = Request.Builder().url(apiUrl).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Sunucu hatası: Kod ${response.code}"))
            }

            val body = response.body?.string() ?: ""
            val authResponse = gson.fromJson(body, XtreamAuthResponse::class.java)

            if (authResponse?.userInfo?.auth == 1) {
                Result.success(authResponse)
            } else {
                val errorMsg = authResponse?.userInfo?.message ?: "Giriş başarısız. Kullanıcı adı veya şifre hatalı."
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Bağlantı hatası: ${e.localizedMessage ?: "Sunucuya ulaşılamadı"}"))
        }
    }

    suspend fun getLiveCategories(): Result<List<LiveCategory>> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl("get_live_categories")
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val type = object : TypeToken<List<LiveCategory>>() {}.type
            val categories: List<LiveCategory> = gson.fromJson(body, type) ?: emptyList()
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLiveStreams(categoryId: String? = null): Result<List<LiveStream>> = withContext(Dispatchers.IO) {
        try {
            val params = if (!categoryId.isNullOrEmpty() && categoryId != "all") mapOf("category_id" to categoryId) else emptyMap()
            val url = buildApiUrl("get_live_streams", params)
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val type = object : TypeToken<List<LiveStream>>() {}.type
            val streams: List<LiveStream> = gson.fromJson(body, type) ?: emptyList()
            Result.success(streams)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVodCategories(): Result<List<VodCategory>> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl("get_vod_categories")
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val type = object : TypeToken<List<VodCategory>>() {}.type
            val categories: List<VodCategory> = gson.fromJson(body, type) ?: emptyList()
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVodStreams(categoryId: String? = null): Result<List<VodStream>> = withContext(Dispatchers.IO) {
        try {
            val params = if (!categoryId.isNullOrEmpty() && categoryId != "all") mapOf("category_id" to categoryId) else emptyMap()
            val url = buildApiUrl("get_vod_streams", params)
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val type = object : TypeToken<List<VodStream>>() {}.type
            val streams: List<VodStream> = gson.fromJson(body, type) ?: emptyList()
            Result.success(streams)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVodInfo(vodId: Int): Result<VodInfoResponse> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl("get_vod_info", mapOf("vod_id" to vodId.toString()))
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val vodInfo = gson.fromJson(body, VodInfoResponse::class.java)
            Result.success(vodInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeriesCategories(): Result<List<SeriesCategory>> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl("get_series_categories")
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val type = object : TypeToken<List<SeriesCategory>>() {}.type
            val categories: List<SeriesCategory> = gson.fromJson(body, type) ?: emptyList()
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeriesStreams(categoryId: String? = null): Result<List<SeriesStream>> = withContext(Dispatchers.IO) {
        try {
            val params = if (!categoryId.isNullOrEmpty() && categoryId != "all") mapOf("category_id" to categoryId) else emptyMap()
            val url = buildApiUrl("get_series", params)
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val type = object : TypeToken<List<SeriesStream>>() {}.type
            val streams: List<SeriesStream> = gson.fromJson(body, type) ?: emptyList()
            Result.success(streams)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeriesInfo(seriesId: Int): Result<SeriesInfoResponse> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl("get_series_info", mapOf("series_id" to seriesId.toString()))
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val seriesInfo = gson.fromJson(body, SeriesInfoResponse::class.java)
            Result.success(seriesInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getShortEpg(streamId: Int): Result<List<EpgProgram>> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl("get_short_epg", mapOf("stream_id" to streamId.toString(), "limit" to "5"))
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val epgResponse = gson.fromJson(body, ShortEpgResponse::class.java)
            Result.success(epgResponse.epgListings ?: emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildLiveStreamUrls(streamId: Int, preferHls: Boolean = false): List<String> {
        val urls = mutableListOf<String>()
        val firstExt = if (preferHls) "m3u8" else "ts"
        val secondExt = if (preferHls) "ts" else "m3u8"

        // 1. Tercih edilen ve ikincil formatlar
        urls.add("$serverUrl/live/$username/$password/$streamId.$firstExt")
        urls.add("$serverUrl/live/$username/$password/$streamId.$secondExt")
        // 2. Ham akış URL'leri
        urls.add("$serverUrl/$username/$password/$streamId")
        urls.add("$serverUrl/live/$username/$password/$streamId")

        // 3. Yedek Sunucular (ISP engellemelerine karşı)
        AppConfig.FALLBACK_DOMAINS.forEach { backup ->
            val clean = backup.trimEnd('/')
            urls.add("$clean/live/$username/$password/$streamId.$firstExt")
            urls.add("$clean/live/$username/$password/$streamId.$secondExt")
        }
        return urls
    }

    fun buildLiveStreamUrl(streamId: Int, useHls: Boolean = false): String {
        val ext = if (useHls) "m3u8" else "ts"
        return "$serverUrl/live/$username/$password/$streamId.$ext"
    }

    fun buildVodStreamUrls(streamId: Int, extension: String = "mp4"): List<String> {
        val ext = extension.ifEmpty { "mp4" }
        val urls = mutableListOf<String>()
        urls.add("$serverUrl/movie/$username/$password/$streamId.$ext")
        if (ext != "mkv") urls.add("$serverUrl/movie/$username/$password/$streamId.mkv")
        if (ext != "mp4") urls.add("$serverUrl/movie/$username/$password/$streamId.mp4")
        return urls
    }

    fun buildVodStreamUrl(streamId: Int, extension: String = "mp4"): String {
        val ext = extension.ifEmpty { "mp4" }
        return "$serverUrl/movie/$username/$password/$streamId.$ext"
    }

    fun buildSeriesStreamUrls(streamId: String, extension: String = "mp4"): List<String> {
        val cleanExt = extension.trim().removePrefix(".").ifEmpty { "mp4" }
        val urls = mutableListOf<String>()
        urls.add("$serverUrl/series/$username/$password/$streamId.$cleanExt")
        if (cleanExt != "mkv") urls.add("$serverUrl/series/$username/$password/$streamId.mkv")
        if (cleanExt != "mp4") urls.add("$serverUrl/series/$username/$password/$streamId.mp4")
        if (cleanExt != "ts") urls.add("$serverUrl/series/$username/$password/$streamId.ts")
        return urls
    }

    fun buildSeriesStreamUrl(streamId: String, extension: String = "mp4"): String {
        val cleanExt = extension.trim().removePrefix(".").ifEmpty { "mp4" }
        return "$serverUrl/series/$username/$password/$streamId.$cleanExt"
    }
}
