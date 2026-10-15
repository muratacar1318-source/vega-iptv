package com.tivimax.player.data.network

import android.util.Log
import com.tivimax.player.data.model.VodDetailInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object TmdbScraper {

    private const val API_KEY = "15d2ea6d0dc1d476efbca3cef2d92dca"
    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"

    private val client = OkHttpClient.Builder()
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
        try {
            val cleanTitle = cleanSearchTitle(rawTitle)
            if (cleanTitle.isBlank()) return@withContext existingInfo

            // 1. Film Arama
            val encodedQuery = URLEncoder.encode(cleanTitle, "UTF-8")
            val searchUrl = "$BASE_URL/search/movie?api_key=$API_KEY&query=$encodedQuery&language=tr-TR"

            val searchReq = Request.Builder().url(searchUrl).build()
            val searchRes = client.newCall(searchReq).execute()

            if (!searchRes.isSuccessful) return@withContext existingInfo

            val searchJson = JSONObject(searchRes.body?.string() ?: "")
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
            val detailRes = client.newCall(detailReq).execute()

            var castText = existingInfo?.cast ?: existingInfo?.actors ?: ""
            var trailerUrl = existingInfo?.youtubeTrailer ?: ""

            if (detailRes.isSuccessful) {
                val detailJson = JSONObject(detailRes.body?.string() ?: "")

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

            val posterUrl = if (posterPath.isNotBlank()) "$IMAGE_BASE_URL$posterPath" else existingInfo?.coverBig
            val backdropUrl = if (backdropPath.isNotBlank()) "$IMAGE_BASE_URL$backdropPath" else existingInfo?.movieImage
            val ratingStr = if (voteAverage > 0.0) String.format(java.util.Locale.US, "%.1f", voteAverage) else existingInfo?.rating

            return@withContext VodDetailInfo(
                tmdbId = tmdbId.toString(),
                name = existingInfo?.name ?: cleanTitle,
                originalName = existingInfo?.originalName,
                coverBig = posterUrl,
                movieImage = backdropUrl,
                releaseDate = if (releaseDate.isNotBlank()) releaseDate else existingInfo?.releaseDate,
                durationMinutes = existingInfo?.durationMinutes,
                youtubeTrailer = trailerUrl,
                director = existingInfo?.director,
                actors = castText,
                cast = castText,
                description = if (overview.isNotBlank()) overview else existingInfo?.description,
                plot = if (overview.isNotBlank()) overview else existingInfo?.plot,
                age = existingInfo?.age,
                country = existingInfo?.country,
                genre = existingInfo?.genre,
                durationSecs = existingInfo?.durationSecs,
                duration = existingInfo?.duration,
                rating = ratingStr
            )
        } catch (e: Exception) {
            Log.e("VegaIPTV", "TMDb Scraper Hatası: ${e.message}")
            return@withContext existingInfo
        }
    }

    /**
     * Film isimlerindeki "4K", "1080p", "TR-EN", "DUBLAJ" gibi IPTV eklerini temizler.
     */
    private fun cleanSearchTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\[.*?\\]|\\(.*\\)"), "") // Parantez içlerini sil
            .replace(Regex("(?i)4K|1080p|720p|UHD|FHD|HD|DUBLAJ|ALTYAZILI|TURKCE|TR-ENG|TR|EN|WEB-DL|BLURAY|CAM"), "")
            .replace(Regex("[._-]"), " ")
            .trim()
    }
}
