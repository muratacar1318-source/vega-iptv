# G-040 — Canlı yayın açılışta doğrudan ~8 sn gecikmeyle başlasın (hızlandırma yok)

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için — **aşağıdaki kodları AYNEN kopyala, değiştirme**.
  (Claude `LiveBurstTrimmer` ve testini kendi ortamında derleyip 9 senaryoyla çalıştırdı; hepsi geçti. `LiveBurstTrimDataSource` Android kütüphanesi gerektirdiği için orada derlenemedi — derleme hatası çıkarsa sadece import/imza düzelt.)
- **Branch:** `fix/canli-8sn-kirpma` (main'den aç)
- **Commit mesajı:** `fix(live): açılışta sunucunun gönderdiği eski yayın atılıyor, canlı ~8 sn gecikmeyle başlıyor; hızlandırma kaldırıldı`
- **Değişecek dosyalar (SADECE bunlar):**
  1. YENİ `app/src/main/java/com/tivimax/player/player/LiveBurstTrimmer.kt`
  2. YENİ `app/src/main/java/com/tivimax/player/player/LiveBurstTrimDataSource.kt`
  3. YENİ `app/src/test/java/com/tivimax/player/player/LiveBurstTrimmerTest.kt`
  4. `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt` (3 küçük yer)
  5. Bu görev dosyası (Agent raporu) ve `YAPILACAKLAR.md` (durum satırı)

## Neden
Kullanıcı G-039'daki hızlandırarak yetişmeyi beğenmedi: "direkt 8 saniyeye düşür, stabil olsun".
IPTV sunucusu bağlantı açılınca son ~30 sn'yi **bir anda** gönderiyor; oynatıcı en baştan oynatıyor → ~30 sn geride.
**Yeni yöntem:** Bağlantı açılınca bu ilk yığını kısa süre (en fazla 3 sn, genelde <1 sn) önceden okuyup, yayının
kendi saat bilgisinden (TS PCR) en güncel ana bakıyor, **en yeni 8 sn dışındaki eski kısmı atıyoruz**. Oynatma
doğrudan ~8 sn gecikmeyle başlar; hızlandırma yok.
**Güvenlik:** TS değilse, saat bilgisi yoksa, saat sıçrarsa veya yığın 8 sn'den kısaysa **hiçbir şey atılmaz**
(eskisi gibi oynar). Beklenmeyen bir hata olursa kırpma kendini kapatır, oynatma normal devam eder.
Sadece **canlı + .ts** yayının **ana adresinde** çalışır; film/dizi, HLS (.m3u8) ve ikinci ekran etkilenmez.

---

## Adım 1 — YENİ dosya `LiveBurstTrimmer.kt` (aynen)
```kotlin
package com.tivimax.player.player

import java.io.ByteArrayOutputStream
import java.util.ArrayDeque

/**
 * G-040: Canlı MPEG-TS yayınında sunucunun bağlantı başında bir anda gönderdiği eski yayını (burst) atar.
 *
 * Bağlantı açılınca veriyi kısa bir süre (en fazla [maxPreReadMs]) önceden okur, TS paketlerindeki saat
 * bilgisinden (PCR) en güncel ana bakar ve en yeni [keepMs] dışındaki eski kısmı atar. Paket sınırında keser.
 * TS değilse, saat bilgisi yoksa, saat sıçrarsa veya beklenmeyen bir durum olursa HİÇBİR ŞEY ATMAZ:
 * okunan her bayt aynı sırayla geri verilir. Saf Kotlin — Android'e bağlı değildir (birim testi yapılabilir).
 */
class LiveBurstTrimmer(
    private val keepMs: Long = 8_000L,
    private val maxPreReadMs: Long = 3_000L,
    private val maxPreReadBytes: Long = 48L * 1024 * 1024,
    private val chunkBytes: Int = 64 * 1024,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private class Chunk(val bytes: ByteArray, val maxPcrMs: Long)

    /** Son çağrıda atılan bayt sayısı (teşhis için). */
    var lastDroppedBytes: Long = 0L
        private set

    /**
     * [read] upstream'den okur (DataSource.read gibi: okunan bayt sayısı ya da -1 = akış bitti).
     * Dönüş: çıkışa verilecek baytlar (sırayla). Sonrası doğrudan upstream'den okunur.
     * IOException'lar olduğu gibi yukarı fırlatılır.
     */
    fun preRead(read: (ByteArray, Int, Int) -> Int): ArrayDeque<ByteArray> {
        lastDroppedBytes = 0L
        val start = clockMs()
        val buf = ByteArray(64 * 1024)
        val raw = ByteArrayOutputStream()          // ilk senkrondan önceki baytlar
        val chunks = ArrayDeque<Chunk>()
        val packetBuf = ByteArrayOutputStream()
        var packetBufMaxPcr = -1L
        var leftover = ByteArray(0)
        var synced = false
        var notTs = false
        var discontinuity = false
        var firstPcr = -1L
        var lastPcr = -1L
        var maxLead = Long.MIN_VALUE
        var lastLeadGrowAt = start
        var total = 0L
        var droppedDuringRead = 0L

        while (true) {
            val n = read(buf, 0, buf.size)
            if (n < 0) break
            if (n == 0) continue
            total += n
            val data = if (leftover.isEmpty()) buf.copyOf(n) else leftover + buf.copyOf(n)
            var i = 0
            if (!synced) {
                val s = findSync(data)
                if (s < 0) {
                    if (data.size >= 4096) {
                        notTs = true
                        leftover = data
                        break
                    }
                    leftover = data
                    continue
                }
                if (s > 0) raw.write(data, 0, s)
                i = s
                synced = true
            }
            while (i + 188 <= data.size) {
                if (data[i] != SYNC) {
                    discontinuity = true
                    break
                }
                val pcr = readPcrMs(data, i)
                if (pcr >= 0) {
                    if (lastPcr >= 0 && (pcr < lastPcr - 1_000L || pcr > lastPcr + 60_000L)) discontinuity = true
                    if (firstPcr < 0) firstPcr = pcr
                    if (pcr > lastPcr) lastPcr = pcr
                    if (pcr > packetBufMaxPcr) packetBufMaxPcr = pcr
                }
                packetBuf.write(data, i, 188)
                i += 188
                if (packetBuf.size() >= chunkBytes) {
                    chunks.addLast(Chunk(packetBuf.toByteArray(), packetBufMaxPcr))
                    packetBuf.reset()
                    packetBufMaxPcr = -1L
                    // Bellek sınırı: en yeni keepMs + 1 sn'den eski parçaları okurken at
                    if (!discontinuity && lastPcr >= 0) {
                        val cutoff = lastPcr - keepMs - 1_000L
                        while (chunks.size > 1 && chunks.peekFirst().maxPcrMs < cutoff) {
                            droppedDuringRead += chunks.pollFirst().bytes.size
                        }
                    }
                }
            }
            leftover = if (i < data.size) data.copyOfRange(i, data.size) else ByteArray(0)
            if (discontinuity) break

            val now = clockMs()
            val wall = now - start
            if (firstPcr >= 0) {
                val lead = (lastPcr - firstPcr) - wall
                if (lead > maxLead + 150L) {
                    maxLead = lead
                    lastLeadGrowAt = now
                }
                // Burst bitti: yayın artık gerçek zamanlı akıyor (öne geçiş 400 ms'dir büyümüyor)
                if (wall >= 300L && now - lastLeadGrowAt >= 400L) break
            } else if (wall >= 1_500L || total >= 2L * 1024 * 1024) {
                break // saat bilgisi yok → kırpma yapılmaz
            }
            if (wall >= maxPreReadMs || total >= maxPreReadBytes) break
        }

        val out = ArrayDeque<ByteArray>()
        val canTrim = synced && !notTs && !discontinuity && lastPcr >= 0
        if (canTrim) {
            val cutoff = lastPcr - keepMs
            if (packetBuf.size() > 0) {
                chunks.addLast(Chunk(packetBuf.toByteArray(), packetBufMaxPcr))
                packetBuf.reset()
            }
            while (chunks.size > 1 && chunks.peekFirst().maxPcrMs < cutoff) {
                droppedDuringRead += chunks.pollFirst().bytes.size
            }
            lastDroppedBytes = droppedDuringRead + if (droppedDuringRead > 0) raw.size().toLong() else 0L
            if (droppedDuringRead == 0L && raw.size() > 0) out.addLast(raw.toByteArray())
            for (c in chunks) out.addLast(c.bytes)
        } else {
            // Kırpma yok. Okurken bellek için atılmış parça varsa (çok nadir: sonradan saat sıçraması)
            // kalan veriyi yine sırayla ver; akış paket sınırında devam eder.
            lastDroppedBytes = droppedDuringRead
            if (droppedDuringRead == 0L && raw.size() > 0) out.addLast(raw.toByteArray())
            for (c in chunks) out.addLast(c.bytes)
            if (packetBuf.size() > 0) out.addLast(packetBuf.toByteArray())
        }
        if (leftover.isNotEmpty()) out.addLast(leftover)
        return out
    }

    companion object {
        private const val SYNC: Byte = 0x47

        fun findSync(d: ByteArray): Int {
            var s = 0
            while (s + 376 < d.size) {
                if (d[s] == SYNC && d[s + 188] == SYNC && d[s + 376] == SYNC) return s
                s++
            }
            return -1
        }

        /** TS paketindeki PCR (milisaniye) ya da -1. */
        fun readPcrMs(d: ByteArray, o: Int): Long {
            val afc = (d[o + 3].toInt() shr 4) and 0x3
            if (afc != 2 && afc != 3) return -1L
            val afLen = d[o + 4].toInt() and 0xFF
            if (afLen < 7) return -1L
            val flags = d[o + 5].toInt() and 0xFF
            if (flags and 0x10 == 0) return -1L
            val b6 = d[o + 6].toLong() and 0xFF
            val b7 = d[o + 7].toLong() and 0xFF
            val b8 = d[o + 8].toLong() and 0xFF
            val b9 = d[o + 9].toLong() and 0xFF
            val b10 = d[o + 10].toLong() and 0xFF
            val base = (b6 shl 25) or (b7 shl 17) or (b8 shl 9) or (b9 shl 1) or (b10 shr 7)
            return base / 90L
        }
    }
}
```

## Adım 2 — YENİ dosya `LiveBurstTrimDataSource.kt` (aynen)
```kotlin
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
            pending = LiveBurstTrimmer().preRead { b, o, l -> upstream.read(b, o, l) }
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
    }
}
```

## Adım 3 — YENİ test `app/src/test/java/com/tivimax/player/player/LiveBurstTrimmerTest.kt` (aynen)
```kotlin
package com.tivimax.player.player

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class LiveBurstTrimmerTest {
    fun packet(pcrMs: Long?, fill: Int): ByteArray {
        val p = ByteArray(188) { (fill and 0xFF).toByte() }
        p[0] = 0x47
        p[1] = 0x01; p[2] = 0x00
        if (pcrMs != null) {
            p[3] = 0x30 // afc=3
            p[4] = 7
            p[5] = 0x10
            val base = pcrMs * 90
            p[6] = ((base shr 25) and 0xFF).toByte()
            p[7] = ((base shr 17) and 0xFF).toByte()
            p[8] = ((base shr 9) and 0xFF).toByte()
            p[9] = ((base shr 1) and 0xFF).toByte()
            p[10] = (((base and 1) shl 7) or 0x7E).toByte()
            p[11] = 0
        } else p[3] = 0x10
        return p
    }

    fun stream(seconds: Int, msPerPacket: Long = 40, startPcr: Long = 100_000): ByteArray {
        val o = ByteArrayOutputStream()
        var t = startPcr
        var k = 0
        while (t < startPcr + seconds * 1000L) {
            o.write(packet(if (k % 2 == 0) t else null, k)); t += msPerPacket; k++
        }
        return o.toByteArray()
    }

    fun source(data: ByteArray, step: Int = 5000): (ByteArray, Int, Int) -> Int {
        var pos = 0
        return { b, off, len -> if (pos >= data.size) -1 else { val n = minOf(len, step, data.size - pos); System.arraycopy(data, pos, b, off, n); pos += n; n } }
    }

    fun drain(t: LiveBurstTrimmer, data: ByteArray): ByteArray {
        val src = source(data)
        val q = t.preRead(src)
        val o = ByteArrayOutputStream()
        q.forEach { o.write(it) }
        val b = ByteArray(4096)
        while (true) { val n = src(b, 0, b.size); if (n < 0) break; o.write(b, 0, n) }
        return o.toByteArray()
    }

    private fun check(name: String, ok: Boolean) = assertTrue(name, ok)

    @Test
    fun `canli burst kirpma senaryolari`() {
        // 1) 30 sn burst: ilk paket PCR >= son - 8000 - parça payı
        val s30 = stream(30)
        val t = LiveBurstTrimmer(chunkBytes = 188 * 10, clockMs = { 0L })
        val out = drain(t, s30)
        check("30s aligned", out.size % 188 == 0 && out[0] == 0x47.toByte())
        var firstPcr = -1L; var i = 0
        while (i < out.size) { val p = LiveBurstTrimmer.readPcrMs(out, i); if (p >= 0) { firstPcr = p; break }; i += 188 }
        val lastPcr = 100_000L + 30_000 - 40
        check("30s trimmed ~8s", lastPcr - firstPcr in 8000..8800)
        check("tail preserved", out.copyOfRange(out.size - 188, out.size).contentEquals(s30.copyOfRange(s30.size - 188, s30.size)))
        // 2) 5 sn: hiçbir şey atılmaz
        val s5 = stream(5)
        check("5s untouched", drain(LiveBurstTrimmer(chunkBytes = 1880, clockMs = { 0L }), s5).contentEquals(s5))
        // 3) TS değil
        val txt = ByteArray(20000) { (it % 50 + 65).toByte() }
        check("non-ts untouched", drain(LiveBurstTrimmer(clockMs = { 0L }), txt).contentEquals(txt))
        // 4) Başta çöp + 30s
        val junk = ByteArray(100) { 1 }
        val jd = junk + s30
        val o4 = drain(LiveBurstTrimmer(chunkBytes = 1880, clockMs = { 0L }), jd)
        check("junk+30s starts with sync", o4[0] == 0x47.toByte() && o4.size % 188 == 0)
        // 5) PCR yok → dokunma
        val np = ByteArrayOutputStream(); repeat(2000) { np.write(packet(null, it)) }
        val npb = np.toByteArray()
        check("no pcr untouched", drain(LiveBurstTrimmer(clockMs = { 0L }), npb).contentEquals(npb))
        // 6) Saat sıçraması → dokunma (bellek kırpması olmadıysa)
        val d1 = stream(3); val d2 = stream(3, startPcr = 10_000)
        val dj = d1 + d2
        check("discontinuity untouched", drain(LiveBurstTrimmer(chunkBytes = 1880, clockMs = { 0L }), dj).contentEquals(dj))
        // 7) Gerçek zamanlı (burst yok) — saat ilerliyor: akış bölünmeden tamamı
        var clock = 0L
        val s3 = stream(3)
        val src7 = source(s3, 1880) 
        val tt = LiveBurstTrimmer(chunkBytes = 1880, clockMs = { clock })
        val q = tt.preRead { b, o, l -> clock += 400; src7(b, o, l) }
        val o7 = ByteArrayOutputStream(); q.forEach { o7.write(it) }; val bb = ByteArray(4096)
        while (true) { val n = src7(bb, 0, bb.size); if (n < 0) break; o7.write(bb, 0, n) }
        check("realtime untouched", o7.toByteArray().contentEquals(s3))
    }
}
```

## Adım 4 — TiviPlayerManager.kt

**4a. Veri kaynağına kırpmayı tak.** `fun createDataSourceFactory()` içindeki
```kotlin
        return DefaultDataSource.Factory(context, okHttpDataSourceFactory)
```
satırını şununla değiştir:
```kotlin
        val base = DefaultDataSource.Factory(context, okHttpDataSourceFactory)
        // G-040: Canlı .ts ana yayında bağlantı başındaki eski yayını at (gecikme ~8 sn)
        return LiveBurstTrimDataSource.Factory(base) { spec ->
            isLiveStream &&
                spec.position == 0L &&
                spec.uri.toString() == currentUrl &&
                !currentUrl.contains(".m3u8", ignoreCase = true)
        }
```

**4b. Hızlandırmayı kapat.** `startPositionUpdates()` içindeki
```kotlin
                        applyLiveCatchUp(player)
```
satırını şununla değiştir:
```kotlin
                        // G-040: Hızlandırma yok; sadece canlıdan gerilik bilgisini güncelle
                        _liveBehindSecs.value = player.totalBufferedDuration.coerceAtLeast(0L) / 1000f
```
(`applyLiveCatchUp` fonksiyonu kullanılmayacak; **silme**, dokunma.)

**4c. "Canlıya geç" = yeniden bağlan (kırpma yine 8 sn'ye getirir).** `fun jumpToLive()` fonksiyonunun gövdesini:
```kotlin
        if (!isLiveStream) return
        liveCatchUpStalls = 0
        liveCatchUpBoost = true
```
şununla değiştir:
```kotlin
        val player = exoPlayer ?: return
        if (!isLiveStream || currentUrl.isBlank()) return
        liveCatchUpBoost = false
        // G-040: Yeniden bağlan; açılıştaki kırpma yayını ~8 sn gecikmeye getirir
        player.stop()
        player.prepare()
        player.play()
```
KDoc yorumunu `G-040: "Canlıya geç" — bağlantıyı yeniler; açılış kırpması yayını ~8 sn gecikmeye getirir.` yap.

Başka hiçbir yere dokunma (TvPlayerOverlay'deki rozet ve düğme aynen çalışır).

---

## Kontrol ve gönderim
1. `./gradlew compileDebugKotlin` → hata varsa sadece import/derleme hatasını düzelt; **algoritmayı değiştirme**.
2. `./gradlew testDebugUnitTest` → `LiveBurstTrimmerTest` dahil tüm testler geçmeli.
3. `git diff --stat`: **sadece** yukarıdaki dosyalar.
4. Commit → `fix/canli-8sn-kirpma` dalına push. **Main'e birleştirme. APK üretme. Sürüm numarasını değiştirme.**
5. `YAPILACAKLAR.md`'de G-040 durumunu `🔍 İncelemede (branch: fix/canli-8sn-kirpma)` yap.

## DOKUNMA
Kanal geçişi (G-037), tampon ayarları (`createLoadControl`), hata/yeniden deneme mantığı, VOD, HLS, ikinci ekran,
TvPlayerOverlay, PlayerScreen, güncelleme sistemi (AutoUpdateManager), imza, CI, sürüm numarası.

## Test (TV varsa — yoksa "test edilemedi" yaz)
- Canlı kanal aç, OK: Tampon ~8 sn civarında (eskiden 30+), rozet "● CANLI"; görüntü normal hızda.
- Birkaç kanal (HEVC dahil) dene; hepsi açılıyor. Kanal açılışı en fazla ~1 sn uzayabilir (önceden okuma).
- "Canlıya geç": kısa siyah ekran, sonra yine ~8 sn gecikme.
- Film/dizi ve telefon canlı yayını eskisi gibi oynuyor.

---

## Agent raporu

_(Agent doldurur.)_
