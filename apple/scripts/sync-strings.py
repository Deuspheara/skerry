#!/usr/bin/env python3
"""Generates apple/Resources/Localizable.xcstrings from the Android string resources (the single source of the translations).

    python3 apple/scripts/sync-strings.py            # write the catalog
    python3 apple/scripts/sync-strings.py --check    # exit 1 when the committed catalog is out of date

Sources: app/src/main/res/values*/strings.xml (the 36 TV locales, 156 keys) and phone/src/main/res/values*/phone_strings.xml (phone-only
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
                entries[name] = {item.get("quantity"): convert("".join(item.itertext())) for item in node.findall("item")}
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
    return {"sourceLanguage": "en", "version": "1.0", "strings": dict(sorted(catalog.items()))}


def main():
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
