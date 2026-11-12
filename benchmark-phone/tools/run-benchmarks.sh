#!/usr/bin/env bash
# Runs the phone's startup and frame-timing benchmarks (not the profile generator) on ONE emulator and keeps the raw JSON.
#
# Usage: benchmark-phone/tools/run-benchmarks.sh <label> [serial]     e.g. run1  ->  docs/perf-data/phone-run1.json
# Then:  benchmark/tools/summarize-benchmarks.py docs/perf-data/phone-run1.json docs/perf-data/phone-run2.json
#
# Gradle only builds and installs the two APKs; its daemons are then stopped and the tests are driven with `am instrument`: a Gradle
# daemon holds gigabytes, and on a memory-starved development machine the emulator's gestures degrade into taps and the numbers into noise.
# Emulator numbers are indicative (host GPU pinned to vsync, noisy CPU): compare two runs, never one.
set -euo pipefail
cd "$(dirname "$0")/../.."
label="${1:?label, e.g. run1}"
export ANDROID_SERIAL="${2:-${ANDROID_SERIAL:-emulator-5554}}"
adb get-state >/dev/null

./gradlew -q :phone:installBenchmark :benchmark-phone:installBenchmark
./gradlew --stop >/dev/null

media=/sdcard/Android/media/com.twitchplayer.phone.benchmark
adb shell rm -rf "$media"/*
adb shell am force-stop com.twitchplayer.phone
status=0
adb shell am instrument -w \
  -e androidx.benchmark.suppressErrors EMULATOR \
  -e notClass com.twitchplayer.phone.benchmark.BaselineProfileGenerator \
  com.twitchplayer.phone.benchmark/androidx.test.runner.AndroidJUnitRunner | tee "/tmp/phone-$label-instrument.log" | grep -E "^(OK|FAILURES|Tests run)|^[0-9]+\) " || status=$?
grep -q "^OK (" "/tmp/phone-$label-instrument.log" || status=1

mkdir -p docs/perf-data
adb pull "$media/com.twitchplayer.phone.benchmark-benchmarkData.json" "docs/perf-data/phone-$label.json" >/dev/null
echo "kept docs/perf-data/phone-$label.json (instrumentation exit status $status)"
exit "$status"
