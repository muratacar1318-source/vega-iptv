package com.tivimax.player.data.network

import android.util.Log
import com.tivimax.player.data.model.VodDetailInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

object TmdbScraper {

    private val API_KEY = com.tivimax.player.BuildConfig.TMDB_API_KEY
    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"
    private const val MAX_CACHE_ENTRIES = 100

    private val PARENS_REGEX = Regex("(?i)\\[.*?\\]|\\(.*?\\)")
    private val DOT_UNDERSCORE_REGEX = Regex("[._]+")
    private val IPTV_TAGS_REGEX = Regex("(?i)\\b(4K|2160p|1080p|720p|UHD|FHD|HD|SD|DUBLAJ|ALTYAZILI|TURKCE|TR-ENG|TR-EN|TR|EN|WEB-DL|WEBRIP|BLURAY|BDRIP|HDRIP|CAM)\\b")
    private val HYPHEN_REGEX = Regex("-+")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<String, VodDetailInfo>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VodDetailInfo>?): Boolean {
            return size > MAX_CACHE_ENTRIES
        }
    }

    private val client = OkHttpClient.Builder()
        .dns(BoundedDns.instance)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Film veya Dizi adından/TMDb ID'sinden afiş, fragman, oyuncu kadrosu ve puanlama çeker.
     */
    suspend fun fetchEnrichedVodInfo(
        rawTitle: String,
        existingInfo: VodDetailInfo?
    ): VodDetailInfo? = withContext(Dispatchers.IO) {
        if (API_KEY.isBlank()) return@withContext existingInfo
        try {
            val cleanTitle = cleanSearchTitle(rawTitle)
            if (cleanTitle.isBlank()) return@withContext existingInfo

            val cacheKey = cleanTitle.lowercase(Locale.ROOT)
            synchronized(cacheLock) {
                cache[cacheKey]
            }?.let { cached ->
                return@withContext mergeWithExisting(cached, existingInfo)
            }

            // 1. Film Arama
            val encodedQuery = URLEncoder.encode(cleanTitle, "UTF-8")
            val searchUrl = "$BASE_URL/search/movie?api_key=$API_KEY&query=$encodedQuery&language=tr-TR"

            val searchReq = Request.Builder().url(searchUrl).build()
            val searchBody = client.newCall(searchReq).execute().use { searchRes ->
                if (!searchRes.isSuccessful) return@withContext existingInfo
                searchRes.body?.string().orEmpty()
            }

            if (searchBody.isBlank()) return@withContext existingInfo
            val searchJson = JSONObject(searchBody)
            val results = searchJson.optJSONArray("results") ?: return@withContext existingInfo

            if (results.length() == 0) return@withContext existingInfo

            val firstResult = results.getJSONObject(0)
            val tmdbId = firstResult.optInt("id", 0)
            val posterPath = firstResult.optString("poster_path", "")
            val backdropPath = firstResult.optString("backdrop_path", "")
            val voteAverage = firstResult.optDouble("vote_average", 0.0)
            val overview = firstResult.optString("overview", "")
            val releaseDate = firstResult.optString("release_date", "")

            if (tmdbId == 0) return@withContext existingInfo

            // 2. Detay ve Oyuncular (Credits + Videos)
            val detailUrl = "$BASE_URL/movie/$tmdbId?api_key=$API_KEY&append_to_response=credits,videos&language=tr-TR"
            val detailReq = Request.Builder().url(detailUrl).build()

            var castText = existingInfo?.cast ?: existingInfo?.actors ?: ""
            var trailerUrl = existingInfo?.youtubeTrailer ?: ""

            val detailBody = client.newCall(detailReq).execute().use { detailRes ->
                if (detailRes.isSuccessful) detailRes.body?.string().orEmpty() else ""
            }

            if (detailBody.isNotBlank()) {
                val detailJson = JSONObject(detailBody)

                // Oyuncu Kadrosu
                val credits = detailJson.optJSONObject("credits")
                val castArray = credits?.optJSONArray("cast")
                if (castArray != null && castArray.length() > 0) {
                    val actorsList = mutableListOf<String>()
                    for (i in 0 until minOf(8, castArray.length())) {
                        val actorObj = castArray.getJSONObject(i)
                        val name = actorObj.optString("name", "")
                        if (name.isNotBlank()) actorsList.add(name)
                    }
                    if (actorsList.isNotEmpty()) {
                        castText = actorsList.joinToString(", ")
                    }
                }

                // Fragman (YouTube Video Key)
                val videos = detailJson.optJSONObject("videos")
                val videoResults = videos?.optJSONArray("results")
                if (videoResults != null && videoResults.length() > 0) {
                    for (i in 0 until videoResults.length()) {
                        val videoObj = videoResults.getJSONObject(i)
                        val site = videoObj.optString("site", "")
                        val type = videoObj.optString("type", "")
                        val key = videoObj.optString("key", "")
                        if (site.equals("YouTube", ignoreCase = true) && (type.equals("Trailer", ignoreCase = true) || key.isNotBlank())) {
                            trailerUrl = key
                            break
                        }
                    }
                }
            }

            val posterUrl = if (posterPath.isNotBlank() && posterPath != "null") "$IMAGE_BASE_URL$posterPath" else existingInfo?.coverBig
            val backdropUrl = if (backdropPath.isNotBlank() && backdropPath != "null") "$IMAGE_BASE_URL$backdropPath" else existingInfo?.movieImage
            val ratingStr = if (voteAverage > 0.0) String.format(Locale.US, "%.1f", voteAverage) else existingInfo?.rating

            val enriched = VodDetailInfo(
                tmdbId = tmdbId.toString(),
                name = existingInfo?.name ?: cleanTitle,
                originalName = existingInfo?.originalName,
                coverBig = posterUrl,
                movieImage = backdropUrl,
                releaseDate = if (releaseDate.isNotBlank() && releaseDate != "null") releaseDate else existingInfo?.releaseDate,
                durationMinutes = existingInfo?.durationMinutes,
                youtubeTrailer = trailerUrl,
                director = existingInfo?.director,
                actors = castText,
                cast = castText,
                description = if (overview.isNotBlank() && overview != "null") overview else existingInfo?.description,
                plot = if (overview.isNotBlank() && overview != "null") overview else existingInfo?.plot,
                age = existingInfo?.age,
                country = existingInfo?.country,
                genre = existingInfo?.genre,
                durationSecs = existingInfo?.durationSecs,
                duration = existingInfo?.duration,
                rating = ratingStr
            )

            synchronized(cacheLock) {
                cache[cacheKey] = enriched
            }
            return@withContext enriched
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("VegaIPTV", "TMDb Scraper Hatası (${e.javaClass.simpleName})")
            return@withContext existingInfo
        }
    }

    private fun mergeWithExisting(cached: VodDetailInfo, existing: VodDetailInfo?): VodDetailInfo {
        if (existing == null) return cached
        return cached.copy(
            name = existing.name ?: cached.name,
            originalName = existing.originalName ?: cached.originalName,
            durationMinutes = existing.durationMinutes ?: cached.durationMinutes,
            director = existing.director ?: cached.director,
            age = existing.age ?: cached.age,
            country = existing.country ?: cached.country,
            genre = existing.genre ?: cached.genre,
            durationSecs = existing.durationSecs ?: cached.durationSecs,
            duration = existing.duration ?: cached.duration
        )
    }

    /**
     * Film isimlerindeki "4K", "1080p", "TR-EN", "DUBLAJ" gibi IPTV eklerini kelime sınırlarıyla temizler.
     */
    private fun cleanSearchTitle(title: String): String {
        return title
            .replace(PARENS_REGEX, " ")
            .replace(DOT_UNDERSCORE_REGEX, " ")
            .replace(IPTV_TAGS_REGEX, " ")
            .replace(HYPHEN_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }
}
