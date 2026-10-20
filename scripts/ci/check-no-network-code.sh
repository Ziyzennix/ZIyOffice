#!/usr/bin/env bash
# Fails if the source uses any networking API.
#
# The manifest check already guarantees there is no android.permission.INTERNET,
# so the platform would block a request even if one were written. This check
# closes the other half: the code should not contain a network call at all,
# because a future permission review or a library that ships its own manifest
# could otherwise turn a dead branch into a live one.
#
# Every pattern below is a deliberate, reviewed ban. Add an exception only
# together with a reason in this file.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"

banned='java\.net\.|javax\.net\.|okhttp3?\.|retrofit2?\.|io\.ktor\.|org\.apache\.http|reactivex\.|org\.jsoup|android\.webkit\.WebView|android\.webkit\.WebResourceRequest'

echo "Scanning source for networking APIs..."
hits="$(find "$root/app/src" "$root/core" -name '*.kt' -not -path '*/build/*' -print0 \
  | xargs -0 grep -nE "$banned" 2>/dev/null || true)"

if [ -n "$hits" ]; then
  echo "FAIL: networking code found in the source:" >&2
  printf '%s\n' "$hits" >&2
  echo "      ZiyOffice must be unable to use the network, in code as well as in" >&2
  echo "      the manifest. If a pattern is a false positive, narrow it here and" >&2
  echo "      say why in a comment." >&2
  exit 1
fi

echo "OK: no networking APIs in the source."
