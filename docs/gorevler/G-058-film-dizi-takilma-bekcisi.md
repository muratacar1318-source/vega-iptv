# G-058 — Film/dizide takılma bekçisi (kalınan saniyeden hızlı yeniden bağlanma)

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent.
- **Branch:** `fix/vod-takilma-bekcisi` (main'den aç)
- **Commit mesajı:** `fix(oynatıcı): film ve dizide uzun takılmada kalınan yerden otomatik yeniden bağlanma`
- **Değişecek dosyalar (SADECE bunlar):**
  1. `app/src/main/java/com/tivimax/player/player/TiviPlayerManager.kt` — sadece G-044 bekçisi bölümü
  2. `app/src/test/java/com/tivimax/player/player/StallPolicyTest.kt` (yeni)
  3. Rapor ve `YAPILACAKLAR.md`'de G-058 satırı

## Sorun
Canlı yayında G-044 bekçisi var: "yükleniyor"da 10/20 sn kalınca aynı kanala yeniden bağlanıyor. Film/dizide
(`isLiveStream == false`) yok: sunucu bağlantıyı kapatmadan veri göndermeyi keserse oynatıcı okuma zaman aşımına
(60 sn, `readTimeout`) kadar dönen yuvarlakla bekliyor; sonra hata akışı devreye giriyor. Kullanıcı 1 dakika siyah
ekran görüyor ya da uygulamayı kapatıp kaldığı yeri arıyor.

## Çözüm
`armLiveStallWatchdog()` (~639. satır) VOD için de çalışsın, ama farklı kuralla:

| | Canlı (değişmez) | Film/dizi (yeni) |
|---|---|---|
| Ne zaman kurulur | `STATE_BUFFERING` | `STATE_BUFFERING` **ve** ilk kare görüntülenmişse (`hasRenderedFirstFrameOrReady`); açılışta kurulmaz (açılış yavaşlığı G-027'nin işi) |
| Süre | açılış 20 sn / sonra 10 sn | **15 sn** |
| Kurtarma | `stop` → `evictAll` → `seekToDefaultPosition` → `prepare` → `play` | `val pos = p.currentPosition` → `stop` → `evictAll` → **`seekTo(pos)`** → `prepare` → `play` |
| En fazla | 3 | 3; sonra `_errorMessage` = "Bağlantı çok yavaş. Biraz sonra tekrar dene." |
| Durum metni | "Yayın yeniden bağlanıyor... (n/3)" | "Bağlantı yenileniyor... (n/3)" |

Kurallar:
1. Kural seçimini saf bir yardımcıya koy: `object StallPolicy { fun timeoutMs(isLive: Boolean, firstFrameShown: Boolean): Long? }`
   (null = bekçi kurulmaz). Canlı için mevcut değerleri aynen döndürür.
2. Mevcut iptal noktaları (READY, IDLE, hata, yeni içerik, `stopAndClear`, `release`) VOD için de geçerli — ek iş yok;
   sadece `if (!isLiveStream) return` satırı `StallPolicy` kontrolüyle değişir ve kurtarma bloğu `isLiveStream`'e göre dallanır.
3. Kullanıcı duraklattıysa (`!playWhenReady`) ya da `isReconnecting` ise tetiklenmez (mevcut kontroller).
4. Değişken adları (`liveStallJob` vb.) yeniden adlandırılmaz — sadece yorumla "canlı + film/dizi" diye belirt.
5. Sayaç sıfırlama (`liveStallRecoveries = 0`) READY'de zaten var; VOD için de geçerli.
6. Altyazı gecikmesi (`DelayedSubtitleController`) ve izleme ilerlemesi kaydı etkilenmemeli: kurtarmada `seekTo(pos)`
   kullanıldığı için konum korunur.

## Test (`StallPolicyTest.kt`)
- canlı + ilk kare yok → 20000; canlı + ilk kare var → 10000
- film + ilk kare yok → null; film + ilk kare var → 15000

## 🚫 DOKUNMA
- `createLoadControl`, `createDataSourceFactory`, zaman aşımları, `onPlayerError` mantığı, `playInternal`, çift ekran kodu.
- `AutoUpdateManager`, imza ayarları, `.github/workflows/`. Sürüm numarası. Release yapma.

## Kontrol
- `./gradlew :app:testDebugUnitTest` (düşük öncelikle, pencere açmadan) → geçmeli.
- Elle deneme (cihaz varsa): film oynarken Wi-Fi'yi 20 sn kapat-aç → kalınan saniyeden devam etmeli. Yoksa raporda belirt.

## Agent raporu
_(Agent doldurur.)_
