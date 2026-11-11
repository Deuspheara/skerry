#!/usr/bin/env bash
# Regenerates the Baseline Profile and the startup profile, then copies them where AGP reads them
# (app/src/main/baselineProfiles/). Runs :benchmark's BaselineProfileGenerator on the non-minified, demo-data build.
#
# ANDROID_SERIAL limits the run to ONE device: Gradle's connected tasks otherwise install on every adb device, the
# TV included (which would replace the real release build and its login with demo data). Default: the emulator.
#
# Usage: benchmark/tools/generate-profiles.sh [serial]   (default emulator-5554; needs API 28+, rooted or API 33+)
#
# The same script serves the phone: benchmark-phone/tools/generate-profiles.sh sets BENCH_MODULE, TEST_PACKAGE, PROFILE_DEST and
# EXTRA_RULES, then runs this file. Defaults below are the TV's.
set -euo pipefail
cd "$(dirname "$0")/../.."

export ANDROID_SERIAL="${1:-${ANDROID_SERIAL:-emulator-5554}}"
adb -s "$ANDROID_SERIAL" get-state >/dev/null

BENCH_MODULE="${BENCH_MODULE:-benchmark}"
TEST_PACKAGE="${TEST_PACKAGE:-com.twitchplayer.tv.benchmark}"
DEST="${PROFILE_DEST:-app/src/main/baselineProfiles}"
EXTRA_RULES="${EXTRA_RULES:-benchmark/tools/extra-profile-rules.txt}"

OUT="$BENCH_MODULE/build/outputs/connected_android_test_additional_output/nonMinified"
rm -rf "$OUT"
./gradlew -q ":$BENCH_MODULE:connectedNonMinifiedAndroidTest" \
  -Pandroid.testInstrumentationRunnerArguments.class="$TEST_PACKAGE.BaselineProfileGenerator"

# File names carry no timestamp in their newest copy: BaselineProfileGenerator_<test>-<kind>-prof.txt.
startup=$(find "$OUT" -name 'BaselineProfileGenerator_startup-startup-prof.txt' | head -1)
journeys=$(find "$OUT" -name 'BaselineProfileGenerator_journeys-baseline-prof.txt' | head -1)
[[ -f "$startup" && -f "$journeys" ]] || { echo "profile files not found under $OUT" >&2; ls -R "$OUT" >&2; exit 1; }

cp "$startup" "$DEST/startup-prof.txt"
# The Baseline Profile (AOT) covers startup, the journeys and the hand-written rules for code the demo run cannot reach.
sort -u "$startup" "$journeys" "$EXTRA_RULES" > "$DEST/baseline-prof.txt"
wc -l "$DEST/baseline-prof.txt" "$DEST/startup-prof.txt"
