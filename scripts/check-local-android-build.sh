#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { echo "BLOCKED: $1" >&2; exit 2; }
command -v java >/dev/null 2>&1 || fail "JDK 17 is required"
command -v gradle >/dev/null 2>&1 || fail "Gradle 8.9 is required (CI installs it automatically)"
command -v sdkmanager >/dev/null 2>&1 || fail "Android SDK command-line tools are required"
command -v adb >/dev/null 2>&1 || echo "NOTE: adb is unavailable; physical-device installation cannot be checked here"

JAVA_MAJOR="$(java -version 2>&1 | sed -n '1s/.*version \"\([0-9]*\).*/\1/p')"
[[ "$JAVA_MAJOR" == "17" ]] || fail "JDK 17 required; found Java ${JAVA_MAJOR:-unknown}"
GRADLE_VERSION="$(gradle --version | sed -n 's/^Gradle //p' | head -1)"
[[ "$GRADLE_VERSION" == "8.9" ]] || fail "Gradle 8.9 required; found ${GRADLE_VERSION:-unknown}"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$SDK" && -d "$SDK" ]] || fail "Set ANDROID_SDK_ROOT to an installed Android SDK"
[[ -d "$SDK/platforms/android-35" ]] || fail "Android SDK Platform 35 is missing"
[[ -d "$SDK/build-tools/35.0.0" ]] || fail "Android Build Tools 35.0.0 are missing"

./scripts/check-release-readiness.sh
./scripts/check-core.sh
gradle --no-daemon --stacktrace :android:app:testDebugUnitTest
gradle --no-daemon --stacktrace :android:app:assembleDebug

APK="android/app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$APK" ]] || fail "Gradle completed but the expected APK is missing"
sha256sum "$APK"
echo "APK READY: $APK"
