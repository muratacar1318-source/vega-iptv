package com.tivimax.player.ui.vm

import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.data.repository.XtreamRepository
import com.tivimax.player.ui.SearchFilter
import com.tivimax.player.ui.live.modern.TurkishStringNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * G-054: Evrensel arama (MainViewModel'den taşındı).
 * İzin verilen kategori kümeleri tembel (lambda) alınır: arama anında güncel değer okunur.
 */
class UniversalSearchController(
    private val scope: CoroutineScope,
    private val repository: XtreamRepository,
    private val preferences: UserPreferences,
    private val allowedLiveCategoryIds: () -> Set<String>,
    private val allowedVodCategoryIds: () -> Set<String>,
    private val allowedSeriesCategoryIds: () -> Set<String>
) {
    val searchQuery = MutableStateFlow("")
    val searchFilter = MutableStateFlow(SearchFilter.ALL)
    val isSearching = MutableStateFlow(false)
    val liveResults = MutableStateFlow<List<LiveStream>>(emptyList())
    val vodResults = MutableStateFlow<List<VodStream>>(emptyList())
    val seriesResults = MutableStateFlow<List<SeriesStream>>(emptyList())

    val recentSearches: StateFlow<List<String>> = preferences.recentSearchesFlow.stateIn(
        scope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private var searchJob: Job? = null

    fun setQuery(query: String) {
        searchQuery.value = query
        triggerSearch(query)
    }

    fun setFilter(filter: SearchFilter) {
        if (searchFilter.value != filter) {
            searchFilter.value = filter
            if (searchQuery.value.trim().length >= 2) {
                triggerSearch(searchQuery.value)
            }
        }
    }

    fun saveRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.length >= 2) {
            scope.launch {
                preferences.addRecentSearch(clean)
            }
        }
    }

    fun removeRecentSearch(query: String) {
        scope.launch {
            preferences.removeRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        scope.launch {
            preferences.clearRecentSearches()
        }
    }

    private fun triggerSearch(query: String) {
        searchJob?.cancel()
        val clean = query.trim()
        if (clean.length < 2) {
            liveResults.value = emptyList()
            vodResults.value = emptyList()
            seriesResults.value = emptyList()
            isSearching.value = false
            return
        }

        val activeFilter = searchFilter.value
        searchJob = scope.launch(Dispatchers.IO) {
            delay(300) // 300ms debounce
            isSearching.value = true

            val qLower = clean.lowercase(Locale.ROOT)
            val qUpperTr = clean.uppercase(Locale.forLanguageTag("tr-TR"))
            val qUpperEn = clean.uppercase(Locale.ENGLISH)
            val qNorm = TurkishStringNormalizer.normalize(clean)
            val variants = setOf(clean, qLower, qUpperTr, qUpperEn, qNorm).filter { it.length >= 2 }

            val needLive = activeFilter == SearchFilter.ALL || activeFilter == SearchFilter.LIVE
            val needVod = activeFilter == SearchFilter.ALL || activeFilter == SearchFilter.MOVIES
            val needSeries = activeFilter == SearchFilter.ALL || activeFilter == SearchFilter.SERIES

            val allowedLiveCats = if (needLive) allowedLiveCategoryIds() else emptySet()
            val allowedVodCats = if (needVod) allowedVodCategoryIds() else emptySet()
            val allowedSeriesCats = if (needSeries) allowedSeriesCategoryIds() else emptySet()

            val (sortedLive, sortedVod, sortedSeries) = coroutineScope {
                val liveDeferred = async {
                    if (!needLive) return@async emptyList<LiveStream>()
                    val liveMap = mutableMapOf<Int, LiveStream>()
                    for (v in variants) {
                        try {
                            val list = repository.searchLiveStreamsFlow(v).first()
                            for (stream in list) {
                                if (stream.categoryId in allowedLiveCats && TurkishStringNormalizer.matches(stream.name, clean)) {
                                    liveMap[stream.streamId] = stream
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                        }
                    }
                    liveMap.values.sortedWith(
                        compareByDescending<LiveStream> {
                            TurkishStringNormalizer.normalize(it.name).startsWith(qNorm)
                        }.thenBy { it.name }
                    ).take(30)
                }

                val vodDeferred = async {
                    if (!needVod) return@async emptyList<VodStream>()
                    val vodMap = mutableMapOf<Int, VodStream>()
                    for (v in variants) {
                        try {
                            val list = repository.searchVodStreamsFlow(v).first()
                            for (stream in list) {
                                if (stream.categoryId in allowedVodCats && TurkishStringNormalizer.matches(stream.name, clean)) {
                                    vodMap[stream.streamId] = stream
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                        }
                    }
                    vodMap.values.sortedWith(
                        compareByDescending<VodStream> {
                            TurkishStringNormalizer.normalize(it.name).startsWith(qNorm)
                        }.thenBy { it.name }
                    ).take(30)
                }

                val seriesDeferred = async {
                    if (!needSeries) return@async emptyList<SeriesStream>()
                    val seriesMap = mutableMapOf<Int, SeriesStream>()
                    for (v in variants) {
                        try {
                            val list = repository.searchSeriesStreamsFlow(v).first()
                            for (stream in list) {
                                if (stream.categoryId in allowedSeriesCats && TurkishStringNormalizer.matches(stream.name, clean)) {
                                    seriesMap[stream.seriesId] = stream
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                        }
                    }
                    seriesMap.values.sortedWith(
                        compareByDescending<SeriesStream> {
                            TurkishStringNormalizer.normalize(it.name).startsWith(qNorm)
                        }.thenBy { it.name }
                    ).take(30)
                }

                Triple(liveDeferred.await(), vodDeferred.await(), seriesDeferred.await())
            }

            liveResults.value = sortedLive
            vodResults.value = sortedVod
            seriesResults.value = sortedSeries
            isSearching.value = false
        }
    }

    /** Devam eden aramayı iptal eder (oturum sıfırlanırken kullanılır). */
    fun cancelSearch() {
        searchJob?.cancel()
        isSearching.value = false
    }
}
