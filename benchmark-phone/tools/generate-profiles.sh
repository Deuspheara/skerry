#!/usr/bin/env bash
# Regenerates the phone's Baseline Profile and startup profile into phone/src/main/baselineProfiles/ (runs benchmark-phone's
# BaselineProfileGenerator on the non-minified, demo-data build, on ONE emulator). Same script as the TV's, with the phone's settings.
#
# Usage: benchmark-phone/tools/generate-profiles.sh [serial]   (default emulator-5554; API 33+ or rooted)
set -euo pipefail
cd "$(dirname "$0")/../.."
export BENCH_MODULE=benchmark-phone
export TEST_PACKAGE=com.twitchplayer.phone.benchmark
export PROFILE_DEST=phone/src/main/baselineProfiles
export EXTRA_RULES=benchmark-phone/tools/extra-profile-rules.txt
exec benchmark/tools/generate-profiles.sh "$@"
