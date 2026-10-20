#!/usr/bin/env bash
# Guardrail against ads, analytics, crash-reporting and tracking dependencies.
# Any declared dependency matching these patterns fails the build.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"

banned='firebase|crashlytics|play-services-ads|ads-identifier|analytics|appsflyer|adjust|mixpanel|amplitude|sentry|bugsnag|facebook|branch|kochava|vungle|unity3d|applovin|ironsource|bytedance|umeng|mopub|chartboost|inmobi|adcolony|tapjoy|smartlook|newrelic|instabug|datadog'

echo "Scanning dependency declarations for ads/tracking/telemetry artifacts..."
hits="$(find "$root" -name 'build.gradle.kts' -not -path '*/build/*' -print0 \
  | xargs -0 grep -niE "$banned" 2>/dev/null || true)"
extra="$(grep -niE "$banned" "$root/gradle/libs.versions.toml" 2>/dev/null || true)"

if [ -n "$hits$extra" ]; then
  echo "FAIL: banned dependency patterns found:" >&2
  printf '%s\n%s\n' "$hits" "$extra" | grep -v '^$' >&2
  exit 1
fi

echo "OK: no ads/tracking/telemetry artifacts declared."
echo "Note: transitive dependencies are reviewed at each milestone gate."
