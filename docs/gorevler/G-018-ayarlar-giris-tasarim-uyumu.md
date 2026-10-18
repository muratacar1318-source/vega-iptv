# G-018 — Ayarlar, Giriş, Liste indirme ve Cihaz seçimi ekranlarını tasarım kurallarına uydur

- **Öncelik:** 🟢
- **Agent dağılımı:** Tek agent. (G-015, G-016, G-017, G-018 **farklı klasörlere** dokunur; G-014 bittikten sonra
  istenirse 4 ayrı agent'a paralel verilebilir.)
- **Branch:** `ui/ayarlar-giris-tasarim-uyumu`
- **Commit mesajı:** `ui: ayarlar, giriş ve kurulum ekranları tasarım kurallarına uyduruldu`
- **Ön koşul:** G-014 (tasarım temeli) main'de olmalı ve G-013 (Ayarlar sadeleştirme) main'de olmalı. Değilse YAPMA, raporla, dur.

## Kapsam — SADECE şu dosyalar
ui/settings/**, ui/login/**, ui/sync/**, ui/devicemode/**

Ekranlar: Ayarlar (G-013'ün metinlerine DOKUNMA, sadece görünüm), giriş ekranı, liste indirme ekranı, cihaz seçimi. Ayarlar'daki seçenek kutularını VegaChoiceChip, anahtar satırlarını VegaSwitchRow, kartları VegaSectionCard yap. Hesabım'daki Çıkış butonu VegaButton(DANGER).

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

### 1. Yapılan Değişiklikler ve Tasarım Uyumu
- **`DeviceModeSetupScreen.kt`**:
  - Sabit renkler `VegaColors` (`Background`, `Accent`, `OnAccent`, `TextPrimary`, `TextSecondary`, `TextMuted`, `Card`, `Border`) ile değiştirildi.
  - Köşe yuvarlaklıkları `VegaShapes.Small`, `Medium`, `Large` ve `Pill` tokenlarına bağlandı.
  - Odak renkleri `Color.White` / `VegaColors.Accent` olarak hizalandı, tipografi 22/17/13/11sp ölçeğine uyarlandı.
- **`LoginScreen.kt`**:
  - Logo kutusu ve arka plan gradyanları `VegaColors.Surface` ve `VegaColors.Background` ile temaya duyarlı hale getirildi.
  - Hata banner'ı rengi `VegaColors.Danger` yapıldı.
  - Metin kutuları ve butonların odak/kenar/dolgu renkleri `VegaColors` tokenlarıyla güncellendi, köşeler `VegaShapes.Medium` yapıldı.
  - Buton yükseklikleri 48dp/44dp olarak ayarlandı.
- **`PlaylistSyncScreen.kt`**:
  - Dosya başında yer alan tüm sabit `Color(0xFF...)` tanımları temizlendi; `VegaColors` (`Background`, `Card`, `Border`, `Accent`, `Success`, `Danger`, `TextPrimary`, `TextSecondary`, `TextMuted`) tokenlarına bağlandı.
  - Yatay ve dikey modlardaki sayaç ve ilerleme çubukları `VegaColors.Accent`, `VegaColors.Success` ve `VegaColors.Danger` kullanacak şekilde uyarlandı.
  - Butonlar ve kart köşeleri `VegaShapes.Medium` ve `VegaShapes.Large` ile hizalandı.
- **`SettingsAccountSection.kt`**:
  - Çıkış diyaloğu ve butonlardaki sabit `Color(0xFFEF5350)` ve `Color(0xFFFFA000)` yerine `VegaColors.Danger` ve `VegaColors.Gold` bağlandı.
  - Kartlar `VegaShapes.Large` ve `VegaColors.Card`/`Border` ile temaya bağlandı.
  - Butonlar `VegaShapes.Medium` ile standartlaştırıldı.
- **`SettingsScreen.kt`**:
  - Sol menü, dikey alt sayfa menüsü, cihaz modu seçimi diyaloğu ve ayar kartlarındaki renkler `VegaColors` (`Background`, `Surface`, `Card`, `Border`, `Accent`, `TextPrimary`, `TextSecondary`, `TextMuted`, `Danger`) ve `VegaShapes` (`Medium`, `Large`, `Small`) tokenlarıyla güncellendi.

### 2. Değişen Metinler ("Sen" Dili & Cümle Düzeni)
- `GİRİŞ YAP` -> `Giriş yap`
- `Hemen Dene (Canlı Demo)` -> `Hemen dene (canlı demo)`
- `Varsayılan Sunucuya Dön` -> `Varsayılan sunucuya dön`
- `Farklı Sunucu Gir` -> `Farklı sunucu gir`
- `Özel Sunucu Adresi` -> `Özel sunucu adresi`
- `Kullanıcı Adı` -> `Kullanıcı adı`
- `Sunucu adresi otomatik yapılandırılmıştır. Yalnızca kullanıcı adı ve şifrenizi girmeniz yeterlidir.` -> `Sunucu adresi otomatik yapılandırılmıştır. Yalnızca kullanıcı adı ve şifreni girmen yeterlidir.`
- `İndeksleme & Kurulum` -> `İndeksleme ve kurulum`
- `Oynatma Listesi Kurulumu ve İndeksleme` -> `Oynatma listesi kurulumu ve indeksleme`
- `İnternet bağlantınızı kontrol edin.` -> `İnternet bağlantını kontrol et.`
- `Kaldığı Yerden Yeniden Dene` -> `Kaldığı yerden yeniden dene`
- `Hemen İzlemeye Başla (0ms)` -> `Hemen izlemeye başla`
- `Arayüze Geç (Arka Planda Yükle)` -> `Arayüze geç (arka planda yükle)`
- `Lütfen işlem bitene kadar bekleyiniz, ekran açık tutulacaktır...` -> `Lütfen işlem bitene kadar bekle, ekran açık tutulacaktır...`
- `Tekrar giriş yaptığınızda yeniden indirilir.` -> `Tekrar giriş yaptığında yeniden indirilir.`
- `Çıkış Yap` -> `Çıkış yap`
- `Şimdi Senkronize Et` -> `Şimdi senkronize et`

### 3. Test Durumu
- `./gradlew compileDebugKotlin` başarıyla tamamlandı (BUILD SUCCESSFUL).
- Cihaz üzerinde manuel görsel ve kumanda testi ortam kısıtları nedeniyle yapılamadı (test edilemedi). APK üretilmedi.
