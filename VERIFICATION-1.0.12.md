# NotiFilter 1.0.12 verification

2026-10-23. Version code 13; production package `com.local.noti_filter`.

## Problem and change

- Reproduced 1.0.11 at 320dp width / font scale 1.6: the fixed-height equal-width sorting row clipped `오래된순`; platform-raised shadows also ended abruptly at parent bounds.
- Buttons now measure their text and padding with a 48dp minimum touch height. They retain system font scaling. Custom rounded backgrounds use bounded platform ripples and no raised state animator/shadow.
- `AdaptiveRow` measures label widths and stacks sorting controls or title/action headers when they do not fit. No new UI dependency or fixed smaller font workaround is used.
- Main scroll clipping/insets keep scrolled content out of status/navigation areas. The rule form scrolls, its controls have content-driven heights, and the app picker bounds its window/list to the available display frame, including the keyboard.
- Existing rule matching, stored rules, sort preference, app discovery cache, permission disclosure, confirmation dialogs and filter/history storage are unchanged.

## Validation

- 18 existing JVM tests passed; lint 0 errors / 8 existing warnings; release assembly succeeded.
- Existing 8 sorting UI checks passed on the regular API35 emulator in isolated package `com.local.noti_filter.uitest`, restoring original clone rules and sort selection.
- Existing async editor/catalog/picker regressions passed, including blocked package discovery, immediate edit, preserved input/scope, disabled-rule editing and duplicate-save protection.
- Actual ADB UI observations: 320dp width at font scales 1.6 and 2.0; sorting labels fit and actions stack; rule form and app selection/cancel controls remain visible at 2.0. With the keyboard open, the picker list shrinks and Cancel stays above the keyboard. The list remains scrollable; only part of a row can be visible when space is short.
- Signed production 1.0.12 installed with `adb install -r` without uninstall or data clear. Existing emulator rules remained visible. Package/version/non-debuggable flags verified; no actual notification behavior or physical-device vendor font/rendering was retested for this layout-only change.
- Restored emulator density 420 and font scale 1.0; checked default portrait layout. No physical device or ARTEMIS was used.
- Existing NotiFilter signing lineage verified independently for API 26/28/32/33/35/36. No keys or user data are included in the APK or staged source.
- No dependencies, permissions, external assets or fonts were added. Project-authored Java uses existing Android platform APIs; existing privacy/license notices are retained.

APK: `NotiFilter-v1.0.12.apk`, 53,892 bytes.

SHA-256: `bf7423123e99e95a3515446e7c81d1bda4a6ecd983608353232b1b09add639e9`.

Local reproduction screenshots, emulator hierarchies, build/test logs and publication/delivery receipts remain outside Git. This validation does not establish every OEM display/font setting or landscape configuration.
