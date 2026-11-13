package com.tivimax.player.ui.vm

import android.util.Log
import com.tivimax.player.BuildConfig
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

    fun loadEpgSummary(streamId: Int) {
        if (epgSummaryMap.value.containsKey(streamId)) return
        scope.launch(Dispatchers.IO) {
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
}
