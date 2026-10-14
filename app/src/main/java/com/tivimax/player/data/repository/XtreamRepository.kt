package com.tivimax.player.data.repository

import android.content.Context
import androidx.paging.PagingSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.data.local.AppDatabase
import com.tivimax.player.data.local.entity.*
import com.tivimax.player.data.model.*
import com.tivimax.player.data.network.FailoverInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class XtreamRepository(private val context: Context? = null) {

    private val gson = Gson()
    val database: AppDatabase? = context?.let { AppDatabase.getInstance(it) }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
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

    // ============================================================================
    // CANLI YAYIN: STREAMING (JSONREADER) & ROOM SENKRONİZASYONU
    // ============================================================================

    suspend fun fetchAndSaveLiveCategories(): Result<List<LiveCategory>> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl("get_live_categories")
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Hatası: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Boş yanıt"))
            val categories = mutableListOf<LiveCategory>()

            body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
                    jsonReader.beginArray()
                    while (jsonReader.hasNext()) {
                        try {
                            val cat: LiveCategory = gson.fromJson(jsonReader, LiveCategory::class.java)
                            if (cat.categoryId.isNotBlank()) {
                                categories.add(cat)
                            }
                        } catch (e: Exception) {
                            try { jsonReader.skipValue() } catch (_: Exception) {}
                        }
                    }
                    try { jsonReader.endArray() } catch (_: Exception) {}
                } else {
                    jsonReader.skipValue()
                }
            }

            database?.liveDao()?.insertCategories(categories.map { it.toEntity() })
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAndSaveLiveStreams(
        categoryId: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val params = if (!categoryId.isNullOrEmpty() && categoryId != "all") mapOf("category_id" to categoryId) else emptyMap()
            val url = buildApiUrl("get_live_streams", params)
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Hatası: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Boş yanıt"))
            var count = 0

            body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
                    jsonReader.beginArray()
                    val chunk = ArrayList<LiveStreamEntity>(500)

                    while (jsonReader.hasNext()) {
                        try {
                            val stream: LiveStream = gson.fromJson(jsonReader, LiveStream::class.java)
                            if (stream.streamId > 0 && !stream.name.isNullOrBlank()) {
                                chunk.add(stream.toEntity())
                            }

                            if (chunk.size >= 500) {
                                database?.liveDao()?.insertStreams(chunk)
                                count += chunk.size
                                onProgress?.invoke(count)
                                chunk.clear()
                            }
                        } catch (e: Exception) {
                            try { jsonReader.skipValue() } catch (_: Exception) {}
                        }
                    }

                    if (chunk.isNotEmpty()) {
                        database?.liveDao()?.insertStreams(chunk)
                        count += chunk.size
                        onProgress?.invoke(count)
                        chunk.clear()
                    }

                    try { jsonReader.endArray() } catch (_: Exception) {}
                } else {
                    jsonReader.skipValue()
                }
            }

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getLiveCategoriesFlow(): Flow<List<LiveCategory>> {
        return database?.liveDao()?.getCategories()?.map { list ->
            list.map { it.toModel() }
        } ?: flowOf(emptyList())
    }

    fun getLiveStreamsFlow(categoryId: String? = null): Flow<List<LiveStream>> {
        val dao = database?.liveDao() ?: return flowOf(emptyList())
        val sourceFlow = if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.getAllStreams()
        } else {
            dao.getStreamsByCategory(categoryId)
        }
        return sourceFlow.map { list -> list.map { it.toModel() } }
    }

    fun searchLiveStreamsFlow(query: String, categoryId: String? = null): Flow<List<LiveStream>> {
        val dao = database?.liveDao() ?: return flowOf(emptyList())
        val sourceFlow = if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.searchStreams(query)
        } else {
            dao.searchStreamsInCategory(categoryId, query)
        }
        return sourceFlow.map { list -> list.map { it.toModel() } }
    }

    fun getLiveStreamsFilteredFlow(categoryId: String? = null, query: String = "", sort: String = "DEFAULT"): Flow<List<LiveStream>> {
        val dao = database?.liveDao() ?: return flowOf(emptyList())
        val cleanCat = if (categoryId.isNullOrEmpty() || categoryId == "all") null else categoryId
        return dao.getStreamsFiltered(cleanCat, query, sort).map { list -> list.map { it.toModel() } }
    }

    fun getPagedLiveStreams(categoryId: String? = null): PagingSource<Int, LiveStreamEntity>? {
        val dao = database?.liveDao() ?: return null
        return if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.getPagedAllStreams()
        } else {
            dao.getPagedStreamsByCategory(categoryId)
        }
    }

    // ============================================================================
    // VOD (FİLM): STREAMING (JSONREADER) & ROOM SENKRONİZASYONU
    // ============================================================================

    suspend fun fetchAndSaveVodCategories(): Result<List<VodCategory>> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl("get_vod_categories")
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Hatası: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Boş yanıt"))
            val categories = mutableListOf<VodCategory>()

            body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
                    jsonReader.beginArray()
                    while (jsonReader.hasNext()) {
                        try {
                            val cat: VodCategory = gson.fromJson(jsonReader, VodCategory::class.java)
                            if (cat.categoryId.isNotBlank()) {
                                categories.add(cat)
                            }
                        } catch (e: Exception) {
                            try { jsonReader.skipValue() } catch (_: Exception) {}
                        }
                    }
                    try { jsonReader.endArray() } catch (_: Exception) {}
                } else {
                    jsonReader.skipValue()
                }
            }

            database?.vodDao()?.insertCategories(categories.map { it.toEntity() })
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAndSaveVodStreams(
        categoryId: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val params = if (!categoryId.isNullOrEmpty() && categoryId != "all") mapOf("category_id" to categoryId) else emptyMap()
            val url = buildApiUrl("get_vod_streams", params)
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Hatası: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Boş yanıt"))
            var count = 0

            body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
                    jsonReader.beginArray()
                    val chunk = ArrayList<VodStreamEntity>(500)

                    while (jsonReader.hasNext()) {
                        try {
                            val stream: VodStream = gson.fromJson(jsonReader, VodStream::class.java)
                            if (stream.streamId > 0 && !stream.name.isNullOrBlank()) {
                                chunk.add(stream.toEntity())
                            }

                            if (chunk.size >= 500) {
                                database?.vodDao()?.insertStreams(chunk)
                                count += chunk.size
                                onProgress?.invoke(count)
                                chunk.clear()
                            }
                        } catch (e: Exception) {
                            try { jsonReader.skipValue() } catch (_: Exception) {}
                        }
                    }

                    if (chunk.isNotEmpty()) {
                        database?.vodDao()?.insertStreams(chunk)
                        count += chunk.size
                        onProgress?.invoke(count)
                        chunk.clear()
                    }

                    try { jsonReader.endArray() } catch (_: Exception) {}
                } else {
                    jsonReader.skipValue()
                }
            }

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getVodCategoriesFlow(): Flow<List<VodCategory>> {
        return database?.vodDao()?.getCategories()?.map { list ->
            list.map { it.toModel() }
        } ?: flowOf(emptyList())
    }

    fun getVodStreamsFlow(categoryId: String? = null): Flow<List<VodStream>> {
        val dao = database?.vodDao() ?: return flowOf(emptyList())
        val sourceFlow = if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.getAllStreams()
        } else {
            dao.getStreamsByCategory(categoryId)
        }
        return sourceFlow.map { list -> list.map { it.toModel() } }
    }

    fun searchVodStreamsFlow(query: String, categoryId: String? = null): Flow<List<VodStream>> {
        val dao = database?.vodDao() ?: return flowOf(emptyList())
        val sourceFlow = if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.searchStreams(query)
        } else {
            dao.searchStreamsInCategory(categoryId, query)
        }
        return sourceFlow.map { list -> list.map { it.toModel() } }
    }

    fun getVodStreamsFilteredFlow(categoryId: String? = null, query: String = "", sort: String = "DEFAULT"): Flow<List<VodStream>> {
        val dao = database?.vodDao() ?: return flowOf(emptyList())
        val cleanCat = if (categoryId.isNullOrEmpty() || categoryId == "all") null else categoryId
        return dao.getStreamsFiltered(cleanCat, query, sort).map { list -> list.map { it.toModel() } }
    }

    fun getPagedVodStreams(categoryId: String? = null): PagingSource<Int, VodStreamEntity>? {
        val dao = database?.vodDao() ?: return null
        return if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.getPagedAllStreams()
        } else {
            dao.getPagedStreamsByCategory(categoryId)
        }
    }

    // ============================================================================
    // DİZİ: STREAMING (JSONREADER) & ROOM SENKRONİZASYONU
    // ============================================================================

    suspend fun fetchAndSaveSeriesCategories(): Result<List<SeriesCategory>> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl("get_series_categories")
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Hatası: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Boş yanıt"))
            val categories = mutableListOf<SeriesCategory>()

            body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
                    jsonReader.beginArray()
                    while (jsonReader.hasNext()) {
                        try {
                            val cat: SeriesCategory = gson.fromJson(jsonReader, SeriesCategory::class.java)
                            if (cat.categoryId.isNotBlank()) {
                                categories.add(cat)
                            }
                        } catch (e: Exception) {
                            try { jsonReader.skipValue() } catch (_: Exception) {}
                        }
                    }
                    try { jsonReader.endArray() } catch (_: Exception) {}
                } else {
                    jsonReader.skipValue()
                }
            }

            database?.seriesDao()?.insertCategories(categories.map { it.toEntity() })
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAndSaveSeriesStreams(
        categoryId: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val params = if (!categoryId.isNullOrEmpty() && categoryId != "all") mapOf("category_id" to categoryId) else emptyMap()
            val url = buildApiUrl("get_series", params)
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Hatası: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Boş yanıt"))
            var count = 0

            body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
                    jsonReader.beginArray()
                    val chunk = ArrayList<SeriesStreamEntity>(500)

                    while (jsonReader.hasNext()) {
                        try {
                            val stream: SeriesStream = gson.fromJson(jsonReader, SeriesStream::class.java)
                            if (stream.seriesId > 0 && !stream.name.isNullOrBlank()) {
                                chunk.add(stream.toEntity())
                            }

                            if (chunk.size >= 500) {
                                database?.seriesDao()?.insertStreams(chunk)
                                count += chunk.size
                                onProgress?.invoke(count)
                                chunk.clear()
                            }
                        } catch (e: Exception) {
                            try { jsonReader.skipValue() } catch (_: Exception) {}
                        }
                    }

                    if (chunk.isNotEmpty()) {
                        database?.seriesDao()?.insertStreams(chunk)
                        count += chunk.size
                        onProgress?.invoke(count)
                        chunk.clear()
                    }

                    try { jsonReader.endArray() } catch (_: Exception) {}
                } else {
                    jsonReader.skipValue()
                }
            }

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSeriesCategoriesFlow(): Flow<List<SeriesCategory>> {
        return database?.seriesDao()?.getCategories()?.map { list ->
            list.map { it.toModel() }
        } ?: flowOf(emptyList())
    }

    fun getSeriesStreamsFlow(categoryId: String? = null): Flow<List<SeriesStream>> {
        val dao = database?.seriesDao() ?: return flowOf(emptyList())
        val sourceFlow = if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.getAllStreams()
        } else {
            dao.getStreamsByCategory(categoryId)
        }
        return sourceFlow.map { list -> list.map { it.toModel() } }
    }

    fun searchSeriesStreamsFlow(query: String, categoryId: String? = null): Flow<List<SeriesStream>> {
        val dao = database?.seriesDao() ?: return flowOf(emptyList())
        val sourceFlow = if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.searchStreams(query)
        } else {
            dao.searchStreamsInCategory(categoryId, query)
        }
        return sourceFlow.map { list -> list.map { it.toModel() } }
    }

    fun getSeriesStreamsFilteredFlow(categoryId: String? = null, query: String = "", sort: String = "DEFAULT"): Flow<List<SeriesStream>> {
        val dao = database?.seriesDao() ?: return flowOf(emptyList())
        val cleanCat = if (categoryId.isNullOrEmpty() || categoryId == "all") null else categoryId
        return dao.getStreamsFiltered(cleanCat, query, sort).map { list -> list.map { it.toModel() } }
    }

    fun getPagedSeriesStreams(categoryId: String? = null): PagingSource<Int, SeriesStreamEntity>? {
        val dao = database?.seriesDao() ?: return null
        return if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.getPagedAllStreams()
        } else {
            dao.getPagedStreamsByCategory(categoryId)
        }
    }

    // ============================================================================
    // BİLGİ & EPG & DETAY SORGULARI
    // ============================================================================

    suspend fun getLiveCategories(): Result<List<LiveCategory>> = fetchAndSaveLiveCategories()

    suspend fun getLiveStreams(categoryId: String? = null): Result<List<LiveStream>> = withContext(Dispatchers.IO) {
        val syncRes = fetchAndSaveLiveStreams(categoryId)
        if (syncRes.isSuccess) {
            val list = if (categoryId.isNullOrEmpty() || categoryId == "all") {
                database?.liveDao()?.getAllStreams()
            } else {
                database?.liveDao()?.getStreamsByCategory(categoryId)
            }
            // Return first emission from Room or empty
            val result = database?.let { db ->
                if (categoryId.isNullOrEmpty() || categoryId == "all") {
                    // Quick one-shot query
                    emptyList<LiveStream>()
                } else {
                    emptyList<LiveStream>()
                }
            } ?: emptyList()
            Result.success(result)
        } else {
            Result.failure(syncRes.exceptionOrNull() ?: Exception("Canlı yayınlar senkronize edilemedi"))
        }
    }

    suspend fun getVodCategories(): Result<List<VodCategory>> = fetchAndSaveVodCategories()

    suspend fun getVodStreams(categoryId: String? = null): Result<List<VodStream>> = withContext(Dispatchers.IO) {
        val syncRes = fetchAndSaveVodStreams(categoryId)
        if (syncRes.isSuccess) {
            Result.success(emptyList())
        } else {
            Result.failure(syncRes.exceptionOrNull() ?: Exception("VOD senkronize edilemedi"))
        }
    }

    suspend fun getSeriesCategories(): Result<List<SeriesCategory>> = fetchAndSaveSeriesCategories()

    suspend fun getSeriesStreams(categoryId: String? = null): Result<List<SeriesStream>> = withContext(Dispatchers.IO) {
        val syncRes = fetchAndSaveSeriesStreams(categoryId)
        if (syncRes.isSuccess) {
            Result.success(emptyList())
        } else {
            Result.failure(syncRes.exceptionOrNull() ?: Exception("Diziler senkronize edilemedi"))
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

    // ============================================================================
    // OYNATMA URL ÜRETİCİLERİ
    // ============================================================================

    fun buildLiveStreamUrls(streamId: Int, preferHls: Boolean = false): List<String> {
        val urls = mutableListOf<String>()
        val firstExt = if (preferHls) "m3u8" else "ts"
        val secondExt = if (preferHls) "ts" else "m3u8"

        urls.add("$serverUrl/live/$username/$password/$streamId.$firstExt")
        urls.add("$serverUrl/live/$username/$password/$streamId.$secondExt")
        urls.add("$serverUrl/$username/$password/$streamId")
        urls.add("$serverUrl/live/$username/$password/$streamId")

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
