#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
errors = []

def fail(msg):
    errors.append(msg)

def valid_http_url(value):
    parsed = urlparse(value)
    return parsed.scheme in {"http", "https"} and bool(parsed.netloc)

paths = [ROOT / "channels.json", ROOT / "app/src/main/assets/channels.json"]
parsed = []
for path in paths:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"{path.relative_to(ROOT)}: geçersiz JSON: {exc}")
        continue
    parsed.append((path, data))
    if data.get("schemaVersion") != 1:
        fail(f"{path.relative_to(ROOT)}: schemaVersion 1 olmalı")
    channels = data.get("channels")
    if not isinstance(channels, list) or not channels:
        fail(f"{path.relative_to(ROOT)}: channels boş olamaz")
        continue

    ids, numbers = set(), set()
    for idx, channel in enumerate(channels):
        label = f"{path.relative_to(ROOT)} channels[{idx}]"
        if not isinstance(channel, dict):
            fail(f"{label}: nesne olmalı")
            continue
        cid = str(channel.get("id", "")).strip()
        name = str(channel.get("name", "")).strip()
        number = channel.get("number")
        primary = str(channel.get("streamUrl", "")).strip()
        fallbacks = channel.get("streamUrls", [])

        if not cid or not re.fullmatch(r"[a-z0-9._-]+", cid):
            fail(f"{label}: geçersiz id '{cid}'")
        elif cid in ids:
            fail(f"{label}: tekrar eden id '{cid}'")
        ids.add(cid)

        if not isinstance(number, int) or number < 1 or number > 999:
            fail(f"{label}: number 1..999 aralığında tamsayı olmalı")
        elif number in numbers:
            fail(f"{label}: tekrar eden kanal numarası {number}")
        numbers.add(number)

        if not name:
            fail(f"{label}: name boş olamaz")

        if primary and not valid_http_url(primary):
            fail(f"{label}: streamUrl http/https URL olmalı")

        if fallbacks is not None and not isinstance(fallbacks, list):
            fail(f"{label}: streamUrls liste olmalı")
        elif isinstance(fallbacks, list):
            seen_urls = set()
            for url_index, raw_url in enumerate(fallbacks):
                url = str(raw_url).strip()
                if not url or not valid_http_url(url):
                    fail(f"{label}: streamUrls[{url_index}] geçerli http/https URL olmalı")
                elif url in seen_urls:
                    fail(f"{label}: tekrar eden streamUrls değeri '{url}'")
                seen_urls.add(url)
            if primary and fallbacks and primary != str(fallbacks[0]).strip():
                fail(f"{label}: geriye uyumluluk için streamUrl, streamUrls[0] ile aynı olmalı")

if len(parsed) == 2 and parsed[0][1] != parsed[1][1]:
    fail("channels.json ile app/src/main/assets/channels.json aynı değil")

try:
    update = json.loads((ROOT / "update.json").read_text(encoding="utf-8"))
    if not isinstance(update.get("versionCode"), int) or update["versionCode"] < 1:
        fail("update.json: versionCode pozitif tamsayı olmalı")
    if not str(update.get("versionName", "")).strip():
        fail("update.json: versionName boş olamaz")
    apk_url = str(update.get("apkUrl", "")).strip()
    if apk_url:
        parsed_url = urlparse(apk_url)
        if parsed_url.scheme != "https" or not parsed_url.netloc:
            fail("update.json: apkUrl HTTPS URL olmalı")
    sha = str(update.get("sha256", "")).strip()
    if sha and not re.fullmatch(r"[0-9a-fA-F]{64}", sha):
        fail("update.json: sha256 64 hex karakter olmalı")
except Exception as exc:
    fail(f"update.json: geçersiz JSON: {exc}")

build_gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
vc = re.search(r"\bversionCode\s+(\d+)", build_gradle)
vn = re.search(r"\bversionName\s+'([^']+)'", build_gradle)
if vc and vn:
    try:
        update = json.loads((ROOT / "update.json").read_text(encoding="utf-8"))
        if int(update.get("versionCode", -1)) > int(vc.group(1)):
            fail("update.json versionCode uygulamadan büyük olamaz")
    except Exception:
        pass
else:
    fail("app/build.gradle sürüm bilgisi okunamadı")

if errors:
    print("Anne TV proje doğrulaması başarısız:")
    for item in errors:
        print(f" - {item}")
    sys.exit(1)

print("Anne TV proje doğrulaması OK")
