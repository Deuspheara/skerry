#!/usr/bin/env bash
# Frame timing of the side-chat expand / collapse on a REAL device with a real stream (what the Macrobenchmark cannot
# do: its build runs on demo data, where the WebView player has no page). Compare the two engines by running it once
# per engine: Settings > Player flips the setting (or install with -PplayerEngine=webview on a fresh install).
#
# Opens the first card of Home (OK), waits for playback, wakes the control bar, walks to the Chat button and presses OK
# <toggles> times (compact -> large -> overlay -> hidden -> ...), 2.2 s apart, while framestats.py samples the frames.
# Prints P50 / P90 / P95 / P99 for the UI thread, the RenderThread and vsync->done, and the share of missed deadlines
# (the "janky frames" %), then leaves the player with Back.
#
# Start from Home with focus on the first card of the first row. Never run against a device whose Settings you care
# about while someone else holds the remote.
#
# Usage: ANDROID_SERIAL=192.168.1.23:5555 benchmark/tools/chat-toggle.sh [toggles=6]
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${ANDROID_SERIAL:?set ANDROID_SERIAL to the target device (adb devices)}"
PKG=com.twitchplayer.tv
TOGGLES="${1:-6}"
WATCH=$(( TOGGLES * 3 + 6 ))

adb shell "input keyevent 23"; sleep 9                       # open the stream, let playback settle
adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
python3 -I benchmark/tools/framestats.py --watch "$WATCH" &
STATS=$!
sleep 1
# One adb shell for the whole sequence: a first key after idle only wakes the bar, and separate calls drift.
adb shell "input keyevent 22; sleep 0.4; for i in 1 2 3 4 5; do input keyevent 22; sleep 0.2; done; sleep 0.8; for i in \$(seq $TOGGLES); do input keyevent 23; sleep 2.2; done"
wait "$STATS"
adb shell "input keyevent 4; sleep 0.5; input keyevent 4; sleep 0.5; input keyevent 4; sleep 0.5; input keyevent 4"
