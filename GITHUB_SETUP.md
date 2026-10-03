# Anne TV — GitHub kurulumu

Bu proje GitHub'ı iki iş için kullanır:

1. `channels.json`: TV'nin uzaktan güncellenen kanal manifesti.
2. `update.json` + Releases: APK güncelleme sistemi.

## 1. Repo

Repo **public** olmalıdır. Uygulama TV üzerinde GitHub hesabı/token taşımadan `raw.githubusercontent.com` ve GitHub Releases üzerinden güncelleme alır. Gizli token, parola veya yetkisiz yayın anahtarı repoya eklenmemelidir.

GitHub Actions derlemesi `owner/repo` bilgisini kendisi APK'ya gömer; kaynak kodda kullanıcı adı sabitlemeye gerek yoktur.

## 2. İmza anahtarı — bir defalık kurulum

APK güncellemelerinin mevcut kurulumun üstüne kurulabilmesi için tüm release sürümleri aynı anahtarla imzalanmalıdır. Anahtar dosyası projeye dahil edilmez ve `.gitignore` tarafından dışlanır.

Windows PowerShell:

```powershell
./scripts/prepare-signing.ps1
```

Sonra repo `Settings > Secrets and variables > Actions` bölümüne şu dört secret eklenir:

- `ANNE_TV_KEYSTORE_B64`
- `ANNE_TV_KEYSTORE_PASSWORD`
- `ANNE_TV_KEY_ALIAS`
- `ANNE_TV_KEY_PASSWORD`

Keystore dosyasını ayrıca güvenli bir yerde yedeklayın. Kaybolursa mevcut kuruluma aynı imzayla güncelleme verilemez.

## 3. Normal build

`main` dalına yapılan kod değişiklikleri `Build Anne TV` workflow'unu çalıştırır. Önce JSON doğrulaması ve Android Lint, ardından debug APK build edilir. APK Actions ekranındaki `AnneTV-debug` artifact'inden alınabilir.

## 4. Release

`app/build.gradle` içindeki `versionCode` ve `versionName` artırılır. Örneğin `0.2.1` için tag `v0.2.1` olmalıdır.

Tag gönderildiğinde `Release Anne TV` workflow'u:

- JSON doğrulaması ve release lint çalıştırır,
- aynı kalıcı anahtarla imzalı APK üretir,
- APK'yı `AnneTV.apk` adıyla GitHub Release'e yükler,
- SHA-256 hesaplar,
- `update.json` dosyasını `main` dalında otomatik yeniler.

Anne TV açılışta `update.json` kontrol eder. Daha yeni `versionCode` varsa güncelleme ekranını gösterir. İndirilen APK SHA-256 ile doğrulanmadan kurulum başlatılmaz.

## 5. Kanal listesi

`channels.json` değiştiğinde APK çıkarmak gerekmez. Uygulama açılışta uzak listeyi indirir ve cihazda önbelleğe alır. İnternet/GitHub erişimi yoksa en son geçerli önbellek, o da yoksa APK içindeki yedek liste kullanılır.

`streamUrl` alanlarına yalnızca kullanma hakkınız bulunan resmi/açık yayın kaynaklarını ekleyin. Kanal kimlikleri ve kanal numaraları benzersiz olmalıdır.

## 6. Yerel geliştirme

Repo GitHub Actions'ta Gradle 9.6.0 kullanır. Yerel geliştirme için Android Studio'nun uyumlu Gradle/JDK 17 ortamını kullanabilir veya sisteminizde Gradle 9.6.0 kuruluysa:

```text
gradle :app:assembleDebug -PANNE_TV_REPO=hackedmylife/anne-tv
```
