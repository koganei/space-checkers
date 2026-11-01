#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
serial="${1:-emulator-5558}"
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$serial" shell am instrument -w com.spacecheckers.test/com.spacecheckers.AppInstrumentation | tee "artifacts/test-${serial}.log"
grep -q 'checks passed' "artifacts/test-${serial}.log"
if grep -q 'FAILED:' "artifacts/test-${serial}.log"; then exit 1; fi
mkdir -p "artifacts/${serial}"
adb -s "$serial" pull /sdcard/Android/data/com.spacecheckers/files/screenshots "artifacts/${serial}/"
