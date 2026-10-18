package com.tivimax.player.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.util.ArrayDeque

/**
 * G-040: Canlı .ts yayınlarda bağlantı başındaki eski yayını [LiveBurstTrimmer] ile atar; gerisini aynen geçirir.
 * [shouldTrim] false ise hiçbir şeye dokunmaz. Beklenmeyen bir hata olursa kırpma bu oturum boyunca kapanır.
 */
@UnstableApi
class LiveBurstTrimDataSource(
    private val upstream: DataSource,
    private val shouldTrim: (DataSpec) -> Boolean
) : DataSource {

    private var pending: ArrayDeque<ByteArray> = ArrayDeque()
    private var current: ByteArray? = null
    private var currentOffset = 0

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        pending = ArrayDeque()
        current = null
        currentOffset = 0
        val length = upstream.open(dataSpec)
        val trim = !trimDisabled && try { shouldTrim(dataSpec) } catch (_: Throwable) { false }
        if (!trim) return length
        try {
            val trimmer = LiveBurstTrimmer()
            pending = trimmer.preRead { b, o, l -> upstream.read(b, o, l) }
            lastStatsText = trimmer.lastStats
        } catch (e: IOException) {
            throw e
        } catch (e: Throwable) {
            // Beklenmeyen durum: bu oturumda kırpmayı kapat, oynatıcı normal yeniden denemeyle devam eder
            trimDisabled = true
            throw IOException("Canlı tampon kırpma devre dışı bırakıldı", e)
        }
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            val cur = current
            if (cur != null && currentOffset < cur.size) {
                val n = minOf(length, cur.size - currentOffset)
                System.arraycopy(cur, currentOffset, buffer, offset, n)
                currentOffset += n
                return n
            }
            val next = pending.pollFirst()
            if (next == null) {
                current = null
                return upstream.read(buffer, offset, length)
            }
            current = next
            currentOffset = 0
        }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        pending = ArrayDeque()
        current = null
        upstream.close()
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val shouldTrim: (DataSpec) -> Boolean
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            LiveBurstTrimDataSource(upstreamFactory.createDataSource(), shouldTrim)
    }

    companion object {
        @Volatile
        var trimDisabled: Boolean = false

        /** G-041: Son kırpmanın teşhis özeti (geliştirici ekranı). */
        @Volatile
        var lastStatsText: String = ""
    }
}
