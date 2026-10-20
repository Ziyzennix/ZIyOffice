# ZiyOffice Threat Model

## What we are protecting

| Asset | Why it matters |
|---|---|
| Document contents | Contracts, finances, medical notes, personal writing |
| Document metadata | Authors, companies, edit history, GPS data, printer names |
| Usage patterns | Which files exist, when they were opened, how often |
| Device integrity | The app must not become a beachhead for anything else |

## Adversaries and how we answer them

| Adversary | Capability | Mitigation |
|---|---|---|
| Ad / telemetry SDKs | Exfiltrate identifiers and content | **No network permission exists.** Ads/tracking dependencies are banned by CI. |
| "Optional" cloud features | Sync documents off-device | There is no cloud, no sync, no login. Nothing to disable because it does not exist. |
| Network attackers | Intercept or inject traffic | There is no traffic, ever. Airplane mode changes nothing about what the app can do. |
| Malicious documents | Exploit parsers, exfiltrate via embedded content | Long-term: strict subset parsing, fuzz-tested format layer, no script execution, no remote resources in documents (links are inert). Tracked in the OOXML ADR. |
| Device thief | Read documents from a stolen phone | M5 encrypted vault with hardware-backed keys; file-level encryption at rest. |
| OEM / OS vendor | Collect usage stats about installed apps | We request no permissions and store nothing identifier-like; there is nothing to collect. |
| Supply chain (dependencies) | Introduce trackers or backdoors via a dependency | Short pinned dependency list, allowlist review at milestone gates, banned-pattern CI scan, F-Droid source builds for distribution. |
| The ZiyOffice project itself (us) | A future maintainer adds tracking | GPL-3.0 keeps the source auditable and forkable; CI guardrails must be removed in public, visible commits; F-Droid rebuilds from source. |

## Trust boundaries

1. **App ↔ device storage.** The app only sees files the user explicitly picks via the
   Storage Access Framework. No broad storage permissions, no background scanning.
2. **App ↔ OS.** The app declares zero permissions. The permission check is a build-time
   guardrail with `INTERNET` hard-banned.
3. **App ↔ other apps.** Sharing/exporting is always an explicit user action; the receiving
   app is the user's choice, not ours.
4. **Document parsers ↔ app process.** Malformed or hostile documents are the main expected
   attack surface from M1 onward. The parser is ours, operates only on local bytes, and the
   format layer is deliberately a documented subset rather than "every file on earth".

## Explicit non-goals

- Protecting against a compromised operating system or rooted device.
- Protecting data from the user's own device administrator (e.g. an employer's MDM).
- Hiding the fact that the app is installed.

## Review triggers

This document must be revisited when: the vault lands (M5), OCR/camera access lands (M5),
any permission is added, or the parser gains support for a new format family.
