#!/usr/bin/env bash
# Builds the release APK, signs it with the debug key and installs it on one device together with its Baseline
# Profile (.dm), so ART compiles the hot code at install time ("speed-profile") instead of running from the APK.
#
# Why: a sideloaded app is not compiled by Play's cloud profiles, and the background dexopt job that would use the
# profile ProfileInstaller writes only runs when the device is idle (on the TCL TV it never had: status was
# run-from-apk, cold start ~1.9 s; compiled: ~0.38 s).
#
# Usage: ANDROID_SERIAL=192.168.1.23:5555 benchmark/tools/install-release.sh [--no-build]
set -euo pipefail
cd "$(dirname "$0")/../.."

: "${ANDROID_SERIAL:?set ANDROID_SERIAL to the target device (adb devices)}"
PKG=com.twitchplayer.tv
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
OUT=app/build/outputs/apk/release
WORK=app/build/install-release
KS="$HOME/.android/debug.keystore"

[[ "${1:-}" == "--no-build" ]] || ./gradlew -q :app:assembleRelease

rm -rf "$WORK"; mkdir -p "$WORK"
"$BT/zipalign" -f -p 4 "$OUT/app-release-unsigned.apk" "$WORK/app.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android "$WORK/app.apk"

# The profile goes next to the APK with the same base name; baselineProfiles/0 is the one for API 31+, 1 for 28-30.
sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
dm=""
if (( sdk >= 31 )); then dm="$OUT/baselineProfiles/0/app-release-unsigned.dm"
elif (( sdk >= 28 )); then dm="$OUT/baselineProfiles/1/app-release-unsigned.dm"; fi

if [[ -n "$dm" && -f "$dm" ]]; then
  cp "$dm" "$WORK/app.dm"
  adb install-multiple --no-incremental -r "$WORK/app.apk" "$WORK/app.dm"
else
  adb install -r "$WORK/app.apk"
fi

# Older devices (and any install that skipped dexopt) get compiled explicitly against the installed profile.
status=$(adb shell dumpsys package dexopt | grep -A2 "^  \[$PKG\]" | grep -o 'status=[a-z-]*' | head -1)
if [[ "$status" != "status=speed-profile" && "$status" != "status=speed" ]]; then
  echo "dexopt after install: ${status:-unknown}; compiling with the profile"
  adb shell am start -W -n "$PKG/.MainActivity" >/dev/null; sleep 3 # lets ProfileInstaller write the profile
  adb shell cmd package compile -m speed-profile -f "$PKG" >/dev/null
  status=$(adb shell dumpsys package dexopt | grep -A2 "^  \[$PKG\]" | grep -o 'status=[a-z-]*' | head -1)
fi
echo "installed on $ANDROID_SERIAL: $status"
