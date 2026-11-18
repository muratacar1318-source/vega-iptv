# G-055 — PlayerScreen.kt'yi dosyalara böl (davranış değişmeden)

- **Öncelik:** 🟡
- **Agent dağılımı:** Tek agent. **Ön koşullar:** (1) açık PR #63 (ana sayfa/buton taşmaları, PlayerScreen'e dokunuyor)
  birleştirilmiş ya da kapatılmış olmalı — `gh pr view 63 --json state` OPEN ise **başlama**, raporla.
  (2) G-054 main'e alınmış olmalı.
- **Branch:** `refactor/playerscreen-bolme` (main'den aç)
- **Commit mesajları:** her adım ayrı commit: `refactor(oynatıcı): <ne> ayrı dosyaya taşındı`
- **Değişecek dosyalar:** `ui/player/PlayerScreen.kt` ve `ui/player/` altında yeni dosyalar. + rapor ve `YAPILACAKLAR.md`'de G-055 satırı.

## Sorun
`PlayerScreen.kt` 3.900 satır. Tek `PlayerScreen()` fonksiyonu ~3.150 satır; altında çift ekran (DualView) bileşenleri,
video yüzeyi ve sarma göstergesi aynı dosyada. Oynatıcıya dokunan her görev bu dosyada çakışıyor.

## Adımlar
### Adım 1 — Saf taşıma (risk yok)
Aşağıdakileri **hiç değiştirmeden** yeni dosyalara taşı (gerekirse `private` → `internal`):
- `DualViewSplitScreen`, `DualViewSingleWindow`, `DualViewChannelPicker` → `ui/player/DualViewScreens.kt`
- `VideoPlayerSurface` → `ui/player/VideoPlayerSurface.kt`
- `SeekIndicator`, `formatDuration` → `ui/player/PlayerIndicators.kt`
Derle, test et, commit.

### Adım 2 — PlayerScreen içinden bağımsız blokları çıkar
`PlayerScreen()` gövdesini oku ve **kendi içinde kapalı** blokları belirle (ör. dokunma/jest katmanı, kilit katmanı,
uyku zamanlayıcısı, kaldığın yerden devam sorusu, hata katmanı). Bir blok yalnızca şu şartlarla çıkarılır:
- Okuduğu durum parametre olarak, değiştirdiği durum lambda (`onXxx`) olarak geçirilebiliyor;
- `remember` / `LaunchedEffect` anahtarları aynı kalıyor (çıkarılan composable'a taşınan `remember` aynı
  çağrı konumunda kalmalı — bir `remember`'ı koşullu dala sokma);
- Blok 40 satırdan uzun.
En fazla **4** blok çıkar, her biri ayrı commit. Şartları sağlamayan bloğa dokunma, rapora yaz.

## Kurallar
- Davranış, görünüm, odak sırası (TV) ve jestler değişmez. Yeniden adlandırma, biçimlendirme yok.
- `TiviPlayerManager`, `TvPlayerOverlay`, `TiviPlayerComponents.kt` dosyalarına dokunma.

## 🚫 DOKUNMA
- `AutoUpdateManager`, imza ayarları, `.github/workflows/`. Sürüm numarası. Release yapma.

## Kontrol
- Her commit'te `:app:compileDebugKotlin` ve `:app:testDebugUnitTest` (düşük öncelikle, pencere açmadan).
- `wc -l` önce/sonra rapora.
- Elle deneme gerekir (telefonda canlı + film oynatma, jestler, çift ekran; TV'de canlı oynatıcı): cihaz yoksa raporda
  "elle denenmedi" yaz — Claude birleştirmeden önce kullanıcıdan deneme isteyecek.

## Agent raporu
Ön koşullar: PR #63 `MERGED`, G-054 main'de (e9638a1). Her commit'ten sonra `:app:compileDebugKotlin` + `:app:testDebugUnitTest` (BelowNormal, pencere açmadan, tek gradle) → BUILD SUCCESSFUL.

**`PlayerScreen.kt` satır sayısı: 3902 → 3085** (yeni dosyalar: DualViewScreens 578, VideoPlayerSurface 236, PlayerIndicators 167, PlayerOverlays ~250).

**Adım 1 — saf taşıma (1 commit):** `DualViewSplitScreen`, `DualViewSingleWindow`, `DualViewChannelPicker` → `DualViewScreens.kt`; `VideoPlayerSurface` → `VideoPlayerSurface.kt`; `formatDuration`, `enum SeekDirection`, `SeekIndicator` → `PlayerIndicators.kt`. Gövdeler değiştirilmeden; yalnız `private` → `internal` (`formatDuration`, `VideoPlayerSurface`, `SeekIndicator`). Yeni dosyalara PlayerScreen.kt'nin import bloğu aynen kopyalandı (kullanılmayanlar uyarı verir, derlemeyi etkilemez).

**Adım 2 — çıkarılan 4 blok (her biri ayrı commit), hepsi `PlayerOverlays.kt`'de:**
1. Hata katmanı → `PlayerErrorOverlay(error, onRetry, onRetryFallback, onBack)` (54 satır). `if (errorMessage != null && reconnectStatus == null && !isInPipMode)` ve `errorMessage?.let` çağrı yerinde kaldı.
2. Parlaklık göstergesi → `BoxScope.BrightnessFeedbackBar(brightnessFeedback: Int?)` (58 satır). `Modifier.align(...)` için `BoxScope` alıcılı; `AnimatedVisibility` aynı konumda.
3. Ses göstergesi → `BoxScope.VolumeFeedbackBar(volumeFeedback: Int?)` (58 satır).
4. Uyku zamanlayıcısı diyaloğu → `SleepTimerDialog(sleepTimerSeconds, onSetTimer, onCancelTimer, onDismiss)` (43 satır). `showSleepTimerDialog` durumu (`remember`) ve `if (showSleepTimerDialog)` çağrı yerinde kaldı; `playerManager.setSleepTimer/cancelSleepTimer` ve `showSleepTimerDialog = false` lambda olarak veriliyor (sıra aynı: önce işlem, sonra kapat).
Şartlar: çıkarılan dört bloğun hiçbirinde `remember`/`LaunchedEffect` yoktu (okudukları durum parametre, yazdıkları lambda); `remember` çağrı konumları ve anahtarları PlayerScreen içinde olduğu gibi duruyor, hiçbiri koşullu dala girmedi. TV odak/jest kodu çıkarılan bloklarda yok.

**Çıkarılmayan adaylar (rapor):** yeniden bağlanma hapı (34 satır, 40'tan kısa), ekran kilidi bildirimi (37), "kaldığın yerden devam" bildirimi (38), sarma göstergesi çağrısı (41, durum birikimi PlayerScreen'deki `remember`/jest durumuna bağlı), bilgi balonu (47), kaydırma (scrub) HUD kartı (55, çıkarılabilir ama 4 blok sınırı doldu), sonraki bölüm kartı (80 satır, `LaunchedEffect`/geri sayım ve oynatıcı yöneticisiyle iç içe), kanal çekmecesi, yan paneller ve ana "CONTROLS OVERLAY" (~1000 satır; çok sayıda yerel durumu okuyup yazıyor, TV odak istekleri içeriyor). Bunlara dokunulmadı.

**Doğrulama:** `TiviPlayerManager`, `TvPlayerOverlay`, `TiviPlayerComponents.kt` dosyalarına dokunulmadı. Elle denenmedi (cihaz yok): telefonda canlı + film oynatma, jestler (parlaklık/ses göstergeleri), çift ekran, hata katmanı ("Tekrar dene"/"Alternatif akış"/"Listeye geri dön"), uyku zamanlayıcısı ve TV'de canlı oynatıcı birleştirmeden önce kullanıcıdan denenmeli.
