# ADR 0002 — Toolchain baseline (AGP 8.13 / Gradle 8.13 / Kotlin 2.4 / Compose 1.10)

**Status:** Accepted (2026-10-18).
**Supersedes:** nothing. Review trigger: before milestone M1 feature work grows, or when the
build machine gains SDK platform 37.

## Context

In late 2026 the Android ecosystem had already moved to a new baseline:

- Compose 1.11+ (BOM 2026.04.01 and later) requires **compileSdk 37** and **AGP 9.1.0+**.
- AGP is at 9.4.x; AGP 9 is a major release with DSL/behaviour changes we have not yet
  validated against this project.
- The development machine has **SDK platform 36** installed, Gradle 8.13, and JDK 17.

First build attempt with the newest Compose BOM (2026.09.00 → Compose 1.12.1) failed on
AAR metadata: `requires compileSdk 37 / AGP 9.1.0 or higher` for 22 artifacts (the whole
Compose stack). That is a hard stop, not a warning.

## Decision

Pin the project to the **newest toolchain that works with compileSdk 36**:

| Component | Version | Why this one |
|---|---|---|
| AGP | 8.13.2 | Newest 8.x line; last line that supports compileSdk 36 without AGP 9 migration. |
| Gradle | 8.13 | Already installed locally; matches AGP 8.13. Kotlin 2.5 will require ≥8.14.4 (warning only today). |
| Kotlin + Compose compiler plugin | 2.4.21 | Current stable; compiler is bundled with Kotlin. |
| Compose BOM | 2026.03.01 (Compose 1.10.6) | Newest BOM whose artifacts target API 36; 2026.04.01+ jumps to the 37/AGP-9 requirement. |
| activity-compose | 1.13.0 | Compatible with compileSdk 36 (verified by the AAR metadata check). |
| SDK platform | android-36 | Already installed on the build machine. |

## Consequences

- The app builds and ships on the API 36 baseline; **nothing user-visible depends on the
  newer Compose releases** for M0/M1.
- A future upgrade to API 37 + AGP 9 is a single, well-defined migration:
  bump `agp`, `composeBom`, `compileSdk`, `targetSdk`; install SDK platform 37; move off
  Gradle 8.13; re-validate the privacy guardrail scripts (nothing about them is AGP-specific).
- Upgrade hygiene: version bumps happen one at a time with a green build between them —
  never as part of a feature change.
- Known warnings we accept for now: Kotlin's "Gradle 8.13 deprecated" notice, and AGP 8.13's
  "SDK XML version 4" notice from newer command-line tools.
