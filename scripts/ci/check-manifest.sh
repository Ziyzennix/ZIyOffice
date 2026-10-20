#!/usr/bin/env bash
# Fails if any merged Android manifest declares a permission that has not been
# explicitly reviewed in scripts/ci/allowed-permissions.txt.
# INTERNET is hard-banned and can never be allowlisted.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
allow_file="$root/scripts/ci/allowed-permissions.txt"

mapfile -t manifests < <(find "$root/app/build/intermediates" -path '*merged_manifest*' -name 'AndroidManifest.xml' 2>/dev/null | sort)
if [ "${#manifests[@]}" -eq 0 ]; then
  echo "ERROR: no merged manifest found. Run './gradlew :app:assembleDebug' first." >&2
  exit 1
fi

declared_all=""
for manifest in "${manifests[@]}"; do
  echo "Checking merged manifest: ${manifest#"$root/"}"
  declared_here="$(grep -o 'android:name="android\.permission\.[^"]*"' "$manifest" | sed 's/.*android\.permission\.//;s/"//' || true)"
  declared_all="${declared_all}${declared_here}"$'\n'
done
declared="$(printf '%s' "$declared_all" | grep -v '^$' | sort -u || true)"

fail=0

if printf '%s\n' "$declared" | grep -qx "INTERNET"; then
  echo "FAIL: android.permission.INTERNET is present." >&2
  echo "      ZIyOffice must be unable to use the network. This is never acceptable." >&2
  fail=1
fi

allowed="$(grep -v '^[[:space:]]*#' "$allow_file" | grep -v '^[[:space:]]*$' | sort -u || true)"
unexpected="$(comm -23 <(printf '%s\n' "$declared" | grep -v '^$' | sort -u) <(printf '%s\n' "${allowed:-}"))"

if [ -n "$unexpected" ]; then
  echo "FAIL: unreviewed permissions in the manifest:" >&2
  printf '      - %s\n' $unexpected >&2
  echo "      If intentional, document the reason in the pull request and add the" >&2
  echo "      permission to scripts/ci/allowed-permissions.txt." >&2
  fail=1
fi

if [ "$fail" -eq 0 ]; then
  if [ -z "$(printf '%s\n' "$declared" | grep -v '^$')" ]; then
    echo "OK: the app declares zero system permissions."
  else
    echo "OK: only reviewed permissions are declared: $(printf '%s ' $declared)"
  fi
fi

exit "$fail"
