# G-013 — Ayarları sadeleştir: işe yaramayanları kaldır, isim ve açıklamaları anlaşılır yap

- **Öncelik:** 🟠
- **Agent dağılımı:** Tek agent
- **Branch:** `ui/ayarlar-sadelestirme`
- **Commit mesajı:** `ui(settings): işlevsiz ayarlar kaldırıldı, kategori ve ayar metinleri sadeleştirildi`
- **Ön koşul:** G-012 (Hesap ekranı) main'e alınmış olmalı — aynı dosyaya (`SettingsScreen.kt`) dokunuyor.
  `YAPILACAKLAR.md`'de G-012 "✅ Tamamlananlar"da değilse bu görevi YAPMA, raporla ve dur.

## Amaç

Kullanıcı: "Altyazı gecikmesini ayarlardan kaldır. Ayarlarda olmaması gerekenleri kaldır. Kategori isimlerini ve
açıklamaları kullanıcı dostu, anlaşılır hale getir."

Ana dosya: `ui/settings/SettingsScreen.kt` (`enum class SettingsSection` ~55 ve `RenderSelectedSection`).

## Kesin kurallar

- Ekranda teknik terim YOK: "Kodek", "Decoder", "Buffer", "VOD", "PiP", "HTTPS", "SP Punto", "Media3", "LibVLC",
  "GPU/CPU", "Ping", "Sunucu Yanıtı: 200" vb. Sade Türkçe, "sen" diliyle kısa cümleler.
- **Profesyonel ve tutarlı yazım (bütün Ayarlar ekranında):**
  - Kategori başlıkları kısa isim: her kelimenin ilk harfi büyük ("Güncelleme ve Bakım", "Ebeveyn Kilidi").
  - Ayar başlıkları, buton ve seçenek metinleri: sadece ilk harf büyük ("Tercih edilen ses dili", "Testi başlat",
    "Şifreyi değiştir", "Kategorileri düzenle"). TÜMÜ BÜYÜK HARF yok (ekran başlığı "AYARLAR" → "Ayarlar").
  - Açıklamalar tek cümle, nokta ile biter; ünlem, emoji, "Pro/Ultra/Süper" gibi pazarlama sözcükleri yok.
  - "&" yerine "ve"; köşeli parantez "[Önerilen]" yerine "(önerilen)"; sayı + birim arasında boşluk ("5 sn", "45 ms").
  - Toast mesajları kısa ve aynı kalıpta: "<Ayar adı>: <yeni değer>" (ör. "Görüntü boyutu: Doldur").
  - Bu kurallara uymayan, bu dosyada tek tek yazılmamış başka metin görürsen de aynı kurala göre düzelt
    ve raporda "ek düzeltilen metinler" listesi olarak yaz.
- Kullanıcıya görünen hiçbir metinde (Toast dahil) **sunucu adresi / URL / port** geçmeyecek.
- Ayarı UI'dan kaldırmak = sadece ekrandan kaldırmak. `UserPreferences` anahtarlarını/fonksiyonlarını SİLME
  (eski kayıtlar bozulmasın). Oynatma mantığına dokunma.

## 1) KALDIRILACAKLAR (ekrandan)

| Ayar | Neden |
|---|---|
| **Altyazı Gecikmesi** (Görünüm, ~1284-1395) | Kullanıcı istedi. Gecikme zaten oynatıcıdaki Ayarlar panelinden içerik izlerken ayarlanıyor. |
| **Oynatıcı Motoru** (ExoPlayer / VLC / Harici VLC, ~516-568) | VLC ve Harici seçenekleri hiçbir şey yapmıyor (`playerEngine` hiçbir yerde kullanılmıyor). |
| **Hızlı Kanal Açılışı** anahtarı (~778-787) | `fastZapping` uygulamada hiçbir yerde kullanılmıyor; etkisiz. |
| **Ayarları Yedekle / Geri Yükle** (~1780-1856) | Sahte: yedek dosyasına sadece tarih yazıyor, hiçbir ayar kaydetmiyor/geri yüklemiyor. |
| **Telegram / WhatsApp Desteği** satırları (~1982-1990) | WhatsApp numarası yer tutucu (+90 555 000 00 00). |
| Sürümdeki **"Pro"** eki | Gerçek bir sürüm türü değil. |
| Görünüm'deki **"Cihaz Modu — Değiştir"** satırı (~1437-1465) | "Cihaz Türü" bölümünün tekrarı. |
| Ayrıca: aynı yerde arka arkaya iki `HorizontalDivider` (~568-569) | Görsel hata. |

Kaldırma sonrası kullanılmayan state/import'ları temizle (derleme uyarısı bırakma).

## 2) KATEGORİ İSİMLERİ VE ALT BAŞLIKLARI (`SettingsSection`)

| Eski | Yeni başlık | Yeni alt başlık |
|---|---|---|
| Hesap ve Liste — "Sunucu, hesap ve liste güncelleme" | **Hesabım** | Abonelik bilgileri ve içerik listesi |
| Oynatıcı ve Kodek — "Video çözücü, tampon (buffer) & PiP" | **Oynatıcı** | Ses ve altyazı dili, görüntü ve akıcılık |
| Görünüm ve Altyazı — "Altyazı boyutu, rengi & kategoriler" | **Görünüm** | Tema, kanal listesi ve altyazı görünümü |
| Cihaz Modu — "Telefon, Tablet ve TV çalışma düzeni" | **Cihaz Türü** | Telefon, tablet veya TV düzeni |
| Ebeveyn Kontrolü — "+18 içerik kilidi & PIN kodu & HTTPS" | **Ebeveyn Kilidi** | Yetişkin içerikleri şifreyle kilitle |
| Yedekleme ve Uygulama — "Yedekle, önbellek, hız testi & sürüm" | **Güncelleme ve Bakım** | Güncellemeler, bağlantı testi ve sürüm bilgisi |

(Enum sabit adlarını değiştirme; sadece `title` ve `subtitle` metinleri.)

## 3) AYAR METİNLERİ — bölüm bölüm

### Oynatıcı
- "Varsayılan Ses Dili" → **Tercih edilen ses dili** · "Birden fazla dili olan yayınlarda önce bu dil açılır."
  Seçenek "Orijinal" kalsın.
- "Varsayılan Altyazı Dili" → **Tercih edilen altyazı dili** · "Altyazısı olan içeriklerde kendiliğinden açılacak dil."
- "Video Çözücü (Decoder)" → **Görüntü işleme** · seçenekler **Hızlı (önerilen)** / **Uyumlu** ·
  açıklama: "Görüntü takılıyor, renkler bozuk ya da ekran siyah kalıyorsa 'Uyumlu'yu dene."
  Toast: "Görüntü işleme: Hızlı" / "Görüntü işleme: Uyumlu".
- "Canlı TV Tampon Süresi (Buffer)" → **Canlı yayın ön yükleme** ·
  "İnternetin zayıfsa artır: kanal biraz geç açılır ama daha az takılır." · seçenekler **2 sn (hızlı açılış)** /
  **5 sn (önerilen)** / **10 sn (en az takılma)**.
- "Film & Dizi Tamponu (VOD Buffer)" → **Film ve dizi ön yükleme** · "Takılma oluyorsa artır." · değer "N sn".
- "Arka Planda Küçük Pencere (PiP)" → **Küçük pencerede devam et** ·
  açık: "Uygulamadan çıkınca video köşede küçük pencerede oynamaya devam eder." · kapalı: "Kapalı".
- "Varsayılan Ekran Oranı" → **Görüntü boyutu** · "Video ekrana nasıl sığdırılsın." · seçenekler oynatıcı
  panelindekiyle aynı isimler: **Orijinal**, **Doldur**, **Yakınlaştır**, **16:9** ("Fit (Orijinal)" → "Orijinal").

### Görünüm
- "Uygulama Teması" → **Renk teması** · "Uygulamanın renklerini seç."
- "Canlı TV Düzeni" → **Canlı TV görünümü** · seçenekler:
  **Raflar** — "Kanallar kategorilere göre yan yana kaydırılan satırlarda" ("Netflix tarzı" ifadesini kaldır) ·
  **Liste** — "Kategoriler üstte, kanallar altta liste halinde".
- "Dikey Kategori Gezinme Düzeni" → **Film ve dizi kategorileri (telefon)** ·
  "Telefonda film ve dizi kategorilerinin nasıl gösterileceği." · seçenekler:
  **Üstte kaydırmalı** — "Kategoriler üstte, afişler altta" ·
  **Yan panel** — "Kategoriler solda liste halinde" ·
  **Açılır menü** — "Kategoriler tek bir menüde, ekranda daha çok yer kalır".
  Toast: "Kategori görünümü: …".
- "Altyazı Boyutu" → **Altyazı boyutu** · "Tüm içeriklerde altyazıların büyüklüğü." ·
  12-36 arası +/- yerine oynatıcı paneliyle **aynı 4 seçenek**: **Küçük (14) · Orta (18) · Büyük (22) · Çok büyük (26)**
  (aynı değerleri `viewModel.setSubtitleSize("14")` vb. ile yaz; mevcut "KUCUK/ORTA…" eşleşmesi korunsun).
  Önizleme metni "Örnek altyazı" kalsın. "SP Punto" yazısını kaldır.
- "Altyazı Metin Rengi" → **Altyazı rengi** (seçenekler aynı).
- "Kategori Gizleme & Sıralama Yönetimi" → **Kategorileri düzenle** ·
  "İstemediğin kategorileri gizle, sık izlediklerini en üste al." · buton **Kategorileri düzenle**.

### Cihaz Türü
- "Cihaz Modu ve Yön Yönetimi" → **Cihaz türü** · "Uygulamanın düzenini cihazına göre ayarla."
- "Aktif Seçim: X" → "Şu an: X" · "Kullanmak İstediğiniz Cihaz Düzenini Seçin:" → "Bir düzen seç:"
- "Cihaz Seçimini Sıfırla" → **İlk kurulum ekranını tekrar göster** ·
  "Uygulama bir sonraki açılışta cihaz türünü yeniden sorar." · buton **Sıfırla**.
- `CihazModu` diyaloğu (~2022-2150): "Cihaz Modunu Değiştir" → "Cihaz türünü değiştir";
  "Kullanım senaryonuza göre arayüzü ve ekran yönünü anında uyarlayın:" → "Uygulamayı hangi cihazda kullanıyorsun?";
  "Cihaz seçimini sıfırla (İlk açılış sihirbazı)" → "İlk kurulum ekranını tekrar göster".

### Ebeveyn Kilidi
- "Ebeveyn Kilidi (+18 Koruma)" → **Ebeveyn kilidi** · "Yetişkin kategorilerini açmak için 4 haneli şifre istenir."
- "4 Haneli Ebeveyn PIN Kodunu Değiştir" → **Şifreyi değiştir**.
- PIN diyaloğu: başlık "Yeni şifre", metin "4 haneli yeni şifreyi gir.", placeholder "••••" ("Örn: 1234" kalksın).
- **"Güvenli Bağlantı (HTTPS)"** anahtarını buradan **Güncelleme ve Bakım** bölümüne taşı (aşağıda).

### Güncelleme ve Bakım
(Sıra: Güncellemeleri kontrol et → Bağlantı testi → Şifreli bağlantı → Önbelleği temizle → Hakkında)
- **Bağlantı testi** (eski "Sunucu Hız ve Ping Testi") · "Yayınlar takılıyorsa bağlantını kontrol et." ·
  buton **Testi başlat** / "Ölçülüyor…".
  `MainViewModel.runSpeedTest()` (~1800) sonuç metinlerini değiştir (ölçüm mantığına dokunma):
  - < 150 ms → "Bağlantı iyi (gecikme N ms)"; < 400 ms → "Bağlantı orta (gecikme N ms)"; diğer → "Bağlantı yavaş (gecikme N ms)".
  - Hata → **"Bağlantı kurulamadı. İnternetini kontrol et."** — `e.localizedMessage` GÖSTERME (sunucu adresi içerebilir).
  - "Sunucuya bağlanılıyor..." → "Ölçülüyor…".
- **Şifreli bağlantı** (eski HTTPS anahtarı, taşındı) · "Açıkken bağlantın şifrelenir. Yayınlar açılmazsa kapatmayı dene."
  Toast: **"Bağlantı ayarı değiştirildi"** — eski `"Bağlantı güncellendi: $newUrl"` URL gösteriyor, kaldır.
  Anahtarın davranışı (setServerUrl + repository.serverUrl) aynen kalsın.
- "Uygulama Önbelleğini Temizle" → **Önbelleği temizle** · "Kanal logoları ve resimler yeniden yüklenir. Listelerin silinmez."
- "Güncellemeleri Kontrol Et" → **Güncellemeleri kontrol et** — sadece metin; güncelleme mantığına DOKUNMA.
- "Destek ve İletişim" başlığı → **Hakkında** · tek satır: "Sürüm: Vega IPTV 1.3.x" (`BuildConfig.VERSION_NAME`, "Pro" yok).

## DOKUNMA

Hesap bölümü içeriği (G-012'nin işi), `UserPreferences` anahtarları, oynatma/senkronizasyon mantığı,
`AutoUpdateManager` ve güncelleme akışı, `logout()`, imza, CI. Oynatıcı içindeki Ayarlar paneli (altyazı gecikmesi orada KALACAK).

## Test

- Ayarlar ana listesinde 6 kategori yeni isim ve alt başlıklarla görünüyor: Hesabım, Oynatıcı, Görünüm, Cihaz Türü, Ebeveyn Kilidi, Güncelleme ve Bakım.
- Görünüm'de "Altyazı Gecikmesi" yok; film oynatırken Ayarlar panelinde gecikme hâlâ çalışıyor.
- Oynatıcı'da motor seçimi ve hızlı kanal açılışı yok; kalan ayarlar çalışıyor.
- Güncelleme ve Bakım'da yedekleme ve Telegram/WhatsApp yok; bağlantı testi sade sonuç veriyor; uçak modunda
  "Bağlantı kurulamadı…" (adres yok).
- Şifreli bağlantı anahtarı değiştirilince Toast'ta adres görünmüyor.
- Altyazı boyutu 4 seçenekli ve oynatıcı paneliyle aynı değeri gösteriyor.
- TV/kumanda ile tüm ayarlara ulaşılıyor.
- Test edemediğin maddeyi "test edilemedi" diye yaz. APK üretme.

---

## Agent raporu

_(Agent doldurur.)_
