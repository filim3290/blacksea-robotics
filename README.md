# 🤖 BLACKSEA ROBOTICS (BSR) — Android Uygulaması

Ünye Fen Lisesi Robotik Takımı Blacksea Robotics (#10380) resmi Android mobil uygulaması.

---

## 📲 APK İNDİR VE YÜKLE

En güncel imzalı Release APK sürümünü doğrudan GitHub Releases üzerinden indirebilirsiniz:

👉 **[En Son Release APK Sürümünü İndir](https://github.com/filim3290/blacksea-robotics/releases/latest)**

> **İmza Uyumluluğu:** Android güvenlik kuralları gereği uygulama içi güncellemelerin çalışabilmesi için ilk kurulan APK ile sonraki tüm güncelleme APK'larının **aynı Release Keystore** ile imzalanması gerekir. İlk APK farklı bir anahtarla (örneğin debug veya farklı keystore ile) kurulduysa, yalnızca bir kez kaldırılıp GitHub Releases üzerinden indirilen imzalı Release APK kurulmalıdır.

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

## 🔐 GitHub Actions Kalıcı Release İmzalama Yapılandırması

`release-apk.yml` iş akışı `debug.keystore` kullanmaz ve her build'de yeni anahtar üretmez. İmzalama için **GitHub Repository Secrets** altında şu 4 bilginin tanımlı olması zorunludur:

- `KEYSTORE_BASE64`: Kalıcı Release `.jks` / `.keystore` dosyasının Base64 metni
- `KEYSTORE_PASSWORD`: Keystore parolası (`storePassword`)
- `KEY_ALIAS`: Anahtar takma adı (`alias`)
- `KEY_PASSWORD`: Anahtar parolası (`keyPassword`)
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
