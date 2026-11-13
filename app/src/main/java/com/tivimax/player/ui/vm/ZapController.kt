package com.tivimax.player.ui.vm

import android.content.Context
import android.widget.Toast
import com.tivimax.player.data.model.LiveStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * G-054: Kanal geçişi (numara ile geçiş, CH+/CH−, önceki kanala dönüş) — MainViewModel'den taşındı; mantık değişmedi.
 * MainViewModel'de kalan ve sonradan kurulan şeyler (ham/süzülmüş kanal listeleri, engel kümesi, oynatma)
 * lambda olarak verilir; böylece başlatma sırasından bağımsız, çağrı anında güncel değer okunur.
 */
class ZapController(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val liveStreams: StateFlow<List<LiveStream>>,
    private val lastPlayedLiveChannel: StateFlow<LiveStream?>,
    private val allLiveStreams: () -> List<LiveStream>,
    private val allLiveStreamsRaw: () -> List<LiveStream>,
    private val blockedLiveCategoryIds: () -> Set<String>,
    private val isTvMode: () -> Boolean,
    private val showLiveTvTab: () -> Unit,
    private val playLiveChannel: (LiveStream) -> Unit,
    private val scheduleTvZap: (LiveStream) -> Unit
) {
    // Hızlı Kanal Numarasıyla Geçiş (Numeric Zapper)
    val zapperNumber = MutableStateFlow("")
    val isZapperActive = MutableStateFlow(false)
    val zapperMatchedChannel = MutableStateFlow<LiveStream?>(null)
    private var zapperJob: Job? = null

    fun onNumericKeyPressed(digit: Int) {
        val current = zapperNumber.value
        val newNumber = if (current.length >= 4) digit.toString() else current + digit.toString()
        zapperNumber.value = newNumber
        isZapperActive.value = true

        val targetNum = newNumber.toIntOrNull() ?: return
        // G-052: Numara eşlemesi ham listede yapılır; engelli (+18 / gizli) kanal seçilirse geçilmez.
        val allList = allLiveStreamsRaw().ifEmpty { liveStreams.value }
        val found = allList.firstOrNull { it.num == targetNum }
            ?: allList.firstOrNull { it.streamId == targetNum }
            ?: allList.getOrNull(targetNum - 1)
        val matched = if (found != null && found.categoryId in blockedLiveCategoryIds()) {
            Toast.makeText(appContext, "Bu kanal kilitli", Toast.LENGTH_SHORT).show()
            null
        } else {
            found
        }
        zapperMatchedChannel.value = matched

        zapperJob?.cancel()
        zapperJob = scope.launch {
            delay(1500L)
            executeZapperSwitch()
        }
    }

    fun executeZapperSwitch() {
        zapperJob?.cancel()
        val matched = zapperMatchedChannel.value
        if (matched != null) {
            if (isTvMode()) {
                showLiveTvTab()
            }
            playLiveChannel(matched)
        }
        dismissZapper()
    }

    fun dismissZapper() {
        zapperJob?.cancel()
        zapperNumber.value = ""
        isZapperActive.value = false
        zapperMatchedChannel.value = null
    }

    fun zapNextChannel(currentStreamId: Int): LiveStream? {
        val list = if (liveStreams.value.any { it.streamId == currentStreamId }) {
            liveStreams.value
        } else {
            allLiveStreams().ifEmpty { liveStreams.value }
        }
        if (list.isEmpty()) return null
        val currentIndex = list.indexOfFirst { it.streamId == currentStreamId }
        val nextIndex = if (currentIndex != -1) (currentIndex + 1) % list.size else 0
        val nextChannel = list[nextIndex]
        if (isTvMode()) {
            scheduleTvZap(nextChannel)
        } else {
            playLiveChannel(nextChannel)
        }
        return nextChannel
    }

    fun zapPreviousChannel(currentStreamId: Int): LiveStream? {
        val list = if (liveStreams.value.any { it.streamId == currentStreamId }) {
            liveStreams.value
        } else {
            allLiveStreams().ifEmpty { liveStreams.value }
        }
        if (list.isEmpty()) return null
        val currentIndex = list.indexOfFirst { it.streamId == currentStreamId }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else if (currentIndex == 0) list.size - 1 else 0
        val prevChannel = list[prevIndex]
        if (isTvMode()) {
            scheduleTvZap(prevChannel)
        } else {
            playLiveChannel(prevChannel)
        }
        return prevChannel
    }

    fun zapRecall(): LiveStream? {
        val prev = lastPlayedLiveChannel.value ?: return null
        if (isTvMode()) {
            scheduleTvZap(prev)
        } else {
            playLiveChannel(prev)
        }
        return prev
    }
}
