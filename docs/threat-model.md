# ZiyOffice threat model

## What we're protecting

| Asset | Why it matters |
|---|---|
| Document contents | Contracts, finances, medical notes, personal writing |
| Document metadata | Authors, companies, edit history, GPS data, printer names |
| Usage patterns | Which files exist, when they were opened, how often |
| Device integrity | The app must not become a beachhead for anything else |

Metadata is protected by not moving it anywhere, not by deleting the user's own. A document
ZiyOffice creates carries no `docProps/` at all, so there's nothing to leak. A document
ZiyOffice edits keeps the metadata it came with, because that metadata belongs to the author
and the file, and ZiyOffice adds none of its own. Nothing in either case leaves the device,
since there's no network permission to leave it with.

## Adversaries and how we answer them

| Adversary | Capability | Mitigation |
|---|---|---|
| Ad / telemetry SDKs | Exfiltrate identifiers and content | **No network permission exists.** Ads/tracking dependencies are banned by CI. |
| "Optional" cloud features | Sync documents off-device | There's no cloud, no sync, no login. Nothing to disable because it doesn't exist. |
| Network attackers | Intercept or inject traffic | There's no traffic, ever. Airplane mode changes nothing about what the app can do. |
| Malicious documents | Exploit parsers, exfiltrate via embedded content | Strict subset parsing, no script execution, no remote resources in documents (links are inert). Tracked in the OOXML ADR. |
| Device thief | Read documents from a stolen phone | M5 encrypted vault with hardware-backed keys; file-level encryption at rest. |
| OEM / OS vendor | Collect usage stats about installed apps | We request no permissions and store nothing identifier-like, so there's nothing to collect. |
| Supply chain (dependencies) | Introduce trackers or backdoors via a dependency | Short pinned dependency list, allowlist review at milestone gates, banned-pattern CI scan, F-Droid source builds for distribution. |
| The ZiyOffice project itself (us) | A future maintainer adds tracking | GPL-3.0 keeps the source auditable and forkable. CI guardrails have to be removed in public, visible commits, and F-Droid rebuilds from source. |

## Trust boundaries

1. **App ↔ device storage.** The app only sees files the user explicitly picks through the
   Storage Access Framework. No broad storage permissions, no background scanning.
2. **App ↔ OS.** The app declares no system permissions. The permission check is a build-time
   guardrail with `INTERNET` hard-banned.
3. **App ↔ other apps.** Sharing and exporting are always an explicit user action, and the
   receiving app is the user's choice.
4. **Document parsers ↔ app process.** Malformed or hostile documents are the main expected
   attack surface from M1 onward. The parser is ours, it only ever touches local bytes, and
   the format layer is a documented subset rather than "every file on earth".

## Explicit non-goals

- Protecting against a compromised operating system or a rooted device.
- Protecting data from the user's own device administrator (an employer's MDM, say).
- Hiding the fact that the app is installed.

## Review triggers

Revisit this document when the vault lands (M5), when OCR or camera access lands (M5), when
any permission gets added, or when the parser picks up a new format family.
