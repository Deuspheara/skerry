#!/bin/bash
# Real-touch measurement of the player sheet's drag: real live channel, fast fictional chat, XCUITest touches, the app logs each drag and settle's frame cadence.
#   apple/scripts/measure-drag.sh <simulator udid> [Debug|Profile] [channel]
set -euo pipefail
udid="$1"; config="${2:-Debug}"; channel="${3:-}"
cd "$(dirname "$0")/.."
[ -n "$channel" ] || channel=$(curl -s -H "Client-ID: $PLAYBACK_CLIENT_ID" -d '{"query":"{streams(first:3){edges{node{broadcaster{login}}}}}"}' https://gql.twitch.tv/gql | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['streams']['edges'][0]['node']['broadcaster']['login'])")
echo "channel: $channel, configuration: $config"
xcrun simctl boot "$udid" 2>/dev/null || true
xcodegen generate >/dev/null
old=$(xcrun simctl get_app_container "$udid" com.twitchplayer.ios data 2>/dev/null || true)
[ -z "$old" ] || rm -f "$old/Library/Caches/framemon.txt"
# A previous run's file would be mixed in: remove it (the container exists once the app was installed).
# The presented frames are recorded too (what reaches the screen, GPU and render server included): scripts/analyze-recording.py reads them.
record="${RECORD:-/tmp/drag-$config.mp4}"
xcrun simctl io "$udid" recordVideo --codec h264 --force "$record" >/dev/null 2>&1 &
recorder=$!
trap 'kill -INT $recorder 2>/dev/null || true' EXIT
xcodebuild test -project TwitchPlayerApple.xcodeproj -scheme TwitchIOSPerf -configuration "$config" -destination "id=$udid" -derivedDataPath "build/dd-perf-$config" \
  -only-testing:TwitchIOSUITests TEST_RUNNER_PERF_CHANNEL="$channel" 2>&1 | grep -E "Test Case|error:|TEST (SUCCEEDED|FAILED)" || true
kill -INT $recorder 2>/dev/null || true; sleep 3
echo "recording: $record"
container=$(xcrun simctl get_app_container "$udid" com.twitchplayer.ios data)
echo "--- $container/Library/Caches/framemon.txt"
cat "$container/Library/Caches/framemon.txt"
