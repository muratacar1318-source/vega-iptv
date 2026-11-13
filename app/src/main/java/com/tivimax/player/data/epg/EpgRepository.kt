package com.tivimax.player.data.epg

import android.content.Context
import android.util.Log
import android.util.Xml
import androidx.room.withTransaction
import com.tivimax.player.data.local.entity.EpgProgramEntity
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.data.repository.XtreamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

/**
 * G-060: Toplu program rehberini (XMLTV) arka planda indirip veritabanına yazar.
 *
 * - Dosya önce diske indirilir (G-028 yardımcısı), sonra akış halinde okunur; hiçbir zaman belleğe tamamen alınmaz.
 * - Yazma tek işlemde yapılır: önce `deleteAll()`, sonra 2.000'lik partilerle `insertAll`. Yarıda hata (ya da boş
 *   sonuç) olursa işlem geri alınır ve eski rehber kalır.
 * - Aynı anda tek indirme. Hata/zaman aşımı sessizce eski veriyle devam eder; log'a yalnızca hata türü yazılır
 *   (sunucu adresi, kullanıcı adı ve şifre hiçbir log'a yazılmaz).
 */
class EpgRepository(
    private val context: Context,
    private val xtream: XtreamRepository,
    private val preferences: UserPreferences
) {
    private val running = AtomicBoolean(false)

    /**
     * Gerekirse (anahtar açık, son indirmeden 12 saat geçmiş ya da hiç veri yok) rehberi indirir.
     * @return yeni rehber yazıldıysa true
     */
    suspend fun syncIfNeeded(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!preferences.epgDownloadEnabledFlow.first()) return@withContext false
        if (!running.compareAndSet(false, true)) return@withContext false
        var file: File? = null
        try {
            val db = xtream.database ?: return@withContext false
            val now = System.currentTimeMillis()
            val epgDao = db.epgDao()
            if (!force) {
                val last = preferences.getEpgLastSyncMs()
                if (last > 0L && now - last < SYNC_INTERVAL_MS && epgDao.count() > 0) return@withContext false
            }

            val channelIds = db.liveDao().getDistinctEpgChannelIds()
                .map { XmltvParser.normalizeChannelId(it) }
                .filter { it.isNotEmpty() }
                .toHashSet()
            if (channelIds.isEmpty()) return@withContext false

            file = xtream.downloadXmltvToFile(File(context.cacheDir, "epg"))
            val windowStart = now - PAST_WINDOW_MS
            val windowEnd = now + FUTURE_WINDOW_MS

            var saved = 0
            db.withTransaction {
                epgDao.deleteAll()
                openXmltvStream(file!!).use { input ->
                    val parser = Xml.newPullParser()
                    parser.setInput(input, null)
                    val batch = ArrayList<EpgProgramEntity>(BATCH_SIZE)
                    saved = XmltvParser.parse(parser, windowStart, windowEnd, channelIds) { program ->
                        batch.add(program)
                        if (batch.size >= BATCH_SIZE) {
                            epgDao.insertAll(ArrayList(batch))
                            batch.clear()
                        }
                    }
                    if (batch.isNotEmpty()) {
                        epgDao.insertAll(ArrayList(batch))
                        batch.clear()
                    }
                }
                // Boş sonuç = bozuk/boş dosya: istisna işlemi geri alır, eski rehber korunur
                if (saved == 0) throw java.io.IOException("Boş program rehberi")
            }
            preferences.setEpgLastSyncMs(System.currentTimeMillis())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logWarn("Program rehberi indirilemedi (${e.javaClass.simpleName})")
            false
        } finally {
            try { file?.delete() } catch (_: Exception) { /* sessiz: geçici dosya silinemedi */ }
            running.set(false)
        }
    }

    /** Dosya gzip ile sıkıştırılmışsa (ilk iki bayt 1F 8B) açarak, değilse olduğu gibi okur. */
    private fun openXmltvStream(file: File): InputStream {
        val buffered = BufferedInputStream(FileInputStream(file), 64 * 1024)
        buffered.mark(2)
        val b0 = buffered.read()
        val b1 = buffered.read()
        buffered.reset()
        return if (b0 == 0x1f && b1 == 0x8b) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024), 64 * 1024) else buffered
    }

    private fun logWarn(message: String) {
        try {
            Log.w("EpgRepository", message)
        } catch (_: Throwable) {
            // sessiz: JVM birim test ortamında Android Log mock'lu olmayabilir
        }
    }

    companion object {
        /** Son indirmeden bu kadar süre geçmeden yeniden indirilmez. */
        const val SYNC_INTERVAL_MS = 12L * 3600_000L
        /** Şimdiden 6 saat geri. */
        const val PAST_WINDOW_MS = 6L * 3600_000L
        /** Şimdiden 48 saat ileri. */
        const val FUTURE_WINDOW_MS = 48L * 3600_000L
        const val BATCH_SIZE = 2000
    }
}
