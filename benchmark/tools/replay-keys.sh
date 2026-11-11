#!/bin/bash
# Replays D-pad key codes <repeat> times (one adb shell, 100 ms apart), then prints per-frame stats.
# usage: replay-keys.sh <repeat> <keycode>...      (19 up, 20 down, 21 left, 22 right, 23 ok, 4 back)
# Run on the :app "benchmark" build (R8, demo data): ./gradlew :app:assembleBenchmark && adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
PKG=com.twitchplayer.tv
HERE="$(cd "$(dirname "$0")" && pwd)"
N=$1; shift
adb shell dumpsys gfxinfo $PKG reset >/dev/null
CMD=""
for ((n = 0; n < N; n++)); do for k in "$@"; do CMD="$CMD input keyevent $k; sleep 0.1;"; done; done
adb shell "$CMD"; sleep 0.8
python3 -I "$HERE/framestats.py"
