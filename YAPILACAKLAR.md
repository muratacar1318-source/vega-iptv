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
| G-014 | [Tasarım temeli: tek vurgu rengi, renk/şekil tokenları, ortak bileşenler](docs/gorevler/G-014-tasarim-temeli.md) | 🟠 | ⏳ Bekliyor |
| G-019 | [Yeni logo: uygulama simgesi, TV afişi, uygulama içi logo (\"Yıldızlı V\")](docs/gorevler/G-019-logo.md) | 🟠 | ⏳ Bekliyor |
| G-015 | [Oynatıcı ekranlarını tasarım kurallarına uydur](docs/gorevler/G-015-oynatici-tasarim-uyumu.md) | 🟢 | ⏳ Bekliyor (G-014'ten sonra) |
| G-016 | [TV ekranlarını tasarım kurallarına uydur](docs/gorevler/G-016-tv-tasarim-uyumu.md) | 🟢 | ⏳ Bekliyor (G-014'ten sonra) |
| G-017 | [Ana sayfa, Canlı TV, Film/Dizi, Arama, Favoriler — tasarım uyumu](docs/gorevler/G-017-gezinme-ekranlari-tasarim-uyumu.md) | 🟢 | ⏳ Bekliyor (G-014'ten sonra) |
| G-018 | [Ayarlar, Giriş, Liste indirme, Cihaz seçimi — tasarım uyumu](docs/gorevler/G-018-ayarlar-giris-tasarim-uyumu.md) | 🟢 | ⏳ Bekliyor (G-014'ten sonra) |

---

## ✅ Tamamlananlar

| # | Görev | Main'e alındı |
|---|---|---|
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

---

## 🧑 Kullanıcının yapması gerekenler (agent'lar bunlara dokunmaz)

- [ ] **`feat/mobil-tablet-mini-oynatici` branch'i:** Daha önce bir agent tarafından yapılmış, main'e
      alınmamış (6 dosya, PlayerScreen dahil). Alınacak mı, atılacak mı? Karar verilene kadar
      G-001 ve G-002 bu branch'ten bağımsız, main üzerinden yapılır.
- [ ] **Release anahtarı:** Yayınlanan tüm sürümler hâlâ derleme bilgisayarının geçici "Android Debug"
      anahtarıyla imzalı. `vega-release.jks` ve `keystore.properties` derleme bilgisayarına konmadı.

## ⏸️ Ertelenenler

- **Güncelleyicide indirme doğrulaması** (eksik inen APK'yı kurmadan önce yakalama).
- **Samsung Tizen / LG webOS sürümü.**
- **Dizi detayında "Oynat" butonu** kaldığın bölümü değil, seçili sezonun ilk bölümünü açıyor.

## 💡 Fikirler

- Ebeveyn kilidi PIN'ini TV'de kumandayla girme
- Çoklu profil (aynı hesapta farklı favoriler ve izleme geçmişi)
- Film/dizi oynatıcı kontrollerini G-002'deki kompakt tasarım diliyle yenileme

---

Eski liste ve tamamlanan işlerin geçmişi: [docs/YAPILACAKLAR-arsiv.md](docs/YAPILACAKLAR-arsiv.md)
