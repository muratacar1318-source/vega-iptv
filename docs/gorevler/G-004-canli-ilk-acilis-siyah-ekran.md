# G-004 — Canlı TV'de kanal ilk açıldığında siyah ekran (ses var/yok), kanal değişince düzeliyor

- **Öncelik:** 🔴
- **Agent dağılımı:** Tek agent
- **Branch:** `fix/canli-ilk-acilis-siyah-ekran`
- **Commit mesajı:** `fix(player): video yüzeyi tek sahipli; canlı yayın ilk açılışta siyah ekran düzeltildi`

## Sorun

Telefonda Canlı TV'de bir kanal ilk açıldığında tam ekran oynatıcı siyah kalıyor. Oynatıcı içinde
kanal değiştirince görüntü geliyor ve sonra sorun olmuyor.

## Kök neden (kod incelemesiyle tespit edildi — agent cihazda/logla DOĞRULASIN)

Aynı tek ExoPlayer örneği (`TiviPlayerManager.player`) uygulamada **6 ayrı PlayerView**'e bağlanıyor:
- `ui/player/PlayerScreen.kt` → `VideoPlayerSurface` (~3659) — tam ekran oynatıcı
- `ui/live/LiveTvScreen.kt` (~479) — mini/önizleme oynatıcı
- `ui/live/modern/LiveTvModernPortraitLayout.kt` (~165) — dikey mini oynatıcı
- `ui/live/LiveTvWideScreenLayout.kt` (~672) — yatay/tablet önizleme
- `ui/tv/TvTiviMateScreen.kt` (~532), `ui/tv/TvPlayerOverlay.kt` (~249) — TV

Bir ExoPlayer aynı anda tek bir video yüzeyine çizer; `playerView.player = p` diyen SON görünüm yüzeyi alır.

Akış:
1. `MainViewModel.playLiveChannel` önce `activePreviewChannel = channel` yapıyor, oynatmayı başlatıyor,
   sonra `navigateTo(Screen.Player)`.
2. `Navigation.kt` ekranlar arası `Crossfade` kullanıyor: geçiş boyunca **eski Home ekranı da
   composition'da kalıyor**. Home'daki önizleme PlayerView'i (mini oynatıcı / yatay önizleme) bu sırada
   `factory`/`update` ile `player = p` yapabiliyor ve yüzeyi tam ekran oynatıcıdan **geri çalıyor**.
3. Crossfade bitince Home kaldırılıyor, önizlemenin `onRelease`'i `playerView.player = null` yapıyor
   → ExoPlayer'ın video yüzeyi temizleniyor.
4. Tam ekran `VideoPlayerSurface.update` sadece `if (playerView.player != currentPlayer)` iken yeniden
   bağlıyor; zaten aynı player olduğu için **yüzeyi bir daha talep etmiyor** → siyah ekran.
5. Oynatıcı içinde kanal değiştirince `playLiveChannel` → yeni `Screen.Player` → yeni PlayerView
   oluşuyor ve yüzeyi alıyor → görüntü geliyor. (Kullanıcının gördüğü davranışla birebir uyumlu.)

## Yapılacaklar

1. **Doğrula (önce):** Debug build'de `VideoTarget` etiketiyle, her PlayerView'in `player` atadığı ve
   `onRelease`'te bıraktığı anı (hangi ekran, sistem zamanı) logla. Kullanıcının senaryosunu tekrarla:
   uygulama açılışı → Canlı TV → bir kanala dokun. Yüzeyin önizlemeye geçip sonra temizlendiğini logda göster.
   Kök neden farklı çıkarsa DUR ve raporla.

2. **Tek sahipli video yüzeyi — `TiviPlayerManager`:**
   - `fun attachView(view: PlayerView)` ve `fun detachView(view: PlayerView)` ekle.
   - Manager o an yüzeyin sahibi olan görünümü (`WeakReference<PlayerView>`) tutsun.
   - `attachView`: sahibi farklıysa Media3'ün `PlayerView.switchTargetView(player, eskiSahip, yeniGörünüm)`
     fonksiyonunu kullanarak yüzeyi taşı; sahibi yoksa `view.player = player`.
   - `detachView`: SADECE görünüm o anki sahipse `view.player = null` ve sahibi temizle. Sahip değilse
     hiçbir şey yapma (başkasının yüzeyini silmesin).
   - Öncelik kuralı: tam ekran oynatıcı (`VideoPlayerSurface`, `TvPlayerOverlay`) açıkken önizleme
     görünümleri yüzeyi ALAMAZ (`attachView(view, isPrimary = false)` → tam ekran sahipse yok say).

3. **Tüm PlayerView'leri bu API'ye geçir** (yukarıdaki 6 yer + `PlayerScreen` içindeki
   `DualViewSingleWindow` varsa ikincil oynatıcı HARİÇ — o ayrı ExoPlayer kullanıyor, dokunma):
   - `factory` ve `update` içindeki `player = ...` atamaları → `playerManager.attachView(this / playerView, isPrimary)`.
   - `onRelease` içindeki `playerView.player = null` → `playerManager.detachView(playerView)`.
   - Tam ekran `VideoPlayerSurface`: ilk karede ve Crossfade bittikten sonra yüzeyi garanti almak için
     `LaunchedEffect(Unit) { delay(350); playerManager.attachView(view, isPrimary = true) }` benzeri bir
     yeniden talep ekle (Crossfade süresinden uzun olsun).

4. **Gereksiz önizlemeyi engelle:** Home içindeki önizleme/mini oynatıcı `AndroidView`'leri,
   `viewModel.currentScreen.value` bir `Screen.Player` iken compose EDİLMESİN
   (Crossfade sırasında yüzey çekişmesi hiç başlamasın).

## DOKUNMA

Oynatma/URL/fallback mantığı, tampon ayarları, kanal değiştirme fonksiyonları (`zapNextChannel` vb.),
`DelayedSubtitleController` iç mantığı (sadece yeni attach API'siyle birlikte çalıştığını kontrol et),
Dual View ikincil oynatıcısı, güncelleme sistemi, imza, CI.

## Test (telefon)

- **A)** Uygulamayı kapatıp aç → Canlı TV → bir kanala dokun → görüntü İLK SEFERDE gelmeli. 5 farklı kanalla tekrarla.
- **B)** Canlı TV'de mini oynatıcı açıkken (bir kanal önizlemedeyken) başka bir kanala dokun → tam ekranda görüntü gelmeli.
- **C)** Tam ekrandan geri dön → mini oynatıcıda görüntü devam etmeli (siyah olmamalı).
- **D)** Mini oynatıcıyı tam ekrana genişlet (`expandMiniPlayerToFullscreen`) → görüntü gelmeli.
- **E)** Telefonu yan çevir (yatay Canlı TV önizleme) → kanal aç → görüntü gelmeli.
- **F)** Film aç/kapat, sonra canlı kanal aç → görüntü gelmeli.
- **G)** TV modunda (emülatör) kanal değiştirme ve tam ekran → bozulma yok.
Her senaryoda `VideoTarget` logunu rapora ekle.

---

## Agent raporu

_(Agent doldurur: doğrulama logu, kök neden doğru muydu, değiştirilen dosyalar, A–G sonuçları.)_

---

## 🔁 Düzeltme turu 1 (Claude incelemesi — 17 Ekim)

**Durum:** `fix/canli-ilk-acilis-siyah-ekran` branch'indeki iş büyük ölçüde doğru (tek sahipli yüzey, `switchTargetView`,
öncelik kuralı, önizlemeleri Player ekranında compose etmeme, 350 ms yeniden talep). Main'e alınmadı; aşağıdaki hata düzeltilmeli.

**AYNI BRANCH'te devam et** (`git checkout fix/canli-ilk-acilis-siyah-ekran && git pull`). Yeni branch AÇMA.

### Hata: Tam ekrandan çıkınca önizleme/mini oynatıcı siyah kalıyor (Test C ve TV)

Senaryo (telefon): Canlı TV → kanal → tam ekran → geri.
1. Geri basınca Home yeniden compose ediliyor; önizleme PlayerView'i `factory` içinde
   `attachView(isPrimary = false)` çağırıyor.
2. O anda tam ekran `VideoPlayerSurface` (Crossfade nedeniyle) hâlâ sahip ve `isPrimary = true` → önizlemenin talebi **reddediliyor**.
3. Ardından tam ekran görünüm `onRelease` → `detachView` → yüzey temizleniyor, sahip = null.
4. Önizlemenin `update` bloğu hiçbir state okumadığı için **tekrar çalışmıyor** → önizleme bir daha yüzey istemiyor → **siyah**.

Aynısı TV'de: `TvTiviMateScreen` içinde `TvPlayerOverlay` (primary) kapanıp önizleme yeniden oluşturulduğunda.

### Yapılacak düzeltme (`TiviPlayerManager`)
- Reddedilen önizleme görünümünü hatırla: `private var pendingSecondaryView: WeakReference<PlayerView>?`
  (`attachView` bir `isPrimary = false` talebini reddettiğinde buraya yaz).
- `detachView` içinde, ayrılan görünüm **primary sahip** ise ve `pendingSecondaryView` hâlâ geçerliyse
  (`view.isAttachedToWindow == true`), yüzeyi hemen ona ver: `attachView(pending, isPrimary = false)`.
  Ardından `pendingSecondaryView = null`.
- Bir önizleme görünümü `detachView` ile ayrılırken pending ise, pending'i temizle.
- `release()` içinde pending'i de temizle.
- `VideoTarget` loguna "pending önizlemeye devredildi" satırı ekle.

Başka hiçbir şeyi değiştirme.

### Test (cihaz/emülatör yoksa derle ve mantığı raporda adım adım açıkla)
- **C)** Canlı TV → kanal → tam ekran → geri → mini oynatıcı/önizleme görüntülü olmalı.
- **G)** TV: kanal → tam ekran (`TvPlayerOverlay`) → geri → önizleme görüntülü olmalı.
- **A)** ve **B)** hâlâ çalışmalı.

Bitince raporu bu bölümün altına "Düzeltme turu 1 — Agent raporu" başlığıyla ekle, push et, dur.
