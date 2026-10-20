# Contributing to ZIyOffice

Thanks for helping build an office suite that respects its users. This file covers the
ground rules and the practical workflow.

## The two absolute rules

1. **Never add network capability.** No internet permission, no network libraries, no
   "optional" telemetry. If a dependency needs the network to work, it does not belong
   in this project.
2. **Never copy from proprietary office apps.** WPS, Microsoft Office and the rest are
   strictly off-limits as sources of code, assets, icons, fonts, strings or layouts.
   Feature parity comes from public documentation and screenshots, reimplemented from
   scratch. (A related trap: bundled fonts must have redistribution licenses — metric
   compatible open fonts like Carlito and Caladea are fine; Microsoft fonts are not.)

## Getting started

```bash
scripts/setup-dev.sh          # verify your toolchain
./gradlew :app:assembleDebug  # build
./gradlew test                # unit tests
scripts/install-device.sh     # install + launch on a connected phone
```

## Before you push: the guardrails

CI runs these on every change and they must pass:

```bash
./gradlew test
./gradlew :app:assembleDebug
bash scripts/ci/check-manifest.sh      # zero permissions, INTERNET hard-banned
bash scripts/ci/check-dependencies.sh  # no ads/analytics/tracking libraries
```

If a change needs a new permission (for example camera access for the offline OCR feature
in M5), it must be: (a) justified in the pull request, (b) added with a comment to
`scripts/ci/allowed-permissions.txt`, and (c) requested at runtime only when the user
starts the feature that needs it.

## Code style

- Kotlin official style (`kotlin.code.style=official`), 4-space indent, trailing commas.
- Compose UI: one screen per file, `@OptIn(ExperimentalMaterial3Api::class)` where needed,
  strings user-visible go through `strings.xml` when reused across screens.
- Keep the model layer (`core/model`) free of Android imports so it stays unit-testable.
- Prefer a small, honest implementation over a speculative abstraction. No dead code, no
  "for future use" parameters.

## Commits and pull requests

- Commit messages: imperative mood, short subject, body explaining *why* when not obvious.
- Keep pull requests focused; UI changes get a screenshot from a real device.
- New dependencies need a justification in the PR description — the bar is high.

## License

By contributing you agree your work is licensed under GPL-3.0-or-later.
