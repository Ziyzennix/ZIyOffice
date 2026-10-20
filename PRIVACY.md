# ZiyOffice Privacy Charter

This document is the project's plain-language promise, followed by how each promise is
enforced in the code and build system. If any of it stops being true, that is a bug of the
highest severity.

## What ZiyOffice never does

1. **It never connects to the internet.** The app declares zero permissions, including no
   `android.permission.INTERNET`. Without that permission, the Android operating system
   prevents the app from opening any network connection. This is not a setting, not a
   toggle, not a policy — it is how Android works.
2. **It never collects anything.** No analytics, no usage statistics, no crash reporting,
   no device identifiers, no advertising IDs.
3. **It never shows ads or sells anything.** No ads, no subscriptions, no in-app purchases,
   no promotional banners, no "upgrade" nagging. Every feature is available to everyone.
4. **It never requires an account.** There is no sign-up, no login, no email collection.
5. **It never uploads your documents.** Files are opened in place, edited in place, and
   saved in place, in folders you choose. There is no cloud, no sync, no telemetry that
   could contain document content.
6. **It never sends data anywhere in the background.** There is no "phone home", no update
   pinging, no A/B testing, no remote configuration.

## What data exists, and where

| Data                         | Where it lives                          | Leaves the device? |
|------------------------------|-----------------------------------------|--------------------|
| Your documents               | The folders you choose (via SAF)        | Never              |
| Recent-files list            | App-private storage on this device      | Never              |
| Autosave drafts              | App-private storage on this device      | Never              |
| Settings                     | App-private storage on this device      | Never              |
| Encrypted vault (planned)    | App-private storage, encrypted at rest  | Never              |

Android backup is explicitly disabled (`android:allowBackup="false"`), so none of this is
copied to any cloud backup service either.

## How the promises are enforced

Not by discipline — by the build:

- `scripts/ci/check-manifest.sh` inspects the merged Android manifest of every build and
  fails if any permission appears without review. `INTERNET` is hard-banned: adding it
  fails the build even if someone tries to allowlist it.
- `scripts/ci/check-dependencies.sh` fails the build if any known ads, analytics,
  crash-reporting or tracking library is declared as a dependency.
- The dependency list is short, pinned in `gradle/libs.versions.toml`, and reviewed by
  humans at every milestone gate.
- Release builds are published from source (F-Droid rebuilds from source as well), so the
  binary you install can be compared against the code you can read.

## Verifying this yourself

1. The About screen in the installed app lists the permissions the APK actually declares.
   It should say: none.
2. You can read the merged manifest of any build:
   `app/build/intermediates/merged_manifest*/debug/AndroidManifest.xml`.
3. Or skip trust entirely: build from source and check the same files.

## Reporting a privacy problem

A privacy issue (any code path that would collect, transmit or leak data) is treated as
the most severe class of bug. Please open an issue describing what you observed, and
mention "privacy" in the title.
