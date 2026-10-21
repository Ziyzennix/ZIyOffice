#!/usr/bin/env bash
# Fails if the source uses any networking API.
#
# The manifest check already guarantees there's no android.permission.INTERNET,
# so the platform would block a request even if one got written. This closes
# the other half: the code shouldn't contain a network call at all, because a
# later permission review or a library shipping its own manifest could turn a
# dead branch into a live one.
#
# Every pattern below is a deliberate, reviewed ban. Add an exception only
# together with a reason in this file.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"

banned='java\.net\.|javax\.net\.|okhttp3?\.|retrofit2?\.|io\.ktor\.|org\.apache\.http|reactivex\.|org\.jsoup|android\.webkit\.WebView|android\.webkit\.WebResourceRequest|java\.nio\.channels\.SocketChannel|java\.net\.DatagramSocket'

echo "scanning source for networking APIs..."
# Kotlin, Java and XML. A WebView in a layout counts too.
hits="$(find "$root/app/src" "$root/core" \
  \( -name '*.kt' -o -name '*.java' -o -name '*.xml' \) -not -path '*/build/*' -print0 \
  | xargs -0 grep -nE "$banned" 2>/dev/null || true)"

if [ -n "$hits" ]; then
  echo "FAIL: networking code in the source:" >&2
  printf '%s\n' "$hits" >&2
  echo "      ZiyOffice can't use the network, in code as well as in the manifest." >&2
  echo "      If a pattern is a false positive, narrow it here and say why in a comment." >&2
  exit 1
fi

echo "OK: no networking APIs in the source."
