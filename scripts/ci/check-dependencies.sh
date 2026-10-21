#!/usr/bin/env bash
# Guardrail against ads, analytics, crash-reporting and tracking dependencies.
#
# Checks the declared dependencies AND the ones that come in transitively, so a
# tracking library can't sneak in through some other library's dependency tree.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"

banned='firebase|crashlytics|play-services-ads|ads-identifier|analytics|appsflyer|adjust|mixpanel|amplitude|sentry|bugsnag|facebook|kochava|vungle|unity3d|applovin|ironsource|bytedance|umeng|mopub|chartboost|inmobi|adcolony|tapjoy|smartlook|newrelic|instabug|datadog'

echo "scanning dependency declarations..."
hits="$(find "$root" -name 'build.gradle.kts' -not -path '*/build/*' -print0 \
  | xargs -0 grep -niE "$banned" 2>/dev/null || true)"
extra="$(grep -niE "$banned" "$root/gradle/libs.versions.toml" 2>/dev/null || true)"

if [ -n "$hits$extra" ]; then
  echo "FAIL: banned dependency patterns found:" >&2
  printf '%s\n%s\n' "$hits" "$extra" | grep -v '^$' >&2
  exit 1
fi

echo "OK: nothing banned in the declared dependencies."

# The declaration files only show what we asked for. Resolve the real graph too.
echo "resolving the dependency graph..."
resolved="$(cd "$root" && ./gradlew --quiet --offline :app:dependencies \
  --configuration releaseRuntimeClasspath 2>/dev/null || true)"
if [ -z "$resolved" ]; then
  resolved="$(cd "$root" && ./gradlew --quiet :app:dependencies \
    --configuration releaseRuntimeClasspath 2>/dev/null || true)"
fi

if [ -z "$resolved" ]; then
  echo "WARNING: couldn't resolve the dependency graph, so transitive dependencies" >&2
  echo "         weren't checked. Run this after a build so Gradle has the" >&2
  echo "         dependencies cached." >&2
  exit 1
fi

transitive="$(printf '%s\n' "$resolved" | grep -niE "$banned" || true)"
if [ -n "$transitive" ]; then
  echo "FAIL: banned dependency in the resolved graph:" >&2
  printf '%s\n' "$transitive" >&2
  exit 1
fi

echo "OK: nothing banned in the resolved dependency graph."
