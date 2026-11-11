# Current-notification rule: immediate one-time application

Verified 2026-11-11. Base: `john3smith/NotiFilter`, `main`,
`d7bd0b4aa8bf4de9ae0569a8896e964bbf426b3d`.
The clean previous feature checkout was one merge commit behind with identical
files; fetched origin, fast-forwarded and created a scoped branch before edits.

Selecting a current notification saves its literal package-specific rule and
immediately scans existing notifications using only that rule. This explicit
one-time scan also runs while paused, without changing the pause setting.
Subsequent automatic filtering, manual editor saves, toggles and listener
reconnection retain their pause checks. Disabled rules remain excluded by the
matcher. Cancellation requests still use full notification keys and confirmed
listener callbacks for history; request counts do not imply confirmed removal.

Validation:

- Release build and 26 existing JVM tests passed (zero failures/errors/skips).
- Release lint passed: zero errors, six existing warnings.
- Regular API 35 emulator, isolated diagnostic package: while paused, selected a
  synthetic shell notification; the picker closed, one literal rule was saved,
  and the selected notification disappeared from the system active list.
- An existing notification matching a different enabled rule remained. Reposting
  the newly matching notification while paused also remained. Stored pause stayed
  true. With pause off, selecting another notification removed it as expected.
- The picker was visually inspected at 320dp width and 130% font size; guidance,
  scrollable list, close and refresh controls remained inside the dialog.
- Original diagnostic preferences, listener permissions, emulator resolution,
  density and font scale were restored. Synthetic diagnostic history remains.
  Production app data was not changed or replaced.

No test code, runtime dependencies, permissions or third-party assets were added.
The existing Android-platform runtime and notices are unchanged. Samsung-specific
or ongoing non-cancellable notifications were not verified.

Two subsequent same-app modification jobs were confirmed queued. Binary signing,
Release publication, LAN delivery and production installation are deferred to the
last queued modification. The application version remains 1.0.25 until that final
release; this change is not yet deployed to the user's device.
