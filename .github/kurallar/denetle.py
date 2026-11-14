#!/usr/bin/env python3
"""EYMEN OS kural denetimi.

docs/1.2/SARTNAME.md ve AGENTS.md kurallarının makineyle denetlenen kısmı.
Bu dosya, korunan.json ve iş akışları yalnız Muhammet Atasoy tarafından değiştirilir.

Kullanım:  python3 .github/kurallar/denetle.py
Çıkış kodu 0 = TAMAM, 1 = kural ihlali.
"""
import hashlib
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

KOK = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
KURAL = json.load(open(os.path.join(KOK, ".github", "kurallar", "korunan.json"), encoding="utf-8"))
HATALAR = []


def hata(s):
    HATALAR.append(s)


def oku(rel):
    with open(os.path.join(KOK, rel), "rb") as f:
        return f.read()


def sha(b):
    return hashlib.sha256(b).hexdigest()


def atla(rel):
    for p in KURAL["yok_say"]:
        if p.endswith("/"):
            if rel.startswith(p) or ("/" + p) in ("/" + rel):
                return True
        elif rel == p:
            return True
    return False


def dosyalar():
    out = []
    for kok, dirs, files in os.walk(KOK):
        dirs[:] = [d for d in dirs if d not in (".git", ".gradle", "build", ".idea", ".kotlin")]
        for f in files:
            rel = os.path.relpath(os.path.join(kok, f), KOK).replace(os.sep, "/")
            if not atla(rel):
                out.append(rel)
    return sorted(out)


# ------------------------------------------------------------------ Kotlin fonksiyon çıkarıcı

def _string_atla(t, i):
    """t[i] is '"'. Returns index just after the closing quote."""
    if t.startswith('"""', i):
        j = t.find('"""', i + 3)
        return len(t) if j < 0 else j + 3
    i += 1
    while i < len(t):
        c = t[i]
        if c == "\\":
            i += 2
            continue
        if c == '"':
            return i + 1
        if c == "$" and i + 1 < len(t) and t[i + 1] == "{":
            i = _blok_atla(t, i + 1)
            continue
        i += 1
    return i


def _blok_atla(t, i):
    """t[i] is '{'. Returns index just after the matching '}'."""
    derinlik = 0
    while i < len(t):
        c = t[i]
        if t.startswith("//", i):
            j = t.find("\n", i)
            i = len(t) if j < 0 else j
            continue
        if t.startswith("/*", i):
            j = t.find("*/", i + 2)
            i = len(t) if j < 0 else j + 2
            continue
        if c == '"':
            i = _string_atla(t, i)
            continue
        if c == "'":
            j = i + 1
            if j < len(t) and t[j] == "\\":
                j += 1
            k = t.find("'", j + 1)
            i = i + 1 if k < 0 else k + 1
            continue
        if c == "{":
            derinlik += 1
        elif c == "}":
            derinlik -= 1
            if derinlik == 0:
                return i + 1
        i += 1
    return i


def fonksiyon(t, ad):
    m = re.search(r"\bfun\s+(?:<[^>]*>\s*)?(?:[A-Za-z_][\w.]*\.)?" + re.escape(ad) + r"\s*\(", t)
    if not m:
        return None
    i = m.end() - 1
    derinlik = 0
    while i < len(t):
        c = t[i]
        if c == '"':
            i = _string_atla(t, i)
            continue
        if c == "(":
            derinlik += 1
        elif c == ")":
            derinlik -= 1
            if derinlik == 0:
                i += 1
                break
        i += 1
    while i < len(t) and t[i] not in "{=":
        i += 1
    if i >= len(t):
        return None
    if t[i] == "{":
        son = _blok_atla(t, i)
        return t[m.start():son]
    # expression body
    j = i + 1
    while j < len(t):
        c = t[j]
        if c == '"':
            j = _string_atla(t, j)
            continue
        if c == "{":
            j = _blok_atla(t, j)
            continue
        if c == "(":
            d = 0
            while j < len(t):
                if t[j] == '"':
                    j = _string_atla(t, j)
                    continue
                if t[j] == "(":
                    d += 1
                elif t[j] == ")":
                    d -= 1
                    if d == 0:
                        break
                j += 1
            j += 1
            continue
        if c == "\n":
            satir = t[t.rfind("\n", 0, j) + 1:j].strip()
            sonraki = t[j + 1:t.find("\n", j + 1) if t.find("\n", j + 1) >= 0 else len(t)].strip()
            if not re.search(r"(\|\||&&|[+\-*/=,(?:]|->)$", satir) and not re.match(r"^(\.|\?\.|\?:|\|\||&&)", sonraki):
                break
        j += 1
    return t[m.start():j]


def norm(s):
    return re.sub(r"\s+", "", s)


# ------------------------------------------------------------------ denetimler

def denetle_dosyalar(liste):
    taban = KURAL["taban"]
    izinli_degisen = set(KURAL["izinli_degisen"])
    izinli_yeni = set(KURAL["izinli_yeni"])
    degisen = []
    for rel in liste:
        if rel in taban:
            if sha(oku(rel)) != taban[rel]:
                if rel in izinli_degisen:
                    degisen.append(rel)
                else:
                    hata(f"DEĞİŞTİRİLMESİ YASAK dosya değişmiş: {rel}")
        else:
            if rel in izinli_yeni:
                degisen.append(rel)
            else:
                hata(f"İZİNSİZ YENİ DOSYA: {rel} (şartname bölüm 1'deki listede yok)")
    for rel in taban:
        if rel not in liste and not atla(rel):
            hata(f"SİLİNMESİ YASAK dosya silinmiş: {rel}")
    return degisen


def denetle_fonksiyonlar():
    for rel, fonks in KURAL["korunan_fonksiyonlar"].items():
        t = oku(rel).decode("utf-8")
        for ad, h in fonks.items():
            g = fonksiyon(t, ad)
            if g is None:
                hata(f"KORUNAN FONKSİYON bulunamadı (silinmiş ya da adı değişmiş): {rel} › {ad}")
            elif sha(norm(g).encode("utf-8")) != h:
                hata(f"KORUNAN FONKSİYON değişmiş: {rel} › {ad}")


def denetle_tek_satir():
    for rel, k in KURAL["tek_satir"].items():
        t = oku(rel).decode("utf-8")
        geri = t.replace(k["yeni"], k["eski"])
        if sha(geri.encode("utf-8")) != KURAL["taban"][rel]:
            hata(f"{rel}: yalnız şu satır değişebilir: {k['yeni']}")


def denetle_manifest():
    rel = "tv/src/main/AndroidManifest.xml"
    ns = "{http://schemas.android.com/apk/res/android}"
    kok = ET.fromstring(oku(rel))
    izinli = set(KURAL["izinler"])
    for e in kok.iter("uses-permission"):
        ad = e.get(ns + "name")
        if ad not in izinli:
            hata(f"Manifest: izinsiz izin {ad}")
    izinli_bil = set(KURAL["bilesenler"])
    app = kok.find("application")
    for e in list(app):
        ad = e.get(ns + "name")
        if e.tag in ("activity", "receiver", "service", "provider", "activity-alias"):
            if ad not in izinli_bil:
                hata(f"Manifest: izinsiz bileşen <{e.tag}> {ad}")
            if ad == ".YedekReceiver" and e.get(ns + "permission") != "android.permission.DUMP":
                hata("Manifest: .YedekReceiver android:permission=\"android.permission.DUMP\" olmalı")


def denetle_gradle():
    surumler = {}
    for mod, izinli in KURAL["bagimliliklar"].items():
        rel = f"{mod}/build.gradle.kts"
        t = oku(rel).decode("utf-8")
        bul = re.findall(r'(?:implementation|api|kapt|ksp|compileOnly|runtimeOnly|debugImplementation)\s*\(\s*(?:platform\()?\s*"([^"]+)"', t)
        for b in bul:
            if b not in izinli:
                hata(f"{rel}: izinsiz bağımlılık {b}")
        for b in re.findall(r'(?:implementation|api)\s*\(\s*(?:project|files|fileTree)\s*\(', t):
            hata(f"{rel}: izinsiz bağımlılık türü {b}")
        ad = re.search(r'versionName\s*=\s*"([^"]+)"', t)
        kod = re.search(r'versionCode\s*=\s*(\d+)', t)
        surumler[mod] = (ad.group(1) if ad else None, int(kod.group(1)) if kod else None)
        for k in ("storePassword", "keyAlias", "keyPassword", "storeFile", "namespace", "applicationId"):
            for v in re.findall(k + r'\s*=\s*(.+)', t):
                if v.strip() not in KURAL["gradle_sabit"][mod][k]:
                    hata(f"{rel}: {k} değiştirilemez")
    izinli_s = [tuple(x) for x in KURAL["surumler"]]
    for mod, s in surumler.items():
        if s not in izinli_s:
            hata(f"{mod}: sürüm {s} izinli değil, izinliler: {izinli_s}")
    if len(set(surumler.values())) != 1:
        hata(f"tv ve telefon sürümleri aynı olmalı: {surumler}")


def denetle_icerik(degisen):
    palet = set(KURAL["palet"])
    hostlar = set(KURAL["hostlar"])
    yasak = KURAL["yasak_ifadeler"]
    for rel in degisen:
        if not rel.endswith((".kt", ".kts", ".xml", ".txt", ".pro", ".json")):
            continue
        t = oku(rel).decode("utf-8", "replace")
        if rel.endswith(".kt"):
            for c in re.findall(r"\b0x([0-9A-Fa-f]{8})\b", t):
                if c[2:].upper() not in palet:
                    hata(f"{rel}: paletde olmayan renk 0x{c} (şartname 17.1)")
            for c in re.findall(r'"#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})"', t):
                if c[-6:].upper() not in palet:
                    hata(f"{rel}: paletde olmayan renk #{c} (şartname 17.1)")
            for h in re.findall(r"https?://([A-Za-z0-9.\-]+)", t):
                if h not in hostlar:
                    hata(f"{rel}: izinsiz internet adresi {h} (şartname 2.4)")
        for y in yasak:
            if y in t:
                hata(f"{rel}: yasak ifade '{y}'")


def denetle_adb():
    izinli = KURAL["adb_komutlari"]
    for rel in KURAL["adb_dosyalari"]:
        p = os.path.join(KOK, rel)
        if not os.path.exists(p):
            continue
        t = open(p, encoding="utf-8").read()
        for m in re.finditer(r'YerelAdb\.shell\(\s*[^,]+,\s*"([^"]*)"', t):
            if m.group(1) not in izinli:
                hata(f"{rel}: izinsiz yerel ADB komutu \"{m.group(1)}\" (şartname bölüm 3)")
        if re.search(r'YerelAdb\.shell\(\s*[^,]+,\s*[^"\s]', t):
            hata(f"{rel}: YerelAdb.shell komutu sabit metin olmalı (şartname bölüm 3)")


def main():
    liste = dosyalar()
    degisen = denetle_dosyalar(liste)
    denetle_fonksiyonlar()
    denetle_tek_satir()
    denetle_manifest()
    denetle_gradle()
    denetle_icerik(degisen)
    denetle_adb()
    if HATALAR:
        print("KURAL İHLALİ — derleme durduruldu. Kurallar: AGENTS.md ve docs/1.2/SARTNAME.md")
        for h in HATALAR:
            print("  ✗ " + h)
        print(f"{len(HATALAR)} hata. Kuralı değil, kodu düzelt.")
        sys.exit(1)
    print(f"TAMAM — {len(liste)} dosya denetlendi, {len(degisen)} izinli değişiklik.")


if __name__ == "__main__":
    main()
