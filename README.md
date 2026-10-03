# Anne TV 0.2.0

Android TV için sade, kumanda odaklı internet TV uygulaması. Hedef, düşük RAM'li Android TV'lerde "aç ve izle" deneyimidir; arayüz native View tabanlıdır ve oynatma Media3/ExoPlayer ile yapılır.

## Bu sürümde

- Tam ekran TV deneyimi
- CH+/CH- ve yön tuşlarıyla kanal değiştirme
- 0-9 ile kanal numarası girme
- OK / Enter ile kanal listesi
- Son izlenen kanalı hatırlama
- Yayın hatasında 1/2/4/8/15 saniyelik yeniden bağlanma
- 28 kanallık başlangıç manifesti + favoriler
- APK içinde yedek kanal listesi
- GitHub `channels.json` ile uzaktan kanal güncelleme
- GitHub `update.json` + Releases ile uygulama güncelleme
- İndirilen APK için SHA-256 doğrulaması
- GitHub Actions JSON doğrulama + Android Lint + debug/release build

## Kanal kaynağı

`channels.json` içindeki `streamUrl` alanları yalnızca resmi/açık ve kullanma hakkı bulunan yayın adresleri için ayrılmıştır. APK'nın veya repo'nun içinde gizli token, parola ya da servis anahtarı tutulmaz.

Geliştirme sırasında oynatıcıyı kontrol etmek için `99 - Test Yayını` bulunur. Gerçek yayınlar bağlandığında bu satır kaldırılacaktır.

## Kumanda

- `CH+`, `↑`, `→`: sonraki kanal
- `CH-`, `↓`, `←`: önceki kanal
- `OK`, `Enter`, `Menu`: kanal listesi
- `0-9`: kanal numarası
- `Back`: kanal listesi açıksa kapatır; kapalıysa Android'in normal geri davranışı

## GitHub

Ayrıntılı kurulum: `GITHUB_SETUP.md`

Repo public olmalıdır; TV kanal ve APK güncellemelerini GitHub'dan kimlik doğrulamasız alır. GitHub Actions derlemesinde repo adresi APK'ya otomatik gömülür.

## Sürüm

- applicationId: `com.annetv.app`
- minSdk: 23
- targetSdk: 36
- versionCode: 2
- versionName: `0.2.0`
