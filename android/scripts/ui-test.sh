#!/usr/bin/env bash
# Runs the instrumented tests on a connected emulator and collects the screenshots.
set -u
cd "$(dirname "$0")/.."
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

./gradlew connectedDebugAndroidTest --stacktrace \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
status=$?

mkdir -p build/ui-screens
adb pull /sdcard/Android/data/io.github.iamandelib.cyberspace/files/screens/. build/ui-screens/ || true
ls -la build/ui-screens || true
exit $status
