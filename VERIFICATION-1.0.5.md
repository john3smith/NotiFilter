# NotiFilter 1.0.5 verification

2026-10-17. Runtime project: NotiFilter. Matching source: github-projects/NotiFilter.

- Tapping a rule card opens an edit dialog prefilled with the current app and expression. Add and edit share the existing single-dialog guard and validation.
- Saving replaces the existing entry by ID, preserving list position and the latest enabled state. An unavailable app retains its stored package scope instead of becoming an all-app rule; this fallback was code-reviewed, not exercised with an uninstalled app.
- Disabled edits skip notification scans. Enabled edits use the updated rule for existing notifications, respecting global pause. Cancel and Android Back do not save.
- JDK 17, SDK 36: testDebugUnitTest lintDebug assembleDebug passed. Twelve existing RuleMatcher tests passed with no failures/errors/skips. Lint reported zero errors and four existing warnings.
- Regular Android Emulator emulator-5554, Android 15: installed with adb install -r; versionName 1.0.5 / versionCode 6 confirmed. Original stored rules survived the upgrade.
- Manual ADB/UI verification: card touch, prefilled expression/app, cancel, Back, phrase and app changes, unchanged item count and ID, disabled-state preservation, process restart and reopening, invalid bad,, rejection with dialog retained and no stored changes.
- Enabled rule save was exercised after switching it on without rerendering the card. A synthetic notification matching the updated condition was removed on save; subsequently the old-condition-only notification remained while a new-condition notification was removed.
- Temporary changes to the existing synthetic QA rule were restored through UI. Final stored rule JSON exactly matched the pre-test snapshot. Only this run's synthetic notifications were cleaned up. Existing history was not cleared.
- No mobile test code was authored; checks were manual emulator exploration plus existing JVM tests. No ARTEMIS or physical device was used. OEM-specific behavior was not tested.
- No runtime diagnostic logging, new permissions or new dependencies were introduced. Existing debug signing is retained for data-preserving updates; the APK filename explicitly identifies the debug build.

Build, source publication and final delivery receipts are recorded separately in ignored local release/verification directories.
