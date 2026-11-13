# G-057 — Kalan sabit renkler tasarım tokenlarına, sessiz hatalara log

- **Öncelik:** 🟡
- **Agent dağılımı:** Tek agent. **Ön koşul:** G-052 main'e alınmış olmalı (LiveTvScreen'e o da dokunuyor).
- **Branch:** `chore/renk-token-ve-loglar` (main'den aç)
- **Commit mesajları:** Bölüm A `style(ui): kalan sabit renkler tasarım tokenlarına çevrildi`,
  Bölüm B `chore(log): veri ve ağ katmanındaki sessiz hatalar loglanıyor`
- **Değişecek dosyalar (yollar `app/src/main/java/com/tivimax/player/` altına göre):**
  - A: `ui/live/LiveTvWideScreenLayout.kt`, `ui/components/TvFocusable.kt`, `ui/live/LiveTvScreen.kt`,
    `ui/player/components/PlayerSettingsPanel.kt`, `ui/settings/SettingsScreen.kt`,
    `ui/components/AsyncShimmerImage.kt`, `ui/login/LoginScreen.kt` — sadece renk satırları
  - B: `data/local/AppDatabase.kt`, `data/network/BoundedDns.kt`, `data/network/StreamingHttpClient.kt`,
    `data/playlist/PlaylistRepository.kt`, `data/preferences/UserPreferences.kt`, `data/repository/XtreamRepository.kt`
    — sadece `catch` blokları
  - Rapor ve `YAPILACAKLAR.md`'de G-057 satırı

## Bölüm A — Sabit renkler
`ui/design` ve `theme/` dışındaki 28 `Color(0x…)` kullanımı (yukarıdaki 7 dosya) ve G-050'den kalan `Color.Red`
kullanımları `docs/TASARIM-KURALLARI.md`'deki tokenlara çevrilir:
- Kırmızı tonları (`0xFFEF5350` vb.), `Color.Red` → `VegaColors.Danger`
- Kart/çerçeve/yüzey grileri → `VegaColors.Card / Border / SurfaceVariant`
- Metin grileri → `VegaColors.TextSecondary / TextMuted`
- Vurgu (camgöbeği) → `VegaColors.Accent`
- Tam karşılığı olmayan renk (ör. shimmer gradyanındaki ara tonlar): `VegaColors.Xxx.copy(alpha = …)` ile en yakını;
  hiç uymuyorsa dokunma, raporda listele.
- `Color.Black.copy(alpha=…)` gradyan/karartma kullanımları kurala göre serbesttir; dokunma.
Kontrol: `grep -rn "Color(0x" app/src/main/java/com/tivimax/player/ui --include=*.kt | grep -v "ui/design\|theme/"`
→ sadece raporda gerekçelendirilenler kalmalı.

## Bölüm B — Sessiz hatalar
Yukarıdaki 6 veri/ağ dosyasında hatayı yutan `catch (_: …)` / boş `catch` bloklarına
`Log.w("<DosyaAdı>", "<ne yapılırken>", e)` ekle (değişken adı `_` ise `e` yap). Akış değişmez: dönen değer,
yeniden deneme, varsayılan değer aynen kalır.
- **İstisnalar (dokunma):** `SecurityUtils` (çözülen dizgiler loglanmamalı), sık çağrılan sıcak yollar
  (ör. her kanal kartında çalışan ayrıştırma) — bunlar için log yerine yorum `// sessiz: <neden>` ekle.
- Log mesajlarında URL, kullanıcı adı, şifre, sunucu adresi **olmaz** (`e.message` Xtream adresi içerebilir:
  sadece `e.javaClass.simpleName` yaz ya da G-051'deki `CrashLogger.sanitize(...)` ile temizle).

## 🚫 DOKUNMA
- Renk dışı UI değişikliği, `catch` dışı kod değişikliği.
- `AutoUpdateManager`, imza ayarları, `.github/workflows/`. Sürüm numarası. Release yapma.

## Kontrol
- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` (düşük öncelikle, pencere açmadan) → geçmeli.

## Agent raporu
_(Agent doldurur.)_
