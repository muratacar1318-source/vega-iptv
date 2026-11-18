# G-061 — Geri izleme: rehberde geçmiş programa tıklayıp izleme

- **Öncelik:** 🟢 (büyük özellik)
- **Agent dağılımı:** Tek agent. **Ön koşul:** G-060 main'e alınmış olmalı (`epg_programs` tablosu ve
  `live_streams.tvArchiveDuration` sütunu orada geliyor).
- **Branch:** `feat/geri-izleme` (main'den aç)
- **Commit mesajı:** `feat(canlı): arşivi olan kanallarda geçmiş programlar izlenebiliyor`
- **Değişecek dosyalar (yollar `app/src/main/java/com/tivimax/player/` altına göre):**
  - `data/repository/XtreamRepository.kt` — yalnızca yeni `buildCatchupUrl(...)` ve sunucu saat dilimi okuma
  - `data/epg/CatchupUrl.kt` (yeni, saf yardımcı)
  - `ui/tv/TvEpgGridScreen.kt`, `ui/live/` altında telefon kanal detayı/program listesi (hangisi programları gösteriyorsa)
  - `ui/MainViewModel.kt` / ilgili `ui/vm/*` — yalnızca "geçmiş programı oynat" çağrısı
  - `ui/player/PlayerScreen.kt` ve `ui/tv/TvPlayerOverlay.kt` — **yalnızca** geri izleme rozeti ve "Canlıya dön" düğmesi
  - yeni test `app/src/test/java/com/tivimax/player/data/epg/CatchupUrlTest.kt`
  - rapor ve `YAPILACAKLAR.md`'de G-061 satırı

## Neden
Sunucu kanal listesinde `tv_archive` (1/0) ve `tv_archive_duration` (gün) gönderiyor; uygulama bunları okuyor ama hiç
kullanmıyor. Türkiye'de IPTV kullanıcılarının en çok aradığı özellik: kaçırılan maçı/diziyi rehberden açıp izlemek.

## Çözüm
1. **Adres (Xtream):** `{sunucu}/timeshift/{kullanıcı}/{şifre}/{süreDakika}/{YYYY-MM-DD:HH-MM}/{streamId}.ts`
   - Başlangıç saati **sunucunun saat diliminde** yazılır: `player_api.php` yanıtındaki `server_info.timezone`
     (`XtreamServerInfo.timezone`, ör. "Europe/Istanbul"). Yoksa cihaz saat dilimi.
   - `süreDakika` = programın süresi (dakika, yukarı yuvarla) + 5 dk pay.
   - Saf fonksiyon: `CatchupUrl.build(server, user, pass, streamId, startMs, endMs, serverZone): String`.
   - TS açılmazsa aynı adresin `.m3u8` biçimini dene (mevcut `playWithFallbacks` listesine iki adres ver).
2. **Hangi programlar:** kanal `tvArchive == 1` ve program `endMs < şimdi` ve `startMs >= şimdi - tvArchiveDuration gün`.
   Rehberde bu programlar küçük bir "geri sar" ikonu (`Icons.Default.History`, `TextSecondary`) ile işaretlenir;
   arşivi olmayan geçmiş programlar soluk ve tıklanamaz kalır.
3. **Oynatma:** OK/dokunma → oynatıcı `isLive = false` gibi davranır (sarma çubuğu programın süresi kadar, ileri/geri
   sarılabilir). Oynatıcıda üstte "Geri izleme" rozeti (`VegaColors.Accent`, "Canlı" rozeti DEĞİL) ve **"Canlıya dön"**
   düğmesi → aynı kanalın canlı yayınını açar. TV'de düğme kumandayla odaklanabilir.
4. Geri izleme içeriği "izleme ilerlemesi"ne ve "son izlenenler"e yazılmaz (geçici içerik).
5. Takılma bekçisi (G-058) film/dizi kuralıyla çalışır (oynatıcı `isLiveStream = false` gördüğü için ek iş yok) — raporda doğrula.
6. Metinler: "Geri izleme", "Canlıya dön", "Bu program için arşiv yok". "Catch-up", "timeshift" arayüzde geçmez.

## Test (`CatchupUrlTest.kt`)
- İstanbul diliminde 13 Kasım 2026 20:00–21:45 programı → `…/110/2026-11-13:20-00/123.ts` (105 + 5 dk).
- Sunucu dilimi UTC, cihaz İstanbul → saat 17-00 yazılır.
- `timezone` boş → cihaz dilimi.
- Arşiv penceresi dışı (tvArchiveDuration = 1 gün, 3 gün önceki program) → uygun değil.

## 🚫 DOKUNMA
- G-060'ın indirme/ayrıştırma kodu, Room şeması (bu görevde geçiş yok).
- Oynatıcıda rozet ve "Canlıya dön" dışındaki her şey (`TiviPlayerManager`'a dokunma; adres listesiyle mevcut `play` çağrılır).
- `AutoUpdateManager`, imza ayarları, `.github/workflows/`. Sürüm numarası. Release yapma.

## Kontrol
- `./gradlew :app:testDebugUnitTest` (BelowNormal, pencere açmadan) → geçmeli.
- Elle deneme gerekli (arşivli bir kanalda dünkü programı aç, sar, "Canlıya dön"). Cihaz yoksa raporda belirt;
  Claude kullanıcıdan deneme ister. Raporda: sağlayıcıda kaç kanalın `tvArchive = 1` olduğu (veritabanından sayım).

## Agent raporu
_(Agent doldurur.)_
