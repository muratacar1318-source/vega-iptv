package com.tivimax.player.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tivimax.player.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<LiveCategoryEntity>)

    @Query("SELECT * FROM live_categories ORDER BY categoryName ASC")
    fun getCategories(): Flow<List<LiveCategoryEntity>>

    @Query("SELECT * FROM live_categories ORDER BY categoryName ASC")
    suspend fun getCategoriesList(): List<LiveCategoryEntity>

    @Query("DELETE FROM live_categories")
    suspend fun clearCategories()

    @Query("UPDATE live_categories SET itemCount = (SELECT COUNT(*) FROM live_streams WHERE live_streams.categoryId = live_categories.categoryId)")
    suspend fun updateAllCategoryItemCounts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<LiveStreamEntity>)

    @Query("SELECT * FROM live_streams ORDER BY num ASC, name ASC")
    fun getAllStreams(): Flow<List<LiveStreamEntity>>

    @Query("SELECT * FROM live_streams WHERE categoryId = :categoryId ORDER BY num ASC, name ASC")
    fun getStreamsByCategory(categoryId: String): Flow<List<LiveStreamEntity>>

    @Query("SELECT * FROM live_streams WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchStreams(query: String): Flow<List<LiveStreamEntity>>

    @Query("SELECT * FROM live_streams WHERE categoryId = :categoryId AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchStreamsInCategory(categoryId: String, query: String): Flow<List<LiveStreamEntity>>

    @Query("SELECT * FROM live_streams WHERE streamId = :streamId LIMIT 1")
    suspend fun getStreamById(streamId: Int): LiveStreamEntity?

    @Query("SELECT * FROM live_streams WHERE streamId IN (:ids) ORDER BY num ASC, name ASC")
    fun getStreamsByIds(ids: List<Int>): Flow<List<LiveStreamEntity>>

    @Query("""
        SELECT * FROM live_streams 
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
        AND (:query = '' OR name LIKE '%' || :query || '%')
        ORDER BY 
            CASE WHEN :sort = 'NAME_ASC' THEN name END COLLATE NOCASE ASC,
            CASE WHEN :sort = 'NAME_DESC' THEN name END COLLATE NOCASE DESC,
            CASE WHEN :sort = 'ADDED_DESC' THEN added END DESC,
            num ASC, name ASC
    """)
    fun getStreamsFiltered(
        categoryId: String?,
        query: String = "",
        sort: String = "DEFAULT"
    ): Flow<List<LiveStreamEntity>>

    @Query("SELECT COUNT(*) FROM live_streams WHERE categoryId = :categoryId")
    suspend fun getStreamCountByCategory(categoryId: String): Int

    @Query("SELECT COUNT(*) FROM live_streams")
    suspend fun getTotalStreamCount(): Int

    @Query("DELETE FROM live_streams WHERE categoryId = :categoryId")
    suspend fun clearStreamsByCategory(categoryId: String)

    @Query("DELETE FROM live_streams")
    suspend fun clearAllStreams()

    // Paging 3 Desteği
    @Query("SELECT * FROM live_streams ORDER BY num ASC, name ASC")
    fun getPagedAllStreams(): PagingSource<Int, LiveStreamEntity>

    @Query("SELECT * FROM live_streams WHERE categoryId = :categoryId ORDER BY num ASC, name ASC")
    fun getPagedStreamsByCategory(categoryId: String): PagingSource<Int, LiveStreamEntity>

    @Query("SELECT * FROM live_streams WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun getPagedSearchStreams(query: String): PagingSource<Int, LiveStreamEntity>
}

@Dao
interface VodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<VodCategoryEntity>)

    @Query("SELECT * FROM vod_categories ORDER BY categoryName ASC")
    fun getCategories(): Flow<List<VodCategoryEntity>>

    @Query("SELECT * FROM vod_categories ORDER BY categoryName ASC")
    suspend fun getCategoriesList(): List<VodCategoryEntity>

    @Query("DELETE FROM vod_categories")
    suspend fun clearCategories()

    @Query("UPDATE vod_categories SET itemCount = (SELECT COUNT(*) FROM vod_streams WHERE vod_streams.categoryId = vod_categories.categoryId)")
    suspend fun updateAllCategoryItemCounts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<VodStreamEntity>)

    @Query("SELECT * FROM vod_streams ORDER BY added DESC, name ASC")
    fun getAllStreams(): Flow<List<VodStreamEntity>>

    @Query("SELECT * FROM vod_streams WHERE categoryId = :categoryId ORDER BY added DESC, name ASC")
    fun getStreamsByCategory(categoryId: String): Flow<List<VodStreamEntity>>

    @Query("SELECT * FROM vod_streams WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchStreams(query: String): Flow<List<VodStreamEntity>>

    @Query("SELECT * FROM vod_streams WHERE categoryId = :categoryId AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchStreamsInCategory(categoryId: String, query: String): Flow<List<VodStreamEntity>>

    @Query("SELECT * FROM vod_streams WHERE streamId = :streamId LIMIT 1")
    suspend fun getStreamById(streamId: Int): VodStreamEntity?

    @Query("SELECT * FROM vod_streams WHERE streamId IN (:ids) ORDER BY name ASC")
    fun getStreamsByIds(ids: List<Int>): Flow<List<VodStreamEntity>>

    @Query("""
        SELECT * FROM vod_streams 
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
        AND (:query = '' OR name LIKE '%' || :query || '%')
        ORDER BY 
            CASE WHEN :sort = 'NAME_ASC' THEN name END COLLATE NOCASE ASC,
            CASE WHEN :sort = 'NAME_DESC' THEN name END COLLATE NOCASE DESC,
            CASE WHEN :sort = 'RATING_DESC' THEN rating5Based END DESC,
            CASE WHEN :sort = 'ADDED_DESC' THEN added END DESC,
            added DESC, name ASC
    """)
    fun getStreamsFiltered(
        categoryId: String?,
        query: String = "",
        sort: String = "DEFAULT"
    ): Flow<List<VodStreamEntity>>

    @Query("SELECT COUNT(*) FROM vod_streams WHERE categoryId = :categoryId")
    suspend fun getStreamCountByCategory(categoryId: String): Int

    @Query("SELECT COUNT(*) FROM vod_streams")
    suspend fun getTotalStreamCount(): Int

    @Query("DELETE FROM vod_streams WHERE categoryId = :categoryId")
    suspend fun clearStreamsByCategory(categoryId: String)

    @Query("DELETE FROM vod_streams")
    suspend fun clearAllStreams()

    // Paging 3 Desteği
    @Query("SELECT * FROM vod_streams ORDER BY added DESC, name ASC")
    fun getPagedAllStreams(): PagingSource<Int, VodStreamEntity>

    @Query("SELECT * FROM vod_streams WHERE categoryId = :categoryId ORDER BY added DESC, name ASC")
    fun getPagedStreamsByCategory(categoryId: String): PagingSource<Int, VodStreamEntity>

    @Query("SELECT * FROM vod_streams WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun getPagedSearchStreams(query: String): PagingSource<Int, VodStreamEntity>
}

@Dao
interface SeriesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<SeriesCategoryEntity>)

    @Query("SELECT * FROM series_categories ORDER BY categoryName ASC")
    fun getCategories(): Flow<List<SeriesCategoryEntity>>

    @Query("SELECT * FROM series_categories ORDER BY categoryName ASC")
    suspend fun getCategoriesList(): List<SeriesCategoryEntity>

    @Query("DELETE FROM series_categories")
    suspend fun clearCategories()

    @Query("UPDATE series_categories SET itemCount = (SELECT COUNT(*) FROM series_streams WHERE series_streams.categoryId = series_categories.categoryId)")
    suspend fun updateAllCategoryItemCounts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<SeriesStreamEntity>)

    @Query("SELECT * FROM series_streams ORDER BY lastModified DESC, name ASC")
    fun getAllStreams(): Flow<List<SeriesStreamEntity>>

    @Query("SELECT * FROM series_streams WHERE categoryId = :categoryId ORDER BY lastModified DESC, name ASC")
    fun getStreamsByCategory(categoryId: String): Flow<List<SeriesStreamEntity>>

    @Query("SELECT * FROM series_streams WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchStreams(query: String): Flow<List<SeriesStreamEntity>>

    @Query("SELECT * FROM series_streams WHERE categoryId = :categoryId AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchStreamsInCategory(categoryId: String, query: String): Flow<List<SeriesStreamEntity>>

    @Query("SELECT * FROM series_streams WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getStreamById(seriesId: Int): SeriesStreamEntity?

    @Query("SELECT * FROM series_streams WHERE seriesId IN (:ids) ORDER BY name ASC")
    fun getSeriesByIds(ids: List<Int>): Flow<List<SeriesStreamEntity>>

    @Query("""
        SELECT * FROM series_streams 
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
        AND (:query = '' OR name LIKE '%' || :query || '%')
        ORDER BY 
            CASE WHEN :sort = 'NAME_ASC' THEN name END COLLATE NOCASE ASC,
            CASE WHEN :sort = 'NAME_DESC' THEN name END COLLATE NOCASE DESC,
            CASE WHEN :sort = 'RATING_DESC' THEN rating5Based END DESC,
            CASE WHEN :sort = 'ADDED_DESC' THEN lastModified END DESC,
            lastModified DESC, name ASC
    """)
    fun getStreamsFiltered(
        categoryId: String?,
        query: String = "",
        sort: String = "DEFAULT"
    ): Flow<List<SeriesStreamEntity>>

    @Query("SELECT COUNT(*) FROM series_streams WHERE categoryId = :categoryId")
    suspend fun getStreamCountByCategory(categoryId: String): Int

    @Query("SELECT COUNT(*) FROM series_streams")
    suspend fun getTotalStreamCount(): Int

    @Query("DELETE FROM series_streams WHERE categoryId = :categoryId")
    suspend fun clearStreamsByCategory(categoryId: String)

    @Query("DELETE FROM series_streams")
    suspend fun clearAllStreams()

    // Paging 3 Desteği
    @Query("SELECT * FROM series_streams ORDER BY lastModified DESC, name ASC")
    fun getPagedAllStreams(): PagingSource<Int, SeriesStreamEntity>

    @Query("SELECT * FROM series_streams WHERE categoryId = :categoryId ORDER BY lastModified DESC, name ASC")
    fun getPagedStreamsByCategory(categoryId: String): PagingSource<Int, SeriesStreamEntity>

    @Query("SELECT * FROM series_streams WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun getPagedSearchStreams(query: String): PagingSource<Int, SeriesStreamEntity>
}
