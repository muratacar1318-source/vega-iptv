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
_(Agent doldurur.)_
