# ZiyOffice privacy charter

What the app promises, and how each promise gets enforced. If one of them stops being true,
that's the worst bug in the project.

## What ZiyOffice never does

1. **It never connects to the internet.** There's no `android.permission.INTERNET` in the
   manifest, and without that permission Android blocks the app from opening any network
   connection. Not a setting, not a toggle, not a policy. That's how Android works.
2. **It never collects anything.** No analytics, no usage statistics, no crash reporting, no
   device identifiers, no advertising IDs.
3. **It never shows ads or sells anything.** No ads, no subscriptions, no in-app purchases, no
   promotional banners, no "upgrade" nagging. Every feature is available to everyone.
4. **It never requires an account.** No sign-up, no login, no email collection.
5. **It never uploads your documents.** Files get opened in place, edited in place and saved in
   place, in folders you pick. There's no cloud, no sync, no telemetry that could carry
   document content.
6. **It never sends data anywhere in the background.** No phone-home, no update pinging, no
   A/B testing, no remote configuration.

## What data exists, and where

| Data                      | Where it lives                         | Leaves the device? |
|---------------------------|----------------------------------------|--------------------|
| Your documents            | The folders you pick (via SAF)         | Never              |
| Recent-files list         | App-private storage on this device     | Never              |
| Autosave drafts           | App-private storage on this device     | Never              |
| Settings                  | App-private storage on this device     | Never              |
| Encrypted vault          | App-private storage, encrypted at rest | Never              |
| A picture read as text    | The picture is read on the device, in memory. The English model ships inside the app | Never              |

Android backup is off (`android:allowBackup="false"`), so none of this gets copied to a cloud
backup service either.

## How the promises are enforced

Not by discipline. By the build:

- `scripts/ci/check-manifest.sh` looks at the merged Android manifest of every build and
  fails if a permission shows up without review. `INTERNET` is hard-banned: adding it fails
  the build even if someone tries to allowlist it.
- `scripts/ci/check-no-network-code.sh` fails the build if the source uses a networking API.
  The manifest check blocks the request, this one stops the call from being written in the
  first place, so a later permission change can't turn dead code into live code.
- `scripts/ci/check-dependencies.sh` fails the build if a known ads, analytics,
  crash-reporting or tracking library gets declared.
- The OCR engine is Tesseract, which is free software, and its English model ships inside the
  app rather than being fetched. The app has no network permission, so "read on the device" is
  not a policy: there is nowhere else for the picture to go.
- The dependency list is short, pinned in `gradle/libs.versions.toml`, and reviewed by
  humans at every milestone gate.
- Release builds get published from source (F-Droid rebuilds from source too), so the binary
  you install can be compared against the code you can read.

## Verifying this yourself

1. The About screen in the installed app lists the permissions the APK actually declares.
   It should say none, apart from the one signature permission AndroidX adds for itself,
   which is worthless outside this app.
2. Read the merged manifest of any build:
   `app/build/intermediates/merged_manifest*/debug/AndroidManifest.xml`.
3. Or skip trust entirely: build from source and check the same files.

## Reporting a privacy problem

A privacy issue (any code path that would collect, transmit or leak data) is the most severe
class of bug. Open an issue describing what you observed and put "privacy" in the title.
