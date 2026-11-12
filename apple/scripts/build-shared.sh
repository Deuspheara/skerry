#!/bin/bash
# Builds the TwitchShared XCFramework (iOS device + simulator, macOS, tvOS device + simulator; all arm64) with Gradle and puts it
# where the Xcode project expects it: apple/Frameworks/TwitchShared.xcframework (git-ignored).
#
#   apple/scripts/build-shared.sh [debug|release]   (default: debug)
#
# Needs Xcode selected (xcode-select) and a JDK 21. The first run downloads Kotlin/Native (about 1.5 GB) and takes several minutes.
set -euo pipefail

configuration="${1:-debug}"
case "$configuration" in
  debug) task="assembleTwitchSharedDebugXCFramework"; dir="debug" ;;
  release) task="assembleTwitchSharedReleaseXCFramework"; dir="release" ;;
  *) echo "usage: $0 [debug|release]" >&2; exit 2 ;;
esac

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
./gradlew ":shared:platform-apple:${task}"

source_framework="shared/platform-apple/build/XCFrameworks/${dir}/TwitchShared.xcframework"
target_framework="apple/Frameworks/TwitchShared.xcframework"
test -d "$source_framework" || { echo "missing $source_framework" >&2; exit 1; }
mkdir -p apple/Frameworks
rm -rf "$target_framework"
cp -R "$source_framework" "$target_framework"
echo "TwitchShared ($configuration) -> $target_framework"
