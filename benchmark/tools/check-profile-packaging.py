#!/usr/bin/env python3
"""Checks that a module's Baseline Profile and startup profile really ended up in its release build.

  benchmark/tools/check-profile-packaging.py <module> <app-name>        e.g.  phone phone   |   app app

For the release APK and AAB of <module> (assembleRelease and bundleRelease must have run) it verifies
  1. assets/dexopt/baseline.prof is in the APK and BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof in the AAB;
  2. the profileinstaller classes and its ProfileInstallReceiver are in the APK;
  3. the startup profile's classes (mapped through R8's mapping.txt) sit in classes.dex, the first dex file, and how many fell elsewhere;
Exit code 1 on a failed hard check (at least 95 % of the surviving startup classes must be in classes.dex). R8 settings and the
absence of -keep rules are checked by CI's build-release job and by `grep` in docs/perf-phone.md, not here."""
import re
import subprocess
import sys
import zipfile
from pathlib import Path

module, app = sys.argv[1], sys.argv[2]
root = Path(__file__).resolve().parents[2]
apk = next((root / module / "build/outputs/apk/release").glob("*-release-unsigned.apk"))
aab = next((root / module / "build/outputs/bundle/release").glob("*.aab"))
mapping = root / module / "build/outputs/mapping/release/mapping.txt"
startup = root / module / "src/main/baselineProfiles/startup-prof.txt"
baseline = root / module / "src/main/baselineProfiles/baseline-prof.txt"
failed = False


def check(name, ok, detail=""):
    global failed
    print(f"{'pass' if ok else 'FAIL'}  {name} {detail}".rstrip())
    failed |= not ok


names = zipfile.ZipFile(apk).namelist()
check("APK has assets/dexopt/baseline.prof", "assets/dexopt/baseline.prof" in names,
      f"({zipfile.ZipFile(apk).getinfo('assets/dexopt/baseline.prof').file_size:,} B)" if "assets/dexopt/baseline.prof" in names else "")
check("APK has assets/dexopt/baseline.profm", "assets/dexopt/baseline.profm" in names)
check("AAB has the baseline profile", "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof" in zipfile.ZipFile(aab).namelist())

# Rule counts.
for label, path in (("baseline", baseline), ("startup", startup)):
    lines = path.read_text().splitlines()
    print(f"info  {label}-prof.txt: {len(lines):,} rules, {sum('com/twitchplayer/' + app in l for l in lines):,} for com/twitchplayer/{app}, "
          f"{sum('com/twitchplayer/tv' in l for l in lines):,} for the shared com/twitchplayer/tv code")

# profileinstaller: its classes are in some dex, its receiver is declared in the merged manifest.
sdk = Path.home() / "Library/Android/sdk/build-tools"
bt = sorted(sdk.iterdir(), key=lambda p: [int(x) for x in re.findall(r"\d+", p.name)])[-1]
dexdump = bt / "dexdump"


def dex_classes(dex_name):
    data = zipfile.ZipFile(apk).read(dex_name)
    tmp = root / module / "build" / dex_name
    tmp.write_bytes(data)
    out = subprocess.run([str(dexdump), str(tmp)], capture_output=True, text=True).stdout
    return set(re.findall(r"Class descriptor\s+:\s+'([^']+)'", out)), out


dexes = sorted(n for n in names if re.fullmatch(r"classes\d*\.dex", n))
per_dex = {}
raw = {}
for d in dexes:
    per_dex[d], raw[d] = dex_classes(d)
all_classes = set().union(*per_dex.values())
check("profileinstaller classes in the APK", any(c.startswith("Landroidx/profileinstaller/") for c in all_classes))
badging = subprocess.run([str(bt / "aapt2"), "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)], capture_output=True, text=True).stdout
check("ProfileInstallReceiver declared in the manifest", "androidx.profileinstaller.ProfileInstallReceiver" in badging)

# Startup classes by their R8 names.
to_new = {}
cur = None
for line in mapping.read_text().splitlines():
    if line.startswith("#") or not line.strip():
        continue
    if not line.startswith(" ") and line.endswith(":"):
        old, new = line[:-1].split(" -> ")
        to_new["L" + old.replace(".", "/") + ";"] = "L" + new.replace(".", "/") + ";"
wanted = set()
for line in startup.read_text().splitlines():
    m = re.match(r"^[HSP]*(L[^;]+;)", line)
    if m:
        wanted.add(m.group(1))
survivors = {to_new.get(c, c) for c in wanted if to_new.get(c, c) in all_classes}
in_first = sum(c in per_dex[dexes[0]] for c in survivors)
share = in_first / max(len(survivors), 1)
check("startup classes in classes.dex", share >= 0.95,
      f"({in_first:,} of {len(survivors):,} surviving startup classes = {share:.1%}; the profile names {len(wanted):,}; dex files: "
      + ", ".join(f"{d} {len(per_dex[d]):,} classes {zipfile.ZipFile(apk).getinfo(d).file_size:,} B" for d in dexes) + ")")
print(f"info  APK {apk.stat().st_size:,} B, AAB {aab.stat().st_size:,} B")
sys.exit(1 if failed else 0)
