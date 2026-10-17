# NotiFilter 1.0.6 verification

2026-10-17. Runtime project: NotiFilter. Matching source: github-projects/NotiFilter.

- Removed the Korean tap-to-edit hint and its layout spacing from every rule card. The existing card click listener remains unchanged.
- JDK 17 / SDK 36: lintDebug and assembleDebug passed. Lint reports zero errors and four existing warnings.
- Regular Android Emulator emulator-5554 (Android 15): data-preserving adb install -r succeeded; versionName 1.0.6 and versionCode 7 confirmed.
- Inspected the fresh main-screen hierarchy and screenshot: the hint is absent and app/phrase, switches and delete controls remain visible.
- Tapped an existing rule card: the edit dialog opened with the existing phrase prefilled. Back dismissed it. Stored rule preferences before the upgrade and after this check are byte-identical.
- No automated tests were added for this text-only change. No ARTEMIS or physical phone was used; notification filtering behavior was not retested.
- Existing debug signing is retained. The versioned APK filename identifies it as a debug build.

GitHub asset and Telegram attachment verification receipts are retained separately in ignored local release directories.
