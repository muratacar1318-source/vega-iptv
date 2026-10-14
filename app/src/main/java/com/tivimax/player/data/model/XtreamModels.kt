package com.tivimax.player.data.model

import com.google.gson.annotations.SerializedName

// --- Authentication Models ---
data class XtreamAuthResponse(
    @SerializedName("user_info") val userInfo: XtreamUserInfo? = null,
    @SerializedName("server_info") val serverInfo: XtreamServerInfo? = null
)

data class XtreamUserInfo(
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("auth") val auth: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("exp_date") val expDate: String? = null,
    @SerializedName("is_trial") val isTrial: String? = null,
    @SerializedName("active_cons") val activeCons: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("max_connections") val maxConnections: String? = null,
    @SerializedName("allowed_output_formats") val allowedOutputFormats: List<String>? = null
)

data class XtreamServerInfo(
    @SerializedName("url") val url: String? = null,
    @SerializedName("port") val port: String? = null,
    @SerializedName("https_port") val httpsPort: String? = null,
    @SerializedName("server_protocol") val serverProtocol: String? = null,
    @SerializedName("rtmp_port") val rtmpPort: String? = null,
    @SerializedName("timezone") val timezone: String? = null,
    @SerializedName("timestamp_now") val timestampNow: Long? = null,
    @SerializedName("time_now") val timeNow: String? = null
)

// --- Live TV Models ---
data class LiveCategory(
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("category_name") val categoryName: String = "",
    @SerializedName("parent_id") val parentId: Int = 0
)

data class LiveStream(
    @SerializedName("num") val num: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("stream_type") val streamType: String = "live",
    @SerializedName("stream_id") val streamId: Int = 0,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("epg_channel_id") val epgChannelId: String? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("custom_sid") val customSid: String? = null,
    @SerializedName("tv_archive") val tvArchive: Int = 0,
    @SerializedName("direct_source") val directSource: String? = null,
    val currentProgram: String? = null,
    val nextProgram: String? = null,
    val programStart: String? = null,
    val programEnd: String? = null,
    val programProgress: Float = 0f
)

// --- VOD (Movies) Models ---
data class VodCategory(
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("category_name") val categoryName: String = "",
    @SerializedName("parent_id") val parentId: Int = 0
)

data class VodStream(
    @SerializedName("num") val num: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("stream_type") val streamType: String = "movie",
    @SerializedName("stream_id") val streamId: Int = 0,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("rating_5based") val rating5Based: Double? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("container_extension") val containerExtension: String = "mp4"
)

data class VodInfoResponse(
    @SerializedName("info") val info: VodDetailInfo? = null,
    @SerializedName("movie_data") val movieData: VodStream? = null
)

data class VodDetailInfo(
    @SerializedName("tmdb_id") val tmdbId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("o_name") val originalName: String? = null,
    @SerializedName("cover_big") val coverBig: String? = null,
    @SerializedName("movie_image") val movieImage: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("episode_run_time") val durationMinutes: String? = null,
    @SerializedName("youtube_trailer") val youtubeTrailer: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("actors") val actors: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("age") val age: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("duration_secs") val durationSecs: Long? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("rating") val rating: String? = null
)

// --- Series Models ---
data class SeriesCategory(
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("category_name") val categoryName: String = "",
    @SerializedName("parent_id") val parentId: Int = 0
)

data class SeriesStream(
    @SerializedName("num") val num: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("series_id") val seriesId: Int = 0,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("rating_5based") val rating5Based: Double? = null,
    @SerializedName("backdrop_path") val backdropPath: List<String>? = null,
    @SerializedName("youtube_trailer") val youtubeTrailer: String? = null,
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("added") val added: String? = null,
    @SerializedName("last_modified") val lastModified: String? = null
)

// --- Sıralama Seçenekleri (TiviMate Tarzı) ---
enum class SortOption(val displayName: String) {
    DEFAULT("Varsayılan Sıra"),
    ADDED_DESC("Eklenme Tarihi"),
    RATING_DESC("Puan Sıralaması"),
    NAME_ASC("A - Z (İsme Göre)"),
    NAME_DESC("Z - A (İsme Göre)")
}

data class SeriesInfoResponse(
    @SerializedName("seasons") val seasons: List<SeriesSeason>? = null,
    @SerializedName("info") val info: SeriesInfoDetail? = null,
    @SerializedName("episodes") val episodes: Map<String, List<SeriesEpisode>>? = null
)

data class SeriesSeason(
    @SerializedName("air_date") val airDate: String? = null,
    @SerializedName("episode_count") val episodeCount: Int = 0,
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("season_number") val seasonNumber: Int = 1,
    @SerializedName("cover") val cover: String? = null
)

data class SeriesInfoDetail(
    @SerializedName("name") val name: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("backdrop_path") val backdropPath: List<String>? = null
)

data class SeriesEpisode(
    @SerializedName("id") val id: String = "",
    @SerializedName("episode_num") val episodeNum: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("container_extension") val containerExtension: String = "mp4",
    @SerializedName("info") val info: EpisodeInfo? = null,
    @SerializedName("season") val season: Int = 1
)

data class EpisodeInfo(
    @SerializedName("movie_image") val movieImage: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("duration_secs") val durationSecs: Long? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("rating") val rating: String? = null
)

// --- EPG Models ---
data class ShortEpgResponse(
    @SerializedName("epg_listings") val epgListings: List<EpgProgram>? = null
)

data class EpgProgram(
    @SerializedName("id") val id: String? = null,
    @SerializedName("epg_id") val epgId: String? = null,
    @SerializedName("title") val title: String = "",
    @SerializedName("lang") val lang: String? = null,
    @SerializedName("start") val start: String? = null,
    @SerializedName("end") val end: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("start_timestamp") val startTimestamp: Long = 0,
    @SerializedName("stop_timestamp") val stopTimestamp: Long = 0,
    @SerializedName("now_playing") val nowPlaying: Int = 0
) {
    fun getDecodedTitle(): String {
        if (title.isBlank()) return "Canlı Yayın"
        return try {
            val decoded = android.util.Base64.decode(title, android.util.Base64.DEFAULT)
            val str = String(decoded, Charsets.UTF_8).trim()
            if (str.isNotBlank() && str.all { it.code >= 32 || it == '\n' || it == '\r' || it == '\t' }) str else title
        } catch (e: Exception) {
            title
        }
    }

    fun getFormattedTime(): String {
        return try {
            val startTime = if (!start.isNullOrBlank() && start.length >= 16) start.substring(11, 16) else ""
            val endTime = if (!end.isNullOrBlank() && end.length >= 16) end.substring(11, 16) else ""
            if (startTime.isNotBlank() && endTime.isNotBlank()) {
                "$startTime - $endTime"
            } else if (startTimestamp > 0 && stopTimestamp > 0) {
                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val s = sdf.format(java.util.Date(startTimestamp * 1000L))
                val e = sdf.format(java.util.Date(stopTimestamp * 1000L))
                "$s - $e"
            } else {
                "Canlı Yayın"
            }
        } catch (e: Exception) {
            "Canlı Yayın"
        }
    }

    fun getProgressFraction(): Float {
        val nowSec = System.currentTimeMillis() / 1000L
        return if (startTimestamp > 0 && stopTimestamp > startTimestamp) {
            if (nowSec in startTimestamp..stopTimestamp) {
                ((nowSec - startTimestamp).toFloat() / (stopTimestamp - startTimestamp).toFloat()).coerceIn(0.05f, 0.98f)
            } else if (nowSec > stopTimestamp) {
                1.0f
            } else {
                0.0f
            }
        } else {
            0.4f
        }
    }
}
