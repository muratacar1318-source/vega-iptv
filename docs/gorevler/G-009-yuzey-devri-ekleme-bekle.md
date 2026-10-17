# G-009 — Önizleme yüzey devri: görünüm henüz pencereye eklenmemişse eklenince devret

- **Öncelik:** 🟢
- **Agent dağılımı:** Tek agent
- **Branch:** `fix/yuzey-devri-ekleme-bekle`
- **Commit mesajı:** `fix(player): bekleyen önizlemeye yüzey devri pencereye eklendikten sonra yapılır`

## Sorun

G-004'te `TiviPlayerManager.detachView` içinde, ayrılan primary görünümden bekleyen önizlemeye yüzey devri
sadece `pending.isAttachedToWindow == true` ise yapılıyor. TV'de `TvTiviMateScreen` içinde tam ekran
(`TvPlayerOverlay`) ile önizleme `if/else` ile yer değiştiriyor; yeni önizleme görünümü, eski overlay'in
`onRelease`'i çalıştığı anda henüz pencereye eklenmemiş olabilir. Bu durumda devir atlanır ve önizleme siyah kalabilir.

## Yapılacak (sadece `player/TiviPlayerManager.kt`, `detachView`)

- `pending.isAttachedToWindow` ise mevcut davranış (hemen `attachView(pending, isPrimary = false)`).
- Değilse: `androidx.core.view.doOnAttach` ile pencereye eklendiği anda bir kez
  `attachView(it as PlayerView, isPrimary = false, tag = "pending-handover-onAttach")` çağır.
  O ana kadar başka bir görünüm primary olarak yüzeyi aldıysa (sahip değiştiyse) devretme.
- `VideoTarget` debug loguna "pending pencereye eklenince devredildi" satırı ekle.

## DOKUNMA
`TiviPlayerManager` dışındaki her şey.

## Test
- TV (emülatör varsa): kanal → tam ekran → geri → önizleme görüntülü. Birkaç kez tekrarla.
- Telefon: Canlı TV → kanal → tam ekran → geri → mini oynatıcı görüntülü (G-004 davranışı bozulmamalı).

---

## Agent raporu

_(Agent doldurur.)_
