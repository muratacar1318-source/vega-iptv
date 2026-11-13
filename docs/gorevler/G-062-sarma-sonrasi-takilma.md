# G-062 — Film/dizide ileri sarınca 10–15 sn "yenileniyor" beklemesi + oynatma günlüğü

- **Öncelik:** 🔴 (kullanıcı deneme APK'sında gördü, v1.5.2'den önce)
- **Agent dağılımı:** Tek agent, 2 bölüm (aynı branch, bölüm başına commit).
- **Branch:** `fix/sarma-sonrasi-takilma` (main'den aç)
- **Commit mesajları:** A `fix(oynatıcı): sarmadan sonra takılınca 5 sn'de sessizce yeniden bağlanıyor`,
  B `feat(destek): hata raporuna son oynatma olayları ekleniyor`
- **Değişecek dosyalar (yollar `app/src/main/java/com/tivimax/player/` altına göre):**
  - `player/TiviPlayerManager.kt` — yalnızca G-044/G-058 bekçi bölümü, `StallPolicy`, olay günlüğü çağrıları
  - `util/PlaybackEventLog.kt` (yeni), `util/CrashLogger.kt` (yalnızca paylaşım metnine günlüğü ekleme)
  - `ui/settings/SettingsScreen.kt` — yalnızca G-051 "Hakkında" bloğu
  - testler: `app/src/test/java/com/tivimax/player/player/StallPolicyTest.kt` (güncelle), `.../util/PlaybackEventLogTest.kt` (yeni)
  - rapor ve `YAPILACAKLAR.md`'de G-062 satırı

## Kullanıcı bildirimi (13 Kasım, deneme APK'sı e9638a1)
"İçerik izlerken ilk açılıştan sonra ileri sarınca yeniden deneniyor diyor, 10-15 saniye bekliyor, sonra bağlanıyor.
Sonra bir sorun çıkmıyor."

## Claude'un değerlendirmesi
Belirti G-058 bekçisine uyuyor: sarma → `STATE_BUFFERING` → 15 sn → "Bağlantı yenileniyor... (1/3)" → `stop` +
`evictAll` + `seekTo(pos)` → oynatma başlıyor. Muhtemel kök neden: Xtream sağlayıcıları çoğunlukla **tek bağlantıya**
izin veriyor; ilk açılışta 180 sn'lik tampon için açık olan bağlantı kapanmadan sarma isteği gelince sunucu yeni isteği
bekletiyor. `evictAll` eski bağlantıyı kapatınca açılıyor. "Sonra sorun çıkmıyor": ilk bağlantı bir kez kapatılınca
sonraki sarmalarda çakışma olmuyor. (G-058'den önce aynı durum 60 sn okuma zaman aşımına kadar sürebiliyordu.)
Bu bir tahmin; Bölüm B ile gerçek cihazdan kanıt toplanacak.

## Bölüm A — Sarma sonrası hızlı ve sessiz kurtarma
1. `StallPolicy.timeoutMs(isLive, firstFrameShown, msSinceSeek: Long?)`:
   - canlı: değişmez (20 sn / 10 sn).
   - film/dizi, ilk kare yok: `null` (değişmez).
   - film/dizi, **son kullanıcı sarmasından bu yana ≤ 3 sn içinde** başlayan takılma: **5 sn**.
   - film/dizi, diğer: 15 sn (değişmez).
   `msSinceSeek` = `System.currentTimeMillis() - lastSeekTimestamp` (alan zaten var: `seekTo`, `forward10Seconds`,
   `rewind10Seconds` dolduruyor; diğer sarma yolları — sarma çubuğu sürükleme, kumanda ◀▶ — da aynı alanı dolduruyor mu
   kontrol et, doldurmuyorsa doldur). Sarma hiç olmadıysa `null`.
2. Sarma kaynaklı kurtarmanın **ilk denemesi sessiz** olsun: `_reconnectStatus` gösterme (kullanıcı normal yükleniyor
   yuvarlağını görür). 2. ve 3. denemede mevcut "Bağlantı yenileniyor... (n/3)" metni.
3. **Önleyici adım:** film/dizide kullanıcı sarması tamponun dışına düşüyorsa (`target` > `bufferedPosition` ya da
   `target` < `currentPosition - 2000`), `exoPlayer.seekTo` çağrısından **hemen önce** `streamingHttpClient.evictAll()`
   çağır (boşta kalan eski bağlantılar kapanır). Tampon içindeki sarmada çağırma (gereksiz yeniden bağlantı olmasın).
   Bunu tek bir özel yardımcıda topla (`prepareVodSeek(target)`), üç sarma fonksiyonu onu çağırsın.
4. Canlı yayın davranışı, `onPlayerError`, `createLoadControl`, zaman aşımları değişmez.

## Bölüm B — Oynatma günlüğü (gerçek cihazdan kanıt)
1. `PlaybackEventLog`: bellekte son **80** olayı tutan halka tampon (uygulama kapanınca silinir; diske yazma yok).
   Olay: `zaman (HH:mm:ss.SSS)`, `tür`, kısa ayrıntı. Kaydedilecekler: oynatma başladı (canlı/film), durum değişimi
   (BUFFERING/READY/IDLE/ENDED), ilk kare, kullanıcı sarması (hedef sn, tampon içi/dışı), bekçi tetiklendi (süre, deneme),
   oynatıcı hatası (`errorCodeName` + HTTP kodu, **adres yok**), yeniden deneme yolları (hangi dal), `evictAll` çağrısı.
   Ayrıntı metinleri `CrashLogger.sanitize(...)`'dan geçer; adres/kullanıcı adı/şifre asla yazılmaz.
2. `CrashLogger.buildShareText`: metnin başına "Son oynatma olayları" bölümü (günlük boş değilse).
3. Ayarlar → Hakkında: "Hata raporu gönder" düğmesi artık **çökme kaydı olmasa da** görünür (metin: "Sorun bildir");
   çökme sayısı satırı yalnız çökme varsa. "Kayıtları sil" yalnız çökme varsa. TV'de odaklanabilir (mevcut düzen).
4. Kullanıcıya not (rapor bölümüne yaz): sorunu yaşadıktan sonra uygulamayı kapatmadan Ayarlar → Hakkında → Sorun bildir.

## Test
- `StallPolicyTest`: yeni durumlar (sarmadan 1 sn sonra → 5000; 10 sn sonra → 15000; sarma yok → 15000; canlıda sarma
  süresi etkisiz).
- `PlaybackEventLogTest`: 80 sınırı (100 olay → son 80), sıra, `sanitize` uygulanıyor (`http://…` ve `password=` metinde yok).

## 🚫 DOKUNMA
- `onPlayerError` mantığı, G-027 yerinde yeniden deneme, `createLoadControl`, `createDataSourceFactory`, zaman aşımları,
  çift ekran kodu, `PlayerScreen.kt` (G-055 PR'ı açık; çakışma olmasın).
- `AutoUpdateManager`, imza ayarları, `.github/workflows/`. Sürüm numarası. Release yapma.

## Kontrol
- `./gradlew :app:testDebugUnitTest` (BelowNormal, pencere açmadan, tek gradle) → geçmeli.
- Elle deneme kullanıcıda: film aç, 30 sn izle, 20 dk ileri sar → en geç ~5 sn'de, mesajsız devam etmeli.

## Agent raporu
_(Agent doldurur.)_
