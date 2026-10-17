# G-007 — Film/dizi oynatıcı: ±10 sn göstergesi kontrollerin arkasında kalıyor, döndür butonu dikeyde kilitliyor

- **Öncelik:** 🟠
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

_(Agent doldurur.)_
