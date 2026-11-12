#!/bin/bash
# Builds TwitchMac (Debug) and runs it. With -snapshot <file.png> the app renders its window to a PNG in its sandbox container and quits; this script
# copies the PNG next to the name you gave. Usage:
#   apple/scripts/mac-run.sh -demo                                  # run on the offline demo data
#   apple/scripts/mac-run.sh -demo -snapshot /tmp/home.png          # screenshot (extra: -snapshot-delay 6, -snapshot-size 1280x800, -tab, -push, -open, -search)
set -euo pipefail
cd "$(dirname "$0")/.."
xcodegen generate >/dev/null
xcodebuild -project TwitchPlayerApple.xcodeproj -scheme TwitchMac -destination 'platform=macOS' -quiet build 2>&1 | grep -E "error:|BUILD" || true
APP="$(xcodebuild -project TwitchPlayerApple.xcodeproj -scheme TwitchMac -destination 'platform=macOS' -showBuildSettings 2>/dev/null | awk '/ BUILT_PRODUCTS_DIR /{print $3}')/TwitchMac.app"
SNAP=""
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do [ "${args[$i]}" = "-snapshot" ] && SNAP="${args[$((i + 1))]}"; done
if [ -n "$SNAP" ]; then
  CONTAINER="$HOME/Library/Containers/com.twitchplayer.mac/Data/tmp"
  rm -f "$CONTAINER/$(basename "$SNAP")"
  "$APP/Contents/MacOS/TwitchMac" "$@" 2>&1 | grep -E "snapshot" || true
  cp "$CONTAINER/$(basename "$SNAP")" "$SNAP" && echo "wrote $SNAP"
else
  exec "$APP/Contents/MacOS/TwitchMac" "$@"
fi
