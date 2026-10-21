#!/usr/bin/env bash
# Builds the debug APK, installs it on the connected device and launches it.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

gradle_cmd="${GRADLE_CMD:-}"
if [ -z "$gradle_cmd" ]; then
  if command -v gradle >/dev/null 2>&1; then
    gradle_cmd="gradle"
  else
    gradle_cmd="./gradlew"
  fi
fi

echo "building the debug APK with: $gradle_cmd"
"$gradle_cmd" :app:assembleDebug

apk="$root/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$apk" ]; then
  echo "no APK at $apk" >&2
  exit 1
fi

echo "installing on the device..."
adb install -r "$apk"
adb shell am start -n org.ziyoffice/.MainActivity
echo "done. ZiyOffice is running on the device."
