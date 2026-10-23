# NotiFilter 1.0.13 verification

2026-10-23. Version code 14; production package `com.local.noti_filter`.

- Main has a 48dp accessible settings icon (`main_settings`, content description `설정`). The new internal/non-exported `SettingsActivity` contains notification-access and privacy/permission actions. Main keeps only a compact permission status.
- Moved the existing permission disclosure unchanged, retaining Cancel, privacy information and explicit consent before Android notification-listener settings. Privacy, offline notices, manifest permissions, filtering and storage behavior are unchanged.
- Added a project-authored vector gear constructed from radial coordinates. No external icon/font/library, dependency or permission was added. Existing legal/privacy assets remain unchanged.
- Includes the previously validated 1.0.12 button layout fixes. 1.0.12 was a local-only intermediate signed APK and was not published or attached; its files are preserved.

Validation:

- Existing 18 JVM tests passed; lint 0 errors/8 existing warnings; release assembly succeeded.
- Existing 8 sort UI checks and async editor/catalog/picker regressions passed in the isolated `com.local.noti_filter.uitest` package, restoring its original rule JSON and sort preference.
- Actual ADB navigation on the regular API35 emulator: main settings icon -> settings screen; privacy button -> original privacy dialog; notification-access button -> original disclosure; explicit consent -> Android `Settings$NotificationAccessSettingsActivity`; Back -> app. No permission toggle was changed. One hierarchy dump during the Android settings transition returned no root, so the destination was verified through the focused Android Activity.
- Main UI hierarchy confirmed both settings-only actions are absent; settings hierarchy contains both actions and Back. Checked the production APK at 320dp width/font scale 2.0: icon and settings buttons remain reachable with complete labels. Restored emulator density 420/font scale 1.0.
- Signed non-debuggable production APK independently verified at API26/28/32/33/35/36 with the existing signing lineage. `adb install -r` updated the installed app without uninstall or data clearing; existing emulator rules remained visible.
- Final archive excludes signing keys/credential files/user databases and synthetic test classes. Build source is synchronized with the matching repository; final delivery is queue-guarded.

APK: `NotiFilter-v1.0.13.apk`, 53,948 bytes.

SHA-256: `383864e6d3f92e5abe721de23f94ec126d6b7a2c8b029b5b8763cd1a454c5a3c`.

This is emulator UI validation, not physical-device/OEM or every landscape configuration. Actual notification removal was not re-tested for this menu relocation. Local screenshots, UI dumps and publication/attachment receipts are outside Git.
