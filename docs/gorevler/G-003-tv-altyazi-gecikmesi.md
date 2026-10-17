# G-003 — TV canlı yayın oynatıcısında altyazı gecikmesi ayarı çalışmıyor

- **Öncelik:** 🟢
- **Branch:** `fix/tv-altyazi-gecikmesi`
- **Commit mesajı:** `fix(tv): canlı yayın oynatıcısında altyazı gecikmesi uygulanıyor`

## Sorun

v1.2.9'da eklenen altyazı gecikmesi (`player/DelayedSubtitleController.kt`) sadece mobil
`PlayerScreen` → `VideoPlayerSurface` içindeki PlayerView'e bağlandı.

TV modunda canlı yayın `PlayerScreen` yerine `ui/tv/TvPlayerOverlay.kt` ile oynatılıyor
(`PlayerScreen.kt` ~121: `if (isTvDevice && isLive) TvPlayerOverlay(...)`). Bu ekranda gecikme
ayarının [-] [+] butonları var (~809-875. satırlar) ve değeri kaydediyor; ancak
`TvPlayerOverlay`'in kendi PlayerView'ine (~249. satır, AndroidView factory/update)
`DelayedSubtitleController` bağlanmadığı için **ayar ekranda değişiyor ama altyazıya hiçbir etkisi yok.**

## Yapılacaklar

`TvPlayerOverlay.kt` içinde, `PlayerScreen.kt` → `VideoPlayerSurface` (~3659-3745) ile AYNI desenle:

- `remember { DelayedSubtitleController(context, coroutineScope) }` oluştur.
- `DisposableEffect` ile çıkışta `detach()` çağır.
- AndroidView `factory` içinde player atandıktan sonra `attach(this, player)`,
  `updateDelay(subtitleDelayMs)`, `updateStyle(subtitleColor, subtitleSize)` çağır.
- `update` bloğunda da aynı üç çağrıyı yap (player değişirse veya ayar değişirse güncellensin).
- `subtitleDelayMs`, `subtitleColor`, `subtitleSize` değerlerini ViewModel'den `collectAsState()` ile al
  (mobil oynatıcının kullandığı aynı StateFlow'lar).

## DOKUNMA

`DelayedSubtitleController.kt`'nin kendisi, mobil `PlayerScreen`, `TvTiviMateScreen`'deki önizleme
PlayerView'i, kumanda tuş işleme (`onPreviewKeyEvent`), odak yönetimi, oynatma mantığı.

## Test

- TV modunda (emülatör veya cihaz) altyazılı bir canlı kanal varsa: gecikmeyi +1,0 sn yap →
  altyazılar 1 sn geç görünmeli. Gecikmeyi 0 yap → normal.
- Altyazılı kanal yoksa: derlemenin geçtiğini ve TV oynatıcısının açılıp kapandığını, kanal
  değiştirmenin çalıştığını doğrula; raporda "altyazılı kanalla test edilemedi" yaz.

---

## Agent raporu

_(Agent doldurur.)_
