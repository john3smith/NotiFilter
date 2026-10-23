# NotiFilter 1.0.11 verification

2026-10-23. Version code 12, production package `com.local.noti_filter`.

- Added three directly selectable rule-list orders: newest, oldest, name. Newest is the default and the selected mode is stored in app-private preferences.
- Creation order uses the existing immutable timestamp IDs. Name order uses the displayed app name with Korean collation, then the phrase, package and creation ID for deterministic ties. The all-app fallback is `모든 앱`.
- Sorting copies the display list. It does not rewrite the stored rule array or change rule matching, enable flags, creation IDs, notification permission handling, history or data collection.
- 18 JVM tests passed. Lint: 0 errors, 8 existing warnings. Release assembly succeeded.
- 8 native UI checks passed in isolated package `com.local.noti_filter.uitest` on the regular Android Emulator, API 35: default newest, oldest, name/phrase order, preference retention across recreation, new rules in the selected mode, editing by ID with refreshed ordering, correct toggle target, empty list. Exact original rule JSON and previous sort preference were restored after the checks.
- The existing async dialog/cache/picker regression passed, including blocked discovery, immediate editing, input preservation, disabled removed-app scope, repeated-save prevention and disk cache deduplication.
- A real ADB tap selected name order, then the isolated app was force-stopped and reopened. Fresh UI hierarchy confirmed name order remained selected. This is emulator verification, not physical-device testing.
- Production non-debuggable release signed with the existing NotiFilter lineage. An independent verifier checked package, version, flags, alignment and the trusted certificates at API 26/28/32/33/35/36. `adb install -r` successfully updated the installed production package without uninstalling or clearing data.
- Final APK contents were checked for private signing files and user databases. Production DEX includes sorting code and excludes the synthetic UI test class and fixtures.
- No new dependencies, permissions, third-party code or artwork were added. Sorting uses the platform Java `Collator` and existing app components; existing legal/privacy assets remain in place.

APK: `NotiFilter-v1.0.11.apk`, 49,796 bytes.

SHA-256: `e572482cd70598efe7c5654ccae846acd85f8b891a718a9479e4bd22d3597cb8`.

Local test logs and delivery receipts are kept outside the repository. The first edit-order test attempted Save before the dialog's posted OnShow handler ran; the test now explicitly waits for UI readiness before editing. The final UI checks passed with no production workaround for that test timing issue.
