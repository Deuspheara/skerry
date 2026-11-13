#!/usr/bin/env python3
"""Generates apple/Resources/Localizable.xcstrings from the Android string resources (the single source of the translations).

    python3 apple/scripts/sync-strings.py            # write the catalog
    python3 apple/scripts/sync-strings.py --check    # exit 1 when the committed catalog is out of date
    python3 apple/scripts/sync-strings.py --report   # which keys exist only in some locales (the rest fall back to English)

Sources: apple/Resources/AppleStrings.json (strings only the Apple apps have, in en/fr/ar), app/src/main/res/values*/strings.xml (the 36 TV locales, 156 keys) and phone/src/main/res/values*/phone_strings.xml (phone-only
strings, English plus French and Arabic; the other locales fall back to English, as on Android).
Key mapping: an Android name `player_pause` is the catalog key `player_pause`; Swift reads it with `String(localized: "player_pause")`
or `Text("player_pause")`. Format arguments: `%s` -> `%@`, `%1$s` -> `%1$@`, `%d` and `%1$d` unchanged; `\\'` and `\\"` are unescaped.
Locale mapping: Android resource qualifier -> BCP 47, see LOCALES below.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "apple" / "Resources" / "Localizable.xcstrings"
APPLE_ONLY = ROOT / "apple" / "Resources" / "apple-strings.json"

SOURCES = [
    (ROOT / "app/src/main/res", "strings.xml"),
    (ROOT / "phone/src/main/res", "phone_strings.xml"),
]

# Android qualifier (folder suffix) -> Apple language identifier. "" is the default (English).
LOCALES = {
    "": "en",
    "b+fil": "fil",
    "in": "id",
    "iw": "he",
    "pt-rPT": "pt-PT",
    "zh-rCN": "zh-Hans",
    "zh-rTW": "zh-Hant",
}


def apple_locale(qualifier: str) -> str:
    return LOCALES.get(qualifier, qualifier.replace("-r", "-"))


def convert(text: str) -> str:
    text = text.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").replace("\\@", "@").replace("\\?", "?")
    # Positional and plain Android format specifiers -> Apple ones. Literal percent signs stay %%.
    text = re.sub(r"%(\d+\$)?s", lambda m: "%" + (m.group(1) or "") + "@", text)
    return text


def plural_format(text: str) -> str:
    """Plural variations take the count as a 64-bit integer: Android's `%d` / `%1$d` become `%lld` (Swift passes an `Int`)."""
    return re.sub(r"%(\d+\$)?d", "%lld", text)


def read(directory: Path, filename: str):
    """{ qualifier: { key: str | {plural: str} } } for one resource folder."""
    result = {}
    for folder in sorted(directory.glob("values*")):
        qualifier = folder.name[len("values"):].lstrip("-")
        file = folder / filename
        if not file.exists():
            continue
        entries = {}
        for node in ET.parse(file).getroot():
            if node.get("translatable") == "false":
                continue
            name = node.get("name")
            if node.tag == "string":
                entries[name] = convert("".join(node.itertext()))
            elif node.tag == "plurals":
                entries[name] = {item.get("quantity"): plural_format(convert("".join(item.itertext()))) for item in node.findall("item")}
        result[qualifier] = entries
    return result


def build():
    catalog = {}
    for directory, filename in SOURCES:
        by_locale = read(directory, filename)
        for qualifier, entries in by_locale.items():
            locale = apple_locale(qualifier)
            for key, value in entries.items():
                item = catalog.setdefault(key, {"extractionState": "manual", "localizations": {}})
                if isinstance(value, dict):
                    item["localizations"][locale] = {"variations": {"plural": {q: {"stringUnit": {"state": "translated", "value": v}} for q, v in value.items()}}}
                else:
                    item["localizations"][locale] = {"stringUnit": {"state": "translated", "value": value}}
    # Strings only the Mac and Apple TV apps use (menus, sidebar, remote hints): apple/Resources/apple-strings.json, English plus French and Arabic.
    for key, by_locale in json.loads(APPLE_ONLY.read_text()).items():
        item = catalog.setdefault(key, {"extractionState": "manual", "localizations": {}})
        for locale, value in by_locale.items():
            item["localizations"][locale] = {"stringUnit": {"state": "translated", "value": value}}
    # Apple-only strings (the Android resources have no counterpart): apple/Resources/AppleStrings.json, { key: { locale: text } }.
    extra = ROOT / "apple" / "Resources" / "AppleStrings.json"
    if extra.exists():
        for key, texts in json.loads(extra.read_text()).items():
            item = catalog.setdefault(key, {"extractionState": "manual", "localizations": {}})
            for locale, text in texts.items():
                item["localizations"][locale] = {"stringUnit": {"state": "translated", "value": text}}
    return {"sourceLanguage": "en", "version": "1.0", "strings": dict(sorted(catalog.items()))}


def report():
    """Which keys exist only in some locales (the others fall back to English): printed as `locales: count` and the keys."""
    catalog = build()["strings"]
    all_locales = sorted({loc for item in catalog.values() for loc in item["localizations"]})
    groups = {}
    for key, item in catalog.items():
        groups.setdefault(tuple(sorted(item["localizations"])), []).append(key)
    for locales, keys in sorted(groups.items(), key=lambda kv: -len(kv[1])):
        label = "all %d locales" % len(all_locales) if len(locales) == len(all_locales) else ", ".join(locales)
        print(f"{label}: {len(keys)} keys")
        if len(locales) != len(all_locales):
            print("  " + ", ".join(sorted(keys)))


def main():
    if "--report" in sys.argv:
        report()
        return
    data = json.dumps(build(), ensure_ascii=False, indent=2, sort_keys=False) + "\n"
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text() != data:
            print(f"{OUT.relative_to(ROOT)} is out of date: run apple/scripts/sync-strings.py", file=sys.stderr)
            sys.exit(1)
        return
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(data)
    catalog = json.loads(data)["strings"]
    locales = {loc for item in catalog.values() for loc in item["localizations"]}
    print(f"{len(catalog)} keys, {len(locales)} locales -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
