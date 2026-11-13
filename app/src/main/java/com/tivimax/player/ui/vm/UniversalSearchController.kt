package com.tivimax.player.ui.vm

import com.tivimax.player.data.model.LiveStream
import com.tivimax.player.data.model.SeriesStream
import com.tivimax.player.data.model.VodStream
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.data.repository.XtreamRepository
import com.tivimax.player.ui.SearchFilter
import com.tivimax.player.ui.live.modern.TurkishStringNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * G-054: Evrensel arama (MainViewModel'den taşındı; mantık değişmedi).
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
        searchFilter.value = filter
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

        searchJob = scope.launch(Dispatchers.IO) {
            delay(300) // 300ms debounce
            isSearching.value = true

            val qLower = clean.lowercase(Locale.ROOT)
            val qUpperTr = clean.uppercase(Locale.forLanguageTag("tr-TR"))
            val qUpperEn = clean.uppercase(Locale.ENGLISH)
            val qNorm = TurkishStringNormalizer.normalize(clean)
            val variants = setOf(clean, qLower, qUpperTr, qUpperEn, qNorm).filter { it.length >= 2 }

            val allowedLiveCats = allowedLiveCategoryIds()
            val allowedVodCats = allowedVodCategoryIds()
            val allowedSeriesCats = allowedSeriesCategoryIds()

            // 1. Canlı TV
            val liveMap = mutableMapOf<Int, LiveStream>()
            for (v in variants) {
                try {
                    val list = repository.searchLiveStreamsFlow(v).first()
                    for (stream in list) {
                        if (stream.categoryId in allowedLiveCats && TurkishStringNormalizer.matches(stream.name, clean)) {
                            liveMap[stream.streamId] = stream
                        }
                    }
                } catch (_: Exception) {}
            }
            val sortedLive = liveMap.values.sortedWith(
                compareByDescending<LiveStream> {
                    TurkishStringNormalizer.normalize(it.name).startsWith(qNorm)
                }.thenBy { it.name }
            ).take(30)

            // 2. Filmler (VOD)
            val vodMap = mutableMapOf<Int, VodStream>()
            for (v in variants) {
                try {
                    val list = repository.searchVodStreamsFlow(v).first()
                    for (stream in list) {
                        if (stream.categoryId in allowedVodCats && TurkishStringNormalizer.matches(stream.name, clean)) {
                            vodMap[stream.streamId] = stream
                        }
                    }
                } catch (_: Exception) {}
            }
            val sortedVod = vodMap.values.sortedWith(
                compareByDescending<VodStream> {
                    TurkishStringNormalizer.normalize(it.name).startsWith(qNorm)
                }.thenBy { it.name }
            ).take(30)

            // 3. Diziler
            val seriesMap = mutableMapOf<Int, SeriesStream>()
            for (v in variants) {
                try {
                    val list = repository.searchSeriesStreamsFlow(v).first()
                    for (stream in list) {
                        if (stream.categoryId in allowedSeriesCats && TurkishStringNormalizer.matches(stream.name, clean)) {
                            seriesMap[stream.seriesId] = stream
                        }
                    }
                } catch (_: Exception) {}
            }
            val sortedSeries = seriesMap.values.sortedWith(
                compareByDescending<SeriesStream> {
                    TurkishStringNormalizer.normalize(it.name).startsWith(qNorm)
                }.thenBy { it.name }
            ).take(30)

            liveResults.value = sortedLive
            vodResults.value = sortedVod
            seriesResults.value = sortedSeries
            isSearching.value = false
        }
    }

    /** Devam eden aramayı iptal eder (oturum sıfırlanırken kullanılır). */
    fun cancelSearch() {
        searchJob?.cancel()
    }
}
