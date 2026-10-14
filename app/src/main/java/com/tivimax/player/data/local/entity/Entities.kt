package com.tivimax.player.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tivimax.player.data.model.*

// --- CANLI YAYIN TABLOLARI ---

@Entity(tableName = "live_categories")
data class LiveCategoryEntity(
    @PrimaryKey val categoryId: String,
    val categoryName: String,
    val parentId: Int = 0
) {
    fun toModel(): LiveCategory = LiveCategory(
        categoryId = categoryId,
        categoryName = categoryName,
        parentId = parentId
    )
}

fun LiveCategory.toEntity(): LiveCategoryEntity = LiveCategoryEntity(
    categoryId = categoryId,
    categoryName = categoryName,
    parentId = parentId
)

@Entity(
    tableName = "live_streams",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["name"]),
        Index(value = ["num"])
    ]
)
data class LiveStreamEntity(
    @PrimaryKey val streamId: Int,
    val num: Int = 0,
    val name: String = "",
    val streamType: String = "live",
    val streamIcon: String? = null,
    val epgChannelId: String? = null,
    val added: String? = null,
    val categoryId: String = "",
    val customSid: String? = null,
    val tvArchive: Int = 0,
    val directSource: String? = null
) {
    fun toModel(): LiveStream = LiveStream(
        num = num,
        name = name,
        streamType = streamType,
        streamId = streamId,
        streamIcon = streamIcon,
        epgChannelId = epgChannelId,
        added = added,
        categoryId = categoryId,
        customSid = customSid,
        tvArchive = tvArchive,
        directSource = directSource
    )
}

fun LiveStream.toEntity(): LiveStreamEntity = LiveStreamEntity(
    streamId = streamId,
    num = num,
    name = name ?: "",
    streamType = streamType ?: "live",
    streamIcon = streamIcon,
    epgChannelId = epgChannelId,
    added = added,
    categoryId = categoryId ?: "",
    customSid = customSid,
    tvArchive = tvArchive,
    directSource = directSource
)

// --- VOD (FİLM) TABLOLARI ---

@Entity(tableName = "vod_categories")
data class VodCategoryEntity(
    @PrimaryKey val categoryId: String,
    val categoryName: String,
    val parentId: Int = 0
) {
    fun toModel(): VodCategory = VodCategory(
        categoryId = categoryId,
        categoryName = categoryName,
        parentId = parentId
    )
}

fun VodCategory.toEntity(): VodCategoryEntity = VodCategoryEntity(
    categoryId = categoryId,
    categoryName = categoryName,
    parentId = parentId
)

@Entity(
    tableName = "vod_streams",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["name"]),
        Index(value = ["rating5Based"])
    ]
)
data class VodStreamEntity(
    @PrimaryKey val streamId: Int,
    val num: Int = 0,
    val name: String = "",
    val streamType: String = "movie",
    val streamIcon: String? = null,
    val rating: String? = null,
    val rating5Based: Double? = null,
    val added: String? = null,
    val categoryId: String = "",
    val containerExtension: String = "mp4"
) {
    fun toModel(): VodStream = VodStream(
        num = num,
        name = name,
        streamType = streamType,
        streamId = streamId,
        streamIcon = streamIcon,
        rating = rating,
        rating5Based = rating5Based,
        added = added,
        categoryId = categoryId,
        containerExtension = containerExtension
    )
}

fun VodStream.toEntity(): VodStreamEntity = VodStreamEntity(
    streamId = streamId,
    num = num,
    name = name ?: "",
    streamType = streamType ?: "movie",
    streamIcon = streamIcon,
    rating = rating,
    rating5Based = rating5Based,
    added = added,
    categoryId = categoryId ?: "",
    containerExtension = containerExtension ?: "mp4"
)

// --- DİZİ TABLOLARI ---

@Entity(tableName = "series_categories")
data class SeriesCategoryEntity(
    @PrimaryKey val categoryId: String,
    val categoryName: String,
    val parentId: Int = 0
) {
    fun toModel(): SeriesCategory = SeriesCategory(
        categoryId = categoryId,
        categoryName = categoryName,
        parentId = parentId
    )
}

fun SeriesCategory.toEntity(): SeriesCategoryEntity = SeriesCategoryEntity(
    categoryId = categoryId,
    categoryName = categoryName,
    parentId = parentId
)

@Entity(
    tableName = "series_streams",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["name"]),
        Index(value = ["rating5Based"])
    ]
)
data class SeriesStreamEntity(
    @PrimaryKey val seriesId: Int,
    val num: Int = 0,
    val name: String = "",
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val rating: String? = null,
    val rating5Based: Double? = null,
    val youtubeTrailer: String? = null,
    val categoryId: String = "",
    val added: String? = null,
    val lastModified: String? = null
) {
    fun toModel(): SeriesStream = SeriesStream(
        num = num,
        name = name,
        seriesId = seriesId,
        cover = cover,
        plot = plot,
        cast = cast,
        director = director,
        genre = genre,
        releaseDate = releaseDate,
        rating = rating,
        rating5Based = rating5Based,
        youtubeTrailer = youtubeTrailer,
        categoryId = categoryId,
        added = added,
        lastModified = lastModified
    )
}

fun SeriesStream.toEntity(): SeriesStreamEntity = SeriesStreamEntity(
    seriesId = seriesId,
    num = num,
    name = name ?: "",
    cover = cover,
    plot = plot,
    cast = cast,
    director = director,
    genre = genre,
    releaseDate = releaseDate,
    rating = rating,
    rating5Based = rating5Based,
    youtubeTrailer = youtubeTrailer,
    categoryId = categoryId ?: "",
    added = added,
    lastModified = lastModified
)
