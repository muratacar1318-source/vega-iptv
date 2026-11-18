package com.tivimax.player.ui.vm

import android.util.Log
import com.tivimax.player.BuildConfig
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.repository.XtreamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * G-054: Canlı TV kanal kartlarındaki kısa EPG önbelleği (MainViewModel'den taşındı).
 * Eşzamanlılık sınırı (Semaphore(3)), uçuş içi tekilleştirme, boş yanıt TTL'si (5 dk),
 * program bitiş süresi kontrolü ve 200 kayıtlık LRU sınırı uygular.
 */
class EpgCache(
    private val scope: CoroutineScope,
    private val repository: XtreamRepository
) {
    companion object {
        private const val MAX_CONCURRENT_REQUESTS = 3
        private const val MAX_CACHE_ENTRIES = 200
        private const val EMPTY_TTL_MS = 5 * 60 * 1000L
    }

    val epgSummaryMap = MutableStateFlow<Map<Int, EpgProgram>>(emptyMap())
    val epgProgramsMap = MutableStateFlow<Map<Int, List<EpgProgram>>>(emptyMap())

    private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val inFlightIds = ConcurrentHashMap.newKeySet<Int>()
    private val emptyIds = ConcurrentHashMap<Int, Long>()
    private val activeJobs = CopyOnWriteArrayList<Job>()
    private val generation = AtomicLong(0L)

    fun loadEpgSummary(streamId: Int) {
        if (streamId <= 0) return
        val nowMs = System.currentTimeMillis()
        val nowSec = nowMs / 1000L
        if (isCacheFresh(streamId, nowSec)) return
        if (isEmptyCached(streamId, nowMs)) return
        if (!inFlightIds.add(streamId)) return

        val expectedGen = generation.get()
        val job = scope.launch(Dispatchers.IO) {
            try {
                semaphore.withPermit {
                    fetchAndStore(streamId, expectedGen)
                }
            } finally {
                inFlightIds.remove(streamId)
            }
        }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    fun loadEpgSummariesBatch(streamIds: List<Int>) {
        if (streamIds.isEmpty()) return
        val nowMs = System.currentTimeMillis()
        val nowSec = nowMs / 1000L
        val toFetch = ArrayList<Int>()
        for (id in streamIds.distinct()) {
            if (id <= 0) continue
            if (isCacheFresh(id, nowSec)) continue
            if (isEmptyCached(id, nowMs)) continue
            if (inFlightIds.add(id)) {
                toFetch.add(id)
            }
        }
        if (toFetch.isEmpty()) return

        val expectedGen = generation.get()
        val job = scope.launch(Dispatchers.IO) {
            try {
                coroutineScope {
                    toFetch.map { id ->
                        async {
                            try {
                                semaphore.withPermit {
                                    fetchAndStore(id, expectedGen)
                                }
                            } finally {
                                inFlightIds.remove(id)
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                toFetch.forEach { inFlightIds.remove(it) }
            }
        }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    fun clear() {
        generation.incrementAndGet()
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        inFlightIds.clear()
        emptyIds.clear()
        epgSummaryMap.value = emptyMap()
        epgProgramsMap.value = emptyMap()
    }

    private fun isCacheFresh(streamId: Int, nowSec: Long): Boolean {
        val cached = epgSummaryMap.value[streamId] ?: return false
        if (cached.stopTimestamp in 1 until nowSec) {
            return false
        }
        return true
    }

    private fun isEmptyCached(streamId: Int, nowMs: Long): Boolean {
        val fetchedAt = emptyIds[streamId] ?: return false
        if (nowMs - fetchedAt < EMPTY_TTL_MS) {
            return true
        }
        emptyIds.remove(streamId, fetchedAt)
        return false
    }

    private suspend fun fetchAndStore(streamId: Int, expectedGen: Long) {
        if (generation.get() != expectedGen) return
        try {
            repository.getShortEpg(streamId).onSuccess { list ->
                if (generation.get() != expectedGen) return@onSuccess
                if (list.isNotEmpty()) {
                    emptyIds.remove(streamId)
                    val nowSec = System.currentTimeMillis() / 1000L
                    val currentProgram = list.firstOrNull {
                        it.nowPlaying == 1 || (it.startTimestamp > 0 && it.stopTimestamp > 0 && nowSec in it.startTimestamp..it.stopTimestamp)
                    } ?: list.first()

                    withContext(Dispatchers.Main) {
                        if (generation.get() != expectedGen) return@withContext
                        epgSummaryMap.update { putCapped(it, streamId, currentProgram) }
                        epgProgramsMap.update { putCapped(it, streamId, list) }
                        if (BuildConfig.DEBUG) {
                            Log.d("G005_EPG", "EPG parsed: streamId=$streamId, title='${currentProgram.getDecodedTitle()}', rawStart='${currentProgram.start}', startTimestamp=${currentProgram.startTimestamp}, getFormattedTime='${currentProgram.getFormattedTime()}', LiveTimeFormatter='${com.tivimax.player.ui.player.components.LiveTimeFormatter.formatTimeRange(currentProgram)}'")
                        }
                    }
                } else {
                    emptyIds[streamId] = System.currentTimeMillis()
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("EpgCache", "EPG fetch failed for $streamId (${e.javaClass.simpleName})")
        }
    }

    private fun <V> putCapped(current: Map<Int, V>, key: Int, value: V): Map<Int, V> {
        val next = LinkedHashMap<Int, V>(current.size + 1)
        next.putAll(current)
        next.remove(key)
        next[key] = value
        while (next.size > MAX_CACHE_ENTRIES) {
            val oldestKey = next.keys.firstOrNull() ?: break
            next.remove(oldestKey)
        }
        return next
    }
}
