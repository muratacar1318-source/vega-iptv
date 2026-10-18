# Vega IPTV — Yapılacaklar

Bu dosya projenin **tek iş listesidir**. Görevleri Claude yazar, kodlayıcı agent (Antigravity,
Android Studio agent vb.) uygular. Her görevin tam tarifi `docs/gorevler/` altındaki dosyadadır.

---

## 🤖 Agent için çalışma talimatı

Kullanıcı "yapılacakları kontrol et ve uygula" dediğinde:

1. `git checkout main && git pull` yap. `AGENTS.md` kurallarını oku ve uygula.
2. Aşağıdaki **Bekleyen görevler** tablosunda, durumu `⏳ Bekliyor` olan **en üstteki** görevi seç.
   Aynı anda yalnızca **bir** görev yap.
3. Görevin dosyasını (`docs/gorevler/...`) baştan sona oku. Dosyadaki branch adını, kapsamı,
   "DOKUNMA" listesini ve test adımlarını aynen uygula.
4. Bitince:
   - **Derleme yapma / APK üretme** (kullanıcı özellikle istemedikçe). Derleme kontrolünü GitHub otomatik yapar.
   - Görev branch'ine commit edip push et. **main'e merge ETME.**
   - Görev dosyasının en altındaki **Agent raporu** bölümünü doldur (ne yaptın, hangi dosyalar,
     test sonuçları, sorunlar). Bu rapor değişikliği görev branch'inde commit et.
   - Bu tablodaki durumu `🔍 İncelemede (branch: ...)` yap — bu değişikliği de görev branch'inde yap.
5. Kullanıcıya kısa bir özet ver ve DUR. Sıradaki göreve kullanıcı tekrar söylemeden geçme.

**Agent dağılımı:** Her görev dosyasının başında `Agent dağılımı` satırı vardır.
- `Tek agent` → görevi tek başına, tek branch'te yap.
- `N agent (paralel)` → görev dosyasındaki "Agent bölümleri" kısmında her agent için ayrı iş, ayrı branch ve
  dokunabileceği dosyalar yazılıdır. Kullanıcı her agent'a hangi bölümü yapacağını söyler
  (ör. "G-00X Bölüm A'yı yap"). Bir agent SADECE kendi bölümündeki dosyalara dokunur; başka bölümün dosyasını
  değiştirmesi gerekirse durur ve raporlar.

Görev dosyası belirsizse veya tarif edilenle kod uyuşmuyorsa: tahmin yürütme, raporda soruyu yaz ve dur.

**Durumlar:** ⏳ Bekliyor · 🔍 İncelemede (agent bitirdi, Claude kontrol ediyor) ·
✅ Tamamlandı (main'e alındı) · ⏸️ Ertelendi

---

## 📋 Bekleyen görevler

| # | Görev | Öncelik | Durum |
|---|---|---|---|
| G-026 | [Oynatma listeleri arayüzü: Listelerim, ekle/düzenle/sil, hızlı geçiş](docs/gorevler/G-026-oynatma-listesi-arayuzu.md) | 🟠 | 🔁 Düzeltme turu 1 (branch: feat/oynatma-listesi-arayuz) |
| G-028 | [Liste indirme: önce dosyaya indir sonra işle; sonraki güncellemeler arka planda](docs/gorevler/G-028-liste-indirme-once-dosyaya.md) | 🟠 | ⏳ Bekliyor (G-026'dan sonra; G-027 tamamlandı) |

---

## ✅ Tamamlananlar

| # | Görev | Main'e alındı |
|---|---|---|
| G-025 | Oynatma listesi altyapısı: çoklu liste, liste başına ayrı veritabanı, güvenli geçiş — PR #35 | 18 Ekim |
| G-027 | Film/dizide anahtar kareye sarma, 429/5xx için yerinde kısa yeniden deneme, ölçüm logları — PR #37 | 18 Ekim |
| G-004 | Canlı TV'de kanal ilk açıldığında siyah ekran (video yüzeyi tek sahipli) — PR #7 | 17 Ekim |
| G-001 | Tam ekranda durum ve gezinme çubukları gizleniyor (tüm tam ekran yolları) — PR #8 | 17 Ekim |
| G-005 | Canlı TV varsayılan detaylı liste, EPG saatleri yerel saat, sahte ilerleme kaldırıldı — PR #9 | 17 Ekim |
| G-006 | Tam ekran evrensel arama (anlık sonuç, Türkçe uyumlu, son aramalar) — PR #10 | 17 Ekim |
| G-007 | ±10 sn göstergesi yanlarda/en üstte; döndür butonu kilitlemiyor — PR #11 | 17 Ekim |
| G-008 | Oynatıcı ayarları tek kompakt panelde (ses, altyazı, görüntü, hız) — PR #12 | 17 Ekim |
| G-010 | Ayarlar paneli içeriği görünüyor, uyku zamanlayıcısı, TV kumanda odağı — PR #13 | 18 Ekim |
| G-003 | TV canlı yayın oynatıcısında altyazı gecikmesi uygulanıyor — PR #15 | 18 Ekim |
| G-002 | Kompakt canlı yayın kartı, sahte EPG/kalite verileri kaldırıldı, üst düğmeler tek hapta — PR #14 | 18 Ekim |
| G-009 | Önizleme yüzey devri: görünüm pencereye eklenince devrediliyor — PR #16 | 18 Ekim |
| G-011 | Yayın bilgisi sadece gerçek değerleri gösteriyor (uydurma FHD/50 FPS kaldırıldı) — PR #17 | 18 Ekim |
| G-012 | Hesap ekranı: gerçek abonelik bilgileri, içerik özeti, çıkış onayı — PR #18 | 18 Ekim |
| G-013 | Ayarlar sadeleştirildi: işlevsiz ayarlar kaldırıldı, kategori ve metinler anlaşılır — PR #19 | 18 Ekim |
| G-014 | Tasarım temeli: tek vurgu rengi, renk/şekil tokenları, ortak bileşenler — PR #20 | 18 Ekim |
| G-015 | Oynatıcı ekranları tasarım kurallarına uyduruldu — PR #21 | 18 Ekim |
| G-016 | TV ekranları tasarım kurallarına uyduruldu — PR #22 | 18 Ekim |
| G-020 | İçerik ilk açılış hatası (\"Session ID must be unique\") giderildi — PR #23 | 18 Ekim |
| G-021 | v1.3.2 doğru anahtarla yeniden imzalandı (güncelleme kurulabiliyor) | 18 Ekim |
| G-022 | Akıllı altyazı içerik başına bir kez uygulanıyor, kullanıcı seçimi korunuyor | 18 Ekim |
| G-023 | Oynatıcıdaki alttan açılan sayfalar kompakt yan panele çevrildi (Daha fazla, Bölümler, Çift ekran) — PR #29 | 18 Ekim |
| G-017 | Ana sayfa, Canlı TV, Film/Dizi, Arama, Favoriler tasarım kurallarına uyduruldu — PR #31 | 18 Ekim |
| G-024 | Sabit http sunucu, şifreli bağlantı kaldırıldı, güncelleme ekranı geri tuşu, 429 yeniden deneme, güvenli eski içerik temizliği — PR #33 | 18 Ekim |
| G-018 | Ayarlar, Giriş, Liste indirme, Cihaz seçimi tasarım kurallarına uyduruldu — PR #34 | 18 Ekim |

---

## 🧑 Kullanıcının yapması gerekenler (agent'lar bunlara dokunmaz)

- [ ] **`feat/mobil-tablet-mini-oynatici` branch'i:** Daha önce bir agent tarafından yapılmış, main'e
      alınmamış (6 dosya, PlayerScreen dahil). Alınacak mı, atılacak mı? Karar verilene kadar
      G-001 ve G-002 bu branch'ten bağımsız, main üzerinden yapılır.
- [ ] **Release anahtarı:** Yayınlanan tüm sürümler hâlâ derleme bilgisayarının geçici "Android Debug"
      anahtarıyla imzalı. `vega-release.jks` ve `keystore.properties` derleme bilgisayarına konmadı.

## ⏸️ Ertelenenler

- **Yeni logo ve uygulama simgesi:** Kullanıcı uygulamaya yeni bir isim bulacak; logo isim belli olunca tasarlanacak.
  (Şu an telefondaki simge hâlâ Android'in hazır yeşil simgesi.)
- **Güncelleyicide indirme doğrulaması** (eksik inen APK'yı kurmadan önce yakalama).
- **Samsung Tizen / LG webOS sürümü.**
- **Dizi detayında "Oynat" butonu** kaldığın bölümü değil, seçili sezonun ilk bölümünü açıyor.

## 💡 Fikirler

- (Referans: Extreme InfiniTV) TV raflarında odaklanan kartın komşu afişlerini önceden yükleme (kaydırma akıcılığı)
- (Referans) Canlı kanal adresi belirsizse içerik türünü (HLS / TS) sunucudan sorup doğru oynatıcı yolunu seçme; TS açılmazsa HLS ile tekrar deneme
- (Referans) Film/dizi için isteğe bağlı "VLC'de aç" (Android'de doğrudan VLC paketine yönlendirme)
- Ebeveyn kilidi PIN'ini TV'de kumandayla girme
- Çoklu profil (aynı hesapta farklı favoriler ve izleme geçmişi)
- Film/dizi oynatıcı kontrollerini G-002'deki kompakt tasarım diliyle yenileme

---

Eski liste ve tamamlanan işlerin geçmişi: [docs/YAPILACAKLAR-arsiv.md](docs/YAPILACAKLAR-arsiv.md)
