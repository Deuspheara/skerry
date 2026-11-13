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
  debug) task="assembleTwitchSharedDebugXCFramework"; dir="debug"; flags="" ;;
  # The release framework leaves the offline demo (fictional data for screenshots and tests) out.
  release) task="assembleTwitchSharedReleaseXCFramework"; dir="release"; flags="-PappleDemo=false -Dorg.gradle.jvmargs=-Xmx4g" ;;
  *) echo "usage: $0 [debug|release]" >&2; exit 2 ;;
esac

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
./gradlew ":shared:platform-apple:${task}" ${flags}

source_framework="shared/platform-apple/build/XCFrameworks/${dir}/TwitchShared.xcframework"
target_framework="apple/Frameworks/TwitchShared.xcframework"
test -d "$source_framework" || { echo "missing $source_framework" >&2; exit 1; }
mkdir -p apple/Frameworks
rm -rf "$target_framework"
cp -R "$source_framework" "$target_framework"
if [ "$configuration" = "release" ]; then
  # Automated check: no demo symbol may be in any slice of the release framework.
  for binary in $(find "$target_framework" -type f -name TwitchShared -not -path "*/Headers/*"); do
    if strings "$binary" | grep -qE "appleDemoModule|AppleDemoData|TwitchDemoKt|demo-client-id"; then
      echo "demo code found in the release framework: $binary" >&2; exit 1
    fi
  done
  echo "release framework contains no demo code"
fi
echo "TwitchShared ($configuration) -> $target_framework"
