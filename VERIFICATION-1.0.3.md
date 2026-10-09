# NotiFilter 1.0.3 validation

## Scope

Fix duplicate rule-add dialogs on repeated taps. MainActivity owns one rule-add dialog and one optional app picker, clears their references on dismissal, disables the main Add button while the form is open, and releases both windows when destroyed. Queued positive-button clicks cannot save an already dismissed form twice. Outside taps do not cancel either dialog; explicit Cancel and Android Back remain supported.

No filtering, storage, history, permission, or dependency changes. Version 1.0.3 / code 4.

## Checks (2026-10-09)

- `gradlew.bat testDebugUnitTest lintDebug assembleDebug`: successful; three existing RuleMatcher JVM tests passed.
- Regular YTDown_API_35 emulator / emulator-5554 / Android 15: upgrade installed without clearing application data.
- Ten repeated taps at the main Add location preserved the same dialog window; one Cancel returned to the main window and re-enabled Add. Reopening worked.
- App picker opened over the form normally; repeated outside taps preserved its window, selecting All Apps returned to the original form.
- Empty phrase did not save. Saving `NFQA_SINGLE_DIALOG_20261009` with five repeated positive-button taps created exactly one matching SharedPreferences record and returned to a single main window.
- Android Back closed the reopened form (after dismissing the keyboard where applicable). Final window inspection showed only the main Activity window; no crash entries for this package were observed. Installed version was 1.0.3 / code 4. APK signature verification passed (v2 scheme).

Physical devices and notification delivery/filtering were not re-tested for this dialog-only patch. Emulator synthetic test rules are not bundled into the APK. Build warnings are not represented as resolved.
