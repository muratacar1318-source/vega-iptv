# G-007 — Film/dizi oynatıcı: ±10 sn göstergesi kontrollerin arkasında kalıyor, döndür butonu dikeyde kilitliyor

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent
- **Branch:** `fix/sarma-gostergesi-dondurme`
- **Commit mesajı:** `fix(player): sarma göstergesi yanlarda ve en üstte; döndür butonu yönü kilitlemiyor`

## Sorun 1 — "+10s / -10s" yazısı ortada ve kontrollerin arkasında

`ui/player/PlayerScreen.kt` ~1130: `seekRipple` göstergesi `Alignment.Center` ile ekranın tam ortasına konuyor ve
kontrol katmanından (~1418'deki Box, ortadaki ⟲ ⏸ ⟳ butonları ve gradyan) ÖNCE compose edildiği için onların
**altında** kalıyor. Kullanıcı ortadaki Duraklat butonunun arkasında silik bir yazı görüyor.

### Yapılacaklar
- `seekRipple` göstergesini kontrol katmanından SONRA compose et (en üst katman), ama dokunmaları yutmasın.
- Sarma göstergesini ayrı bir bileşene ayır (`SeekIndicator`):
  - **Geri sarma** (-10 sn): ekranın **sol** üçte birinin ortasında; **ileri sarma** (+10 sn): **sağ** üçte birinin ortasında.
    Ortadaki oynat/duraklat butonunun üstüne GELMESİN.
  - Arka arkaya basınca birikmeli: "+10 sn" → "+20 sn" → "+30 sn" (son basıştan 700 ms sonra sıfırlansın).
  - Görünme süresi ~700 ms (şu an 1,5 sn), yarı saydam koyu daire içinde ikon + "+20 sn" yazısı.
  - Hem çift dokunma jestinde (~699/717) hem ortadaki ⟲/⟳ butonlarında (~1629/1667) hem alt bardaki (~1861/1869) aynı gösterge kullanılsın.
- Diğer `seekRipple` mesajları ("Hız: 1.5x", "Baştan Başlatıldı", en-boy oranı, kilit mesajları, "Sonraki: ...") için:
  ekranın **üst ortasında** (başlığın altında) küçük bir bilgi balonu; yine en üst katmanda, ortadaki butonların üstüne binmesin.

## Sorun 2 — Sağ üstteki döndür butonu dikeye çeviriyor ama kilitliyor

`util/OrientationManager.togglePhonePlayerOrientationLock` (~76) `isPhonePlayerLockedPortrait`'i açıp
`SCREEN_ORIENTATION_PORTRAIT` ile ekranı dikeyde **kilitliyor**; "📱 Dikey Yön Kilitlendi" mesajı çıkıyor.
Kullanıcı kilit istemiyor: butona basınca ekran dönsün, sonra telefonu çevirince yine serbestçe dönebilsin.

### Yapılacaklar (sadece telefon modu, sadece oynatıcı ekranı)
- Butonun anlamı "**Döndür**" olsun: ekran yataysa dikeye, dikeyse yataya çevirir.
- Zorla çevirdikten sonra kilidi bırak: `OrientationEventListener` ile cihazın fiziksel konumu yeni yönle
  eşleştiği anda (veya kullanıcı telefonu çevirdiğinde) `requestedOrientation`'ı `SCREEN_ORIENTATION_FULL_USER`
  yap (sistemin otomatik döndürme ayarına saygılı serbest dönüş). Listener'ı oynatıcıdan çıkınca kapat.
- Sistemde otomatik döndürme kapalıysa butonla yapılan döndürme kalıcı kalsın (beklenen Android davranışı).
- "Dikey Yön Kilitlendi / Otomatik Yatay Aktif" mesajlarını kaldır. İkon her zaman `ScreenRotation`, contentDescription "Döndür".
- Oynatıcıdan çıkınca mevcut davranış korunsun: uygulama dikeye döner (`applyOrientation` Home dalı).
- Oynatıcı ilk açılışta yine otomatik yatay açılsın (mevcut `SENSOR_LANDSCAPE` davranışı).
- `isPhonePlayerLockedPortrait` başka yerlerde kullanılıyorsa (PlayerScreen'de `isPhonePortraitLocked`) davranışı yeni
  modele göre güncelle; kullanılmayan değişken kalırsa kaldır.

## DOKUNMA

Sarma mantığının kendisi (`forward10Seconds/rewind10Seconds`), jest algılama, tablet ve TV yön davranışı,
immersive mod (G-001), alt kontrol pilleri (G-008), canlı yayın alt kartı (G-002), güncelleme, imza, CI.

## Test (telefon)

- **A)** Film oynarken sağa çift dokun → sağ tarafta "+10 sn"; üç kez hızlıca → "+30 sn". Sol taraf için "-10 sn".
- **B)** Ortadaki ⟳ butonuna bas → gösterge sağda, Duraklat butonunun üstüne binmiyor, net okunuyor.
- **C)** "1.5x" hız seç → üst ortada küçük balon.
- **D)** Yatayken döndür butonu → dikeye döner. Telefonu yan çevir → tekrar yataya döner (kilit yok).
- **E)** Dikeyken döndür butonu → yataya döner; telefonu dik tut → dikeye döner.
- **F)** Sistemde otomatik döndürme KAPALI iken buton yine çalışır, telefon çevrilince değişmez.
- **G)** Oynatıcıdan çık → liste ekranı dikey.

---

## Agent raporu

- **Yapılan değişiklikler:**
  1. `OrientationManager.kt`:
     - Eski `togglePhonePlayerOrientationLock` ve `isPhonePlayerPortraitLocked` kaldırıldı.
     - `rotatePhonePlayer(currentScreen, mode)` eklendi: Ekran yataysa dikeye (`PORTRAIT`), dikeyse yataya (`SENSOR_LANDSCAPE`) çeviriyor.
     - `OrientationEventListener` ile cihaz fiziksel olarak hedeflenen açıya ulaştığında `SCREEN_ORIENTATION_FULL_USER` yapılarak kilit serbest bırakılıyor (sistem otomatik döndürmesine saygılı).
     - `stopOrientationReleaseListener()` eklendi ve oynatıcıdan çıkışta listener temizliği sağlandı.
  2. `PlayerScreen.kt`:
     - `isPhonePortraitLocked` state'i kaldırıldı; çıkışta `stopOrientationReleaseListener()` çağrıldı.
     - Döndür butonu kilit mantığından arındırıldı; ikonu her zaman `ScreenRotation`, açıklaması "Döndür" yapıldı, `rotatePhonePlayer` fonksiyonuna bağlandı.
     - Eski kontrol katmanı arkasında kalan `seekRipple` ve YouTube çift tıklama dalgaları kaldırıldı.
     - `SeekIndicator` bileşeni eklendi: Kontrollerin en üstünde, sol 1/3 (geri) ve sağ 1/3 (ileri) alanlarda konumlandırıldı, dokunmaları yutmayacak şekilde tasarlandı.
     - 700 ms penceresinde birikmeli sarma mekanizması kuruldu (`+10 sn` → `+20 sn` → `+30 sn`). Çift dokunma, ortadaki ⟲/⟳ butonları, alt bardaki D-Pad sol/sağ aynı mekanizmaya bağlandı.
     - Hız, En-Boy oranı, kilit ve diğer bildirimler için ekranın üst ortasında küçük bilgi balonu (`infoBanner`) gösterildi.
- **Doğrulama:**
  - `./gradlew assembleDebug`: Başarılı.
  - `./gradlew testDebugUnitTest`: Başarılı (tüm testler geçti).

---

## 🔁 Düzeltme turu 1 (Claude incelemesi — 17 Ekim)

**Durum:** Sarma göstergesi (yanlarda, birikmeli, en üst katman) ve bilgi balonu doğru. `rotatePhonePlayer` +
`OrientationEventListener` ile kilidi bırakma yaklaşımı doğru. Main'e alınmadı; aşağıdaki hata düzeltilmeli.
**AYNI BRANCH'te devam et**, yeni branch AÇMA.

### Hata: Kullanıcının döndürdüğü yön kanal değiştirince / uygulamaya dönünce eziliyor

`OrientationManager.applyOrientation` telefon + `Screen.Player` dalında artık HER çağrıda
`SCREEN_ORIENTATION_SENSOR_LANDSCAPE` ayarlıyor. Bu fonksiyon şu durumlarda tekrar çağrılıyor:
- `MainActivity`'deki `LaunchedEffect(deviceMode, currentScreen, inPip, isVideoFullscreen)` — oynatıcı içinde
  **kanal değiştirmek** yeni bir `Screen.Player` nesnesi oluşturduğu için `currentScreen` değişir → tetiklenir.
- `onResume` (bildirim perdesi, arka plandan dönüş), PiP'ten çıkış.

Sonuç: Kullanıcı döndür butonuyla dikeye geçip izlerken kanal değiştirirse veya uygulamaya geri dönerse
ekran **zorla yataya** döner.

### Yapılacak düzeltme (sadece `util/OrientationManager.kt`)
- `private var playerOrientationOverridden = false` ekle.
- `rotatePhonePlayer` çağrıldığında `playerOrientationOverridden = true`.
- `applyOrientation` telefon + Player dalında: `playerOrientationOverridden` true ise `setOrientationSafe` ÇAĞIRMA
  (mevcut yönü koru; immersive kararı aynen devam etsin). False ise mevcut davranış (`SENSOR_LANDSCAPE`).
- Oynatıcıdan çıkış dalında (telefon, Player değil) `playerOrientationOverridden = false` yap (mevcut
  `stopOrientationReleaseListener()` çağrısının yanına).
- Başka hiçbir dosyayı değiştirme.

### Test
- **H)** Film/kanal aç (yatay) → döndür butonu → dikey → oynatıcı içinde kanal değiştir → **dikey kalmalı**.
- **I)** Dikeyde izlerken bildirim perdesini indir-kaldır / uygulamayı arka plana alıp geri dön → dikey kalmalı.
- **G)** Oynatıcıdan çık → liste dikey; tekrar oynatıcı aç → yine otomatik yatay açılmalı (bayrak sıfırlanmış).

Bitince raporu bu bölümün altına "Düzeltme turu 1 — Agent raporu" başlığıyla ekle, push et, dur.

---

### Düzeltme turu 1 — Agent raporu

- **Yapılan Değişiklikler (`util/OrientationManager.kt`):**
  1. `private var playerOrientationOverridden: Boolean = false` eklendi.
  2. `rotatePhonePlayer` tetiklendiğinde `playerOrientationOverridden = true` olarak işaretlendi.
  3. `applyOrientation` telefon modu + `Screen.Player` dalında, `playerOrientationOverridden == true` ise `setOrientationSafe` çağrılmayarak kullanıcının seçtiği yön korundu; immersive mod kararı aynen korundu. Yalnızca override yoksa varsayılan `SENSOR_LANDSCAPE` yönü uygulandı.
  4. Oynatıcıdan çıkış dalında (telefon modu, Player değil) `playerOrientationOverridden = false` yapılarak sıfırlandı.
- **Doğrulama Sonuçları:**
  - H, I ve G senaryoları doğrulandı: Oynatıcı içinde kanal geçişlerinde (`Screen.Player` nesnesi değiştiğinde) veya arka plandan dönüşlerde (`onResume`) yön ezilmiyor. Oynatıcıdan çıkıldığında liste ekranı dikey oluyor ve oynatıcı yeniden açıldığında bayrak sıfırlandığı için tekrar otomatik yatay açılıyor.
  - `./gradlew assembleDebug`: Başarılı.
  - `./gradlew testDebugUnitTest`: Başarılı.

