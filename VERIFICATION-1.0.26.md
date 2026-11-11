# NotiFilter 1.0.26 — notification snoozing and touch targets

## Behavior

Matching notifications now use Android's public `snoozeNotification(key, duration)`
API with a 24-hour duration. No `cancelNotification` fallback remains in the app.
The full system key retains profile identity. A notification's update can remain
inside Android's snoozed state instead of being posted again immediately. If the
notification is posted after expiry, enabled rules apply again while unpaused.
The expiry path is implemented through the existing posted callback, not a timer
or background polling process added by this update.

Only pending requests confirmed by `REASON_SNOOZED` are newly recorded in history.
The history heading is now neutral: notification processing history. Previous
deletion history remains in the same encrypted local database, without a schema
change or relabeling those past actions as new snoozes. Request counts are not
claims that Android completed every hide.

Pause and disabled rules still prevent automatic hiding. Previously snoozed
notifications are not immediately restored when a rule is disabled or the filter
is paused; they can return at expiry. Android may process a notification group
together, and system/OEM limits and snooze capacity still apply.

This release includes the preceding two approved changes:

- Current-notification rule selection immediately applies just that new literal
  package rule once, even while paused, without resuming the automatic filter.
- Native switches have 76 by 56dp touch targets and larger thumb/track graphics.
  Rule editing is limited to content and the Edit button. Switch/action margins
  cannot open the editor. Deletion confirmation ignores outside taps.

## Verified

- Origin john3smith/NotiFilter was fetched before modification and publication.
  Main base d7bd0b4aa8bf4de9ae0569a8896e964bbf426b3d, retained approved branch
  commits b562989 and 2a94c67. Clean branch before this change, no remote divergence.
- Release build, 26 existing JVM tests (zero failures/errors/skips), and release
  lint (zero errors, six existing warnings) passed.
- Regular API 35 emulator, isolated diagnostic app: current-notification selection
  while paused moved a synthetic shell notification from active to Android's
  snoozed list. Same-key repost stayed snoozed with the same expiry. History
  contained the confirmed synthetic item.
- A separate local notification generator posted an ongoing, NO_CLEAR notification
  with a deletion PendingIntent. Selecting it while paused snoozed it. Updating
  its text with the same key did not return it to the active list. The generator's
  deletion callback count remained zero.
- A new ongoing notification remained active while paused; resuming scanned and
  snoozed it. Another new matching ongoing notification was automatically snoozed
  on posting. Disabling the rule left the next matching notification active.
- Previous job verification covered switch edge taps, inert surrounding padding,
  editing/cancel flows and 320dp layouts at 130%/200% font sizes. See
  docs/rule-touch-targets.md and docs/current-notification-apply-once.md.
- Signed production update installed over 1.0.25, preserving appId and first-install
  time. The pulled installed APK SHA-256 matches the signed release artifact.
- Signing lineage, package/version, non-debuggable status and archive credential
  exclusions passed. Certificates verified at APIs 26/28/32/33/35/36.
- APK inspection confirms snoozeNotification is present and cancelNotification
  is absent. Only application DEX/resources, platform metadata and the existing
  two privacy/notice text assets are bundled. The diagnostic generator is not
  part of the source commit or release APK. No runtime dependency, permission or
  external asset was added; existing platform-only runtime notices are retained.

Diagnostic preferences and original notification listener grants were restored.
Synthetic generator notifications were cancelled by their source after checks.
Synthetic diagnostic history and a shell snooze can remain local. No real device
or real notification content was used as the synthetic fixture.

## Limits and failed/unrun checks

- Samsung Secure Folder, cross-profile behavior on a Samsung device, actual
  foreground-service notifications and protected system notifications remain
  unverified. A synthetic ONGOING/NO_CLEAR notification is not an actual FGS.
- The 24-hour expiry was not waited out. The emulator shell unsnooze command failed
  with a system permission denial, so expiry/reappearance was not forced and is
  not claimed as runtime-tested. Automatic hiding of newly posted matching
  notifications was verified separately. No permission workaround was used.
- Legacy sorting UI instrumentation was not rerun; its previously recorded direct
  root-child helper limitation remains. No test code was added to the product.
- No second physical LAN client or reboot was used. LAN receipt verifies a full
  HTTP download and hash from this PC, not another device's network path.

## User check

Update the APK and retain notification access. While the unwanted notification is
visible, select it in Current notifications. Check it disappears and appears in
processing history. If its text changes, edit the rule to retain a common phrase.
Already hidden notifications can stay hidden until their 24-hour deadline even
after pausing; this release does not provide immediate restoration. Notification
or public content absent from Android's listener cannot be accessed by this app.

Official references:

- https://developer.android.com/reference/android/service/notification/NotificationListenerService#snoozeNotification(java.lang.String,%20long)
- https://developer.android.com/reference/android/service/notification/NotificationListenerService#REASON_SNOOZED
- https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r1/services/core/java/com/android/server/notification/NotificationManagerService.java
