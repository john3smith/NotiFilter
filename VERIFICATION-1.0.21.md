# NotiFilter 1.0.21 - adjacent rule actions

Moves the current-notification rule action out of the pause/history card and
places it immediately to the right of the manual rule action, below the rule
section title. The horizontal row assigns one third of its available width to
the manual action and two thirds to the longer current-notification action,
with an 8dp gap. Labels wrap without truncation and retain the existing 48dp
minimum touch height and ripple feedback.

Rule creation, picker behavior, notification access and existing stored data
are unchanged. No dependency, permission, bundled asset or signing identity is
added; the existing platform-only license and third-party notices apply.

Validation: `testDebugUnitTest lintDebug assembleDebug assembleRelease` passed.
All 26 host tests passed (zero failures, errors or skips). Lint reported zero
errors and seven warnings, covering the existing target API, backup declaration,
generated store flag and text concatenation categories.

The signed release APK was installed with `adb install -r` on the regular API 35
emulator. Version 1.0.21/code 22, existing app ID and first-install time were
verified. Normal display and 320dp width at font scale 1.3 were inspected using
fresh screenshots and UI bounds. Both rule actions stay side by side and their
labels wrap within their bounds. Each action opens its original dialog. The
manual editor's keyboard layout leaves its cancel/add controls visible. No rule
was saved or deleted during these layout checks. Display and font overrides
were restored. Physical phones/OEM layouts were not tested.

The non-debuggable 62,140-byte APK has SHA-256
`cab555e933b781fabc69c3c3bc6544586d900f352a3e792ea5befc9065594b23`.
Alignment, package/version, credential-file exclusion and the existing signing
rotation were verified at APIs 26/28/32/33/35/36. No new security scanner or
full dependency/license audit was run for this layout-only change.
