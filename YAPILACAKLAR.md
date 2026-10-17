# Vega IPTV — Yapılacaklar

Bu dosya projenin **tek iş listesidir**. Görevleri Claude yazar, kodlayıcı agent (Antigravity,
Android Studio agent vb.) uygular. Her görevin tam tarifi `docs/gorevler/` altındaki dosyadadır.

---

## 🤖 Agent için çalışma talimatı

Kullanıcı "yapılacakları kontrol et ve uygula" dediğinde:

1. `git checkout main && git pull` yap. `AGENTS.md` kurallarını oku ve uygula.
2. Aşağıdaki **Bekleyen görevler** tablosunda, durumu `⏳ Bekliyor` olan **en üstteki** görevi seç.
   Aynı anda yalnızca **bir** görev yap.
3. **ONAY AL — kod yazmadan ÖNCE DUR ve kullanıcıya şunu sor:**
   > Sıradaki görev: **G-XXX — <görev adı>**
   > Önerilen düşünme seviyesi: **<seviye>** · Önerilen model: listedeki en güçlü kodlama modeli
   > Şu an hangi model ve seviyedeyim: <bildiğin kadarıyla yaz; bilmiyorsan "bilmiyorum" de>
   > Model ve seviye uygunsa **"onay"** yaz; değiştirmen gerekiyorsa değiştirip sonra "onay" yaz.

   Kullanıcı açıkça "onay" (veya "evet", "başla") yazmadan hiçbir dosyayı değiştirme, branch açma, komut çalıştırma.
   Kullanıcı başka bir görev numarası söylerse onu yap.
4. Görevin dosyasını (`docs/gorevler/...`) baştan sona oku. Dosyadaki branch adını, kapsamı,
   "DOKUNMA" listesini ve test adımlarını aynen uygula.
5. Bitince:
   - `./gradlew assembleDebug` ile derle.
   - Görev branch'ine commit edip push et. **main'e merge ETME.**
   - Görev dosyasının en altındaki **Agent raporu** bölümünü doldur (ne yaptın, hangi dosyalar,
     test sonuçları, sorunlar). Bu rapor değişikliği görev branch'inde commit et.
   - Bu tablodaki durumu `🔍 İncelemede (branch: ...)` yap — bu değişikliği de görev branch'inde yap.
6. Kullanıcıya kısa bir özet ver ve DUR. Sıradaki göreve kullanıcı tekrar söylemeden geçme.

Görev dosyası belirsizse veya tarif edilenle kod uyuşmuyorsa: tahmin yürütme, raporda soruyu yaz ve dur.

**Seviye:** Görevi yaptırırken Antigravity'de seçilmesi önerilen düşünme seviyesi (low/medium/high).

**Durumlar:** ⏳ Bekliyor · 🔍 İncelemede (agent bitirdi, Claude kontrol ediyor) ·
✅ Tamamlandı (main'e alındı) · ⏸️ Ertelendi

---

## 📋 Bekleyen görevler

| # | Görev | Öncelik | Seviye | Durum |
|---|---|---|---|---|
| G-001 | [Tam ekranda durum ve gezinme çubukları gizlenmiyor](docs/gorevler/G-001-tam-ekran-immersive.md) | 🔴 | high | ⏳ Bekliyor |
| G-002 | [Canlı yayın alt kartını kompaktlaştır, sahte EPG/kalite verilerini kaldır](docs/gorevler/G-002-kompakt-canli-kart.md) | 🟠 | high | ⏳ Bekliyor |
| G-003 | [TV canlı yayın oynatıcısında altyazı gecikmesi ayarı çalışmıyor](docs/gorevler/G-003-tv-altyazi-gecikmesi.md) | 🟢 | medium | ⏳ Bekliyor |

---

## 🧑 Kullanıcının yapması gerekenler (agent'lar bunlara dokunmaz)

- [ ] **`feat/mobil-tablet-mini-oynatici` branch'i:** Daha önce bir agent tarafından yapılmış, main'e
      alınmamış (6 dosya, PlayerScreen dahil). Alınacak mı, atılacak mı? Karar verilene kadar
      G-001 ve G-002 bu branch'ten bağımsız, main üzerinden yapılır.
- [ ] **Release anahtarı:** Yayınlanan tüm sürümler hâlâ derleme bilgisayarının geçici "Android Debug"
      anahtarıyla imzalı. `vega-release.jks` ve `keystore.properties` derleme bilgisayarına konmadı.
- [ ] **GitHub Actions** çalışmıyor (ilk derlemeden sonra hiç tetiklenmedi). Repo → Actions sekmesi kontrol edilmeli.

## ⏸️ Ertelenenler

- **Harici oynatıcı (VLC/MX Player):** Ayarlar'daki "VLC (LibVLC)" ve "Harici VLC" seçenekleri şu an
  hiçbir işe yaramıyor. Dağıtımdan önce ya çalışır hale getirilmeli ya da kaldırılmalı.
- **Güncelleyicide indirme doğrulaması** (eksik inen APK'yı kurmadan önce yakalama).
- **Samsung Tizen / LG webOS sürümü.**
- **Dizi detayında "Oynat" butonu** kaldığın bölümü değil, seçili sezonun ilk bölümünü açıyor.

## 💡 Fikirler

- Ebeveyn kilidi PIN'ini TV'de kumandayla girme
- Çoklu profil (aynı hesapta farklı favoriler ve izleme geçmişi)
- Film/dizi oynatıcı kontrollerini G-002'deki kompakt tasarım diliyle yenileme

---

Eski liste ve tamamlanan işlerin geçmişi: [docs/YAPILACAKLAR-arsiv.md](docs/YAPILACAKLAR-arsiv.md)
