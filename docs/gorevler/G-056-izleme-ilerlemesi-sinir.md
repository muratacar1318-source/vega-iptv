# G-056 — İzleme ilerlemesi kaydı sınırsız büyümesin

- **Öncelik:** 🟡
- **Agent dağılımı:** Tek agent.
- **Branch:** `perf/izleme-ilerlemesi-sinir` (main'den aç)
- **Commit mesajı:** `perf(veri): izleme ilerlemesi en fazla 500 kayıt, gereksiz yeniden ayrıştırma kaldırıldı`
- **Değişecek dosyalar (SADECE bunlar):**
  1. `app/src/main/java/com/tivimax/player/data/preferences/UserPreferences.kt` — sadece izleme ilerlemesi fonksiyonları
  2. `app/src/test/java/com/tivimax/player/data/preferences/WatchProgressTrimTest.kt` (yeni)
  3. Bu görev dosyasının "Agent raporu" bölümü ve `YAPILACAKLAR.md` — sadece G-056 satırı

## Sorun
- `saveWatchProgress` her çağrıda listenin tamamını JSON olarak okuyup yazıyor ve kayıt **hiç silinmiyor**; yıllar
  içinde binlerce film/bölüm birikir. Oynatıcı ilerlemeyi sık kaydettiği için (PlayerScreen'de 4 yer) dosya büyüdükçe
  her kayıt yavaşlar.
- `watchProgressMapFlow` DataStore'daki **herhangi bir** ayar değiştiğinde (favori, tema, ses…) bütün JSON'u yeniden
  ayrıştırıyor.

## Neden Room'a taşımıyoruz
İçerik veritabanları liste başına ayrı, `fallbackToDestructiveMigration` kullanıyor ve G-050'den beri yedeğe girmiyor.
İzleme geçmişi oraya taşınırsa yedekten dönüşte ve veritabanı sürümü değişince kaybolur. DataStore'da kalacak.

## Çözüm
1. Saf yardımcı: `fun trimProgress(map: Map<String, WatchProgress>, max: Int = 500): Map<String, WatchProgress>` —
   `max`'ı aşarsa `timestamp`'ı en eski olanları atar. `saveWatchProgress` yazmadan önce bunu uygular.
2. `watchProgressMapFlow`: ayrıştırmadan önce ham JSON metnine `distinctUntilChanged()` uygula
   (`map { prefs[key] }.distinctUntilChanged().map { parse(it) }`), böylece başka ayarlar değişince yeniden
   ayrıştırma olmaz. `flatMapLatest` (aktif liste) yapısı korunur.
3. JSON anahtarları ve `WatchProgress` sınıfı değişmez (G-048 uyumluluğu).

## Test (`WatchProgressTrimTest.kt`)
- 499 kayıt → aynen kalır; 501 kayıt → 500 kalır ve en eski `timestamp`'lı biri gider.
- Yeni eklenen kayıt hiçbir zaman atılmaz.

## 🚫 DOKUNMA
- `WatchProgress` sınıfı ve anotasyonları, DataStore anahtar adları, `PlayerScreen` ve kaydetme sıklığı.
- `AutoUpdateManager`, imza ayarları, `.github/workflows/`. Sürüm numarası. Release yapma.

## Kontrol
- `./gradlew :app:testDebugUnitTest` (düşük öncelikle, pencere açmadan) → geçmeli.

## Agent raporu
- `UserPreferences.kt` (yalnızca izleme ilerlemesi kısımları + `distinctUntilChanged` importu): yeni üst düzey `trimProgress(map, max = 500, keepKey = null)` ve `MAX_WATCH_PROGRESS_ENTRIES = 500`. Sayı `max`'ı aşarsa `timestamp`'ı en eski olanlar atılır; `keepKey` (az önce eklenen kayıt) zaman damgası eşit/eski olsa bile asla atılmaz (görev tarifindeki `trimProgress(map, max)` imzasına isteğe bağlı üçüncü parametre eklendi).
- `saveWatchProgress`: yazmadan önce `trimProgress(map, 500, keepKey = <kaydedilen streamId>)` uygulanıyor.
- `watchProgressMapFlow`: `flatMapLatest` (aktif liste) yapısı korundu; içeride `map { prefs[key] ?: "{}" }.distinctUntilChanged().map { parse }` — başka bir ayar (favori, tema, ses…) değişince ham JSON aynı kaldığı için yeniden ayrıştırma yapılmıyor.
- `WatchProgress` sınıfı, `@SerializedName` anotasyonları, DataStore anahtar adları, `PlayerScreen` ve kaydetme sıklığı değişmedi (G-048 uyumu korundu).
- Yeni `WatchProgressTrimTest.kt` (6 test): 499 ve 500 kayıt aynen kalır; 501 kayıtta 500 kalır ve en eski zaman damgalı gider; 1200 kayıtta en yeni 500 kalır; yeni eklenen (zaman damgası en eski olsa da) atılmaz; özel sınır.
- Test: `:app:testDebugUnitTest` → BUILD SUCCESSFUL.
- Not: Akışın "başka ayar değişince ayrıştırma yapılmaz" davranışı birim testle ölçülmedi (DataStore gerektirir); kod incelemesiyle doğrulandı. Cihazda elle denenmedi.
