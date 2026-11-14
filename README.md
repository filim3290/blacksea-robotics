# 🤖 BLACKSEA ROBOTICS (BSR) — Android Uygulaması

Ünye Fen Lisesi Robotik Takımı Blacksea Robotics (#10380) resmi Android mobil uygulaması.

---

## 📲 APK İNDİR VE YÜKLE

En güncel Android APK sürümünü doğrudan GitHub Releases üzerinden indirebilirsiniz:

👉 **[En Son APK Sürümünü İndir](https://github.com/filim3290/blacksea-robotics/releases/latest)**

> **Birleşik APK İmzalama:** Hem Google AI Studio üzerinden oluşturulan APK'lar hem de GitHub Actions tarafından yayınlanan Release APK'lar aynı imza sertifikasıyla (`SHA-256: 53:71:10:32:...`) imzalanır. Telefonunuzdaki mevcut AI Studio APK'sının üzerine GitHub Releases güncellemeleri doğrudan kurulabilir. *(Yalnızca daha önce geçiş sürümü `v1.0.5` veya `v1.0.6` yüklendiyse bir defalık kaldırıp yeniden kurulum gerekir.)*

---

## ⚡ Kurulum Adımları

1. Yukarıdaki bağlantıdan en son yayınlanan **`BlackseaRobotics-vX.Y.Z.apk`** dosyasını telefonunuza indirin.
2. İndirme tamamlandığında bildirime veya indirilen dosyaya tıklayın.
3. Android sistemi *"Bilinmeyen kaynaklardan uygulama yükleme"* izni isterse:
   - **Ayarlar**'a tıklayın.
   - Kullandığınız tarayıcıya (Chrome vb.) veya Dosya Yöneticisine **"Bu kaynaktan izin ver"** iznini açın.
4. **"Yükle"** butonuna basarak kurulumu tamamlayın.
5. Uygulama bir kez kurulduktan sonra, bundan sonraki tüm güncellemeleri uygulama içerisinden otomatik olarak tek tıkla yapabilirsiniz.

---

## 🛠️ GitHub Actions APK Derleme Yapılandırması

`release-apk.yml` iş akışı varsayılan olarak projedeki `debug.keystore.base64` anahtarını kullanır; böylece hiçbir ek keystore secret'ı gerektirmeden Google AI Studio ile %100 aynı imzaya sahip APK üretir. İstenirse ileride Play Store yayını için `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` ve `KEY_PASSWORD` secret'ları eklenerek özel release keystore'a da geçilebilir.

Gerekli temel secret:
- `GOOGLE_SERVICES_JSON`: Firebase yapılandırması (`app/google-services.json`)

---

## 🚀 Özellikler

- **🛡️ Otomatik APK Güncelleyici:** GitHub Releases API üzerinden yeni sürümleri otomatik denetleme, SHA-256 ve imza doğrulama, tek tıkla güncelleme.
- **📁 Gelişmiş Dosya Paylaşımı:** Resimler, videolar, ses kayıtları, PDF belgeleri ve CAD / 3D STL robotik tasarım dosyalarını takım arkadaşlarıyla paylaşabilme.
- **💬 Takım Sohbeti & Rol Yönetimi:** Superadmin, Admin, Mentor, Kaptan, Üye yetkilendirmesi, gerçek zamanlı sohbet.
- **💡 Fikir Havuzu & Görev Takibi:** Robot mekanik, yazılım ve otonom stratejileri için ortak proje panosu.
- **🔔 Gerçek Zamanlı Bildirimler:** Onay bildirimleri, duyurular ve sürüm güncellemeleri.

---

## 🛠️ Repository Bilgileri

- **Repository:** [https://github.com/filim3290/blacksea-robotics](https://github.com/filim3290/blacksea-robotics)
- **Application ID:** `com.aistudio.blacksearobotics.kxnqpv`
