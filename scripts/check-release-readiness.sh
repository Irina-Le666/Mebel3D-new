#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fail() { echo "FAIL: $1" >&2; exit 1; }
pass() { echo "PASS: $1"; }

MANIFEST="$ROOT/android/app/src/main/AndroidManifest.xml"
APP_GRADLE="$ROOT/android/app/build.gradle.kts"
ROOT_GRADLE="$ROOT/build.gradle.kts"
WORKFLOW="$ROOT/.github/workflows/android.yml"
MAIN="$ROOT/android/app/src/main/java/ru/mebel3d/app/MainActivity.kt"

for f in "$MANIFEST" "$APP_GRADLE" "$ROOT_GRADLE" "$WORKFLOW" "$MAIN"; do
  [[ -s "$f" ]] || fail "required build file missing: ${f#$ROOT/}"
done
pass "Android app and build entry points exist"

grep -q 'android:screenOrientation="landscape"' "$MANIFEST" || fail "landscape orientation is not locked"
grep -q 'android:name=".MainActivity"' "$MANIFEST" || fail "launcher activity is missing"
grep -q 'android:exported="true"' "$MANIFEST" || fail "launcher activity must be exported"
pass "landscape launcher manifest is configured"

grep -q 'id("com.android.application") version "8.7.3"' "$ROOT_GRADLE" || fail "Android Gradle Plugin version drift"
grep -q 'id("org.jetbrains.kotlin.android") version "2.0.21"' "$ROOT_GRADLE" || fail "Kotlin Android plugin version drift"
grep -q 'compileSdk = 35' "$APP_GRADLE" || fail "compileSdk must be 35"
grep -q 'minSdk = 26' "$APP_GRADLE" || fail "minSdk must be 26"
grep -q 'targetSdk = 35' "$APP_GRADLE" || fail "targetSdk must be 35"
pass "Android and Kotlin toolchain versions are aligned"

grep -q 'workflow_dispatch:' "$WORKFLOW" || fail "manual APK build trigger is missing"
grep -q ':android:app:testDebugUnitTest' "$WORKFLOW" || fail "Android unit-test task is missing from CI"
grep -q ':android:app:assembleDebug' "$WORKFLOW" || fail "APK assemble task is missing from CI"
grep -q 'actions/upload-artifact@v4' "$WORKFLOW" || fail "APK artifact upload is missing from CI"
grep -q 'app-debug.apk' "$WORKFLOW" || fail "APK artifact path is missing from CI"
grep -q 'check-release-readiness.sh' "$WORKFLOW" || fail "release preflight is not wired into CI"
pass "CI has manual build, Android tests, APK assembly and artifact delivery"

LOCAL_BUILD="$ROOT/scripts/check-local-android-build.sh"
[[ -x "$LOCAL_BUILD" ]] || fail "local Android build gate is missing or not executable"
grep -q 'Gradle 8.9' "$LOCAL_BUILD" || fail "local build gate does not pin Gradle 8.9"
grep -q 'assembleDebug' "$LOCAL_BUILD" || fail "local build gate does not assemble APK"
grep -q 'sha256sum' "$LOCAL_BUILD" || fail "local build gate does not verify APK checksum"
pass "local build gate checks pinned toolchain, tests, APK and SHA-256"

if grep -RqlE 'RoomDrawingPackageExporter|Комплект: план \+ 4 стены|Экспорт выбранной стены|A3 landscape' "$ROOT/android/app/src/main"; then
  fail "formal multi-sheet drawing export is still exposed in Android sources"
fi
grep -q 'Схема комнаты' "$MAIN" || fail "schematic room workspace is missing"
pass "interior schematic workspace is retained; formal drawing package is absent"

CORE_COUNT=$(find "$ROOT/android/app/src/main/java/ru/mebel3d/app/core" -maxdepth 1 -name '*.kt' | wc -l | tr -d ' ')
SMOKE_COUNT=$(find "$ROOT/build-check" -maxdepth 1 -name '*.kt' | wc -l | tr -d ' ')
[[ "$CORE_COUNT" -ge 30 ]] || fail "unexpectedly few core source files: $CORE_COUNT"
[[ "$SMOKE_COUNT" -ge 60 ]] || fail "unexpectedly few core smoke suites: $SMOKE_COUNT"
pass "source inventory: $CORE_COUNT core files and $SMOKE_COUNT smoke suites"

grep -q 'timeout-minutes: 25' "$WORKFLOW" || fail "core regression job timeout is not bounded"
grep -q 'timeout-minutes: 35' "$WORKFLOW" || fail "Android build job timeout is not bounded"
grep -q 'retention-days: 30' "$WORKFLOW" || fail "APK artifact retention must be 30 days"
pass "CI timeouts and APK artifact retention are explicit"

echo "RELEASE PREFLIGHT: PASS"
echo "NOTE: this script is a static preflight; it does not replace Gradle compilation, APK assembly, or device installation."
