#!/usr/bin/env bash
# Verifies the development toolchain for ZiyOffice and prints next steps.
# This script only inspects and reports; it never installs or changes anything.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
problems=0

note() { printf '  %s\n' "$1"; }
ok()   { printf '\033[32mOK\033[0m   %s\n' "$1"; }
bad()  { printf '\033[31mMISS\033[0m %s\n' "$1"; problems=$((problems + 1)); }

echo "ZiyOffice toolchain check"
echo

# Java 17+
if command -v java >/dev/null 2>&1; then
  java_version="$(java -version 2>&1 | head -1)"
  ok "java: $java_version"
else
  bad "java not found (need JDK 17+)"
fi

# Gradle (wrapper or installed)
if [ -x "$root/gradlew" ]; then
  ok "gradle wrapper present (./gradlew)"
elif command -v gradle >/dev/null 2>&1; then
  ok "gradle: $(gradle --version | grep -m1 Gradle)"
else
  bad "no gradle and no wrapper found"
fi

# Android SDK
sdk_dir=""
if [ -f "$root/local.properties" ]; then
  sdk_dir="$(grep -E '^sdk\.dir=' "$root/local.properties" | cut -d= -f2- || true)"
fi
if [ -z "$sdk_dir" ] && [ -n "${ANDROID_HOME:-}" ]; then sdk_dir="$ANDROID_HOME"; fi
if [ -z "$sdk_dir" ] && [ -n "${ANDROID_SDK_ROOT:-}" ]; then sdk_dir="$ANDROID_SDK_ROOT"; fi

if [ -n "$sdk_dir" ] && [ -d "$sdk_dir" ]; then
  ok "Android SDK: $sdk_dir"
  if [ -d "$sdk_dir/platforms/android-36" ]; then
    ok "SDK platform android-36 installed"
  else
    bad "SDK platform android-36 missing (sdkmanager \"platforms;android-36\")"
  fi
  if [ -x "$sdk_dir/platform-tools/adb" ]; then
    devices="$("$sdk_dir/platform-tools/adb" devices | sed '1d' | grep -c device || true)"
    ok "adb present ($devices device(s) connected)"
  else
    bad "adb not found in platform-tools"
  fi
else
  bad "Android SDK not configured (set sdk.dir in local.properties)"
fi

echo
if [ "$problems" -eq 0 ]; then
  echo "All good. Build with:  ./gradlew :app:assembleDebug"
  echo "Install on a phone:    scripts/install-device.sh"
else
  echo "$problems problem(s) found — fix the items marked MISS above."
  exit 1
fi
