# Plus 1.3 (Pelerinler, Babric, Ekran Kaydedici ve daha fazlası)

### Pelerinler
- minecraftcapes.net galeri API'si ile yeni pelerin koleksiyon sistemi
- Düzenleme (kalem simgesi), favori, silme, "Pelerin Yok" seçeneği ile pelerin seçici diyalogu
- Resmi galeriden indirme ile pelerin galerisi
- Kırpılmış pelerin arka küçük resimleri (bilinear filtreleme)
- Pelerin seçici diyaloguna "Galeriden Pelerin Yükle" butonu eklendi
- Buton isimleri: "Pelerin Seç" → "Pelerinler", "Galeriden Pelerin Yükle"
- Pelerin değişiklikleri için gardırop önizleme düzeltmeleri
- ely.by hesapları için istemci taraflı pelerin desteği

### Babric Mod Yükleyici
- b1.7.3 için tam Babric mod yükleyici desteği

### Ekran Kaydedici
- Süre sayacı ile yerleşik oyun ekran kaydedicisi
- Ses desteği (PLAYBACK_CAPTURE), video-only'ya düşüş

### Düzeltmeler
- Boş cihazda OpenAL çökme düzeltmesi (`ALSOFT_DISABLE_EVENTS`)
- Yinelenen libopenal.so uyarısı çözüldü
- Galeri pelerin küçük resim yüklemesi ana iş parçacığından taşındı

### Diğer
- Yeniden deneme + geri çekilme ile oyuncu bildirimi yoklaması
- GameVersionFilter'da Kararlı/Snapshot sekme hapı
- SearchAssetsScreen iyileştirmeleri
- OSMesa gecelik yapıya güncellendi
- Varsayılan geçiş animasyonu SLICE_IN olarak değiştirildi
- CI action-gh-release v3'e yükseltildi
- Çeviriler: zh-cn, vi-vn, tr güncellemeleri
- Pull request şablonu eklendi
