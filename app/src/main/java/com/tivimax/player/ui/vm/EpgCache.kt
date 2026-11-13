package com.tivimax.player.ui.vm

import android.util.Log
import com.tivimax.player.BuildConfig
import com.tivimax.player.data.epg.XmltvParser
import com.tivimax.player.data.local.entity.EpgProgramEntity
import com.tivimax.player.data.model.EpgProgram
import com.tivimax.player.data.repository.XtreamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * G-054: Canlı TV kanal kartlarındaki kısa EPG önbelleği (MainViewModel'den taşındı; mantık değişmedi).
 */
class EpgCache(
    private val scope: CoroutineScope,
    private val repository: XtreamRepository
) {
    val epgSummaryMap = MutableStateFlow<Map<Int, EpgProgram>>(emptyMap())
    val epgProgramsMap = MutableStateFlow<Map<Int, List<EpgProgram>>>(emptyMap())

    /**
     * G-060: Önce toplu program rehberi (veritabanı); kanal için veri yoksa eski yol (kanal başına `get_short_epg`).
     */
    fun loadEpgSummary(streamId: Int) {
        if (epgSummaryMap.value.containsKey(streamId)) return
        scope.launch(Dispatchers.IO) {
            val fromDatabase = loadFromDatabase(streamId)
            if (fromDatabase != null) {
                withContext(Dispatchers.Main) { publish(streamId, fromDatabase.first, fromDatabase.second) }
                return@launch
            }
            repository.getShortEpg(streamId).onSuccess { list ->
                if (list.isNotEmpty()) {
                    val nowSec = System.currentTimeMillis() / 1000L
                    val currentProgram = list.firstOrNull {
                        it.nowPlaying == 1 || (it.startTimestamp > 0 && it.stopTimestamp > 0 && nowSec in it.startTimestamp..it.stopTimestamp)
                    } ?: list.first()

                    withContext(Dispatchers.Main) {
                        epgSummaryMap.value = epgSummaryMap.value + (streamId to currentProgram)
                        epgProgramsMap.value = epgProgramsMap.value + (streamId to list)
                        if (BuildConfig.DEBUG) {
                            Log.d("G005_EPG", "EPG parsed: streamId=$streamId, title='${currentProgram.getDecodedTitle()}', rawStart='${currentProgram.start}', startTimestamp=${currentProgram.startTimestamp}, getFormattedTime='${currentProgram.getFormattedTime()}', LiveTimeFormatter='${com.tivimax.player.ui.player.components.LiveTimeFormatter.formatTimeRange(currentProgram)}'")
                        }
                    }
                }
            }
        }
    }

    /** G-060: Yeni rehber indirilince bellekteki kanalları veritabanından tazeler. */
    fun refreshFromDatabase() {
        scope.launch(Dispatchers.IO) {
            for (streamId in epgSummaryMap.value.keys.toList()) {
                val fresh = loadFromDatabase(streamId) ?: continue
                withContext(Dispatchers.Main) { publish(streamId, fresh.first, fresh.second) }
            }
        }
    }

    private fun publish(streamId: Int, current: EpgProgram, list: List<EpgProgram>) {
        epgSummaryMap.value = epgSummaryMap.value + (streamId to current)
        epgProgramsMap.value = epgProgramsMap.value + (streamId to list)
    }

    /** Kanalın şu anki programı ve şimdiden 24 saat ilerisi (15 dk geriden başlar); veri yoksa null. */
    private suspend fun loadFromDatabase(streamId: Int): Pair<EpgProgram, List<EpgProgram>>? {
        val db = repository.database ?: return null
        return try {
            val epgId = db.liveDao().getStreamById(streamId)?.epgChannelId
                ?.let { XmltvParser.normalizeChannelId(it) }
                ?.takeIf { it.isNotEmpty() } ?: return null
            val now = System.currentTimeMillis()
            val entities = db.epgDao().programsFor(epgId, now - PAST_GRACE_MS, now + AHEAD_MS)
            if (entities.isEmpty()) return null
            val list = entities.map { it.toEpgProgram(now) }
            val current = list.firstOrNull { it.nowPlaying == 1 } ?: list.first()
            current to list
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("EpgCache", "Program rehberi okunamadı (${e.javaClass.simpleName})")
            null
        }
    }

    private companion object {
        const val PAST_GRACE_MS = 15L * 60_000L
        const val AHEAD_MS = 24L * 3600_000L
    }
}

/**
 * G-060: Veritabanı programını mevcut arayüzün kullandığı `EpgProgram`'a çevirir. Arayüz başlık/açıklamayı
 * `decodeBase64IfNeeded` ile çözdüğü için düz metin Base64 olarak verilir (ör. "News" yanlışlıkla çözülmesin).
 */
internal fun EpgProgramEntity.toEpgProgram(nowMs: Long): EpgProgram {
    fun b64(text: String) = android.util.Base64.encodeToString(text.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
    val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
    return EpgProgram(
        id = id.toString(),
        epgId = channelEpgId,
        title = b64(title),
        start = format.format(java.util.Date(startMs)),
        end = format.format(java.util.Date(endMs)),
        description = description?.let { b64(it) },
        startTimestamp = startMs / 1000L,
        stopTimestamp = endMs / 1000L,
        nowPlaying = if (nowMs in startMs until endMs) 1 else 0
    )
}
