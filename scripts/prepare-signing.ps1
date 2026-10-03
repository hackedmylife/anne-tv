param(
    [string]$Alias = "annetv",
    [string]$Output = "$HOME\anne-tv-release.jks"
)

$ErrorActionPreference = "Stop"
Write-Host "Anne TV imza anahtarı hazırlanıyor. Bu dosyayı kaybetmeyin ve repoya eklemeyin." -ForegroundColor Yellow

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool bulunamadı. JDK 17 kurulu olmalı."
}

$storePass = Read-Host "Keystore parolası" -AsSecureString
$keyPass = Read-Host "Key parolası" -AsSecureString
$storePlain = [System.Net.NetworkCredential]::new('', $storePass).Password
$keyPlain = [System.Net.NetworkCredential]::new('', $keyPass).Password

& keytool -genkeypair -v -keystore $Output -alias $Alias -keyalg RSA -keysize 3072 -validity 10000 `
    -storepass $storePlain -keypass $keyPlain -dname "CN=Anne TV, OU=Home, O=Anne TV, L=Local, C=TR"

$bytes = [System.IO.File]::ReadAllBytes($Output)
$b64 = [Convert]::ToBase64String($bytes)
$b64Path = "$Output.base64.txt"
[System.IO.File]::WriteAllText($b64Path, $b64)

Write-Host ""
Write-Host "Oluşturuldu: $Output" -ForegroundColor Green
Write-Host "Base64 kopyası: $b64Path" -ForegroundColor Green
Write-Host "GitHub Secrets isimleri:" -ForegroundColor Cyan
Write-Host "  ANNE_TV_KEYSTORE_B64        = base64 dosyasının içeriği"
Write-Host "  ANNE_TV_KEYSTORE_PASSWORD   = keystore parolası"
Write-Host "  ANNE_TV_KEY_ALIAS           = $Alias"
Write-Host "  ANNE_TV_KEY_PASSWORD        = key parolası"
