# 🤖 BLACKSEA ROBOTICS (BSR) — Android Uygulaması

Ünye Fen Lisesi Robotik Takımı Blacksea Robotics (#10380) resmi Android mobil uygulaması.

---

## 📲 APK İNDİR VE YÜKLE (Tek Yetkili Dağıtım Kanalı: GitHub Releases)

Telefonlara ilk kurulum ve sonraki tüm otomatik güncellemelerin **aynı kalıcı Release imza sertifikasıyla** sorunsuz çalışabilmesi için uygulama **yalnızca GitHub Releases üzerinden** indirilebilir:

👉 **[En Son GitHub Release APK Sürümünü İndir](https://github.com/filim3290/blacksea-robotics/releases/latest)**

> **Önemli İmza Notu:** Google AI Studio önizleme/test APK'sı (`app-debug.apk`) geliştirme debug anahtarıyla (`53:71:10:32:...`) imzalanır. Telefonunuzda daha önce AI Studio'dan yüklenmiş sürüm varsa, GitHub Releases'tan indireceğiniz ilk Release APK'yı kurmadan önce eski sürümü bir kez kaldırın. Bu tek seferlik geçişten sonra uygulama içi tüm güncellemeler aynı kalıcı imzayla otomatik yüklenecektir.

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

`release-apk.yml` iş akışı keystore dosyasını repodan okumaz; yalnızca **GitHub Repository Secrets** üzerinden alır:

| Secret Adı | Açıklama |
|---|---|
| `KEYSTORE_BASE64` | Kalıcı `.jks` keystore dosyasının Base64 çıktısı |
| `KEYSTORE_PASSWORD` | Keystore parolası (`storePassword`) |
| `KEY_ALIAS` | Anahtar takma adı (`alias`) |
| `KEY_PASSWORD` | Anahtar parolası (`keyPassword`) |
| `GOOGLE_SERVICES_JSON` | Firebase yapılandırması (`app/google-services.json`) |

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
