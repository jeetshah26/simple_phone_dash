#!/usr/bin/env bash
set -euo pipefail

# GRADLE_USER_HOME=$(pwd)/.gradle ./gradlew --no-daemon assembleDebug

targets=$(adb devices | awk 'NR>1 && $2=="device"{print $1}')
for serial in $targets; do
  echo "Deploying to $serial"
  adb -s "$serial" uninstall com.example.alwaysondashboard || true
  adb -s "$serial" install -r ./app/build/outputs/apk/debug/app-debug.apk
  adb -s "$serial" shell am start -n com.example.alwaysondashboard/.MainActivity
done
