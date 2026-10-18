# G-027 — Film/dizide yavaş açılış ve ileri sarmada 7-8 sn bekleme

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent
- **Branch:** `perf/vod-sarma-hizi`
- **Commit mesajı:** `perf(player): film/dizide sarma ve açılış hızlandırıldı (anahtar kareye sarma, 429 için yerinde kısa yeniden deneme)`

## Sorun (kullanıcı)
Canlı yayın iyi; film/dizide **bazen açılış gecikiyor**, **ileri sarınca 7-8 saniyeye kadar** bekliyor. İnternet 50 Mbit, stabil.

## Kök nedenler (Claude incelemesi — `player/TiviPlayerManager.kt`)
1. **Tam kareye sarma:** `STATE_READY`'de her seferinde `setSeekParameters(SeekParameters.DEFAULT)` (= EXACT) yapılıyor.
   Uzun sarmada oynatıcı önceki anahtar kareden hedef saniyeye kadar tüm kareleri çözüyor; IPTV filmlerinde anahtar kare
   aralığı 5-10 sn olabildiği için bu tek başına saniyeler ekler.
2. **Sağlayıcı tek bağlantıya izin veriyor.** Tamponun dışına sarınca ExoPlayer eski bağlantıyı kesip yenisini açar;
   sunucu eski bağlantıyı birkaç saniye "açık" sayıp **429/509** dönebilir. Şu an bu durumda `onPlayerError` →
   **1,5 sn bekle + oynatıcıyı baştan hazırla (`playInternal`)** yolu çalışıyor (~642-662) ve bu yol **sadece bir kez**
   (`firstUrlAuthRetried`) var; ikinci seferde yedek uzantılara geçiliyor → çok daha uzun bekleme.
   ExoPlayer'ın kendi yükleme hata politikası (`DefaultLoadErrorHandlingPolicy`) HTTP 4xx'i hiç yeniden denemiyor.
3. `bufferForPlaybackAfterRebufferMs = 5_000`: takılma sonrası oynatma 5 sn tampon dolmadan başlamıyor (50 Mbit için fazla).

## Yapılacaklar
1. **Sarma ayarı (sadece film/dizi):**
   - `STATE_READY`'deki `setSeekParameters(SeekParameters.DEFAULT)` satırını **canlı yayınla sınırla**.
   - Film/dizi: kullanıcı kaydırıcıyla ya da ±30 sn sardığında `SeekParameters.CLOSEST_SYNC`; ±10 sn için
     `SeekParameters.PREVIOUS_SYNC` (geri sarınca hedefin gerisine düşmesin diye). `seekTo/forward/rewind` fonksiyonlarında
     sarmadan hemen önce uygun parametreyi ayarla. Kaldığı yerden devam (`PREVIOUS_SYNC`) aynen kalsın.
2. **429/509/503'te yerinde kısa yeniden deneme (oynatıcıyı yeniden hazırlamadan):**
   - Film/dizi `MediaSource` için özel `LoadErrorHandlingPolicy` (`DefaultLoadErrorHandlingPolicy`'den türet):
     `InvalidResponseCodeException` kodu 429, 503, 509 veya 5xx ise **yeniden denenebilir** say;
     `getRetryDelayMsFor` → 1. deneme 400 ms, 2. 800 ms, 3. 1500 ms, 4. 2500 ms; `getMinimumLoadableRetryCount` = 4.
     403/404 eskisi gibi hata (yeniden deneme yok).
   - `DefaultMediaSourceFactory(createDataSourceFactory()).setLoadErrorHandlingPolicy(...)` — sadece `isLive == false`
     iken oluşturulan oynatıcıda (canlı davranışı DEĞİŞMESİN).
   - Mevcut `onPlayerError` içindeki 403/429/509 bloğu son çare olarak kalsın (politika denemeleri tükenirse çalışır).
3. `bufferForPlaybackAfterRebufferMs`: film/dizi için **5000 → 2500**. `bufferForPlaybackMs` 1500 kalsın.
4. **Ölçüm (zorunlu, rapora yaz):** debug derlemede `VodTiming` logları zaten var. Bir filmde:
   açılış süresi (`onRenderedFirstFrame` toplam ms), ±10 sn, +30 sn ve kaydırıcıyla ~20 dk ileri sarma için
   "sarma → oynatma" süresi (ms) ve varsa görülen HTTP kodları — **önce/sonra** tablosu. Ölçüm için `seekTo` ile
   `STATE_READY` arası süreyi `TAG_TIMING`'e yazan bir debug log ekleyebilirsin (`BuildConfig.DEBUG` içinde).
   (Yerel ölçüm için gerekirse `assembleDebug` + cihaza kurulum serbest; release APK üretme.)

## DOKUNMA
Canlı yayın yükleme ayarları ve davranışı, `VodPrewarmer`, yüzey devri, medya oturumu, altyazı, yedek uzantı sırası,
güncelleme, imza, CI, sürüm numarası.

## Test
- Film aç: açılış öncekinden kısa ya da aynı.
- Kaydırıcıyla 20 dk ileri sar: görüntü 1-3 sn içinde geliyor (önce 7-8 sn).
- ±10 sn ve ±30 sn sarma akıcı; geri sarınca hedefin ilerisine atlamıyor.
- Hızlıca art arda 5 kez ileri sar: hata ekranı çıkmıyor, sonunda oynatma başlıyor.
- Canlı yayında hiçbir fark yok.
- Test edemediğini "test edilemedi" yaz.

---

## Agent raporu

_(Agent doldurur.)_
