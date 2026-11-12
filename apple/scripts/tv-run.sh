#!/bin/bash
# Builds TwitchTV for the Apple TV simulator, installs and launches it. Extra arguments are launch arguments (e.g. -demo, -section browse, -open lunaria).
#   apple/scripts/tv-run.sh [-sim "Apple TV 4K (3rd generation) (at 1080p)"] [-demo] [-screenshot out.png] [-wait 6]
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
sim_name="Apple TV 4K (3rd generation) (at 1080p)"; shot=""; wait_s=6; args=()
while [ $# -gt 0 ]; do
  case "$1" in
    -sim) sim_name="$2"; shift 2 ;;
    -screenshot) shot="$2"; shift 2 ;;
    -wait) wait_s="$2"; shift 2 ;;
    *) args+=("$1"); shift ;;
  esac
done
udid=$(xcrun simctl list devices available | grep -F "    ${sim_name} (" | head -1 | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/')
xcrun simctl boot "$udid" 2>/dev/null || true
cd "$root"
xcodegen generate >/dev/null
xcodebuild -project TwitchPlayerApple.xcodeproj -scheme TwitchTV -destination "id=$udid" -derivedDataPath build/dd build -quiet
xcrun simctl install "$udid" build/dd/Build/Products/Debug-appletvsimulator/TwitchTV.app
xcrun simctl terminate "$udid" com.twitchplayer.tv 2>/dev/null || true; sleep 2
xcrun simctl launch "$udid" com.twitchplayer.tv "${args[@]}"
if [ -n "$shot" ]; then sleep "$wait_s"; xcrun simctl io "$udid" screenshot "$shot"; fi
