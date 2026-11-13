#!/bin/bash
# Records the slow drag test and cuts frames from the recording: apple/scripts/drag-frames.sh <udid> <out dir> [appearance]
set -euo pipefail
udid="$1"; out="$2"; mode="${3:-light}"
cd "$(dirname "$0")/.."
mkdir -p "$out"
xcrun simctl boot "$udid" 2>/dev/null || true
xcrun simctl ui "$udid" appearance "$mode"
xcodegen generate >/dev/null
xcodebuild build-for-testing -project TwitchPlayerApple.xcodeproj -scheme TwitchIOSPerf -destination "id=$udid" -derivedDataPath build/dd3 -quiet 2>&1 | grep -E "error: " | grep -v state_retry || true
rec="$out/$mode-drag.mp4"
xcrun simctl io "$udid" recordVideo --codec h264 --force "$rec" >/dev/null 2>&1 &
pid=$!
xcodebuild test-without-building -project TwitchPlayerApple.xcodeproj -scheme TwitchIOSPerf -destination "id=$udid" -derivedDataPath build/dd3 -only-testing:TwitchIOSUITests/DragFramesUITests 2>&1 | grep -E "Executed|TEST (SUCC|FAIL)" | tail -1
kill -INT $pid 2>/dev/null || true; sleep 3
ffmpeg -loglevel error -y -i "$rec" -vf "fps=6,scale=300:-1" "$out/$mode-drag-%03d.png"
ls "$out" | grep -c "$mode-drag-.*png"
