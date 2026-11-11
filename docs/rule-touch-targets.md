# Larger switches and separate rule editing targets

Verified 2026-11-11. Source: john3smith/NotiFilter, branch
fix/current-notification-apply-20261111, starting at
b562989f985c99dcd2e0ec6880263f13f5c11d75. Fresh origin fetch confirmed main remained
d7bd0b4aa8bf4de9ae0569a8896e964bbf426b3d and the clean local branch retained the
one approved, already-pushed immediate-application change. No synchronization
reset, stash or merge was needed.

Both global pause and per-rule switches now reserve at least 76 by 56dp for
touch. Their native Android Switch behavior, checked state, accessible labels,
focus and toggle listener are retained. Platform GradientDrawable thumb and track
assets increase the visible size without view scaling or overlapping hit targets.
The thumb is 26dp and the switch minimum width is 56dp; padding surrounds them.

Only the rule's content block and explicit Edit button open its editor. The card
itself no longer handles clicks, so outer padding, the gap next to the switch,
the action row's spare area and the gap between actions cannot open the editor.
Content and switch have an 8dp layout gap. Card bottom padding increases to 12dp.
The deletion confirmation ignores outside taps; Cancel and Back still dismiss it.
The editor already ignored outside taps, and retains that behavior.

Validation:

- Before update, a real emulator tap just outside a switch opened the editor.
  The observed baseline switch bounds were approximately 47 by 27dp.
- After update, measured switch bounds are 76 by 56dp. Manual taps in the card
  outer edge, content/switch gap, action row spare area, inter-button gap and
  bottom padding left the editor closed. A tap inside the enlarged switch edge
  changed checked state without opening the editor.
- Content tapping opened the populated editor. Cancel returned to the list.
  Delete opened confirmation; taps near its buttons and outside the dialog kept
  confirmation open. Cancel closed it without opening the editor.
- Visually checked normal size and a 320dp-wide regular API 35 emulator at 130%
  and 200% font size, including long and short phrases, both switch states,
  scrollable content and reachable action buttons. The editor's Cancel/Save
  footer remained visible at 200%. The software keyboard did not appear with
  the emulator's current hardware-keyboard configuration; IME overlap was not
  verified. TalkBack and physical/OEM devices were not tested.
- Release build and 26 existing JVM tests passed (zero failures/errors/skips).
  Release lint: zero errors and six existing warnings. No instrumentation test
  code was added or changed. The previously recorded legacy sorting UI helper
  limitation remains outside this change.
- Diagnostic preferences, emulator resolution, density and font size were
  restored. Production package/data and listener permissions were not changed.

No runtime dependency, permission, external asset or license obligation was added.
The preceding current-notification one-time application remains in the source.
Another same-app modification (notification hiding semantics) is queued, so the
version bump, signed APK, main integration, Release, LAN delivery and production
installation are deferred to the final queued modification. No new APK delivery
is claimed by this source change.
