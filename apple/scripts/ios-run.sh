#!/bin/bash
# Builds TwitchIOS for a simulator, installs and launches it. Extra arguments are launch arguments (e.g. -demo, -demo-signed-out).
#   apple/scripts/ios-run.sh [-sim "iPhone 17"] [-demo] [-screenshot out.png]
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
sim_name="iPhone 17"; shot=""; args=()
while [ $# -gt 0 ]; do
  case "$1" in
    -sim) sim_name="$2"; shift 2 ;;
    -screenshot) shot="$2"; shift 2 ;;
    *) args+=("$1"); shift ;;
  esac
done
udid=$(xcrun simctl list devices available | grep -E "^ +${sim_name} \(" | grep Booted | head -1 | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/' || true)
[ -n "$udid" ] || udid=$(xcrun simctl list devices available | grep -E "^ +${sim_name} \(" | head -1 | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/')
xcrun simctl boot "$udid" 2>/dev/null || true
cd "$root"
xcodegen generate >/dev/null
xcodebuild -project TwitchPlayerApple.xcodeproj -scheme TwitchIOS -destination "id=$udid" -derivedDataPath build/dd build -quiet
xcrun simctl install "$udid" build/dd/Build/Products/Debug-iphonesimulator/TwitchIOS.app
xcrun simctl terminate "$udid" com.twitchplayer.ios 2>/dev/null || true
xcrun simctl launch "$udid" com.twitchplayer.ios "${args[@]}"
if [ -n "$shot" ]; then sleep 4; xcrun simctl io "$udid" screenshot "$shot"; fi
