#!/usr/bin/env bash
# Fails if the merged Android manifest declares a permission nobody reviewed.
#
# INTERNET is hard-banned and can never be allowlisted. Everything else has to
# sit in scripts/ci/allowed-permissions.txt with a reason next to it.
#
# This looks at every <uses-permission> in the manifest, not just the ones
# starting with android.permission. A third-party SDK brings its own
# permission (com.google.android.gms.permission.AD_ID, for one) and that one
# matters just as much.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
allow_file="$root/scripts/ci/allowed-permissions.txt"

mapfile -t manifests < <(find "$root/app/build/intermediates" -path '*merged_manifest*' -name 'AndroidManifest.xml' 2>/dev/null | sort)
if [ "${#manifests[@]}" -eq 0 ]; then
  echo "no merged manifest found. run './gradlew :app:assembleDebug' first." >&2
  exit 1
fi

declared_all=""
for manifest in "${manifests[@]}"; do
  echo "checking merged manifest: ${manifest#"$root/"}"
  declared_here="$(grep -o '<uses-permission[^>]*android:name="[^"]*"' "$manifest" \
    | sed 's/.*android:name="//;s/".*//' || true)"
  declared_all="${declared_all}${declared_here}"$'\n'
done
declared="$(printf '%s' "$declared_all" | grep -v '^$' | sort -u || true)"

fail=0

if printf '%s\n' "$declared" | grep -qx "android.permission.INTERNET"; then
  echo "FAIL: android.permission.INTERNET is in the manifest." >&2
  echo "      ZiyOffice can't use the network. That one is never acceptable." >&2
  fail=1
fi

allowed="$(grep -v '^[[:space:]]*#' "$allow_file" | grep -v '^[[:space:]]*$' | sort -u || true)"
unexpected="$(comm -23 <(printf '%s\n' "$declared" | grep -v '^$' | sort -u) <(printf '%s\n' "${allowed:-}"))"

if [ -n "$unexpected" ]; then
  echo "FAIL: permissions nobody reviewed are in the manifest:" >&2
  printf '      - %s\n' $unexpected >&2
  echo "      If it's on purpose, say why in the pull request and add it to" >&2
  echo "      scripts/ci/allowed-permissions.txt." >&2
  fail=1
fi

if [ "$fail" -eq 0 ]; then
  if [ -z "$(printf '%s\n' "$declared" | grep -v '^$')" ]; then
    echo "OK: the app declares no permissions at all."
  else
    echo "OK: only reviewed permissions are declared: $(printf '%s ' $declared)"
  fi
fi

exit "$fail"
