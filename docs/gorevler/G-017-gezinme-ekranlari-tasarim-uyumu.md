# G-017 — Ana sayfa, Canlı TV, Film/Dizi, Arama ve Favori ekranlarını tasarım kurallarına uydur

- **Öncelik:** 🟢
- **Agent dağılımı:** Tek agent. (G-015, G-016, G-017, G-018 **farklı klasörlere** dokunur; G-014 bittikten sonra
  istenirse 4 ayrı agent'a paralel verilebilir.)
- **Branch:** `ui/gezinme-ekranlari-tasarim-uyumu`
- **Commit mesajı:** `ui: gezinme ekranları tasarım kurallarına uyduruldu`
- **Ön koşul:** G-014 (tasarım temeli) main'de olmalı. Değilse YAPMA, raporla, dur.

## Kapsam — SADECE şu dosyalar
ui/main/**, ui/live/**, ui/catalog/**, ui/vod/**, ui/series/**, ui/search/**, ui/favorites/**, ui/components/**

Ekranlar: Ana sayfa, alt menü, kanal kartları, kategori çipleri/kenar çubuğu, film/dizi detay, arama, favoriler, kategori düzenleme diyaloğu. Dizi detayındaki "★ Listemden Kaldır", "☆ Listeme Ekle", "▶ S1 B2 Başlat" gibi simgeli metinleri Icon + metin yap.

## Yapılacaklar
Önce `docs/TASARIM-KURALLARI.md`'yi oku. Kapsamdaki dosyalarda:

1. **Renk:** Sabit `Color(0xFF…)` / `Color(0x…)` değerlerini `VegaColors` tokenlarıyla değiştir (tema ile değişsin).
   Video üstü karartma/gradyanlar (`Color.Black.copy(alpha=…)`, `Brush.verticalGradient` siyah tonları) kalabilir.
   Kırmızılar: canlı rozet → `VegaColors.Live`; çıkış/sil/hata → `VegaColors.Danger`.
2. **Vurgu:** Seçili/aktif/birincil öğelerde `OpusCyan` veya `VegaColors.Accent` dışındaki vurgu renklerini
   (indigo, mor `0xFFA855F7`, `NeonCyan`, sabit `0xFF00E5FF`) `VegaColors.Accent` yap. Altın sadece yıldız/puan/favori.
3. **Köşe:** `RoundedCornerShape(x.dp)` değerlerini 8/12/16 ölçeğine (`VegaShapes`) yuvarla:
   ≤6 → Small (8), 10–14 → Medium (12), ≥16 → Large (16); yuvarlak düğmeler `CircleShape`. 1–3dp ilerleme çubukları hariç.
4. **Odak:** `focusedBorderColor` her yerde `VegaColors.Accent` (birincil dolgu butonda beyaz serbest).
5. **Butonlar/seçimler:** Davranışı ve yerleşimi aynı kalacak şekilde, doğrudan karşılığı olan yerlerde
   `VegaButton` / `VegaIconButton` / `VegaChoiceChip` / `VegaSwitchRow` / `VegaSectionCard` kullan.
   Karşılığı birebir değilse en azından renk/köşe/yükseklik kurallarını uygula; zorlama.
6. **Yazı:** `fontSize` değerlerini 22/17/15/14/13/11 ölçeğine yuvarla (oynatıcıdaki büyük saat/sayılar hariç).
7. **Metin:** Bu kapsamda gördüğün metinleri `TASARIM-KURALLARI.md` "Dil ve metin" kuralına uydur
   ("siz" → "sen" dili, emojili buton metinleri → Icon, TÜMÜ BÜYÜK HARF → normal). Raporda değişen metinleri listele.

## DOKUNMA
Kapsam dışı dosyalar, davranış/iş mantığı, gezinme, odak SIRASI ve `FocusRequester` bağlantıları, oynatma,
güncelleme sistemi, imza, CI. `ui/design/*` ve `theme/*` dosyalarını değiştirme (eksik görürsen raporla).

## Test
- Kapsamdaki her ekranı aç: düzen aynı, sadece renk/köşe/yazı tutarlı.
- Zümrüt ve Ametist temalarında da ekranlar temaya uyuyor (eski sabit lacivert paneller kalmadı).
- TV/kumanda: odak çerçevesi her yerde aynı camgöbeği.
- Test edemediğin maddeyi "test edilemedi" yaz. APK üretme.

---

## Agent raporu

### Yapılan İşler
- **Renk ve Vurgu:** Kapsam dahilindeki tüm dosyalarda (`ui/main/**`, `ui/live/**`, `ui/catalog/**`, `ui/vod/**`, `ui/series/**`, `ui/search/**`, `ui/favorites/**`, `ui/components/**`) sabit `Color(0x...)` ve `Tivi...` renkleri yerine `VegaColors` (`Accent`, `Background`, `Surface`, `SurfaceVariant`, `Card`, `Border`, `TextPrimary`, `TextSecondary`, `TextMuted`, `Live`, `Gold`, `Success`, `Danger`, `OverlayPanel`) tokenları bağlandı.
- **Köşe ve Şekil:** Sabit `RoundedCornerShape(x.dp)` değerleri `VegaShapes` (`Small`, `Medium`, `Large`, `Pill`) tokenlarına uyarlandı.
- **Odak Çerçevesi:** TV ve D-Pad odağı için `focusedBorderColor = VegaColors.Accent` yapıldı (birincil dolgulu butonlarda `Color.White`).
- **Yazı Ölçeği:** Metin boyutları 22, 17, 15, 14, 13, 11 sp basamaklarına yuvarlandı.
- **Dil ve Metin Kuralları:**
  - "Siz" dili "sen" diline çevrildi ("eklemediniz" -> "eklemedin", "ekleyebilirsiniz" -> "ekleyebilirsin", "arama terimi deneyebilirsiniz" -> "arama terimi deneyebilirsin", "kategorileri gizleyin veya sabitleyin" -> "kategorileri gizle veya sabitle").
  - Başlık ve menü kilit büyük harfleri cümle düzenine geçirildi ("CANLI TV" -> "Canlı TV", "FİLMLER" -> "Filmler", "DİZİLER" -> "Diziler", "KATEGORİLER" -> "Kategoriler", "TÜMÜNÜ GÖSTER" -> "Tümünü göster", "TÜMÜNÜ GİZLE" -> "Tümünü gizle", "Kanalı Gizle" -> "Kanalı gizle").
  - Butonlardaki emojili/simgeli metinler Icon + Metin olarak ayrıştırıldı (`SeriesDetailScreen.kt`: "★ Listemden Kaldır" -> Icon + "Listemden kaldır", "☆ Listeme Ekle" -> Icon + "Listeme ekle", "▶ S1 B2 Başlat" -> Icon + "Sezon X, Y. bölümü başlat", `VodDetailScreen.kt`: "▶ Hemen İzle" -> Icon + "Filmi izle").

### Değiştirilen Dosyalar
1. `app/src/main/java/com/tivimax/player/ui/main/HomeScreen.kt`
2. `app/src/main/java/com/tivimax/player/ui/main/OpusDashboardScreen.kt`
3. `app/src/main/java/com/tivimax/player/ui/vod/VodScreen.kt`
4. `app/src/main/java/com/tivimax/player/ui/vod/VodDetailScreen.kt`
5. `app/src/main/java/com/tivimax/player/ui/series/SeriesScreen.kt`
6. `app/src/main/java/com/tivimax/player/ui/series/SeriesDetailScreen.kt`
7. `app/src/main/java/com/tivimax/player/ui/search/UniversalSearchScreen.kt`
8. `app/src/main/java/com/tivimax/player/ui/favorites/FavoritesScreen.kt`
9. `app/src/main/java/com/tivimax/player/ui/catalog/VegaMediaCatalogScreen.kt`
10. `app/src/main/java/com/tivimax/player/ui/components/MediaPosterCard.kt`
11. `app/src/main/java/com/tivimax/player/ui/components/CategoryManagementDialog.kt`
12. `app/src/main/java/com/tivimax/player/ui/components/ActionChipButton.kt`
13. `app/src/main/java/com/tivimax/player/ui/components/CategoryItem.kt`
14. `app/src/main/java/com/tivimax/player/ui/components/CategorySidebar.kt`
15. `app/src/main/java/com/tivimax/player/ui/components/MediaCategoryNavigationBar.kt`
16. `app/src/main/java/com/tivimax/player/ui/live/modern/ChannelDetailedCard.kt`
17. `app/src/main/java/com/tivimax/player/ui/live/modern/ChannelGridCard.kt`
18. `app/src/main/java/com/tivimax/player/ui/live/modern/ChannelRowCard.kt`
19. `app/src/main/java/com/tivimax/player/ui/live/modern/LiveShelvesSection.kt`
20. `app/src/main/java/com/tivimax/player/ui/live/shelf/HeroCard.kt`
21. `app/src/main/java/com/tivimax/player/ui/live/shelf/ShelfChannelCard.kt`
22. `docs/gorevler/G-017-gezinme-ekranlari-tasarim-uyumu.md`
23. `YAPILACAKLAR.md`

### Test Sonuçları
- `./gradlew compileDebugKotlin`: Başarılı (`BUILD SUCCESSFUL in 43s`, sıfır hata).
- Görsel ve cihaz testleri: APK üretilmedi (kural gereği test edilemedi, CI doğrulayacak).
