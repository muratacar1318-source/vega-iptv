# G-016 — TV ekranlarını tasarım kurallarına uydur

- **Öncelik:** 🟢
- **Agent dağılımı:** Tek agent. (G-015, G-016, G-017, G-018 **farklı klasörlere** dokunur; G-014 bittikten sonra
  istenirse 4 ayrı agent'a paralel verilebilir.)
- **Branch:** `ui/tv-tasarim-uyumu`
- **Commit mesajı:** `ui(tv): TV ekranları tasarım kurallarına uyduruldu`
- **Ön koşul:** G-014 (tasarım temeli) main'de olmalı. Değilse YAPMA, raporla, dur.

## Kapsam — SADECE şu dosyalar
ui/tv/**

Ekranlar: TV ana ekranı, yan menü, TV oynatıcı katmanı, EPG ızgarası.

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

_(Agent doldurur.)_
