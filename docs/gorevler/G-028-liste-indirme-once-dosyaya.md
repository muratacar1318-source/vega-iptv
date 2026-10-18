# G-028 — Liste indirme: önce dosyaya indir, sonra işle

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent (bölme). Gemini 3.6 Flash için adım adım yazıldı — **adımların dışına çıkma**.
- **Branch:** `perf/liste-indirme-dosyaya` (main'den aç)
- **Commit mesajı:** `perf(sync): listeler önce dosyaya indiriliyor, sonra işleniyor`
- **Değişecek dosyalar (sadece bunlar):**
  1. `app/src/main/java/com/tivimax/player/data/repository/XtreamRepository.kt`
  2. `app/src/main/java/com/tivimax/player/ui/MainViewModel.kt` (yalnızca Adım 5'teki tek satır)
  3. Yeni test: `app/src/test/java/com/tivimax/player/data/repository/SyncDownloadTest.kt`
  4. Bu görev dosyası (Agent raporu) ve `YAPILACAKLAR.md` (durum satırı)

> Not: Eski görevdeki "sonraki güncellemeler arka planda" kısmı bu görevden **çıkarıldı**, ayrı görev olacak (G-029). Bu görevde ekran/akış değişikliği **yok**.

## Neden
Şu an film/dizi/kanal listesi sunucudan **okunurken** aynı anda ayrıştırılıp veritabanına yazılıyor. Veritabanı yazımı
ağdan yavaş olduğu için sunucu bağlantısı dakikalarca açık kalıyor; sağlayıcı bağlantıyı kesiyor, 502/504/429 artıyor.
Çözüm: yanıtı **önce tam hızla geçici bir dosyaya** yaz, bağlantıyı kapat, **sonra dosyadan** aynı kodla ayrıştır.
**Ayrıştırma ve veritabanı mantığı hiç değişmiyor** — sadece okunan kaynak ağ yerine dosya oluyor.

---

## Adım 1 — Yardımcı fonksiyonları ekle (XtreamRepository.kt dosyasının EN ALTINA, sınıfın DIŞINA)

Dosyanın en altında zaten sınıf dışında `fun toUserMessage(...)` var. Onun altına **aynen** şunu ekle:

```kotlin
/**
 * G-028: Yanıt gövdesini geçici dosyaya yazar ve bağlantıyı kapatır.
 * Eksik indirme, okuma hatası veya iptal durumunda dosyayı siler ve hatayı yukarı fırlatır.
 */
internal suspend fun downloadBodyToFile(
    response: okhttp3.Response,
    dir: java.io.File,
    prefix: String,
    onBytes: ((Long) -> Unit)? = null
): java.io.File {
    dir.mkdirs()
    val file = java.io.File(dir, "$prefix-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.json")
    try {
        response.use { resp ->
            val body = resp.body ?: throw java.io.IOException("Boş sunucu yanıtı")
            val expected = body.contentLength()
            var total = 0L
            var lastReported = 0L
            body.byteStream().use { input ->
                java.io.FileOutputStream(file).buffered(64 * 1024).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        total += read
                        if (total - lastReported >= 512 * 1024) {
                            lastReported = total
                            onBytes?.invoke(total)
                        }
                    }
                }
            }
            if (expected > 0 && total != expected) {
                throw java.io.IOException("Eksik indirme: $total / $expected bayt")
            }
            onBytes?.invoke(total)
        }
        return file
    } catch (e: Throwable) {
        file.delete()
        throw e
    }
}

/** G-028: "12,4 MB" biçiminde Türkçe boyut metni. */
internal fun formatMegabytes(bytes: Long): String =
    String.format(java.util.Locale("tr", "TR"), "%.1f MB", bytes / (1024.0 * 1024.0))

/** G-028: Geçici indirme klasöründeki tüm dosyaları siler. Hata fırlatmaz. */
internal fun clearSyncTempDir(dir: java.io.File) {
    try {
        dir.listFiles()?.forEach { it.delete() }
    } catch (_: Throwable) {}
}
```

Gerekli import (dosyanın başındaki import listesine ekle, zaten yoksa):
```kotlin
import kotlinx.coroutines.ensureActive
```

## Adım 2 — Geçici klasör özelliği (XtreamRepository sınıfının İÇİNE)

`clearCache()` fonksiyonunun hemen altına ekle:

```kotlin
    /** G-028: Senkron indirmelerinin geçici dosya klasörü (cacheDir/sync). Context yoksa null. */
    private val syncTempDir: java.io.File?
        get() = context?.let { java.io.File(it.cacheDir, "sync") }

    /** G-028: Uygulama açılışında önceki oturumdan kalan geçici dosyaları temizler. */
    fun cleanupSyncTempFiles() {
        syncTempDir?.let { clearSyncTempDir(it) }
    }
```

## Adım 3 — 5 yerde "ağdan oku" yerine "dosyaya indir, dosyadan oku"

**Sadece aşağıdaki 5 yer** değişecek. `fetchAndSaveLiveStreams`, `fetchAndSave...Categories`, `authenticate`, EPG ve
diğer fonksiyonlara **dokunma**.

Her yerde kalıp aynı:

```kotlin
// ÖNCE (örnek):
body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
    ... (içerik aynen kalır)
}

// SONRA:
val tempDir = syncTempDir ?: throw java.io.IOException("Geçici klasör yok")
val tempFile = downloadBodyToFile(response, tempDir, "<önek>") { bytes ->
    /* ilerleme — aşağıdaki tabloya bak */
}
try {
    tempFile.bufferedReader(Charsets.UTF_8).use { reader ->
        ... (içerik AYNEN kalır — tek karakter değiştirme)
    }
} finally {
    tempFile.delete()
}
```

| # | Fonksiyon | Yer | `response` değişkeni | önek | İndirme sırasında ilerleme (onBytes içinde) |
|---|---|---|---|---|---|
| 3a | `syncLiveStreams` | tek parça istek | `response` | `live` | **Hiçbir şey çağırma** (`onProgress` sadece sayı alıyor; boş bırak: `{ }`) |
| 3b | `syncVodStreams` | toplu (bulk) blok | `response` | `vod` | `onProgress?.invoke(0, "Filmler indiriliyor… ${formatMegabytes(bytes)}")` |
| 3c | `syncVodStreams` | kategori yedek modu | `catResponse` | `vod-cat-${cat.categoryId}` | **Hiçbir şey çağırma** (`{ }`) — mevcut kategori ilerlemesi yeterli |
| 3d | `syncSeriesStreams` | toplu (bulk) blok | `response` | `series` | `onProgress?.invoke(0, "Diziler indiriliyor… ${formatMegabytes(bytes)}")` |
| 3e | `syncSeriesStreams` | kategori yedek modu | `catResponse` | `series-cat-${cat.categoryId}` | **Hiçbir şey çağırma** (`{ }`) |

Dikkat edilecekler (Flash için özellikle):
- `val body = response.body` / `if (body != null)` / `?: return ... Boş sunucu yanıtı` satırları: **mevcut kontrolleri koru**.
  `downloadBodyToFile` çağrısını bu `body != null` kontrolünün **içine**, eski `body.byteStream()...` satırının **yerine** koy.
- 3a'da `use {}` bloğunun içindeki `return@withContext Result.failure(...)` satırı **aynen kalsın**; `finally` dosyayı yine siler.
- Bulk ve kategori bloklarındaki mevcut `finally { response?.close() }` / `finally { catResponse?.close() }` satırlarını
  **silme**. (Yanıt iki kez kapatılırsa sorun olmaz.)
- `parseAndInsertVodChunk` / `parseAndInsertSeriesChunk` fonksiyonlarına, `seenIds`, `safeStaleIds`, silme koşullarına,
  `executeWithRetry`'a, chunk boyutlarına, mutex/semaphore'a **dokunma**.
- `downloadBodyToFile` hata fırlatırsa (eksik indirme, IOException) bu, **mevcut** `catch` bloklarına düşer ve bugünkü gibi
  "başarısız deneme" sayılır. Bunun için ek kod yazma.

## Adım 4 — Debug ölçüm logu (sadece 3b ve 3d'de)
Toplu indirmede `downloadBodyToFile` döndükten hemen sonra:
```kotlin
if (BuildConfig.DEBUG) {
    android.util.Log.d("SyncTiming", "Dosyaya indirildi: <vod veya series>, ${tempFile.length()} bayt")
}
```

## Adım 5 — Açılışta eski geçici dosyaları temizle (MainViewModel.kt)
`MainViewModel` içinde `repository` nesnesinin oluşturulduğu yerden sonra çalışan **ilk** `init { ... }` bloğunun en başına
tek satır ekle:
```kotlin
repository.cleanupSyncTempFiles()
```
Başka hiçbir şey değiştirme. (Bu satır senkron başlamadan önce çalışır; çalışan bir indirmeyi silmez.)

## Adım 6 — Birim testi (yeni dosya)
`app/src/test/java/com/tivimax/player/data/repository/SyncDownloadTest.kt` oluştur. JUnit4 + `org.junit.rules.TemporaryFolder`
ve `kotlinx.coroutines.runBlocking` kullan. Yanıtı şöyle kur:

```kotlin
private fun fakeResponse(body: okhttp3.ResponseBody): okhttp3.Response =
    okhttp3.Response.Builder()
        .request(okhttp3.Request.Builder().url("http://example.com/").build())
        .protocol(okhttp3.Protocol.HTTP_1_1)
        .code(200).message("OK")
        .body(body)
        .build()
```
Testler:
1. **Tam indirme:** `"[1,2,3]".toResponseBody("application/json".toMediaType())` → dönen dosyanın içeriği `"[1,2,3]"`.
2. **Eksik indirme:** `contentLength()` değerini 100 döndüren ama 7 bayt veren sahte bir `ResponseBody` (object: ResponseBody()
   override `contentType()`, `contentLength() = 100`, `source()` → `okio.Buffer().writeUtf8("[1,2,3]")`) → `IOException`
   fırlatılmalı **ve** klasörde hiç dosya kalmamalı.
3. **formatMegabytes:** `formatMegabytes(13_002_342)` → `"12,4 MB"`.
4. **clearSyncTempDir:** klasöre 2 dosya yaz, çağır, klasör boş olmalı.

(okhttp ve okio zaten projede var; `toResponseBody`/`toMediaType` için `okhttp3.ResponseBody.Companion.toResponseBody` ve
`okhttp3.MediaType.Companion.toMediaType` import et.)

## Adım 7 — Kontrol ve gönderim
1. `./gradlew compileDebugKotlin` → hata varsa düzelt.
2. `./gradlew testDebugUnitTest` → tüm testler geçmeli.
3. `git diff --stat` çıktısında **sadece** yukarıdaki dosyalar olmalı. Başka dosya değiştiyse geri al.
4. Commit → `perf/liste-indirme-dosyaya` dalına push. **Main'e birleştirme. APK üretme. Sürüm numarasını değiştirme.**
5. `YAPILACAKLAR.md`'de G-028 durumunu `🔍 İncelemede (branch: perf/liste-indirme-dosyaya)` yap.

## DOKUNMA
Silme kuralları (`safeStaleIds`, G-024), ayrıştırma fonksiyonları, `executeWithRetry`, oynatıcı, oynatma listesi
altyapısı, ekranlar/tasarım, güncelleme sistemi (AutoUpdateManager), imza, CI, sürüm numarası.

## Test (cihaz varsa — yoksa "test edilemedi" yaz)
- İlk kurulum: Filmler adımında önce "Filmler indiriliyor… X MB", sonra "N film kaydedildi" görünür.
- Uçak modunda "İçerikleri güncelle": hata mesajı çıkar, mevcut liste bozulmaz.
- Logcat `SyncTiming`: indirme süresi ve dosya boyutu görünür.

---

## Agent raporu

- **Durum:** Tamamlandı, Claude'un incelemesine hazır.
- **Değişiklikler:**
  - `XtreamRepository.kt`:
    - Adım 1: Sınıf dışına `downloadBodyToFile`, `formatMegabytes`, `clearSyncTempDir` yardımcı fonksiyonları eklendi. `downloadBodyToFile`, yanıt gövdesini `cacheDir/sync` geçici klasörüne `64 KB` tampon kullanarak indirir, indirme sırasında ilerlemeyi her `512 KB` bir bildirir, eksik indirme/iptal durumunda geçici dosyayı temizler.
    - Adım 2: `XtreamRepository` sınıfına `syncTempDir` ve uygulama açılışında kalan geçici dosyaları silen `cleanupSyncTempFiles()` eklendi.
    - Adım 3: 5 noktada (`syncLiveStreams`, `syncVodStreams` bulk, `syncVodStreams` category fallback, `syncSeriesStreams` bulk, `syncSeriesStreams` category fallback) ağ akışı doğrudan okunmak yerine yanıt önce `downloadBodyToFile` ile geçici dosyaya yazıldı, ardından dosyadan okuma yapılarak `finally` bloğunda geçici dosya silindi.
    - Adım 4: Toplu VOD ve Dizi indirmelerinde indirme sonrasında `SyncTiming` etiketiyle debug log eklendi.
  - `MainViewModel.kt`:
    - Adım 5: `init` bloğunun en başına `repository.cleanupSyncTempFiles()` çağrısı eklendi.
  - `SyncDownloadTest.kt` (Yeni Test):
    - Adım 6: Tam indirme, eksik indirmede `IOException` ve dosya temizliği, `formatMegabytes` Türkçe metin biçimlendirmesi, `clearSyncTempDir` klasör temizliği için 4 adet birim testi yazıldı.
- **Doğrulama:**
  - `./gradlew compileDebugKotlin` başarıyla tamamlandı.
  - `./gradlew testDebugUnitTest` başarıyla tamamlandı (60 test geçti, 0 hata).
  - Fiziksel cihaz / emülatör bağlı olmadığı için canlı cihazda UI indirme akışı test edilemedi (birim testleri ve derleme ile doğrulandı).
