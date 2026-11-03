# NotiFilter 1.0.20 - rules from current notifications

Adds a main-screen button and a bounded, scrollable current-notification picker.
The listener supplies a transient snapshot off the UI thread, sorted newest
first. Null/unavailable and successful empty results have distinct messages;
unavailable results offer a route to the existing settings screen. Refreshes
and dialog/activity dismissal invalidate late results. Blank notifications are
disabled. Selecting one row saves one app-scoped enabled rule and dismisses the
picker, then runs the existing current-notification scan with pause respected.

Rules created here use an explicit persisted literal flag. The combined title,
body and other available notification text is compared as a case-insensitive
substring, preserving punctuation and newlines. Old rules default to expression
mode; an invalid literal flag fails closed. Toggle, editing and persistence
retain the flag. The editor identifies literal mode and supports multiline text.
No new dependency, permission, bundled asset or signing identity is introduced.
Existing platform-only runtime notices are retained. This is not a new legal or
full dependency/security audit.

Verification on the regular API 35 emulator used the isolated UI-test package
and synthetic shell notifications. No new test source was authored. Confirmed:

- Missing listener access displays an error and its settings route works.
- Current notifications show app/package/text, with textless rows disabled.
- One selection stores exactly one rule for com.android.shell with the exact
  combined title/body, comma and semicolon, and closes the picker.
- Paused filtering keeps the source notification. Resuming removes it.
- A new case-insensitive identical notification is filtered; a notification
  containing only a partial phrase remains.
- Saved rule survives process restart; literal editor retains both lines and
  identifies its comparison mode. Keyboard leaves save/cancel reachable.
- Normal display, 320dp width at font scale 1.3, and a short 600x320dp viewport
  at font scale 1.3 were inspected using fresh screenshots and UI bounds.
  The list scrolls; native close/refresh controls remain within the dialog.
- Existing catalog/editor/cache/picker checks, eight sorting checks and four
  history-load checks pass. Original clone preferences/database bytes and
  listener permissions were restored; temporary display/font changes reverted.

Host unit tests, lint and final non-debuggable release/signature checks are
recorded with the delivery evidence. Empty-list messaging is implemented but
was not forced by clearing unrelated system notifications. Real phone/OEM and
Android-restricted notification contents remain untested. The picker is a
snapshot: use refresh to reflect changes while it is open.

Final build passed: 26 host tests with no failures/errors/skips; lint zero errors
and seven warnings. Signed APK is 62,140 bytes, SHA-256
`c56047f0a39be6c1c070b6b31590cf18958503258a74968fdf79fac62b03151f`.
Existing signature rotation verifies at APIs 26/28/32/33/35/36; alignment,
non-debuggable/non-testOnly identity and credential-file exclusions pass.
Regular emulator production update retained app ID and first-install time;
the pulled installed APK equals these signed bytes and the button renders.
