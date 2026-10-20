package com.tivimax.player.data.repository

import android.content.Context
import androidx.paging.PagingSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.tivimax.player.BuildConfig
import com.tivimax.player.data.config.AppConfig
import com.tivimax.player.data.local.AppDatabase
import com.tivimax.player.data.local.entity.*
import com.tivimax.player.data.model.*
import com.tivimax.player.data.network.FailoverInterceptor
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class CategorySyncResult(
    val liveCount: Int,
    val vodCount: Int,
    val seriesCount: Int,
    val totalCount: Int
)
class XtreamRepository(private val context: Context? = null) {
    private val gson = Gson()

    var database: AppDatabase? = context?.let { AppDatabase.getInstance(it) }
        private set

    private val _activeDb = MutableStateFlow<AppDatabase?>(database)
    val activeDbFlow: StateFlow<AppDatabase?> = _activeDb.asStateFlow()

    fun switchDatabase(dbName: String) {
        val ctx = context ?: return
        _activeDb.value = null
        AppDatabase.close()
        database = AppDatabase.open(ctx, dbName)
        _activeDb.value = database
        clearMemoryCaches()
    }

    fun clearMemoryCaches() {
        liveStreamsCache.clear()
        vodStreamsCache.clear()
        seriesStreamsCache.clear()
        liveCategoriesCache.clear()
        vodCategoriesCache.clear()
        seriesCategoriesCache.clear()
    }

    // 0ms gecikme için Bellek İçi Önbellek (In-Memory Cache)
    private val liveStreamsCache = ConcurrentHashMap<String, List<LiveStream>>()
    private val vodStreamsCache = ConcurrentHashMap<String, List<VodStream>>()
    private val seriesStreamsCache = ConcurrentHashMap<String, List<SeriesStream>>()

    private val liveCategoriesCache = CopyOnWriteArrayList<LiveCategory>()
    private val vodCategoriesCache = CopyOnWriteArrayList<VodCategory>()
    private val seriesCategoriesCache = CopyOnWriteArrayList<SeriesCategory>()

    fun getCachedLiveStreams(categoryId: String?): List<LiveStream>? = liveStreamsCache[categoryId ?: "all"]
    fun getCachedVodStreams(categoryId: String?): List<VodStream>? = vodStreamsCache[categoryId ?: "all"]
    fun getCachedSeriesStreams(categoryId: String?): List<SeriesStream>? = seriesStreamsCache[categoryId ?: "all"]

    fun getCachedLiveCategories(): List<LiveCategory> = liveCategoriesCache.toList()
    fun getCachedVodCategories(): List<VodCategory> = vodCategoriesCache.toList()
    fun getCachedSeriesCategories(): List<SeriesCategory> = seriesCategoriesCache.toList()

    fun clearCache() {
        liveStreamsCache.clear()
        vodStreamsCache.clear()
        seriesStreamsCache.clear()
        liveCategoriesCache.clear()
        vodCategoriesCache.clear()
        seriesCategoriesCache.clear()
    }

    /** G-028: Senkron indirmelerinin geçici dosya klasörü (cacheDir/sync). Context yoksa null. */
    private val syncTempDir: java.io.File?
        get() = context?.let { java.io.File(it.cacheDir, "sync") }

    /** G-028: Uygulama açılışında önceki oturumdan kalan geçici dosyaları temizler. */
    fun cleanupSyncTempFiles() {
        syncTempDir?.let { clearSyncTempDir(it) }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(FailoverInterceptor())
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", "IPTV Smarters Pro")
                .header("Connection", "close")
                .header("Accept", "*/*")
                .build()
            chain.proceed(request)
        }
        .build()

    private var customServerUrl: String = ""

    var serverUrl: String
        get() = if (customServerUrl.isNotBlank()) customServerUrl else FailoverInterceptor.currentWorkingBaseUrl
        set(value) {
            val clean = value.trimEnd('/')
            customServerUrl = clean
            FailoverInterceptor.setWorkingBaseUrl(clean)
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

    /**
     * Ortak yeniden deneme motoru:
     * HTTP 429, 503, 502 ve SocketTimeout / IO istisnalarında en fazla 3 deneme yapar.
     * Bekleme süresi Retry-After başlığından okunur; yoksa sırasıyla 2s, 5s, 10s uygulanır.
     */
    suspend fun executeWithRetry(
        request: Request,
        onRetry: ((attempt: Int, max: Int, waitSec: Int) -> Unit)? = null
    ): okhttp3.Response {
        val maxAttempts = 3
        var lastException: Exception? = null
        val actionName = request.url.queryParameter("action") ?: "auth_or_base"

        for (attempt in 1..maxAttempts) {
            val startMs = System.currentTimeMillis()
            try {
                val response = client.newCall(request).execute()
                val firstByteMs = System.currentTimeMillis() - startMs
                val code = response.code

                if (BuildConfig.DEBUG) {
                    val networkResp = response.networkResponse
                    val contentEncoding = networkResp?.header("Content-Encoding") ?: response.header("Content-Encoding") ?: "none"
                    val contentLength = networkResp?.header("Content-Length") ?: response.header("Content-Length") ?: "unknown"
                    val retryAfter = response.header("Retry-After") ?: "none"
                    android.util.Log.d(
                        "SyncTiming",
                        "İstek: $actionName, HTTP: $code, İlk bayt: ${firstByteMs}ms, Boyut: $contentLength, gzip: $contentEncoding, Retry-After: $retryAfter, Deneme: $attempt"
                    )
                }

                if (response.isSuccessful) {
                    return response
                }

                if (code == 429 || code == 503 || code == 502) {
                    if (attempt < maxAttempts) {
                        val retryAfterHeader = response.header("Retry-After")?.toLongOrNull()
                        val waitSec = (retryAfterHeader ?: when (attempt) {
                            1 -> 2L
                            2 -> 5L
                            else -> 10L
                        }).coerceAtLeast(1L).toInt()

                        response.close()
                        android.util.Log.w("XtreamRepository", "HTTP $code alındı, $waitSec sn sonra tekrar deneniyor ($attempt/$maxAttempts)")
                        onRetry?.invoke(attempt, maxAttempts, waitSec)
                        delay(waitSec * 1000L)
                        continue
                    }
                }

                // Yeniden denenmeyecek durum veya deneme hakları bitti
                return response
            } catch (e: Exception) {
                lastException = e
                val isNetworkOrTimeout = e is SocketTimeoutException || e is java.io.IOException
                if (attempt < maxAttempts && isNetworkOrTimeout) {
                    val waitSec = when (attempt) {
                        1 -> 2
                        2 -> 5
                        else -> 10
                    }
                    android.util.Log.w("XtreamRepository", "Bağlantı hatası: ${e.message}, $waitSec sn sonra tekrar deneniyor ($attempt/$maxAttempts)")
                    onRetry?.invoke(attempt, maxAttempts, waitSec)
                    delay(waitSec * 1000L)
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: java.io.IOException("İstek başarısız oldu")
    }

    suspend fun authenticate(url: String, user: String, pass: String): Result<XtreamAuthResponse> = withContext(Dispatchers.IO) {
        try {
            updateCredentials(url, user, pass)
            val apiUrl = buildApiUrl()
            val request = Request.Builder().url(apiUrl).get().build()
            val response = client.newCall(request).execute()
            val authResponse = response.use { resp ->
                if (!resp.isSuccessful) {
                    try {
                        android.util.Log.w("XtreamRepository", "Authentication failed with HTTP code ${resp.code}")
                    } catch (_: Throwable) {}
                    return@withContext Result.failure(Exception("Sunucu şu an yanıt vermiyor. Biraz sonra tekrar dene."))
                }
                val body = resp.body?.string() ?: ""
                gson.fromJson(body, XtreamAuthResponse::class.java)
            }

            if (authResponse?.userInfo?.auth == 1) {
                Result.success(authResponse)
            } else {
                // G-046: Sunucunun (çoğu zaman İngilizce) mesajını gösterme; sade Türkçe mesaj
                val status = authResponse?.userInfo?.status?.lowercase() ?: ""
                val errorMsg = when {
                    status.contains("expired") -> "Aboneliğinin süresi dolmuş. Sağlayıcınla iletişime geç."
                    status.contains("banned") || status.contains("disabled") ->
                        "Bu hesap şu an kullanıma kapalı. Sağlayıcınla iletişime geç."
                    else -> "Giriş başarısız. Kullanıcı adı veya şifre hatalı."
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            try {
                android.util.Log.w("XtreamRepository", "Authentication error", e)
            } catch (_: Throwable) {}
            Result.failure(Exception(toUserMessage(e)))
        }
    }

    // ============================================================================
    // OYNATMA LİSTESİ ADIM ADIM TAM SENKRONİZASYON (5 ADIMLI ÇEKİRDEK MOTOR)
    // ============================================================================

    suspend fun syncAllCategories(
        onRetry: ((attempt: Int, max: Int, waitSec: Int) -> Unit)? = null
    ): Result<CategorySyncResult> = withContext(Dispatchers.IO) {
        try {
            val liveRes = fetchAndSaveLiveCategories(onRetry)
            val vodRes = fetchAndSaveVodCategories(onRetry)
            val seriesRes = fetchAndSaveSeriesCategories(onRetry)

            if (liveRes.isFailure && vodRes.isFailure && seriesRes.isFailure) {
                val firstErr = liveRes.exceptionOrNull()
                    ?: vodRes.exceptionOrNull()
                    ?: seriesRes.exceptionOrNull()
                    ?: Exception("Kategoriler alınamadı")
                return@withContext Result.failure(firstErr)
            }

            val liveCount = liveRes.getOrNull()?.size ?: 0
            val vodCount = vodRes.getOrNull()?.size ?: 0
            val seriesCount = seriesRes.getOrNull()?.size ?: 0
            val total = liveCount + vodCount + seriesCount

            Result.success(CategorySyncResult(liveCount, vodCount, seriesCount, total))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncLiveStreams(
        onProgress: ((savedCount: Int) -> Unit)? = null,
        onRetry: ((attempt: Int, max: Int, waitSec: Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext Result.failure(Exception("Veritabanı başlatılamadı"))
        val syncStartMs = System.currentTimeMillis()
        try {
            val url = buildApiUrl("get_live_streams")
            val request = Request.Builder().url(url).get().build()
            val response = executeWithRetry(request, onRetry)

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Hatası: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Boş sunucu yanıtı"))
            var totalSaved = 0
            val seenIds = HashSet<Int>()

            val tempDir = syncTempDir ?: throw java.io.IOException("Geçici klasör yok")
            val tempFile = downloadBodyToFile(response, tempDir, "live") { }
            try {
                tempFile.bufferedReader(Charsets.UTF_8).use { reader ->
                    val jsonReader = JsonReader(reader)
                    jsonReader.isLenient = true

                    if (jsonReader.peek() != JsonToken.BEGIN_ARRAY) {
                        return@withContext Result.failure(Exception("HTTP Hatası: geçersiz yanıt"))
                    }

                    jsonReader.beginArray()
                    val chunk = ArrayList<LiveStreamEntity>(2000)

                    while (jsonReader.hasNext()) {
                        try {
                            val stream: LiveStream = gson.fromJson(jsonReader, LiveStream::class.java)
                            if (stream.streamId > 0 && !stream.name.isNullOrBlank()) {
                                chunk.add(stream.toEntity())
                                seenIds.add(stream.streamId)
                            }

                            if (chunk.size >= 2000) {
                                val toInsert = ArrayList(chunk)
                                db.withTransaction {
                                    db.liveDao().insertStreams(toInsert)
                                }
                                totalSaved += toInsert.size
                                onProgress?.invoke(totalSaved)
                                chunk.clear()
                            }
                        } catch (e: Exception) {
                            try { jsonReader.skipValue() } catch (_: Exception) {}
                        }
                    }

                    if (chunk.isNotEmpty()) {
                        val toInsert = ArrayList(chunk)
                        db.withTransaction {
                            db.liveDao().insertStreams(toInsert)
                        }
                        totalSaved += toInsert.size
                        onProgress?.invoke(totalSaved)
                        chunk.clear()
                    }

                    try { jsonReader.endArray() } catch (_: Exception) {}
                }
            } finally {
                tempFile.delete()
            }

            // Sunucuda artık bulunmayan eski canlı yayınları temizle
            val existingIds = db.liveDao().getAllStreamIds()
            val staleIds = safeStaleIds(existingIds, seenIds, "LiveStreams")
            if (staleIds.isNotEmpty()) {
                staleIds.chunked(900).forEach { chunk ->
                    db.liveDao().deleteStreamsByIds(chunk)
                }
            }

            db.liveDao().updateAllCategoryItemCounts()
            if (BuildConfig.DEBUG) {
                val duration = System.currentTimeMillis() - syncStartMs
                android.util.Log.d(
                    "SyncTiming",
                    "Akış tamamlandı: get_live_streams, Toplam süre: ${duration}ms, Kayıt sayısı: $totalSaved"
                )
            }
            Result.success(totalSaved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun parseAndInsertVodChunk(
        reader: java.io.Reader,
        db: AppDatabase,
        chunkSize: Int = 1000,
        dbMutex: Mutex? = null,
        onIdSeen: ((Int) -> Unit)? = null,
        onChunkInserted: (count: Int) -> Unit
    ): Int {
        var count = 0
        val jsonReader = JsonReader(reader)
        jsonReader.isLenient = true
        if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
            jsonReader.beginArray()
            val chunk = ArrayList<VodStreamEntity>(chunkSize)
            while (jsonReader.hasNext()) {
                try {
                    val stream: VodStream = gson.fromJson(jsonReader, VodStream::class.java)
                    if (stream.streamId > 0 && !stream.name.isNullOrBlank()) {
                        chunk.add(stream.toEntity())
                        onIdSeen?.invoke(stream.streamId)
                    }
                    if (chunk.size >= chunkSize) {
                        val toInsert = ArrayList(chunk)
                        if (dbMutex != null) {
                            dbMutex.withLock {
                                db.vodDao().insertStreams(toInsert)
                            }
                        } else {
                            db.withTransaction {
                                db.vodDao().insertStreams(toInsert)
                            }
                        }
                        count += toInsert.size
                        onChunkInserted(toInsert.size)
                        chunk.clear()
                    }
                } catch (e: Exception) {
                    try { jsonReader.skipValue() } catch (_: Exception) {}
                }
            }
            if (chunk.isNotEmpty()) {
                val toInsert = ArrayList(chunk)
                if (dbMutex != null) {
                    dbMutex.withLock {
                        db.vodDao().insertStreams(toInsert)
                    }
                } else {
                    db.withTransaction {
                        db.vodDao().insertStreams(toInsert)
                    }
                }
                count += toInsert.size
                onChunkInserted(toInsert.size)
                chunk.clear()
            }
            try { jsonReader.endArray() } catch (_: Exception) {}
        } else {
            throw Exception("HTTP Hatası: geçersiz yanıt")
        }
        return count
    }

    private suspend fun parseAndInsertSeriesChunk(
        reader: java.io.Reader,
        db: AppDatabase,
        chunkSize: Int = 1000,
        dbMutex: Mutex? = null,
        onIdSeen: ((Int) -> Unit)? = null,
        onChunkInserted: (count: Int) -> Unit
    ): Int {
        var count = 0
        val jsonReader = JsonReader(reader)
        jsonReader.isLenient = true
        if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
            jsonReader.beginArray()
            val chunk = ArrayList<SeriesStreamEntity>(chunkSize)
            while (jsonReader.hasNext()) {
                try {
                    val stream: SeriesStream = gson.fromJson(jsonReader, SeriesStream::class.java)
                    if (stream.seriesId > 0 && !stream.name.isNullOrBlank()) {
                        chunk.add(stream.toEntity())
                        onIdSeen?.invoke(stream.seriesId)
                    }
                    if (chunk.size >= chunkSize) {
                        val toInsert = ArrayList(chunk)
                        if (dbMutex != null) {
                            dbMutex.withLock {
                                db.seriesDao().insertStreams(toInsert)
                            }
                        } else {
                            db.withTransaction {
                                db.seriesDao().insertStreams(toInsert)
                            }
                        }
                        count += toInsert.size
                        onChunkInserted(toInsert.size)
                        chunk.clear()
                    }
                } catch (e: Exception) {
                    try { jsonReader.skipValue() } catch (_: Exception) {}
                }
            }
            if (chunk.isNotEmpty()) {
                val toInsert = ArrayList(chunk)
                if (dbMutex != null) {
                    dbMutex.withLock {
                        db.seriesDao().insertStreams(toInsert)
                    }
                } else {
                    db.withTransaction {
                        db.seriesDao().insertStreams(toInsert)
                    }
                }
                count += toInsert.size
                onChunkInserted(toInsert.size)
                chunk.clear()
            }
            try { jsonReader.endArray() } catch (_: Exception) {}
        } else {
            throw Exception("HTTP Hatası: geçersiz yanıt")
        }
        return count
    }

    suspend fun syncVodStreams(
        onProgress: ((savedCount: Int, statusText: String) -> Unit)? = null,
        onRetry: ((attempt: Int, max: Int, waitSec: Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext Result.failure(Exception("Veritabanı başlatılamadı"))
        val syncStartMs = System.currentTimeMillis()
        try {
            var totalSaved = 0
            var bulkSucceeded = false
            val seenIds = HashSet<Int>()
            val seenMutex = Mutex()

            // 1. Önce tek parça tüm VOD listesini çekmeyi dene (Hızlı yöntem)
            val bulkUrl = buildApiUrl("get_vod_streams")
            var bulkAttempt = 0
            val maxBulkAttempts = 2

            while (bulkAttempt < maxBulkAttempts && !bulkSucceeded) {
                bulkAttempt++
                var response: okhttp3.Response? = null
                try {
                    val request = Request.Builder().url(bulkUrl).get().build()
                    response = executeWithRetry(request, onRetry)

                    if (response.isSuccessful) {
                        val body = response.body
                        if (body != null) {
                            var currentBulkSaved = 0
                            val tempDir = syncTempDir ?: throw java.io.IOException("Geçici klasör yok")
                            val tempFile = downloadBodyToFile(response, tempDir, "vod") { bytes ->
                                onProgress?.invoke(0, "Filmler indiriliyor… ${formatMegabytes(bytes)}")
                            }
                            if (BuildConfig.DEBUG) {
                                android.util.Log.d("SyncTiming", "Dosyaya indirildi: vod, ${tempFile.length()} bayt")
                            }
                            try {
                                tempFile.bufferedReader(Charsets.UTF_8).use { reader ->
                                    parseAndInsertVodChunk(
                                        reader, db, chunkSize = 2000,
                                        onIdSeen = { seenIds.add(it) }
                                    ) { added ->
                                        currentBulkSaved += added
                                        onProgress?.invoke(currentBulkSaved, "$currentBulkSaved film kaydedildi")
                                    }
                                }
                            } finally {
                                tempFile.delete()
                            }
                            totalSaved = currentBulkSaved
                            bulkSucceeded = true
                        }
                    } else {
                        android.util.Log.w("XtreamRepository", "Bulk get_vod_streams attempt $bulkAttempt returned HTTP ${response.code}")
                        if (bulkAttempt < maxBulkAttempts && response.code in listOf(500, 502, 503, 504)) {
                            delay(2000)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("XtreamRepository", "Bulk get_vod_streams attempt $bulkAttempt failed: ${e.message}")
                    if (bulkAttempt < maxBulkAttempts && (e is SocketTimeoutException || e is java.io.IOException)) {
                        delay(2000)
                    }
                } finally {
                    try { response?.close() } catch (_: Exception) {}
                }
            }

            // 2. Eğer toplu liste 502 Bad Gateway / 500 / 503 / 504 veya zaman aşımı nedeniyle başarısız olduysa:
            // Sunucunun PHP-FPM belleğini zorlamadan Kategori Bazlı Parçalı İndirme (Category-by-Category Fallback) moduna geç
            var failedCategoryCount = 0
            var totalCategoryCount = 0
            if (!bulkSucceeded) {
                android.util.Log.i("XtreamRepository", "Switching to Category-by-Category fallback for VOD streams...")
                seenIds.clear()
                val categories = db.vodDao().getCategoriesList().filter { 
                    it.categoryId.isNotBlank() && it.categoryId != "all" 
                }.ifEmpty {
                    fetchAndSaveVodCategories(onRetry).getOrNull()?.map { it.toEntity() }?.filter {
                        it.categoryId.isNotBlank() && it.categoryId != "all"
                    } ?: emptyList()
                }

                if (categories.isNotEmpty()) {
                    totalCategoryCount = categories.size
                    val totalCategories = totalCategoryCount
                    val processedCategories = AtomicInteger(0)
                    val failedCategories = AtomicInteger(0)
                    val lastExceptionRef = AtomicReference<Exception?>(null)
                    val totalSavedAtomic = AtomicInteger(totalSaved)
                    val semaphore = Semaphore(3)
                    val dbWriteMutex = Mutex()

                    onProgress?.invoke(totalSavedAtomic.get(), "Kategori bazlı parçalı indirme başlatılıyor (0/$totalCategories)...")

                    coroutineScope {
                        categories.map { cat ->
                            async(Dispatchers.IO) {
                                semaphore.withPermit {
                                    var catResponse: okhttp3.Response? = null
                                    try {
                                        val catUrl = buildApiUrl("get_vod_streams", mapOf("category_id" to cat.categoryId))
                                        val catRequest = Request.Builder().url(catUrl).get().build()
                                        catResponse = executeWithRetry(catRequest, onRetry)

                                        if (catResponse.isSuccessful) {
                                            val catBody = catResponse.body
                                            if (catBody != null) {
                                                val tempDir = syncTempDir ?: throw java.io.IOException("Geçici klasör yok")
                                                val tempFile = downloadBodyToFile(catResponse, tempDir, "vod-cat-${cat.categoryId}") { }
                                                try {
                                                    tempFile.bufferedReader(Charsets.UTF_8).use { reader ->
                                                        parseAndInsertVodChunk(
                                                            reader, db, chunkSize = 500, dbMutex = dbWriteMutex,
                                                            onIdSeen = { id ->
                                                                synchronized(seenIds) {
                                                                    seenIds.add(id)
                                                                }
                                                            }
                                                        ) { added ->
                                                            val currentTotal = totalSavedAtomic.addAndGet(added)
                                                            val curCatIdx = processedCategories.get()
                                                            onProgress?.invoke(
                                                                currentTotal,
                                                                "$currentTotal film kaydedildi - Kategori $curCatIdx/$totalCategories (${cat.categoryName})"
                                                            )
                                                        }
                                                    }
                                                } finally {
                                                    tempFile.delete()
                                                }
                                            } else {
                                                failedCategories.incrementAndGet()
                                                lastExceptionRef.set(Exception("Boş sunucu yanıtı"))
                                            }
                                        } else {
                                            failedCategories.incrementAndGet()
                                            lastExceptionRef.set(Exception("HTTP Hatası: ${catResponse.code}"))
                                        }
                                    } catch (catEx: Exception) {
                                        failedCategories.incrementAndGet()
                                        lastExceptionRef.set(catEx)
                                        android.util.Log.w("XtreamRepository", "Category ${cat.categoryName} (${cat.categoryId}) VOD sync failed: ${catEx.message}")
                                    } finally {
                                        try { catResponse?.close() } catch (_: Exception) {}
                                        val curCatIdx = processedCategories.incrementAndGet()
                                        val currentTotal = totalSavedAtomic.get()
                                        onProgress?.invoke(
                                            currentTotal,
                                            "$currentTotal film kaydedildi - Kategori $curCatIdx/$totalCategories (${cat.categoryName})"
                                        )
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                    totalSaved = totalSavedAtomic.get()
                    failedCategoryCount = failedCategories.get()
                    if (failedCategoryCount == totalCategories) {
                        return@withContext Result.failure(lastExceptionRef.get() ?: Exception("HTTP Hatası: Tüm kategoriler başarısız oldu"))
                    }
                } else {
                    return@withContext Result.failure(Exception("VOD kategorileri bulunamadı ve toplu indirme başarısız oldu"))
                }
            }

            // Sunucuda artık bulunmayan eski VOD filmlerini temizle
            if (bulkSucceeded || failedCategoryCount == 0) {
                val existingIds = db.vodDao().getAllStreamIds()
                val staleIds = safeStaleIds(existingIds, seenIds, "VodStreams")
                if (staleIds.isNotEmpty()) {
                    staleIds.chunked(900).forEach { chunk ->
                        db.vodDao().deleteStreamsByIds(chunk)
                    }
                }
            } else {
                android.util.Log.w("XtreamRepository", "Kısmi senkron, eski içerik korunuyor ($failedCategoryCount/$totalCategoryCount kategori başarısız)")
            }

            db.vodDao().updateAllCategoryItemCounts()
            if (BuildConfig.DEBUG) {
                val duration = System.currentTimeMillis() - syncStartMs
                val method = if (bulkSucceeded) "toplu" else "kategori_yedek"
                android.util.Log.d(
                    "SyncTiming",
                    "Akış tamamlandı: get_vod_streams, Yöntem: $method, Toplam süre: ${duration}ms, Kayıt sayısı: $totalSaved"
                )
            }
            Result.success(totalSaved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncSeriesStreams(
        onProgress: ((savedCount: Int, statusText: String) -> Unit)? = null,
        onRetry: ((attempt: Int, max: Int, waitSec: Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext Result.failure(Exception("Veritabanı başlatılamadı"))
        val syncStartMs = System.currentTimeMillis()
        try {
            var totalSaved = 0
            var bulkSucceeded = false
            val seenIds = HashSet<Int>()

            // 1. Önce tek parça toplu isteği dene (Hızlı yöntem)
            val bulkUrl = buildApiUrl("get_series")
            var bulkAttempt = 0
            val maxBulkAttempts = 2

            while (bulkAttempt < maxBulkAttempts && !bulkSucceeded) {
                bulkAttempt++
                var response: okhttp3.Response? = null
                try {
                    val request = Request.Builder().url(bulkUrl).get().build()
                    response = executeWithRetry(request, onRetry)

                    if (response.isSuccessful) {
                        val body = response.body
                        if (body != null) {
                            var currentBulkSaved = 0
                            val tempDir = syncTempDir ?: throw java.io.IOException("Geçici klasör yok")
                            val tempFile = downloadBodyToFile(response, tempDir, "series") { bytes ->
                                onProgress?.invoke(0, "Diziler indiriliyor… ${formatMegabytes(bytes)}")
                            }
                            if (BuildConfig.DEBUG) {
                                android.util.Log.d("SyncTiming", "Dosyaya indirildi: series, ${tempFile.length()} bayt")
                            }
                            try {
                                tempFile.bufferedReader(Charsets.UTF_8).use { reader ->
                                    parseAndInsertSeriesChunk(
                                        reader, db, chunkSize = 2000,
                                        onIdSeen = { seenIds.add(it) }
                                    ) { added ->
                                        currentBulkSaved += added
                                        onProgress?.invoke(currentBulkSaved, "$currentBulkSaved dizi kaydedildi")
                                    }
                                }
                            } finally {
                                tempFile.delete()
                            }
                            totalSaved = currentBulkSaved
                            bulkSucceeded = true
                        }
                    } else {
                        android.util.Log.w("XtreamRepository", "Bulk get_series attempt $bulkAttempt returned HTTP ${response.code}")
                        if (bulkAttempt < maxBulkAttempts && response.code in listOf(500, 502, 503, 504)) {
                            delay(2000)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("XtreamRepository", "Bulk get_series attempt $bulkAttempt failed: ${e.message}")
                    if (bulkAttempt < maxBulkAttempts && (e is SocketTimeoutException || e is java.io.IOException)) {
                        delay(2000)
                    }
                } finally {
                    try { response?.close() } catch (_: Exception) {}
                }
            }

            // 2. Kategori bazlı parçalı indirmeye geç (Category-by-Category Fallback)
            var failedCategoryCount = 0
            var totalCategoryCount = 0
            if (!bulkSucceeded) {
                android.util.Log.i("XtreamRepository", "Switching to Category-by-Category fallback for Series streams...")
                seenIds.clear()
                val categories = db.seriesDao().getCategoriesList().filter { 
                    it.categoryId.isNotBlank() && it.categoryId != "all" 
                }.ifEmpty {
                    fetchAndSaveSeriesCategories(onRetry).getOrNull()?.map { it.toEntity() }?.filter {
                        it.categoryId.isNotBlank() && it.categoryId != "all"
                    } ?: emptyList()
                }

                if (categories.isNotEmpty()) {
                    totalCategoryCount = categories.size
                    val totalCategories = totalCategoryCount
                    val processedCategories = AtomicInteger(0)
                    val failedCategories = AtomicInteger(0)
                    val lastExceptionRef = AtomicReference<Exception?>(null)
                    val totalSavedAtomic = AtomicInteger(totalSaved)
                    val semaphore = Semaphore(3)
                    val dbWriteMutex = Mutex()

                    onProgress?.invoke(totalSavedAtomic.get(), "Kategori bazlı parçalı indirme başlatılıyor (0/$totalCategories)...")

                    coroutineScope {
                        categories.map { cat ->
                            async(Dispatchers.IO) {
                                semaphore.withPermit {
                                    var catResponse: okhttp3.Response? = null
                                    try {
                                        val catUrl = buildApiUrl("get_series", mapOf("category_id" to cat.categoryId))
                                        val catRequest = Request.Builder().url(catUrl).get().build()
                                        catResponse = executeWithRetry(catRequest, onRetry)

                                        if (catResponse.isSuccessful) {
                                            val catBody = catResponse.body
                                            if (catBody != null) {
                                                val tempDir = syncTempDir ?: throw java.io.IOException("Geçici klasör yok")
                                                val tempFile = downloadBodyToFile(catResponse, tempDir, "series-cat-${cat.categoryId}") { }
                                                try {
                                                    tempFile.bufferedReader(Charsets.UTF_8).use { reader ->
                                                        parseAndInsertSeriesChunk(
                                                            reader, db, chunkSize = 500, dbMutex = dbWriteMutex,
                                                            onIdSeen = { id ->
                                                                synchronized(seenIds) {
                                                                    seenIds.add(id)
                                                                }
                                                            }
                                                        ) { added ->
                                                            val currentTotal = totalSavedAtomic.addAndGet(added)
                                                            val curCatIdx = processedCategories.get()
                                                            onProgress?.invoke(
                                                                currentTotal,
                                                                "$currentTotal dizi kaydedildi - Kategori $curCatIdx/$totalCategories (${cat.categoryName})"
                                                            )
                                                        }
                                                    }
                                                } finally {
                                                    tempFile.delete()
                                                }
                                            } else {
                                                failedCategories.incrementAndGet()
                                                lastExceptionRef.set(Exception("Boş sunucu yanıtı"))
                                            }
                                        } else {
                                            failedCategories.incrementAndGet()
                                            lastExceptionRef.set(Exception("HTTP Hatası: ${catResponse.code}"))
                                        }
                                    } catch (catEx: Exception) {
                                        failedCategories.incrementAndGet()
                                        lastExceptionRef.set(catEx)
                                        android.util.Log.w("XtreamRepository", "Category ${cat.categoryName} (${cat.categoryId}) Series sync failed: ${catEx.message}")
                                    } finally {
                                        try { catResponse?.close() } catch (_: Exception) {}
                                        val curCatIdx = processedCategories.incrementAndGet()
                                        val currentTotal = totalSavedAtomic.get()
                                        onProgress?.invoke(
                                            currentTotal,
                                            "$currentTotal dizi kaydedildi - Kategori $curCatIdx/$totalCategories (${cat.categoryName})"
                                        )
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                    totalSaved = totalSavedAtomic.get()
                    failedCategoryCount = failedCategories.get()
                    if (failedCategoryCount == totalCategories) {
                        return@withContext Result.failure(lastExceptionRef.get() ?: Exception("HTTP Hatası: Tüm kategoriler başarısız oldu"))
                    }
                } else {
                    return@withContext Result.failure(Exception("Dizi kategorileri bulunamadı ve toplu indirme başarısız oldu"))
                }
            }

            // Sunucuda artık bulunmayan eski dizileri temizle
            if (bulkSucceeded || failedCategoryCount == 0) {
                val existingIds = db.seriesDao().getAllSeriesIds()
                val staleIds = safeStaleIds(existingIds, seenIds, "SeriesStreams")
                if (staleIds.isNotEmpty()) {
                    staleIds.chunked(900).forEach { chunk ->
                        db.seriesDao().deleteSeriesByIds(chunk)
                    }
                }
            } else {
                android.util.Log.w("XtreamRepository", "Kısmi senkron, eski içerik korunuyor ($failedCategoryCount/$totalCategoryCount kategori başarısız)")
            }

            db.seriesDao().updateAllCategoryItemCounts()
            if (BuildConfig.DEBUG) {
                val duration = System.currentTimeMillis() - syncStartMs
                val method = if (bulkSucceeded) "toplu" else "kategori_yedek"
                android.util.Log.d(
                    "SyncTiming",
                    "Akış tamamlandı: get_series, Yöntem: $method, Toplam süre: ${duration}ms, Kayıt sayısı: $totalSaved"
                )
            }
            Result.success(totalSaved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun optimizeAndPreload(): Result<Unit> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext Result.failure(Exception("Veritabanı başlatılamadı"))
        try {
            // Öğe sayılarını güncelle
            db.liveDao().updateAllCategoryItemCounts()
            db.vodDao().updateAllCategoryItemCounts()
            db.seriesDao().updateAllCategoryItemCounts()

            // Kategorileri bellek içi önbelleğe (In-Memory Cache) al -> 0ms anında açılma
            val liveCats = db.liveDao().getCategories().firstOrNull() ?: emptyList()
            val vodCats = db.vodDao().getCategories().firstOrNull() ?: emptyList()
            val seriesCats = db.seriesDao().getCategories().firstOrNull() ?: emptyList()

            liveCategoriesCache.clear()
            liveCategoriesCache.addAll(liveCats.map { it.toModel() })

            vodCategoriesCache.clear()
            vodCategoriesCache.addAll(vodCats.map { it.toModel() })

            seriesCategoriesCache.clear()
            seriesCategoriesCache.addAll(seriesCats.map { it.toModel() })

            // Popüler/Varsayılan akışları önbelleğe al
            val topLive = db.liveDao().getAllStreams().firstOrNull() ?: emptyList()
            liveStreamsCache["all"] = topLive.map { it.toModel() }

            val topVod = db.vodDao().getAllStreams().firstOrNull() ?: emptyList()
            vodStreamsCache["all"] = topVod.map { it.toModel() }

            val topSeries = db.seriesDao().getAllStreams().firstOrNull() ?: emptyList()
            seriesStreamsCache["all"] = topSeries.map { it.toModel() }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================================
    // CANLI YAYIN: STREAMING (JSONREADER) & ROOM SENKRONİZASYONU
    // ============================================================================

    suspend fun fetchAndSaveLiveCategories(
        onRetry: ((attempt: Int, max: Int, waitSec: Int) -> Unit)? = null
    ): Result<List<LiveCategory>> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext Result.failure(Exception("Veritabanı başlatılamadı"))
        try {
            val url = buildApiUrl("get_live_categories")
            val request = Request.Builder().url(url).get().build()
            val response = executeWithRetry(request, onRetry)

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Hatası: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Boş yanıt"))
            val categories = mutableListOf<LiveCategory>()
            val seenCategoryIds = HashSet<String>()

            body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                if (jsonReader.peek() != JsonToken.BEGIN_ARRAY) {
                    return@withContext Result.failure(Exception("HTTP Hatası: geçersiz yanıt"))
                }
                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    try {
                        val cat: LiveCategory = gson.fromJson(jsonReader, LiveCategory::class.java)
                        if (cat.categoryId.isNotBlank()) {
                            categories.add(cat)
                            seenCategoryIds.add(cat.categoryId)
                        }
                    } catch (e: Exception) {
                        try { jsonReader.skipValue() } catch (_: Exception) {}
                    }
                }
                try { jsonReader.endArray() } catch (_: Exception) {}
            }

            db.liveDao().insertCategories(categories.map { it.toEntity() })

            // Sunucuda artık bulunmayan eski canlı kategorilerini temizle
            val existingIds = db.liveDao().getAllCategoryIds()
            val staleIds = safeStaleIds(existingIds, seenCategoryIds, "LiveCategories")
            if (staleIds.isNotEmpty()) {
                staleIds.chunked(900).forEach { chunk ->
                    db.liveDao().deleteCategoriesByIds(chunk)
                }
            }

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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getLiveCategoriesFlow(): Flow<List<LiveCategory>> = activeDbFlow.flatMapLatest { db ->
        db?.liveDao()?.getCategories()?.map { list ->
            list.map { it.toModel() }
        } ?: flowOf(emptyList())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getLiveStreamsFlow(categoryId: String? = null): Flow<List<LiveStream>> = activeDbFlow.flatMapLatest { db ->
        val dao = db?.liveDao() ?: return@flatMapLatest flowOf(emptyList())
        val sourceFlow = if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.getAllStreams()
        } else {
            dao.getStreamsByCategory(categoryId)
        }
        sourceFlow.map { list -> list.map { it.toModel() } }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getLiveStreamsByIdsFlow(ids: List<Int>): Flow<List<LiveStream>> = activeDbFlow.flatMapLatest { db ->
        val dao = db?.liveDao() ?: return@flatMapLatest flowOf(emptyList())
        if (ids.isEmpty()) return@flatMapLatest flowOf(emptyList())
        dao.getStreamsByIds(ids).map { list -> list.map { it.toModel() } }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun searchLiveStreamsFlow(query: String, categoryId: String? = null): Flow<List<LiveStream>> = activeDbFlow.flatMapLatest { db ->
        val dao = db?.liveDao() ?: return@flatMapLatest flowOf(emptyList())
        val sourceFlow = if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.searchStreams(query)
        } else {
            dao.searchStreamsInCategory(categoryId, query)
        }
        sourceFlow.map { list -> list.map { it.toModel() } }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getLiveStreamsFilteredFlow(categoryId: String? = null, query: String = "", sort: String = "DEFAULT"): Flow<List<LiveStream>> = activeDbFlow.flatMapLatest { db ->
        val dao = db?.liveDao() ?: return@flatMapLatest flowOf(emptyList())
        val cleanCat = if (categoryId.isNullOrEmpty() || categoryId == "all") null else categoryId
        dao.getStreamsFiltered(cleanCat, query, sort).map { list -> list.map { it.toModel() } }
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

    suspend fun fetchAndSaveVodCategories(
        onRetry: ((attempt: Int, max: Int, waitSec: Int) -> Unit)? = null
    ): Result<List<VodCategory>> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext Result.failure(Exception("Veritabanı başlatılamadı"))
        try {
            val url = buildApiUrl("get_vod_categories")
            val request = Request.Builder().url(url).get().build()
            val response = executeWithRetry(request, onRetry)

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Hatası: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Boş yanıt"))
            val categories = mutableListOf<VodCategory>()
            val seenCategoryIds = HashSet<String>()

            body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                if (jsonReader.peek() != JsonToken.BEGIN_ARRAY) {
                    return@withContext Result.failure(Exception("HTTP Hatası: geçersiz yanıt"))
                }
                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    try {
                        val cat: VodCategory = gson.fromJson(jsonReader, VodCategory::class.java)
                        if (cat.categoryId.isNotBlank()) {
                            categories.add(cat)
                            seenCategoryIds.add(cat.categoryId)
                        }
                    } catch (e: Exception) {
                        try { jsonReader.skipValue() } catch (_: Exception) {}
                    }
                }
                try { jsonReader.endArray() } catch (_: Exception) {}
            }

            db.vodDao().insertCategories(categories.map { it.toEntity() })

            // Sunucuda artık bulunmayan eski VOD kategorilerini temizle
            val existingIds = db.vodDao().getAllCategoryIds()
            val staleIds = safeStaleIds(existingIds, seenCategoryIds, "VodCategories")
            if (staleIds.isNotEmpty()) {
                staleIds.chunked(900).forEach { chunk ->
                    db.vodDao().deleteCategoriesByIds(chunk)
                }
            }

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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getVodCategoriesFlow(): Flow<List<VodCategory>> = activeDbFlow.flatMapLatest { db ->
        db?.vodDao()?.getCategories()?.map { list ->
            list.map { it.toModel() }
        } ?: flowOf(emptyList())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getVodStreamsFlow(categoryId: String? = null): Flow<List<VodStream>> = activeDbFlow.flatMapLatest { db ->
        val dao = db?.vodDao() ?: return@flatMapLatest flowOf(emptyList())
        val sourceFlow = if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.getAllStreams()
        } else {
            dao.getStreamsByCategory(categoryId)
        }
        sourceFlow.map { list -> list.map { it.toModel() } }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getVodStreamsByIdsFlow(ids: List<Int>): Flow<List<VodStream>> = activeDbFlow.flatMapLatest { db ->
        val dao = db?.vodDao() ?: return@flatMapLatest flowOf(emptyList())
        if (ids.isEmpty()) return@flatMapLatest flowOf(emptyList())
        dao.getStreamsByIds(ids).map { list -> list.map { it.toModel() } }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun searchVodStreamsFlow(query: String, categoryId: String? = null): Flow<List<VodStream>> = activeDbFlow.flatMapLatest { db ->
        val dao = db?.vodDao() ?: return@flatMapLatest flowOf(emptyList())
        val sourceFlow = if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.searchStreams(query)
        } else {
            dao.searchStreamsInCategory(categoryId, query)
        }
        sourceFlow.map { list -> list.map { it.toModel() } }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getVodStreamsFilteredFlow(categoryId: String? = null, query: String = "", sort: String = "DEFAULT"): Flow<List<VodStream>> = activeDbFlow.flatMapLatest { db ->
        val dao = db?.vodDao() ?: return@flatMapLatest flowOf(emptyList())
        val cleanCat = if (categoryId.isNullOrEmpty() || categoryId == "all") null else categoryId
        dao.getStreamsFiltered(cleanCat, query, sort).map { list -> list.map { it.toModel() } }
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

    suspend fun fetchAndSaveSeriesCategories(
        onRetry: ((attempt: Int, max: Int, waitSec: Int) -> Unit)? = null
    ): Result<List<SeriesCategory>> = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext Result.failure(Exception("Veritabanı başlatılamadı"))
        try {
            val url = buildApiUrl("get_series_categories")
            val request = Request.Builder().url(url).get().build()
            val response = executeWithRetry(request, onRetry)

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Hatası: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Boş yanıt"))
            val categories = mutableListOf<SeriesCategory>()
            val seenCategoryIds = HashSet<String>()

            body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                val jsonReader = JsonReader(reader)
                jsonReader.isLenient = true
                if (jsonReader.peek() != JsonToken.BEGIN_ARRAY) {
                    return@withContext Result.failure(Exception("HTTP Hatası: geçersiz yanıt"))
                }
                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    try {
                        val cat: SeriesCategory = gson.fromJson(jsonReader, SeriesCategory::class.java)
                        if (cat.categoryId.isNotBlank()) {
                            categories.add(cat)
                            seenCategoryIds.add(cat.categoryId)
                        }
                    } catch (e: Exception) {
                        try { jsonReader.skipValue() } catch (_: Exception) {}
                    }
                }
                try { jsonReader.endArray() } catch (_: Exception) {}
            }

            db.seriesDao().insertCategories(categories.map { it.toEntity() })

            // Sunucuda artık bulunmayan eski dizi kategorilerini temizle
            val existingIds = db.seriesDao().getAllCategoryIds()
            val staleIds = safeStaleIds(existingIds, seenCategoryIds, "SeriesCategories")
            if (staleIds.isNotEmpty()) {
                staleIds.chunked(900).forEach { chunk ->
                    db.seriesDao().deleteCategoriesByIds(chunk)
                }
            }

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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getSeriesCategoriesFlow(): Flow<List<SeriesCategory>> = activeDbFlow.flatMapLatest { db ->
        db?.seriesDao()?.getCategories()?.map { list ->
            list.map { it.toModel() }
        } ?: flowOf(emptyList())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getSeriesStreamsFlow(categoryId: String? = null): Flow<List<SeriesStream>> = activeDbFlow.flatMapLatest { db ->
        val dao = db?.seriesDao() ?: return@flatMapLatest flowOf(emptyList())
        val sourceFlow = if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.getAllStreams()
        } else {
            dao.getStreamsByCategory(categoryId)
        }
        sourceFlow.map { list -> list.map { it.toModel() } }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getSeriesStreamsByIdsFlow(ids: List<Int>): Flow<List<SeriesStream>> = activeDbFlow.flatMapLatest { db ->
        val dao = db?.seriesDao() ?: return@flatMapLatest flowOf(emptyList())
        if (ids.isEmpty()) return@flatMapLatest flowOf(emptyList())
        dao.getSeriesByIds(ids).map { list -> list.map { it.toModel() } }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun searchSeriesStreamsFlow(query: String, categoryId: String? = null): Flow<List<SeriesStream>> = activeDbFlow.flatMapLatest { db ->
        val dao = db?.seriesDao() ?: return@flatMapLatest flowOf(emptyList())
        val sourceFlow = if (categoryId.isNullOrEmpty() || categoryId == "all") {
            dao.searchStreams(query)
        } else {
            dao.searchStreamsInCategory(categoryId, query)
        }
        sourceFlow.map { list -> list.map { it.toModel() } }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getSeriesStreamsFilteredFlow(categoryId: String? = null, query: String = "", sort: String = "DEFAULT"): Flow<List<SeriesStream>> = activeDbFlow.flatMapLatest { db ->
        val dao = db?.seriesDao() ?: return@flatMapLatest flowOf(emptyList())
        val cleanCat = if (categoryId.isNullOrEmpty() || categoryId == "all") null else categoryId
        dao.getStreamsFiltered(cleanCat, query, sort).map { list -> list.map { it.toModel() } }
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
            val vodInfo = client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                gson.fromJson(body, VodInfoResponse::class.java)
            }
            Result.success(vodInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeriesInfo(seriesId: Int): Result<SeriesInfoResponse> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl("get_series_info", mapOf("series_id" to seriesId.toString()))
            val request = Request.Builder().url(url).get().build()
            val seriesInfo = client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                gson.fromJson(body, SeriesInfoResponse::class.java)
            }
            Result.success(seriesInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getShortEpg(streamId: Int): Result<List<EpgProgram>> = withContext(Dispatchers.IO) {
        try {
            val url = buildApiUrl("get_short_epg", mapOf("stream_id" to streamId.toString(), "limit" to "5"))
            val request = Request.Builder().url(url).get().build()
            val listings = client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "{}"
                val epgResponse = gson.fromJson(body, ShortEpgResponse::class.java)
                epgResponse.epgListings ?: emptyList()
            }
            Result.success(listings)
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
        val ext = extension.trim().removePrefix(".").ifEmpty { "mp4" }
        val urls = mutableListOf<String>()
        urls.add("$serverUrl/movie/$username/$password/$streamId.$ext")
        if (ext != "mkv") urls.add("$serverUrl/movie/$username/$password/$streamId.mkv")
        if (ext != "mp4") urls.add("$serverUrl/movie/$username/$password/$streamId.mp4")
        if (ext != "ts") urls.add("$serverUrl/movie/$username/$password/$streamId.ts")
        return urls
    }

    fun buildVodStreamUrl(streamId: Int, extension: String = "mp4"): String {
        val ext = extension.trim().removePrefix(".").ifEmpty { "mp4" }
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

    companion object {
        /**
         * Eski içerik/kategori silme için ortak güvenlik filtresi:
         * - Görülen kimlikler boşsa silme yapılmaz (boş liste döner).
         * - Görülen kimlik sayısı mevcut kimliklerin yarısından azsa silme yapılmaz (boş liste döner).
         * - Normal durumda mevcut - görülen farkı döndürülür.
         */
        internal fun <T> safeStaleIds(existing: Collection<T>, seen: Set<T>, label: String): List<T> {
            if (seen.isEmpty()) {
                logWarn("[$label] Görülen liste boş, silme atlandı")
                return emptyList()
            }
            if (existing.isNotEmpty() && seen.size < existing.size / 2) {
                logWarn("[$label] Yeni liste mevcut listenin yarısından az (${seen.size} < ${existing.size / 2}), silme atlandı")
                return emptyList()
            }
            return (existing.toSet() - seen).toList()
        }

        private fun logWarn(msg: String) {
            try {
                android.util.Log.w("XtreamRepository", msg)
            } catch (_: Throwable) {
                // JVM birim test ortamında Android Log mock'lu olmayabilir
            }
        }
    }
}

fun toUserMessage(e: Throwable): String {
    val cause = e.cause ?: e
    return when {
        e is java.net.UnknownHostException || cause is java.net.UnknownHostException ||
        e is java.net.ConnectException || cause is java.net.ConnectException ||
        e is java.net.SocketTimeoutException || cause is java.net.SocketTimeoutException ||
        e is java.io.IOException || cause is java.io.IOException -> "Sunucuya ulaşılamadı. İnternetini kontrol et."
        else -> "Bağlanılamadı. Bilgilerini kontrol edip tekrar dene."
    }
}

/**
 * G-028: Yanıt gövdesini geçici dosyaya yazar ve bağlantıyı kapatır.
 * Eksik indirme, okuma hatası veya iptal durumunda dosyayı siler ve hatayı yukarı fırlatır.
 */
internal suspend fun downloadBodyToFile(
    response: okhttp3.Response,
    dir: java.io.File,
    prefix: String,
    onBytes: ((Long) -> Unit)? = null
): java.io.File {
    dir.mkdirs()
    val file = java.io.File(dir, "$prefix-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.json")
    try {
        response.use { resp ->
            val body = resp.body ?: throw java.io.IOException("Boş sunucu yanıtı")
            val expected = body.contentLength()
            var total = 0L
            var lastReported = 0L
            body.byteStream().use { input ->
                java.io.FileOutputStream(file).buffered(64 * 1024).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        total += read
                        if (total - lastReported >= 512 * 1024) {
                            lastReported = total
                            onBytes?.invoke(total)
                        }
                    }
                }
            }
            if (expected > 0 && total != expected) {
                throw java.io.IOException("Eksik indirme: $total / $expected bayt")
            }
            onBytes?.invoke(total)
        }
        return file
    } catch (e: Throwable) {
        file.delete()
        throw e
    }
}

/** G-028: "12,4 MB" biçiminde Türkçe boyut metni. */
internal fun formatMegabytes(bytes: Long): String =
    String.format(java.util.Locale("tr", "TR"), "%.1f MB", bytes / (1024.0 * 1024.0))

/** G-028: Geçici indirme klasöründeki tüm dosyaları siler. Hata fırlatmaz. */
internal fun clearSyncTempDir(dir: java.io.File) {
    try {
        dir.listFiles()?.forEach { it.delete() }
    } catch (_: Throwable) {}
}
